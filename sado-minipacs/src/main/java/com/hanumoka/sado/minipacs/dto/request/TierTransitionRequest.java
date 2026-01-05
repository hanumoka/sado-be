package com.hanumoka.sado.minipacs.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tier 전환 요청 DTO
 *
 * <p>관리자가 수동으로 FileAsset의 Tier를 변경할 때 사용합니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TierTransitionRequest {

    /**
     * 목표 Tier (HOT, WARM, COLD)
     */
    @NotBlank(message = "Tier is required")
    @Pattern(regexp = "^(HOT|WARM|COLD)$", message = "Tier must be HOT, WARM, or COLD")
    private String tier;

    /**
     * 전환 사유 (선택)
     * 예: "Cost optimization", "Restore for analysis", "Regulatory compliance"
     */
    @Size(max = 512, message = "Reason must be less than 512 characters")
    private String reason;
}
