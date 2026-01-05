package com.hanumoka.sado.minipacs.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Storage Tier 분포 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierDistribution {

    /**
     * HOT Tier 파일 사이즈 합계 (bytes)
     */
    private Long hot;

    /**
     * WARM Tier 파일 사이즈 합계 (bytes)
     */
    private Long warm;

    /**
     * COLD Tier 파일 사이즈 합계 (bytes)
     */
    private Long cold;
}
