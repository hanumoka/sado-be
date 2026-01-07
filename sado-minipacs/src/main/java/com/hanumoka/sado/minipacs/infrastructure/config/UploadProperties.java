package com.hanumoka.sado.minipacs.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 업로드 제한 설정 Properties
 *
 * <p>application.yml의 upload.* 설정을 바인딩합니다.
 *
 * <p>사용 예시:
 * <pre>
 * upload:
 *   max-concurrent: 5
 *   acquire-timeout-ms: 30000
 *   retry-after-seconds: 30
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "upload")
public class UploadProperties {

    /**
     * 최대 동시 업로드 수
     * - Semaphore permit 개수
     * - 기본값: 5
     */
    private int maxConcurrent = 5;

    /**
     * Semaphore permit 획득 대기 시간 (밀리초)
     * - 초과 시 429 Too Many Requests 반환
     * - 기본값: 30000 (30초)
     */
    private long acquireTimeoutMs = 30000;

    /**
     * 클라이언트에게 전달할 재시도 대기 시간 (초)
     * - Retry-After 헤더 값
     * - 기본값: 30
     */
    private int retryAfterSeconds = 30;
}
