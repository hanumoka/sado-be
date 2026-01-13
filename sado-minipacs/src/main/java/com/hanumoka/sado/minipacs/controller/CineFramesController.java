package com.hanumoka.sado.minipacs.controller;

import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import com.hanumoka.sado.minipacs.domain.entity.Instance;
import com.hanumoka.sado.minipacs.domain.service.CineFramesService;
import com.hanumoka.sado.minipacs.domain.service.InstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cine Frames 컨트롤러
 *
 * <p>Pre-rendered cine JPEG 프레임을 일괄 조회하는 API입니다.
 *
 * <p>MJPEG 무한 스트리밍의 대안으로, 클라이언트가 모든 프레임을 한 번에 다운로드하고
 * 로컬에서 캐싱하여 재생할 수 있도록 합니다.
 *
 * <p>엔드포인트:
 * <ul>
 *   <li>GET /dicomweb/cine-frames/{sopInstanceUID} - 모든 프레임 조회</li>
 *   <li>GET /dicomweb/cine-frames/{sopInstanceUID}/info - 프레임 정보 조회</li>
 * </ul>
 *
 * <p>Frontend 사용법:
 * <pre>{@code
 * // 1. 모든 프레임 다운로드 (1회)
 * const response = await fetch('/dicomweb/cine-frames/{sopInstanceUID}?resolution=256');
 * const { frames } = await response.json();  // Base64 인코딩된 JPEG 배열
 *
 * // 2. 클라이언트 사이드 캐싱
 * const images = frames.map(base64 => {
 *   const img = new Image();
 *   img.src = `data:image/jpeg;base64,${base64}`;
 *   return img;
 * });
 *
 * // 3. requestAnimationFrame으로 로컬 재생 (무한 루프)
 * let frameIndex = 0;
 * function animate() {
 *   ctx.drawImage(images[frameIndex], 0, 0);
 *   frameIndex = (frameIndex + 1) % images.length;
 *   requestAnimationFrame(animate);
 * }
 * }</pre>
 *
 * @see CineFramesService Cine Frames 서비스
 * @see MjpegController MJPEG 스트리밍 컨트롤러 (기존 방식)
 */
@RestController
@RequestMapping("/dicomweb/cine-frames")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cine Frames", description = "Cine 프레임 일괄 조회 API (클라이언트 사이드 캐싱용)")
public class CineFramesController {

    private final InstanceService instanceService;
    private final CineFramesService cineFramesService;

    /**
     * 모든 cine 프레임 조회
     *
     * <p>Instance의 모든 pre-rendered cine JPEG 프레임을 Base64로 인코딩하여 반환합니다.
     *
     * <p>응답 형식:
     * <pre>{@code
     * {
     *   "sopInstanceUid": "1.2.3...",
     *   "numberOfFrames": 50,
     *   "resolution": 256,
     *   "frames": [
     *     "/9j/4AAQSkZJRg...",  // Base64 JPEG frame 1
     *     "/9j/4AAQSkZJRg...",  // Base64 JPEG frame 2
     *     ...
     *   ]
     * }
     * }</pre>
     *
     * @param sopInstanceUID SOP Instance UID
     * @param resolution 해상도 (256, 128, 64, 32) - 기본값 256
     * @return 모든 프레임 (Base64 인코딩)
     */
    @GetMapping(value = "/{sopInstanceUID}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "모든 Cine 프레임 조회",
            description = "Pre-rendered cine JPEG 프레임을 모두 조회합니다 (클라이언트 사이드 캐싱용)"
    )
    public ResponseEntity<Map<String, Object>> getAllFrames(
            @Parameter(description = "SOP Instance UID", required = true)
            @PathVariable String sopInstanceUID,

            @Parameter(description = "해상도 (256, 128, 64, 32)")
            @RequestParam(defaultValue = "256") int resolution
    ) {
        log.info("[CineFrames] Get all frames: sopInstanceUID={}, resolution={}",
                sopInstanceUID, resolution);

        // Instance 조회
        Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
                .orElseThrow(() -> new BusinessException(MiniPacsErrorCode.INSTANCE_NOT_FOUND));

        // 조회 가능 여부 확인
        if (!cineFramesService.isAvailable(instance)) {
            log.warn("[CineFrames] Instance not available: sopInstanceUID={}, status={}",
                    sopInstanceUID, instance.getTranscodingStatus());
            throw new BusinessException(MiniPacsErrorCode.TRANSCODING_NOT_COMPLETED);
        }

        // 모든 프레임 조회
        List<String> frames = cineFramesService.getAllFramesBase64(instance, resolution);

        // 응답 생성
        Map<String, Object> response = new HashMap<>();
        response.put("sopInstanceUid", instance.getSopInstanceUid());
        response.put("numberOfFrames", frames.size());
        response.put("resolution", resolution);
        response.put("frames", frames);

        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=3600")  // 1시간 캐싱
                .body(response);
    }

    /**
     * Cine 프레임 정보 조회
     *
     * <p>다운로드 전에 Instance의 메타데이터를 조회합니다.
     *
     * @param sopInstanceUID SOP Instance UID
     * @return 프레임 정보 (프레임 수, 예상 크기, 조회 가능 여부 등)
     */
    @GetMapping("/{sopInstanceUID}/info")
    @Operation(summary = "Cine 프레임 정보", description = "Instance의 cine 프레임 메타데이터를 조회합니다")
    public ResponseEntity<Map<String, Object>> getFrameInfo(
            @Parameter(description = "SOP Instance UID", required = true)
            @PathVariable String sopInstanceUID
    ) {
        // Instance 조회
        Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
                .orElseThrow(() -> new BusinessException(MiniPacsErrorCode.INSTANCE_NOT_FOUND));

        int numberOfFrames = instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1;
        double frameRate = instance.getFrameRate() != null ? instance.getFrameRate() : 30.0;

        Map<String, Object> info = new HashMap<>();
        info.put("sopInstanceUid", instance.getSopInstanceUid());
        info.put("numberOfFrames", numberOfFrames);
        info.put("frameRate", frameRate);
        info.put("transcodingStatus", instance.getTranscodingStatus());
        info.put("available", cineFramesService.isAvailable(instance));
        info.put("supportedResolutions", new int[]{256, 128, 64, 32});
        info.put("defaultResolution", 256);

        // 예상 파일 크기 (프레임당 ~15KB 가정)
        int estimatedSizePerFrame = 15 * 1024;  // 15KB
        info.put("estimatedTotalSizeBytes", numberOfFrames * estimatedSizePerFrame);
        info.put("estimatedTotalSizeKB", numberOfFrames * 15);
        info.put("estimatedTotalSizeMB", Math.round(numberOfFrames * 15.0 / 1024 * 100) / 100.0);

        // 예상 재생 시간 (초)
        info.put("estimatedDurationSeconds", numberOfFrames / frameRate);

        // API URL
        info.put("framesUrl", "/dicomweb/cine-frames/" + sopInstanceUID);

        return ResponseEntity.ok(info);
    }
}
