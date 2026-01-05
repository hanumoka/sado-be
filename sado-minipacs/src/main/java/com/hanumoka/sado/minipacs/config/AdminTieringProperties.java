package com.hanumoka.sado.minipacs.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Admin Tiering 설정 프로퍼티
 *
 * <p>application.yml의 admin.tiering 설정을 바인딩합니다.
 *
 * <p>사용 예시:
 * <pre>
 * {@code
 * @Autowired
 * private AdminTieringProperties properties;
 *
 * int days = properties.getHotToWarmDays();  // 30
 * }
 * </pre>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "admin.tiering")
public class AdminTieringProperties {

    /**
     * HOT → WARM 전환 기준 일수
     * 기본값: 30일
     */
    private int hotToWarmDays = 30;

    /**
     * WARM → COLD 전환 기준 일수
     * 기본값: 365일
     */
    private int warmToColdDays = 365;

    /**
     * 자동 Tiering 스케줄러 활성화 여부
     * 기본값: true
     */
    private boolean schedulerEnabled = true;

    /**
     * HOT → WARM 스케줄 (Cron 표현식)
     * 기본값: "0 0 3 * * *" (매일 새벽 3시)
     */
    private String hotToWarmSchedule = "0 0 3 * * *";

    /**
     * WARM → COLD 스케줄 (Cron 표현식)
     * 기본값: "0 0 4 * * *" (매일 새벽 4시)
     */
    private String warmToColdSchedule = "0 0 4 * * *";
}
