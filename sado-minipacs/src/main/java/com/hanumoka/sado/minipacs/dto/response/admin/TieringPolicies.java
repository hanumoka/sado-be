package com.hanumoka.sado.minipacs.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Storage Tiering 정책 정보 DTO
 *
 * <p>불변 DTO: setter가 없어 생성 후 수정 불가
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TieringPolicies {

    /**
     * HOT → WARM 전환 기준 일수
     */
    private int hotToWarmDays;

    /**
     * WARM → COLD 전환 기준 일수
     */
    private int warmToColdDays;

    /**
     * 자동 Tiering 스케줄러 활성화 여부
     */
    private boolean schedulerEnabled;

    /**
     * HOT → WARM 스케줄 (Cron 표현식)
     * 예: "0 0 3 * * *" (매일 새벽 3시)
     */
    private String hotToWarmSchedule;

    /**
     * WARM → COLD 스케줄 (Cron 표현식)
     * 예: "0 0 4 * * *" (매일 새벽 4시)
     */
    private String warmToColdSchedule;
}
