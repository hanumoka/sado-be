package com.hanumoka.sado.minipacs.domain.service;

import com.hanumoka.sado.minipacs.domain.entity.Instance;
import com.hanumoka.sado.minipacs.storage.service.DicomStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * MJPEG 스트리밍 서비스
 *
 * <p>Pre-rendered cine JPEG 프레임을 MJPEG (Motion JPEG) 형식으로 스트리밍합니다.
 *
 * <p>MJPEG 형식:
 * <pre>
 * Content-Type: multipart/x-mixed-replace; boundary=frame
 *
 * --frame
 * Content-Type: image/jpeg
 * Content-Length: 12345
 *
 * [JPEG binary data]
 * --frame
 * Content-Type: image/jpeg
 * Content-Length: 12340
 *
 * [JPEG binary data]
 * ...
 * </pre>
 *
 * <p>S3 경로 구조:
 * <pre>
 * {prerenderedBasePath}/cine/frame-{NNN}-{resolution}.jpg
 * 예: studies/.../instances/{sopInstanceUID}/cine/frame-001-256.jpg
 * </pre>
 *
 * <p>지원 해상도: 256, 128, 64, 32 (px)
 *
 * @see PreRenderingService Pre-rendering 서비스 (cine JPEG 생성)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MjpegService {

    private final DicomStorageService dicomStorageService;

    // MJPEG boundary
    private static final String BOUNDARY = "frame";
    private static final String CRLF = "\r\n";
    private static final byte[] BOUNDARY_HEADER = ("--" + BOUNDARY + CRLF).getBytes(StandardCharsets.UTF_8);
    private static final byte[] CONTENT_TYPE_HEADER = ("Content-Type: image/jpeg" + CRLF).getBytes(StandardCharsets.UTF_8);

    // 지원 해상도
    private static final int[] SUPPORTED_RESOLUTIONS = {256, 128, 64, 32};
    private static final int DEFAULT_RESOLUTION = 256;
    private static final int DEFAULT_FRAME_RATE = 30;
    private static final int MIN_FRAME_RATE = 1;
    private static final int MAX_FRAME_RATE = 60;

    /**
     * MJPEG 스트리밍 (무한 루프)
     *
     * <p>Pre-rendered cine JPEG 프레임을 MJPEG 형식으로 스트리밍합니다.
     * 마지막 프레임 후 처음부터 반복합니다 (무한 루프).
     *
     * <p>스트리밍은 클라이언트가 연결을 끊을 때까지 계속됩니다.
     *
     * @param instance Instance 엔티티 (numberOfFrames, prerenderedBasePath 필요)
     * @param resolution 해상도 (256, 128, 64, 32)
     * @param frameRate 프레임 레이트 (1-60 fps)
     * @param outputStream 출력 스트림
     * @throws IOException 스트리밍 실패 시
     */
    public void streamMjpeg(
            Instance instance,
            int resolution,
            int frameRate,
            OutputStream outputStream
    ) throws IOException {
        // 파라미터 검증
        int validResolution = validateResolution(resolution);
        int validFrameRate = validateFrameRate(frameRate);
        long frameDelayMs = 1000L / validFrameRate;

        String basePath = instance.getPrerenderedBasePath();
        int numberOfFrames = instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1;

        log.info("[MJPEG] Starting stream: sopInstanceUid={}, frames={}, resolution={}, fps={}",
                instance.getSopInstanceUid(), numberOfFrames, validResolution, validFrameRate);

        int loopCount = 0;
        try {
            // 무한 루프
            while (!Thread.currentThread().isInterrupted()) {
                loopCount++;
                log.debug("[MJPEG] Loop #{}: streaming {} frames", loopCount, numberOfFrames);

                for (int frameNumber = 1; frameNumber <= numberOfFrames; frameNumber++) {
                    // 프레임 스트리밍
                    writeFrame(basePath, frameNumber, validResolution, outputStream);

                    // 프레임 딜레이
                    if (frameDelayMs > 0) {
                        Thread.sleep(frameDelayMs);
                    }
                }
            }
        } catch (InterruptedException e) {
            log.info("[MJPEG] Stream interrupted: sopInstanceUid={}, loops={}",
                    instance.getSopInstanceUid(), loopCount);
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // 클라이언트 연결 종료 (정상적인 종료)
            log.info("[MJPEG] Client disconnected: sopInstanceUid={}, loops={}",
                    instance.getSopInstanceUid(), loopCount);
        }
    }

    /**
     * 단일 프레임 스트리밍
     *
     * <p>MJPEG boundary 형식으로 단일 JPEG 프레임을 출력합니다.
     *
     * @param basePath Pre-rendered base path
     * @param frameNumber 프레임 번호 (1-based)
     * @param resolution 해상도
     * @param outputStream 출력 스트림
     * @throws IOException 스트리밍 실패 시
     */
    private void writeFrame(
            String basePath,
            int frameNumber,
            int resolution,
            OutputStream outputStream
    ) throws IOException {
        // S3 경로 생성
        String s3Key = buildCineJpegPath(basePath, frameNumber, resolution);

        // S3에서 JPEG 다운로드 및 스트리밍
        try (InputStream jpegStream = dicomStorageService.downloadFileStream(s3Key)) {
            byte[] jpegData = jpegStream.readAllBytes();

            // MJPEG boundary 헤더
            outputStream.write(BOUNDARY_HEADER);
            outputStream.write(CONTENT_TYPE_HEADER);
            outputStream.write(("Content-Length: " + jpegData.length + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));

            // JPEG 데이터
            outputStream.write(jpegData);
            outputStream.write(CRLF.getBytes(StandardCharsets.UTF_8));

            // 즉시 전송
            outputStream.flush();
        }
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
        // 프레임 번호 3자리 패딩 (001, 002, ...)
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
        log.warn("[MJPEG] Invalid resolution {}, using default {}", resolution, DEFAULT_RESOLUTION);
        return DEFAULT_RESOLUTION;
    }

    /**
     * 프레임 레이트 검증
     *
     * @param frameRate 요청 프레임 레이트
     * @return 유효한 프레임 레이트 (범위: 1-60)
     */
    private int validateFrameRate(int frameRate) {
        if (frameRate < MIN_FRAME_RATE) {
            log.warn("[MJPEG] Frame rate {} too low, using {}", frameRate, MIN_FRAME_RATE);
            return MIN_FRAME_RATE;
        }
        if (frameRate > MAX_FRAME_RATE) {
            log.warn("[MJPEG] Frame rate {} too high, using {}", frameRate, MAX_FRAME_RATE);
            return MAX_FRAME_RATE;
        }
        return frameRate;
    }

    /**
     * MJPEG Content-Type 헤더 생성
     *
     * @return "multipart/x-mixed-replace; boundary=frame"
     */
    public static String getMjpegContentType() {
        return "multipart/x-mixed-replace; boundary=" + BOUNDARY;
    }

    /**
     * Instance가 MJPEG 스트리밍 가능한지 확인
     *
     * @param instance Instance 엔티티
     * @return 스트리밍 가능 여부
     */
    public boolean isStreamable(Instance instance) {
        // Pre-rendering 완료 확인
        if (instance.getTranscodingStatus() != Instance.TranscodingStatus.COMPLETED) {
            return false;
        }
        // Pre-rendered base path 존재 확인
        if (instance.getPrerenderedBasePath() == null || instance.getPrerenderedBasePath().isBlank()) {
            return false;
        }
        // 프레임 수 확인 (최소 1프레임)
        return instance.getNumberOfFrames() != null && instance.getNumberOfFrames() >= 1;
    }
}
