package com.hanumoka.sado.minipacs.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 비동기 처리 설정 Properties
 *
 * <p>application.yml의 async 섹션을 바인딩합니다.
 *
 * <p>설정 예시:
 * <pre>
 * async:
 *   pre-rendering:
 *     core-pool-size: 2
 *     max-pool-size: 3
 *     queue-capacity: 500
 *   s3-upload:
 *     core-pool-size: 2
 *     max-pool-size: 3
 *     queue-capacity: 500
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "async")
@Data
public class AsyncProperties {

    private PoolConfig preRendering = new PoolConfig();
    private PoolConfig s3Upload = new PoolConfig();

    @Data
    public static class PoolConfig {
        private int corePoolSize = 2;
        private int maxPoolSize = 3;
        private int queueCapacity = 500;
    }
}
