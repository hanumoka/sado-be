package com.hanumoka.sado.minipacs.domain.entity;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Storage Tiering 정책 엔티티
 *
 * <p>DB 기반 Tier 전환 정책 관리로 애플리케이션 재시작 없이 정책 변경이 가능합니다.
 *
 * <p>주요 용도:
 * <ul>
 *   <li>HOT → WARM 전환 기준 일수 설정</li>
 *   <li>WARM → COLD 전환 기준 일수 설정</li>
 *   <li>스케줄러 활성화/비활성화</li>
 *   <li>Cron 표현식 기반 실행 시간 설정</li>
 * </ul>
 *
 * <p>설계 특징:
 * <ul>
 *   <li>Tenant당 1개의 정책 (Singleton Pattern)</li>
 *   <li>애플리케이션 시작 시 기본값 자동 생성</li>
 *   <li>Admin API를 통한 런타임 변경</li>
 * </ul>
 */
@Entity
@Table(name = "tiering_policy", uniqueConstraints = {
    @UniqueConstraint(name = "uk_tiering_policy_tenant", columnNames = {"tenant_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class TieringPolicy extends TenantAwareEntity {

    /**
     * HOT → WARM 전환 기준 일수
     * 마지막 접근 후 N일 경과 시 WARM으로 이동
     * 기본값: 90일
     */
    @Column(name = "hot_to_warm_days", nullable = false)
    private Integer hotToWarmDays;

    /**
     * WARM → COLD 전환 기준 일수
     * 마지막 접근 후 N일 경과 시 COLD로 이동
     * 기본값: 365일
     */
    @Column(name = "warm_to_cold_days", nullable = false)
    private Integer warmToColdDays;

    /**
     * 스케줄러 활성화 여부
     * false: 수동 Tier 전환만 가능
     * true: 자동 + 수동 모두 가능
     */
    @Column(name = "scheduler_enabled", nullable = false)
    private Boolean schedulerEnabled;

    /**
     * HOT → WARM 스케줄 (Cron 표현식)
     * 기본값: "0 0 1 * * *" (매일 01:00)
     */
    @Column(name = "hot_to_warm_schedule", length = 64)
    private String hotToWarmSchedule;

    /**
     * WARM → COLD 스케줄 (Cron 표현식)
     * 기본값: "0 0 2 * * *" (매일 02:00)
     */
    @Column(name = "warm_to_cold_schedule", length = 64)
    private String warmToColdSchedule;

    // ========== 비즈니스 메서드 ==========

    /**
     * 정책 유효성 검증
     *
     * @throws IllegalArgumentException 정책이 유효하지 않은 경우
     */
    public void validate() {
        if (hotToWarmDays == null || hotToWarmDays < 1 || hotToWarmDays > 3650) {
            throw new IllegalArgumentException("hotToWarmDays must be between 1 and 3650");
        }
        if (warmToColdDays == null || warmToColdDays < 1 || warmToColdDays > 3650) {
            throw new IllegalArgumentException("warmToColdDays must be between 1 and 3650");
        }
        if (warmToColdDays <= hotToWarmDays) {
            throw new IllegalArgumentException("warmToColdDays must be greater than hotToWarmDays");
        }
        if (schedulerEnabled == null) {
            throw new IllegalArgumentException("schedulerEnabled is required");
        }
    }

    // ========== Builder Pattern ==========

    public static class Builder {
        private final TieringPolicy policy = new TieringPolicy();

        public Builder hotToWarmDays(Integer hotToWarmDays) {
            policy.hotToWarmDays = hotToWarmDays;
            return this;
        }

        public Builder warmToColdDays(Integer warmToColdDays) {
            policy.warmToColdDays = warmToColdDays;
            return this;
        }

        public Builder schedulerEnabled(Boolean schedulerEnabled) {
            policy.schedulerEnabled = schedulerEnabled;
            return this;
        }

        public Builder hotToWarmSchedule(String hotToWarmSchedule) {
            policy.hotToWarmSchedule = hotToWarmSchedule;
            return this;
        }

        public Builder warmToColdSchedule(String warmToColdSchedule) {
            policy.warmToColdSchedule = warmToColdSchedule;
            return this;
        }

        public TieringPolicy build() {
            // 기본값 설정
            if (policy.hotToWarmDays == null) {
                policy.hotToWarmDays = 90;
            }
            if (policy.warmToColdDays == null) {
                policy.warmToColdDays = 365;
            }
            if (policy.schedulerEnabled == null) {
                policy.schedulerEnabled = true;
            }
            if (policy.hotToWarmSchedule == null) {
                policy.hotToWarmSchedule = "0 0 1 * * *"; // 매일 01:00
            }
            if (policy.warmToColdSchedule == null) {
                policy.warmToColdSchedule = "0 0 2 * * *"; // 매일 02:00
            }

            policy.validate();
            return policy;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 기본 정책 생성
     *
     * @return 기본값으로 초기화된 정책
     */
    public static TieringPolicy createDefault() {
        return TieringPolicy.builder().build();
    }
}
