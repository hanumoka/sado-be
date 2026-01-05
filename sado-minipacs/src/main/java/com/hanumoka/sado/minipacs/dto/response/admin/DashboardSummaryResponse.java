package com.hanumoka.sado.minipacs.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin Dashboard 전체 통계 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {

    /**
     * 총 환자 수
     */
    private Long totalPatients;

    /**
     * 총 Study 수
     */
    private Long totalStudies;

    /**
     * 총 Series 수
     */
    private Long totalSeries;

    /**
     * 총 Instance 수
     */
    private Long totalInstances;

    /**
     * 스토리지 사용량 요약
     */
    private StorageSummary storageSummary;

    /**
     * Tier 분포
     */
    private TierDistribution tierDistribution;
}
