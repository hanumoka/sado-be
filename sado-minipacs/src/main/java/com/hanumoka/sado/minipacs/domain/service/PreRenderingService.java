package com.hanumoka.sado.minipacs.domain.service;

import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import com.hanumoka.sado.minipacs.domain.entity.Instance;
import com.hanumoka.sado.minipacs.domain.repository.InstanceRepository;
import com.hanumoka.sado.minipacs.infrastructure.config.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * DICOM 사전 렌더링 서비스
 *
 * <p>DICOM 파일 업로드 시 비동기로 사전 렌더링 데이터를 생성합니다.
 *
 * <p>생성 데이터:
 * <ul>
 *   <li>Thumbnail: 128x128 JPEG (첫 번째 프레임)</li>
 *   <li>BulkData: Raw PixelData (.raw, 각 프레임)</li>
 *   <li>Rendered: PNG 이미지 (각 프레임)</li>
 * </ul>
 *
 * <p>S3 저장 구조:
 * <pre>
 * {sopInstanceUID}/
 * ├── thumbnail.jpg           (128x128, 첫 프레임)
 * ├── bulkdata/
 * │   ├── frame-001.raw
 * │   ├── frame-002.raw
 * │   └── ...
 * └── rendered/
 *     ├── frame-001.png
 *     ├── frame-002.png
 *     └── ...
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PreRenderingService {

    private final DicomRenderingService dicomRenderingService;
    private final InstanceRepository instanceRepository;
    private final S3Client s3Client;
    private final S3Properties s3Properties;

    // 썸네일 크기
    private static final int THUMBNAIL_SIZE = 128;

    /**
     * 사전 렌더링 결과 DTO
     */
    public record PreRenderingResult(
            String prerenderedBasePath,
            int frameCount,
            long totalSize,
            LocalDateTime completedAt
    ) {}

    /**
     * Instance에 대한 사전 렌더링 수행 (비동기)
     *
     * <p>비동기로 실행되어 업로드 응답에 영향을 주지 않습니다.
     * 완료 후 Instance 엔티티를 업데이트합니다.
     *
     * @param instanceId Instance ID (PK)
     * @param studyInstanceUid Study Instance UID
     * @param seriesInstanceUid Series Instance UID
     */
    @Async("preRenderingExecutor")
    public void preRenderAsync(Long instanceId, String studyInstanceUid, String seriesInstanceUid) {
        log.info("[PreRender] Starting async pre-rendering: instanceId={}", instanceId);

        try {
            // 1. 상태를 PROCESSING으로 변경
            updateTranscodingStatus(instanceId, Instance.TranscodingStatus.PROCESSING);

            // 2. Instance 조회 (최신 상태)
            Instance instance = instanceRepository.findById(instanceId)
                    .orElseThrow(() -> new IllegalStateException("Instance not found: " + instanceId));

            String sopInstanceUid = instance.getSopInstanceUid();
            String storagePath = instance.getStoragePath();
            int numberOfFrames = instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1;

            log.info("[PreRender] Processing: sopInstanceUid={}, frames={}", sopInstanceUid, numberOfFrames);

            // 3. 사전 렌더링 실행
            PreRenderingResult result = executePreRendering(
                    storagePath,
                    studyInstanceUid,
                    seriesInstanceUid,
                    sopInstanceUid,
                    numberOfFrames
            );

            // 4. Instance 업데이트 (성공)
            updatePreRenderingResult(instanceId, result);

            log.info("[PreRender] Completed: sopInstanceUid={}, basePath={}, frames={}, totalSize={} bytes",
                    sopInstanceUid, result.prerenderedBasePath(), result.frameCount(), result.totalSize());

        } catch (Exception e) {
            log.error("[PreRender] Failed: instanceId={}", instanceId, e);
            // 상태를 FAILED로 변경
            try {
                updateTranscodingStatus(instanceId, Instance.TranscodingStatus.FAILED);
            } catch (Exception updateEx) {
                log.error("[PreRender] Failed to update status to FAILED: instanceId={}", instanceId, updateEx);
            }
        }
    }

    /**
     * Instance 트랜스코딩 상태 업데이트
     */
    @Transactional
    public void updateTranscodingStatus(Long instanceId, Instance.TranscodingStatus status) {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalStateException("Instance not found: " + instanceId));
        instance.setTranscodingStatus(status);
        instanceRepository.save(instance);
        log.debug("[PreRender] Status updated: instanceId={}, status={}", instanceId, status);
    }

    /**
     * 사전 렌더링 결과로 Instance 업데이트
     */
    @Transactional
    public void updatePreRenderingResult(Long instanceId, PreRenderingResult result) {
        Instance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalStateException("Instance not found: " + instanceId));

        instance.setTranscodingStatus(Instance.TranscodingStatus.COMPLETED);
        instance.setPrerenderedBasePath(result.prerenderedBasePath());
        instance.setPrerenderedFrameCount(result.frameCount());
        instance.setPrerenderedTotalSize(result.totalSize());
        instance.setPrerenderedAt(result.completedAt());
        instance.setThumbnailPath(result.prerenderedBasePath() + "/thumbnail.jpg");

        instanceRepository.save(instance);
        log.debug("[PreRender] Result updated: instanceId={}, basePath={}", instanceId, result.prerenderedBasePath());
    }

    /**
     * 사전 렌더링 실행 (동기)
     *
     * <p>테스트 또는 동기 실행이 필요한 경우 직접 호출합니다.
     *
     * @param storagePath 원본 DICOM 파일 S3 경로
     * @param studyInstanceUid Study Instance UID
     * @param seriesInstanceUid Series Instance UID
     * @param sopInstanceUid SOP Instance UID
     * @param numberOfFrames 프레임 수
     * @return PreRenderingResult 결과
     */
    public PreRenderingResult executePreRendering(
            String storagePath,
            String studyInstanceUid,
            String seriesInstanceUid,
            String sopInstanceUid,
            int numberOfFrames
    ) {
        log.info("[PreRender] Executing: storagePath={}, sopInstanceUid={}, frames={}",
                storagePath, sopInstanceUid, numberOfFrames);

        // 기본 경로 생성
        String basePath = buildPrerenderedBasePath(studyInstanceUid, seriesInstanceUid, sopInstanceUid);
        long totalSize = 0;

        try {
            // 1. 썸네일 생성 (첫 번째 프레임, 128x128 JPEG)
            long thumbnailSize = generateThumbnail(storagePath, basePath);
            totalSize += thumbnailSize;
            log.debug("[PreRender] Thumbnail generated: {} bytes", thumbnailSize);

            // 2. 각 프레임에 대해 BulkData + Rendered PNG 생성
            for (int frameNumber = 1; frameNumber <= numberOfFrames; frameNumber++) {
                // 2.1 Raw PixelData 추출 및 저장
                long rawSize = generateBulkData(storagePath, basePath, frameNumber);
                totalSize += rawSize;

                // 2.2 Rendered PNG 생성 및 저장
                long pngSize = generateRenderedPng(storagePath, basePath, frameNumber);
                totalSize += pngSize;

                if (frameNumber % 10 == 0 || frameNumber == numberOfFrames) {
                    log.debug("[PreRender] Progress: {}/{} frames processed", frameNumber, numberOfFrames);
                }
            }

            LocalDateTime completedAt = LocalDateTime.now();

            log.info("[PreRender] Completed: basePath={}, frames={}, totalSize={} bytes",
                    basePath, numberOfFrames, totalSize);

            return new PreRenderingResult(basePath, numberOfFrames, totalSize, completedAt);

        } catch (Exception e) {
            log.error("[PreRender] Failed during execution: sopInstanceUid={}", sopInstanceUid, e);
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                    "Pre-rendering failed: " + e.getMessage());
        }
    }

    /**
     * 사전 렌더링 기본 경로 생성
     *
     * <p>구조: studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/
     */
    private String buildPrerenderedBasePath(String studyUid, String seriesUid, String sopInstanceUid) {
        return String.format("studies/%s/series/%s/instances/%s",
                studyUid, seriesUid, sopInstanceUid);
    }

    /**
     * 썸네일 생성 (128x128 JPEG, 첫 번째 프레임)
     *
     * @return 저장된 썸네일 파일 크기 (bytes)
     */
    private long generateThumbnail(String storagePath, String basePath) {
        log.debug("[PreRender] Generating thumbnail: basePath={}", basePath);

        try {
            // 첫 번째 프레임을 PNG로 렌더링 후 리사이징
            byte[] pngBytes = dicomRenderingService.renderToPng(storagePath, 1);

            // PNG 바이트를 BufferedImage로 변환
            BufferedImage original = ImageIO.read(new java.io.ByteArrayInputStream(pngBytes));
            if (original == null) {
                throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                        "Failed to read rendered PNG as BufferedImage");
            }

            // 리사이징 (128x128, 비율 유지)
            BufferedImage thumbnail = resizeImage(original, THUMBNAIL_SIZE, THUMBNAIL_SIZE);

            // JPEG로 인코딩
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean written = ImageIO.write(thumbnail, "JPEG", baos);
            if (!written) {
                throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                        "Failed to encode thumbnail as JPEG");
            }

            byte[] jpegBytes = baos.toByteArray();

            // S3에 업로드
            String s3Key = basePath + "/thumbnail.jpg";
            uploadToS3(s3Key, jpegBytes, "image/jpeg");

            log.debug("[PreRender] Thumbnail saved: s3Key={}, size={} bytes", s3Key, jpegBytes.length);
            return jpegBytes.length;

        } catch (IOException e) {
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                    "Thumbnail generation failed: " + e.getMessage());
        }
    }

    /**
     * BulkData (Raw PixelData) 생성
     *
     * @return 저장된 raw 파일 크기 (bytes)
     */
    private long generateBulkData(String storagePath, String basePath, int frameNumber) {
        // Raw PixelData 추출
        byte[] rawPixels = dicomRenderingService.extractFramePixelData(storagePath, frameNumber);

        // S3에 업로드
        String s3Key = String.format("%s/bulkdata/frame-%03d.raw", basePath, frameNumber);
        uploadToS3(s3Key, rawPixels, "application/octet-stream");

        return rawPixels.length;
    }

    /**
     * Rendered PNG 생성
     *
     * @return 저장된 PNG 파일 크기 (bytes)
     */
    private long generateRenderedPng(String storagePath, String basePath, int frameNumber) {
        // PNG 렌더링
        byte[] pngBytes = dicomRenderingService.renderToPng(storagePath, frameNumber);

        // S3에 업로드
        String s3Key = String.format("%s/rendered/frame-%03d.png", basePath, frameNumber);
        uploadToS3(s3Key, pngBytes, "image/png");

        return pngBytes.length;
    }

    /**
     * S3에 파일 업로드
     */
    private void uploadToS3(String s3Key, byte[] data, String contentType) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(s3Key)
                    .contentType(contentType)
                    .contentLength((long) data.length)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(data));

        } catch (S3Exception e) {
            log.error("[PreRender] S3 upload failed: s3Key={}", s3Key, e);
            throw new BusinessException(MiniPacsErrorCode.STORAGE_UPLOAD_FAILED,
                    "Pre-rendered file upload failed: " + s3Key);
        }
    }

    /**
     * 이미지 리사이징 (비율 유지)
     */
    private BufferedImage resizeImage(BufferedImage original, int targetWidth, int targetHeight) {
        double widthRatio = (double) targetWidth / original.getWidth();
        double heightRatio = (double) targetHeight / original.getHeight();
        double ratio = Math.min(widthRatio, heightRatio);

        // 원본이 목표보다 작으면 리사이징 하지 않음
        if (ratio >= 1.0) {
            return original;
        }

        int newWidth = Math.max(1, (int) (original.getWidth() * ratio));
        int newHeight = Math.max(1, (int) (original.getHeight() * ratio));

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return resized;
    }
}
