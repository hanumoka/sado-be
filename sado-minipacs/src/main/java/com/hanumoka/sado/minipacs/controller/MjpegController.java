package com.hanumoka.sado.minipacs.controller;

import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import com.hanumoka.sado.minipacs.domain.entity.Instance;
import com.hanumoka.sado.minipacs.domain.service.InstanceService;
import com.hanumoka.sado.minipacs.domain.service.MjpegService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.HashMap;
import java.util.Map;

/**
 * MJPEG 스트리밍 컨트롤러
 *
 * <p>Pre-rendered cine JPEG 프레임을 MJPEG (Motion JPEG) 형식으로 스트리밍합니다.
 *
 * <p>엔드포인트:
 * <ul>
 *   <li>GET /dicomweb/mjpeg/{sopInstanceUID} - MJPEG 스트리밍</li>
 *   <li>GET /dicomweb/mjpeg/{sopInstanceUID}/info - 스트리밍 정보 조회</li>
 * </ul>
 *
 * <p>Frontend 사용법:
 * <pre>{@code
 * <img src="http://localhost:10201/dicomweb/mjpeg/{sopInstanceUID}?resolution=256&frameRate=30" />
 * }</pre>
 *
 * <p>브라우저가 multipart/x-mixed-replace Content-Type을 자동으로 처리하여
 * 연속적인 이미지 스트림을 표시합니다.
 *
 * @see MjpegService MJPEG 스트리밍 서비스
 */
@RestController
@RequestMapping("/dicomweb/mjpeg")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "MJPEG", description = "MJPEG 스트리밍 API (POC)")
public class MjpegController {

    private final InstanceService instanceService;
    private final MjpegService mjpegService;

    /**
     * MJPEG 스트리밍
     *
     * <p>Pre-rendered cine JPEG 프레임을 MJPEG 형식으로 스트리밍합니다.
     * 무한 루프로 재생되며, 클라이언트가 연결을 끊을 때까지 계속됩니다.
     *
     * <p>요구사항:
     * <ul>
     *   <li>Instance의 TranscodingStatus가 COMPLETED여야 함</li>
     *   <li>Pre-rendered cine JPEG 파일이 존재해야 함</li>
     * </ul>
     *
     * @param sopInstanceUID SOP Instance UID
     * @param resolution 해상도 (256, 128, 64, 32) - 기본값 256
     * @param frameRate 프레임 레이트 (1-60 fps) - 기본값 30
     * @return MJPEG 스트리밍 응답
     */
    @GetMapping(value = "/{sopInstanceUID}", produces = "multipart/x-mixed-replace; boundary=frame")
    @Operation(summary = "MJPEG 스트리밍", description = "Pre-rendered cine JPEG를 MJPEG 형식으로 스트리밍합니다 (무한 루프)")
    public ResponseEntity<StreamingResponseBody> streamMjpeg(
            @Parameter(description = "SOP Instance UID", required = true)
            @PathVariable String sopInstanceUID,

            @Parameter(description = "해상도 (256, 128, 64, 32)")
            @RequestParam(defaultValue = "256") int resolution,

            @Parameter(description = "프레임 레이트 (1-60 fps)")
            @RequestParam(defaultValue = "30") int frameRate
    ) {
        log.info("[MJPEG] Stream request: sopInstanceUID={}, resolution={}, frameRate={}",
                sopInstanceUID, resolution, frameRate);

        // Instance 조회
        Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
                .orElseThrow(() -> new BusinessException(MiniPacsErrorCode.INSTANCE_NOT_FOUND));

        // 스트리밍 가능 여부 확인
        if (!mjpegService.isStreamable(instance)) {
            log.warn("[MJPEG] Instance not streamable: sopInstanceUID={}, status={}",
                    sopInstanceUID, instance.getTranscodingStatus());
            throw new BusinessException(MiniPacsErrorCode.TRANSCODING_NOT_COMPLETED);
        }

        // 스트리밍 응답 생성
        StreamingResponseBody responseBody = outputStream -> {
            mjpegService.streamMjpeg(instance, resolution, frameRate, outputStream);
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(MjpegService.getMjpegContentType()))
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .header("Connection", "keep-alive")
                .header("X-Accel-Buffering", "no")  // nginx 프록시 버퍼링 비활성화
                .body(responseBody);
    }

    /**
     * MJPEG 스트리밍 정보 조회
     *
     * <p>스트리밍 전에 Instance의 메타데이터를 조회합니다.
     *
     * @param sopInstanceUID SOP Instance UID
     * @return 스트리밍 정보 (프레임 수, 기본 FPS, 스트리밍 가능 여부 등)
     */
    @GetMapping("/{sopInstanceUID}/info")
    @Operation(summary = "MJPEG 스트리밍 정보", description = "Instance의 스트리밍 메타데이터를 조회합니다")
    public ResponseEntity<Map<String, Object>> getStreamInfo(
            @Parameter(description = "SOP Instance UID", required = true)
            @PathVariable String sopInstanceUID
    ) {
        // Instance 조회
        Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
                .orElseThrow(() -> new BusinessException(MiniPacsErrorCode.INSTANCE_NOT_FOUND));

        Map<String, Object> info = new HashMap<>();
        info.put("sopInstanceUid", instance.getSopInstanceUid());
        info.put("numberOfFrames", instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1);
        info.put("frameRate", instance.getFrameRate() != null ? instance.getFrameRate() : 30.0);
        info.put("transcodingStatus", instance.getTranscodingStatus());
        info.put("streamable", mjpegService.isStreamable(instance));
        info.put("supportedResolutions", new int[]{256, 128, 64, 32});
        info.put("defaultResolution", 256);
        info.put("defaultFrameRate", 30);

        // 스트리밍 URL 생성
        String streamUrl = "/dicomweb/mjpeg/" + sopInstanceUID;
        info.put("streamUrl", streamUrl);

        // 예상 재생 시간 (초)
        int frames = instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1;
        double fps = instance.getFrameRate() != null ? instance.getFrameRate() : 30.0;
        info.put("estimatedDurationSeconds", frames / fps);

        return ResponseEntity.ok(info);
    }
}
