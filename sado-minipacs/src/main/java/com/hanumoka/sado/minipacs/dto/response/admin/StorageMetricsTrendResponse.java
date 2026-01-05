package com.hanumoka.sado.minipacs.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 스토리지 메트릭 트렌드 응답 DTO
 *
 * <p>시계열 차트용 데이터 포인트를 제공합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageMetricsTrendResponse {

    /**
     * 스냅샷 시각 (X축)
     */
    private LocalDateTime timestamp;

    /**
     * 전체 스토리지 사용량 (bytes)
     */
    private Long totalSize;

    /**
     * HOT Tier 사용량 (bytes)
     */
    private Long hotSize;

    /**
     * WARM Tier 사용량 (bytes)
     */
    private Long warmSize;

    /**
     * COLD Tier 사용량 (bytes)
     */
    private Long coldSize;

    /**
     * 전체 파일 개수
     */
    private Long fileCount;

    /**
     * HOT Tier 파일 개수
     */
    private Long hotFileCount;

    /**
     * WARM Tier 파일 개수
     */
    private Long warmFileCount;

    /**
     * COLD Tier 파일 개수
     */
    private Long coldFileCount;
}
