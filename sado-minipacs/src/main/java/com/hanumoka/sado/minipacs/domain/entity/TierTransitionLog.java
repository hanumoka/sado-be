package com.hanumoka.sado.minipacs.domain.entity;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Tier 전환 이력 엔티티
 *
 * <p>모든 Storage Tier 변경(HOT/WARM/COLD)을 추적하여 감사 로그 및 비용 분석에 활용합니다.
 *
 * <p>주요 용도:
 * <ul>
 *   <li>HIPAA/GDPR 준수: 모든 데이터 이동 추적</li>
 *   <li>비용 분석: Tier별 데이터 이동 패턴 분석</li>
 *   <li>정책 검증: 자동 Tier 전환 정책의 효과 측정</li>
 *   <li>트러블슈팅: 비정상적인 Tier 변경 추적</li>
 * </ul>
 */
@Entity
@Table(name = "tier_transition_log", indexes = {
    @Index(name = "idx_tier_transition_file_asset", columnList = "file_asset_id"),
    @Index(name = "idx_tier_transition_transitioned_at", columnList = "transitioned_at"),
    @Index(name = "idx_tier_transition_type", columnList = "transition_type")
})
@Getter
@Setter
@NoArgsConstructor
public class TierTransitionLog extends TenantAwareEntity {

    /**
     * FileAsset ID (FK)
     * 어떤 파일이 Tier를 변경했는지 추적
     *
     * @deprecated 직접 ID 접근 대신 {@link #fileAsset} 관계 사용 권장
     */
    @Deprecated
    @Column(name = "file_asset_id", nullable = false, insertable = false, updatable = false)
    private Long fileAssetId;

    /**
     * FileAsset 관계 (N+1 방지용)
     *
     * <p>TierTransitionLog 조회 시 FileAsset 정보가 필요한 경우:
     * <ul>
     *   <li>✅ JOIN FETCH로 eager loading</li>
     *   <li>❌ lazy loading 후 반복 조회 (N+1 문제)</li>
     * </ul>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_asset_id", nullable = false)
    private FileAsset fileAsset;

    /**
     * 이전 Tier (HOT/WARM/COLD)
     * nullable: 최초 업로드 시 fromTier는 null
     */
    @Column(name = "from_tier", length = 16)
    private String fromTier;

    /**
     * 새로운 Tier (HOT/WARM/COLD)
     */
    @Column(name = "to_tier", length = 16, nullable = false)
    private String toTier;

    /**
     * 전환 사유
     * 예: "Auto transition after 90 days", "Manual cost optimization", "Restore from archive"
     */
    @Column(name = "reason", length = 512)
    private String reason;

    /**
     * 전환 유형 (MANUAL / AUTO)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transition_type", length = 16, nullable = false)
    private TransitionType transitionType;

    /**
     * 작업 수행자 (User ID or System)
     * 예: "admin@hospital.com" (수동), "SYSTEM" (자동)
     */
    @Column(name = "performed_by", length = 128)
    private String performedBy;

    /**
     * 전환 시각
     */
    @Column(name = "transitioned_at", nullable = false)
    private LocalDateTime transitionedAt;

    // ========== Enum 정의 ==========

    /**
     * Tier 전환 유형
     */
    public enum TransitionType {
        /**
         * 수동 전환 (관리자가 직접 실행)
         */
        MANUAL,

        /**
         * 자동 전환 (스케줄러에 의한 정책 기반 전환)
         */
        AUTO
    }

    // ========== Builder Pattern ==========

    public static class Builder {
        private final TierTransitionLog log = new TierTransitionLog();

        /**
         * @deprecated {@link #fileAsset(FileAsset)} 사용 권장
         */
        @Deprecated
        public Builder fileAssetId(Long fileAssetId) {
            log.fileAssetId = fileAssetId;
            return this;
        }

        public Builder fileAsset(FileAsset fileAsset) {
            log.fileAsset = fileAsset;
            return this;
        }

        public Builder fromTier(String fromTier) {
            log.fromTier = fromTier;
            return this;
        }

        public Builder toTier(String toTier) {
            log.toTier = toTier;
            return this;
        }

        public Builder reason(String reason) {
            log.reason = reason;
            return this;
        }

        public Builder transitionType(TransitionType transitionType) {
            log.transitionType = transitionType;
            return this;
        }

        public Builder performedBy(String performedBy) {
            log.performedBy = performedBy;
            return this;
        }

        public Builder transitionedAt(LocalDateTime transitionedAt) {
            log.transitionedAt = transitionedAt;
            return this;
        }

        public TierTransitionLog build() {
            if (log.fileAsset == null && log.fileAssetId == null) {
                throw new IllegalArgumentException("fileAsset or fileAssetId is required");
            }
            if (log.toTier == null || log.toTier.isEmpty()) {
                throw new IllegalArgumentException("toTier is required");
            }
            if (log.transitionType == null) {
                throw new IllegalArgumentException("transitionType is required");
            }
            if (log.transitionedAt == null) {
                log.transitionedAt = LocalDateTime.now();
            }
            return log;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
