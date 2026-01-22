package com.hanumoka.sado.minipacs.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Batch 프레임 조회 요청 DTO
 *
 * <p>여러 Instance의 프레임을 한 번의 요청으로 조회합니다.
 *
 * <p>요청 예시:
 * <pre>
 * {
 *   "requests": [
 *     { "instanceId": 123, "frames": [1, 2, 3] },
 *     { "instanceId": 124, "frames": [1, 2, 3, 4, 5] },
 *     { "instanceId": 125, "frames": [1] }
 *   ],
 *   "format": "rendered",
 *   "quality": 85,
 *   "resolution": 256
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Batch 프레임 조회 요청")
public class BatchFrameRequest {

    /**
     * 프레임 요청 목록
     */
    @NotEmpty(message = "requests는 비어있을 수 없습니다")
    @Valid
    @Schema(description = "프레임 요청 목록", required = true)
    private List<FrameRequestItem> requests;

    /**
     * 출력 형식
     * - "rendered": 렌더링된 이미지 (JPEG/PNG)
     * - "bulkdata": Raw Pixel Data
     */
    @Builder.Default
    @Schema(description = "출력 형식 (rendered: 이미지, bulkdata: Raw Pixel)", example = "rendered")
    private String format = "rendered";

    /**
     * 이미지 품질 (1-100, rendered 형식에서만 사용)
     */
    @Builder.Default
    @Schema(description = "이미지 품질 (1-100, rendered 형식에서만 사용)", example = "85")
    private Integer quality = 85;

    /**
     * 출력 해상도 (rendered 형식에서 사용, 정사각형 기준)
     */
    @Schema(description = "출력 해상도 (rendered 형식에서 사용)", example = "256")
    private Integer resolution;

    /**
     * 개별 Instance 프레임 요청
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "개별 Instance 프레임 요청")
    public static class FrameRequestItem {

        /**
         * Instance DB ID
         */
        @NotNull(message = "instanceId는 필수입니다")
        @Schema(description = "Instance DB ID", required = true, example = "123")
        private Long instanceId;

        /**
         * 조회할 프레임 번호 목록 (1부터 시작)
         */
        @NotEmpty(message = "frames는 비어있을 수 없습니다")
        @Schema(description = "프레임 번호 목록 (1부터 시작)", required = true, example = "[1, 2, 3]")
        private List<Integer> frames;
    }
}
