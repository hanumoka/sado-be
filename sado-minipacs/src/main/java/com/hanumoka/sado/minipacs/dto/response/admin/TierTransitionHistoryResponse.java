package com.hanumoka.sado.minipacs.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tier 전환 이력 응답 DTO
 *
 * <p>Tier 변경 이력 조회 시 사용합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierTransitionHistoryResponse {

    /**
     * 로그 ID
     */
    private Long id;

    /**
     * FileAsset ID
     */
    private Long fileAssetId;

    /**
     * 파일 경로 (S3 Key)
     */
    private String storagePath;

    /**
     * 이전 Tier
     */
    private String fromTier;

    /**
     * 새로운 Tier
     */
    private String toTier;

    /**
     * 전환 사유
     */
    private String reason;

    /**
     * 전환 유형 (MANUAL / AUTO)
     */
    private String transitionType;

    /**
     * 작업 수행자
     */
    private String performedBy;

    /**
     * 전환 시각
     */
    private LocalDateTime transitionedAt;

    /**
     * Tenant ID
     */
    private Long tenantId;
}
