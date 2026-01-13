package com.hanumoka.sado.minipacs.domain.service;

import com.hanumoka.sado.minipacs.domain.entity.Instance;
import com.hanumoka.sado.minipacs.storage.service.DicomStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Cine Frames 서비스
 *
 * <p>Pre-rendered cine JPEG 프레임을 일괄 조회합니다.
 *
 * <p>MJPEG 무한 스트리밍의 대안으로, 클라이언트가 모든 프레임을 한 번에 다운로드하고
 * 로컬에서 캐싱하여 재생할 수 있도록 합니다.
 *
 * <p>장점:
 * <ul>
 *   <li>네트워크 요청 1회로 모든 프레임 확보</li>
 *   <li>클라이언트 사이드 캐싱으로 반복 재생 시 네트워크 부하 없음</li>
 *   <li>HTTP/1.1 동시 연결 제한 (4-6개) 우회</li>
 * </ul>
 *
 * <p>S3 경로 구조:
 * <pre>
 * {prerenderedBasePath}/cine/frame-{NNN}-{resolution}.jpg
 * 예: studies/.../instances/{sopInstanceUID}/cine/frame-001-256.jpg
 * </pre>
 *
 * @see MjpegService MJPEG 무한 스트리밍 서비스 (기존 방식)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CineFramesService {

    private final DicomStorageService dicomStorageService;

    // 지원 해상도
    private static final int[] SUPPORTED_RESOLUTIONS = {256, 128, 64, 32};
    private static final int DEFAULT_RESOLUTION = 256;

    /**
     * 모든 cine 프레임 조회 (Base64 인코딩)
     *
     * <p>Instance의 모든 pre-rendered cine JPEG 프레임을 S3에서 다운로드하고
     * Base64로 인코딩하여 반환합니다.
     *
     * @param instance Instance 엔티티
     * @param resolution 해상도 (256, 128, 64, 32)
     * @return Base64 인코딩된 JPEG 프레임 목록
     */
    public List<String> getAllFramesBase64(Instance instance, int resolution) {
        int validResolution = validateResolution(resolution);
        String basePath = instance.getPrerenderedBasePath();
        int numberOfFrames = instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1;

        log.info("[CineFrames] Fetching all frames: sopInstanceUid={}, frames={}, resolution={}",
                instance.getSopInstanceUid(), numberOfFrames, validResolution);

        long startTime = System.currentTimeMillis();
        List<String> frames = new ArrayList<>(numberOfFrames);

        for (int frameNumber = 1; frameNumber <= numberOfFrames; frameNumber++) {
            String s3Key = buildCineJpegPath(basePath, frameNumber, validResolution);
            try (InputStream jpegStream = dicomStorageService.downloadFileStream(s3Key)) {
                byte[] jpegData = jpegStream.readAllBytes();
                String base64 = Base64.getEncoder().encodeToString(jpegData);
                frames.add(base64);
            } catch (Exception e) {
                log.warn("[CineFrames] Failed to read frame {}: {}", frameNumber, e.getMessage());
                // 실패한 프레임은 빈 문자열로 대체
                frames.add("");
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[CineFrames] Fetched {} frames in {}ms: sopInstanceUid={}",
                frames.size(), elapsed, instance.getSopInstanceUid());

        return frames;
    }

    /**
     * 모든 cine 프레임 조회 (Raw Bytes)
     *
     * <p>Instance의 모든 pre-rendered cine JPEG 프레임을 S3에서 다운로드하여 반환합니다.
     *
     * @param instance Instance 엔티티
     * @param resolution 해상도 (256, 128, 64, 32)
     * @return JPEG 프레임 바이트 배열 목록
     */
    public List<byte[]> getAllFramesRaw(Instance instance, int resolution) {
        int validResolution = validateResolution(resolution);
        String basePath = instance.getPrerenderedBasePath();
        int numberOfFrames = instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1;

        log.info("[CineFrames] Fetching all frames (raw): sopInstanceUid={}, frames={}, resolution={}",
                instance.getSopInstanceUid(), numberOfFrames, validResolution);

        long startTime = System.currentTimeMillis();
        List<byte[]> frames = new ArrayList<>(numberOfFrames);

        for (int frameNumber = 1; frameNumber <= numberOfFrames; frameNumber++) {
            String s3Key = buildCineJpegPath(basePath, frameNumber, validResolution);
            try (InputStream jpegStream = dicomStorageService.downloadFileStream(s3Key)) {
                byte[] jpegData = jpegStream.readAllBytes();
                frames.add(jpegData);
            } catch (Exception e) {
                log.warn("[CineFrames] Failed to read frame {}: {}", frameNumber, e.getMessage());
                frames.add(new byte[0]);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[CineFrames] Fetched {} frames (raw) in {}ms: sopInstanceUid={}",
                frames.size(), elapsed, instance.getSopInstanceUid());

        return frames;
    }

    /**
     * Cine JPEG S3 경로 생성
     *
     * @param basePath Pre-rendered base path
     * @param frameNumber 프레임 번호 (1-based)
     * @param resolution 해상도
     * @return S3 Key
     */
    private String buildCineJpegPath(String basePath, int frameNumber, int resolution) {
        String paddedFrameNumber = String.format("%03d", frameNumber);
        return basePath + "/cine/frame-" + paddedFrameNumber + "-" + resolution + ".jpg";
    }

    /**
     * 해상도 검증
     *
     * @param resolution 요청 해상도
     * @return 유효한 해상도 (지원되지 않으면 기본값)
     */
    private int validateResolution(int resolution) {
        for (int supported : SUPPORTED_RESOLUTIONS) {
            if (resolution == supported) {
                return resolution;
            }
        }
        log.warn("[CineFrames] Invalid resolution {}, using default {}", resolution, DEFAULT_RESOLUTION);
        return DEFAULT_RESOLUTION;
    }

    /**
     * Instance가 cine 프레임 조회 가능한지 확인
     *
     * @param instance Instance 엔티티
     * @return 조회 가능 여부
     */
    public boolean isAvailable(Instance instance) {
        if (instance.getTranscodingStatus() != Instance.TranscodingStatus.COMPLETED) {
            return false;
        }
        if (instance.getPrerenderedBasePath() == null || instance.getPrerenderedBasePath().isBlank()) {
            return false;
        }
        return instance.getNumberOfFrames() != null && instance.getNumberOfFrames() >= 1;
    }
}
