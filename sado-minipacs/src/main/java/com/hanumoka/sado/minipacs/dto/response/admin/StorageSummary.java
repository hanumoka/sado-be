package com.hanumoka.sado.minipacs.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 스토리지 사용량 요약 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageSummary {

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
}
