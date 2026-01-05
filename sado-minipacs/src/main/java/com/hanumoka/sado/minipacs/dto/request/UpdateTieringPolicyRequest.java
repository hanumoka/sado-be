package com.hanumoka.sado.minipacs.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tiering 정책 업데이트 요청 DTO
 *
 * <p>관리자가 Tier 전환 정책을 런타임에 변경할 때 사용합니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTieringPolicyRequest {

    /**
     * HOT → WARM 전환 기준 일수
     * 1일 ~ 3650일 (10년)
     */
    @NotNull(message = "hotToWarmDays is required")
    @Min(value = 1, message = "hotToWarmDays must be at least 1")
    @Max(value = 3650, message = "hotToWarmDays must be at most 3650")
    private Integer hotToWarmDays;

    /**
     * WARM → COLD 전환 기준 일수
     * 1일 ~ 3650일 (10년)
     * warmToColdDays > hotToWarmDays 조건 필수
     */
    @NotNull(message = "warmToColdDays is required")
    @Min(value = 1, message = "warmToColdDays must be at least 1")
    @Max(value = 3650, message = "warmToColdDays must be at most 3650")
    private Integer warmToColdDays;

    /**
     * 스케줄러 활성화 여부
     */
    @NotNull(message = "schedulerEnabled is required")
    private Boolean schedulerEnabled;

    /**
     * HOT → WARM 스케줄 (Cron 표현식)
     * 예: "0 0 1 * * *" (매일 01:00)
     */
    @Pattern(
        regexp = "^([0-9*,/-]+\\s+){5}[0-9*,/-]+$",
        message = "Invalid cron expression for hotToWarmSchedule"
    )
    private String hotToWarmSchedule;

    /**
     * WARM → COLD 스케줄 (Cron 표현식)
     * 예: "0 0 2 * * *" (매일 02:00)
     */
    @Pattern(
        regexp = "^([0-9*,/-]+\\s+){5}[0-9*,/-]+$",
        message = "Invalid cron expression for warmToColdSchedule"
    )
    private String warmToColdSchedule;
}
