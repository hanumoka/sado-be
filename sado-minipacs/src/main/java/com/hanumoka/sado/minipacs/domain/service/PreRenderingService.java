package com.hanumoka.sado.minipacs.domain.service;

import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.common.tenant.TenantContext;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import com.hanumoka.sado.minipacs.domain.entity.Instance;
import com.hanumoka.sado.minipacs.domain.repository.InstanceRepository;
import com.hanumoka.sado.minipacs.dto.FrameExtractionResult;
import com.hanumoka.sado.minipacs.infrastructure.config.PreRenderingProperties;
import com.hanumoka.sado.minipacs.infrastructure.config.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DICOM 사전 렌더링 서비스
 *
 * <p>DICOM 파일 업로드 시 비동기로 사전 렌더링 데이터를 생성합니다.
 *
 * <p>생성 데이터:
 * <ul>
 *   <li>Thumbnail: 128x128 JPEG (첫 번째 프레임)</li>
 *   <li>BulkData: Raw PixelData (.raw, 각 프레임)</li>
 *   <li>Compressed: 원본 압축 유지 (.j2k/.jpg/.jls, 압축 DICOM만)</li>
 *   <li>JPEG Baseline: 원본 해상도 JPEG 90% (.jpg, 각 프레임)</li>
 *   <li>Rendered: PNG 이미지 (각 프레임)</li>
 *   <li>Cine: 다양한 해상도 JPEG (256/128/64/32px)</li>
 * </ul>
 *
 * <p>S3 저장 구조:
 * <pre>
 * {sopInstanceUID}/
 * ├── thumbnail.jpg           (128x128, 첫 프레임)
 * ├── bulkdata/
 * │   ├── frame-001.raw       (Raw PixelData, 비압축)
 * │   └── ...
 * ├── compressed/             (압축 DICOM인 경우만)
 * │   ├── frame-001.j2k       (JPEG 2000)
 * │   └── ...
 * ├── jpeg/                   (JPEG Baseline, 원본 해상도)
 * │   ├── frame-001.jpg       (90% 품질)
 * │   └── ...
 * ├── rendered/
 * │   ├── frame-001.png       (W/L 적용 PNG)
 * │   └── ...
 * └── cine/
 *     ├── frame-001-256.jpg   (256px)
 *     ├── frame-001-128.jpg   (128px)
 *     ├── frame-001-64.jpg    (64px)
 *     ├── frame-001-32.jpg    (32px)
 *     └── ...
 * </pre>
 */
@Service
@Slf4j
public class PreRenderingService {

    private final DicomRenderingService dicomRenderingService;
    private final InstanceRepository instanceRepository;
    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final PreRenderingProperties preRenderingProperties;
    private final Executor s3UploadExecutor;

    /**
     * 생성자 - S3 업로드 전용 Executor 및 설정 주입
     */
    public PreRenderingService(
            DicomRenderingService dicomRenderingService,
            InstanceRepository instanceRepository,
            S3Client s3Client,
            S3Properties s3Properties,
            PreRenderingProperties preRenderingProperties,
            @Qualifier("s3UploadExecutor") Executor s3UploadExecutor
    ) {
        this.dicomRenderingService = dicomRenderingService;
        this.instanceRepository = instanceRepository;
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
        this.preRenderingProperties = preRenderingProperties;
        this.s3UploadExecutor = s3UploadExecutor;
    }

    // 썸네일 크기
    private static final int THUMBNAIL_SIZE = 128;

    // Cine 재생용 해상도 (256px, 128px, 64px, 32px JPEG)
    private static final int CINE_SIZE_256 = 256;
    private static final int CINE_SIZE_128 = 128;
    private static final int CINE_SIZE_64 = 64;
    private static final int CINE_SIZE_32 = 32;
    private static final float CINE_JPEG_QUALITY = 0.8f;  // 80% 품질
    private static final float JPEG_BASELINE_QUALITY = 0.9f;  // 90% 품질 (권장)
    private static final int JPEG_BASELINE_QUALITY_INT = 90;  // 정수형 (Instance 저장용)

    /**
     * 사전 렌더링 결과 DTO
     */
    public record PreRenderingResult(
            String prerenderedBasePath,
            int frameCount,
            long totalSize,
            LocalDateTime completedAt,
            boolean hasCompressedBulkdata,  // 압축 BulkData 존재 여부
            boolean hasJpegBaseline,        // JPEG Baseline 존재 여부
            int jpegBaselineQuality         // JPEG Baseline 인코딩 품질 (1-100)
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

        // 사전 렌더링 전체 비활성화 체크
        if (!preRenderingProperties.isEnabled()) {
            log.info("[PreRender] Skipped (disabled in config): instanceId={}", instanceId);
            updateTranscodingStatus(instanceId, Instance.TranscodingStatus.NONE);
            return;
        }

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
        instance.setHasCompressedBulkdata(result.hasCompressedBulkdata());
        instance.setHasJpegBaseline(result.hasJpegBaseline());
        instance.setJpegBaselineQuality(result.jpegBaselineQuality());

        instanceRepository.save(instance);
        log.debug("[PreRender] Result updated: instanceId={}, basePath={}, hasCompressed={}, hasJpegBaseline={}",
                instanceId, result.prerenderedBasePath(), result.hasCompressedBulkdata(), result.hasJpegBaseline());
    }

    /**
     * 사전 렌더링 실행 (동기 - S3 업로드는 병렬)
     *
     * <p>성능 최적화 (2026-01-13):
     * <ul>
     *   <li>기존: 순차 S3 업로드 (350회 × 200ms = 70초)</li>
     *   <li>개선: 병렬 S3 업로드 (8스레드 → ~10초)</li>
     * </ul>
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

        // 병렬 업로드를 위한 Future 수집 및 사이즈 추적
        List<CompletableFuture<Long>> uploadFutures = new ArrayList<>();
        AtomicLong totalSize = new AtomicLong(0);
        boolean[] hasCompressedBulkdata = {false};  // effectively final for lambda
        boolean[] hasJpegBaseline = {false};  // JPEG Baseline 생성 여부

        try {
            // 0. Transfer Syntax 확인 (압축 DICOM 여부)
            String transferSyntaxUid = dicomRenderingService.getTransferSyntaxUid(storagePath);
            boolean isCompressedDicom = dicomRenderingService.isCompressedTransferSyntax(transferSyntaxUid);
            log.debug("[PreRender] Transfer Syntax: {}, compressed={}", transferSyntaxUid, isCompressedDicom);

            // 활성화된 렌더링 대상 로그
            log.debug("[PreRender] Enabled targets: thumbnail={}, bulkdata={}, compressed={}, jpeg={}, rendered={}, cine={}",
                    preRenderingProperties.isThumbnailEnabled(),
                    preRenderingProperties.isBulkdataEnabled(),
                    preRenderingProperties.isCompressedEnabled(),
                    preRenderingProperties.isJpegEnabled(),
                    preRenderingProperties.isRenderedEnabled(),
                    preRenderingProperties.isCineEnabled());

            // 품질 설정 가져오기
            float jpegQuality = preRenderingProperties.getJpegQualityAsFloat();
            float cineQuality = preRenderingProperties.getCineQualityAsFloat();
            PreRenderingProperties.CineConfig cineConfig = preRenderingProperties.getTargets().getCine();

            // 1. 썸네일 생성 (첫 번째 프레임, 128x128 JPEG) - 비동기 업로드
            if (preRenderingProperties.isThumbnailEnabled()) {
                CompletableFuture<Long> thumbnailFuture = generateThumbnailAsync(storagePath, basePath);
                uploadFutures.add(thumbnailFuture);
                log.debug("[PreRender] Thumbnail generation submitted");
            }

            // 2. 각 프레임에 대해 BulkData + Compressed + Rendered PNG + Cine JPEG 생성
            for (int frameNumber = 1; frameNumber <= numberOfFrames; frameNumber++) {
                final int frame = frameNumber;  // effectively final for lambda

                // 2.1 Raw PixelData 추출 및 비동기 저장
                if (preRenderingProperties.isBulkdataEnabled()) {
                    CompletableFuture<Long> rawFuture = generateBulkDataAsync(storagePath, basePath, frame);
                    uploadFutures.add(rawFuture);
                }

                // 2.2 원본 압축 데이터 저장 (압축 DICOM인 경우만, 설정 활성화 시)
                if (preRenderingProperties.isCompressedEnabled() && isCompressedDicom) {
                    CompletableFuture<Long> compressedFuture = generateCompressedBulkDataAsync(
                            storagePath, basePath, frame, transferSyntaxUid);
                    uploadFutures.add(compressedFuture.thenApply(size -> {
                        if (size > 0) {
                            hasCompressedBulkdata[0] = true;
                        }
                        return size;
                    }));
                }

                // 2.3 Rendered PNG 생성 및 비동기 저장 (512px)
                if (preRenderingProperties.isRenderedEnabled()) {
                    CompletableFuture<Long> pngFuture = generateRenderedPngAsync(storagePath, basePath, frame);
                    uploadFutures.add(pngFuture);
                }

                // 2.4 Cine JPEG 생성 (개별 해상도별 on/off 지원)
                if (preRenderingProperties.isCineEnabled()) {
                    if (cineConfig.isSize256()) {
                        uploadFutures.add(generateCineJpegAsync(storagePath, basePath, frame, CINE_SIZE_256, cineQuality));
                    }
                    if (cineConfig.isSize128()) {
                        uploadFutures.add(generateCineJpegAsync(storagePath, basePath, frame, CINE_SIZE_128, cineQuality));
                    }
                    if (cineConfig.isSize64()) {
                        uploadFutures.add(generateCineJpegAsync(storagePath, basePath, frame, CINE_SIZE_64, cineQuality));
                    }
                    if (cineConfig.isSize32()) {
                        uploadFutures.add(generateCineJpegAsync(storagePath, basePath, frame, CINE_SIZE_32, cineQuality));
                    }
                }

                // 2.5 JPEG Baseline 생성 (원본 해상도)
                if (preRenderingProperties.isJpegEnabled()) {
                    CompletableFuture<Long> jpegBaselineFuture = generateJpegBaselineAsync(
                            storagePath, basePath, frame, jpegQuality);
                    uploadFutures.add(jpegBaselineFuture.thenApply(size -> {
                        if (size > 0) {
                            hasJpegBaseline[0] = true;
                        }
                        return size;
                    }));
                }

                if (frameNumber % 10 == 0 || frameNumber == numberOfFrames) {
                    log.debug("[PreRender] Progress: {}/{} frames submitted (pending uploads: {})",
                            frameNumber, numberOfFrames, uploadFutures.size());
                }
            }

            // 3. 모든 업로드 완료 대기 및 사이즈 합산
            log.info("[PreRender] Waiting for {} uploads to complete...", uploadFutures.size());
            long startWait = System.currentTimeMillis();

            CompletableFuture<Void> allUploads = CompletableFuture.allOf(
                    uploadFutures.toArray(new CompletableFuture[0]));

            // 각 Future의 결과(사이즈)를 합산
            allUploads.join();  // 모든 업로드 완료 대기

            for (CompletableFuture<Long> future : uploadFutures) {
                try {
                    totalSize.addAndGet(future.get());
                } catch (Exception e) {
                    log.warn("[PreRender] Failed to get upload size: {}", e.getMessage());
                }
            }

            long uploadDuration = System.currentTimeMillis() - startWait;
            log.info("[PreRender] All uploads completed in {} ms", uploadDuration);

            LocalDateTime completedAt = LocalDateTime.now();

            log.info("[PreRender] Completed: basePath={}, frames={}, totalSize={} bytes, hasCompressed={}, hasJpegBaseline={}, uploads={}",
                    basePath, numberOfFrames, totalSize.get(), hasCompressedBulkdata[0], hasJpegBaseline[0], uploadFutures.size());

            return new PreRenderingResult(
                    basePath,
                    numberOfFrames,
                    totalSize.get(),
                    completedAt,
                    hasCompressedBulkdata[0],
                    hasJpegBaseline[0],
                    preRenderingProperties.getTargets().getJpeg().getQuality()
            );

        } catch (Exception e) {
            log.error("[PreRender] Failed during execution: sopInstanceUid={}", sopInstanceUid, e);
            throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                    "Pre-rendering failed: " + e.getMessage());
        }
    }

    /**
     * 사전 렌더링 기본 경로 생성
     *
     * <p>구조: tenant-{tenantId}/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/
     */
    private String buildPrerenderedBasePath(String studyUid, String seriesUid, String sopInstanceUid) {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            tenantId = 1L; // 기본 테넌트
        }
        return String.format("tenant-%d/studies/%s/series/%s/instances/%s",
                tenantId, studyUid, seriesUid, sopInstanceUid);
    }

    /**
     * 썸네일 생성 (128x128 JPEG, 첫 번째 프레임) - 비동기 버전
     *
     * @return CompletableFuture<Long> - 저장된 썸네일 파일 크기 (bytes)
     */
    private CompletableFuture<Long> generateThumbnailAsync(String storagePath, String basePath) {
        return CompletableFuture.supplyAsync(() -> {
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
                return (long) jpegBytes.length;

            } catch (IOException e) {
                throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                        "Thumbnail generation failed: " + e.getMessage());
            }
        }, s3UploadExecutor);
    }

    /**
     * BulkData (Raw PixelData) 생성 - 비동기 버전
     *
     * @return CompletableFuture<Long> - 저장된 raw 파일 크기 (bytes)
     */
    private CompletableFuture<Long> generateBulkDataAsync(String storagePath, String basePath, int frameNumber) {
        return CompletableFuture.supplyAsync(() -> {
            // Raw PixelData 추출
            byte[] rawPixels = dicomRenderingService.extractFramePixelData(storagePath, frameNumber);

            // S3에 업로드
            String s3Key = String.format("%s/bulkdata/frame-%03d.raw", basePath, frameNumber);
            uploadToS3(s3Key, rawPixels, "application/octet-stream");

            return (long) rawPixels.length;
        }, s3UploadExecutor);
    }

    /**
     * 원본 압축 BulkData 생성 (JPEG 2000, JPEG, JPEG-LS 등) - 비동기 버전
     *
     * <p>압축된 DICOM 파일에서 원본 압축 데이터를 추출하여 저장합니다.
     *
     * @return CompletableFuture<Long> - 저장된 압축 파일 크기 (bytes), 0이면 저장 안 함
     */
    private CompletableFuture<Long> generateCompressedBulkDataAsync(
            String storagePath, String basePath, int frameNumber, String transferSyntaxUid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 원본 압축 데이터 추출 (디코딩 없이)
                FrameExtractionResult result = dicomRenderingService.extractFrameDataPreservingTransferSyntax(
                        storagePath, frameNumber, true);

                // 압축되지 않은 경우 스킵
                if (!result.isCompressed()) {
                    log.debug("[PreRender] Frame {} is not compressed, skipping compressed bulkdata", frameNumber);
                    return 0L;
                }

                // 파일 확장자 결정
                String extension = getExtensionForMimeType(result.mimeType());

                // S3에 업로드
                String s3Key = String.format("%s/compressed/frame-%03d.%s", basePath, frameNumber, extension);
                uploadToS3(s3Key, result.data(), result.mimeType());

                if (frameNumber == 1) {
                    log.debug("[PreRender] Compressed bulkdata generated: s3Key={}, size={} bytes, mime={}",
                            s3Key, result.dataSize(), result.mimeType());
                }

                return (long) result.dataSize();

            } catch (Exception e) {
                log.warn("[PreRender] Failed to generate compressed bulkdata for frame {}: {}",
                        frameNumber, e.getMessage());
                return 0L;  // 실패 시 0 반환 (RAW는 있으므로 진행)
            }
        }, s3UploadExecutor);
    }

    /**
     * MIME 타입에 따른 파일 확장자 반환
     */
    private String getExtensionForMimeType(String mimeType) {
        return switch (mimeType) {
            case "image/jp2" -> "j2k";
            case "image/jpeg" -> "jpg";
            case "image/jls" -> "jls";
            default -> "bin";
        };
    }

    /**
     * Rendered PNG 생성 - 비동기 버전
     *
     * @return CompletableFuture<Long> - 저장된 PNG 파일 크기 (bytes)
     */
    private CompletableFuture<Long> generateRenderedPngAsync(String storagePath, String basePath, int frameNumber) {
        return CompletableFuture.supplyAsync(() -> {
            // PNG 렌더링
            byte[] pngBytes = dicomRenderingService.renderToPng(storagePath, frameNumber);

            // S3에 업로드
            String s3Key = String.format("%s/rendered/frame-%03d.png", basePath, frameNumber);
            uploadToS3(s3Key, pngBytes, "image/png");

            return (long) pngBytes.length;
        }, s3UploadExecutor);
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

    /**
     * Cine 재생용 JPEG 생성 - 비동기 버전
     *
     * <p>S3 저장 경로: {basePath}/cine/frame-{frameNumber}-{size}.jpg
     *
     * @return CompletableFuture<Long> - 저장된 JPEG 파일 크기 (bytes)
     */
    /**
     * Cine JPEG 생성 (기본 품질 사용) - 하위 호환
     */
    private CompletableFuture<Long> generateCineJpegAsync(String storagePath, String basePath, int frameNumber, int size) {
        return generateCineJpegAsync(storagePath, basePath, frameNumber, size, CINE_JPEG_QUALITY);
    }

    /**
     * Cine JPEG 생성 (품질 지정)
     */
    private CompletableFuture<Long> generateCineJpegAsync(String storagePath, String basePath, int frameNumber, int size, float quality) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. DICOM에서 PNG 렌더링
                byte[] pngBytes = dicomRenderingService.renderToPng(storagePath, frameNumber);

                // 2. BufferedImage로 변환
                BufferedImage original = ImageIO.read(new ByteArrayInputStream(pngBytes));
                if (original == null) {
                    throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                            "Failed to read PNG as BufferedImage for cine frame " + frameNumber);
                }

                // 3. 리사이징 (정사각형 타겟)
                BufferedImage resized = resizeImage(original, size, size);

                // 4. JPEG로 인코딩 (지정된 품질)
                byte[] jpegBytes = encodeToJpeg(resized, quality);

                // 5. S3에 업로드
                String s3Key = String.format("%s/cine/frame-%03d-%d.jpg", basePath, frameNumber, size);
                uploadToS3(s3Key, jpegBytes, "image/jpeg");

                return (long) jpegBytes.length;
            } catch (IOException e) {
                throw new BusinessException(MiniPacsErrorCode.DICOM_RENDER_FAILED,
                        "Cine JPEG generation failed for frame " + frameNumber + ": " + e.getMessage());
            }
        }, s3UploadExecutor);
    }

    /**
     * JPEG Baseline 생성 (원본 해상도, W/L 미적용 선형 스케일링) - 비동기 버전
     *
     * <p>WADO-RS BulkData API에서 JPEG Baseline 형식으로 서빙하기 위해
     * 원본 해상도의 JPEG를 생성합니다.
     *
     * <p>변환 과정:
     * <ol>
     *   <li>원본 DICOM에서 raw pixel 추출 (W/L 미적용)</li>
     *   <li>16-bit → 8-bit 선형 스케일링</li>
     *   <li>JPEG Baseline으로 인코딩</li>
     * </ol>
     *
     * <p>클라이언트에서 8-bit 범위 내에서 W/L 조절이 가능합니다.
     *
     * <p>S3 저장 경로: {basePath}/jpeg/frame-{frameNumber}.jpg
     *
     * @param storagePath 원본 DICOM 파일 S3 경로
     * @param basePath 사전 렌더링 기본 경로
     * @param frameNumber 프레임 번호 (1-based)
     * @param quality JPEG 품질 (0.0f ~ 1.0f, 0.9f = 90%)
     * @return CompletableFuture<Long> - 저장된 JPEG 파일 크기 (bytes)
     */
    private CompletableFuture<Long> generateJpegBaselineAsync(
            String storagePath, String basePath, int frameNumber, float quality) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // JPEG Baseline 변환 (W/L 미적용, 선형 스케일링)
                // dicomRenderingService.transcodeToJpegBaseline()이 다음을 수행:
                // 1. raw pixel 추출 (readRaster, W/L 미적용)
                // 2. 16-bit → 8-bit 선형 스케일링
                // 3. JPEG Baseline 인코딩
                byte[] jpegBytes = dicomRenderingService.transcodeToJpegBaseline(storagePath, frameNumber, quality);

                // S3에 업로드
                String s3Key = String.format("%s/jpeg/frame-%03d.jpg", basePath, frameNumber);
                uploadToS3(s3Key, jpegBytes, "image/jpeg");

                if (frameNumber == 1) {
                    log.debug("[PreRender] JPEG Baseline generated (W/L-free): s3Key={}, size={} bytes, quality={}%",
                            s3Key, jpegBytes.length, (int)(quality * 100));
                }

                return (long) jpegBytes.length;

            } catch (Exception e) {
                log.warn("[PreRender] Failed to generate JPEG Baseline for frame {}: {}",
                        frameNumber, e.getMessage());
                return 0L;  // 실패 시 0 반환 (다른 포맷은 있으므로 진행)
            }
        }, s3UploadExecutor);
    }

    /**
     * BufferedImage를 지정된 품질의 JPEG 바이트 배열로 변환
     *
     * @param image 원본 이미지
     * @param quality JPEG 품질 (0.0f ~ 1.0f)
     * @return JPEG 바이트 배열
     */
    private byte[] encodeToJpeg(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // JPEG Writer 획득
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writer available");
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        // 압축 품질 설정
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        // 출력 스트림 설정
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);

            // RGB로 변환 (JPEG는 알파 채널 미지원)
            BufferedImage rgbImage = convertToRgb(image);

            writer.write(null, new IIOImage(rgbImage, null, null), param);
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }

    /**
     * 이미지를 RGB 포맷으로 변환 (알파 채널 제거)
     */
    private BufferedImage convertToRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }

        BufferedImage rgbImage = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g = rgbImage.createGraphics();
        g.setColor(Color.WHITE);  // 배경색 (투명 영역 대체)
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();

        return rgbImage;
    }
}
