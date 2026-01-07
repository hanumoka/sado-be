package com.hanumoka.sado.minipacs.infrastructure.config;

import com.hanumoka.sado.common.exception.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 동시 업로드 제한기 (Semaphore 기반)
 *
 * <p>서버 자원 보호를 위해 동시 업로드 수를 제한합니다.
 *
 * <p>동작 방식:
 * <ol>
 *   <li>업로드 요청 시 Semaphore permit 획득 시도</li>
 *   <li>타임아웃 내 획득 성공 → 업로드 진행</li>
 *   <li>타임아웃 초과 → 429 Too Many Requests 응답</li>
 *   <li>업로드 완료/실패 시 permit 반환</li>
 * </ol>
 *
 * <p>설정:
 * <ul>
 *   <li>upload.max-concurrent: 동시 업로드 수 (기본: 5)</li>
 *   <li>upload.acquire-timeout-ms: 대기 시간 (기본: 30초)</li>
 *   <li>upload.retry-after-seconds: Retry-After 헤더 값 (기본: 30초)</li>
 * </ul>
 *
 * <p>보호 효과:
 * <ul>
 *   <li>DB 연결 풀 고갈 방지 (HikariCP maximum-pool-size: 10)</li>
 *   <li>메모리 폭발 방지 (500MB × N 동시 처리)</li>
 *   <li>S3/SeaweedFS 과부하 방지</li>
 * </ul>
 */
@Component
@EnableConfigurationProperties(UploadProperties.class)
@Slf4j
public class UploadLimiter {

    private final Semaphore semaphore;
    private final UploadProperties properties;

    public UploadLimiter(UploadProperties properties) {
        this.properties = properties;
        this.semaphore = new Semaphore(properties.getMaxConcurrent());

        log.info("UploadLimiter initialized - maxConcurrent: {}, acquireTimeoutMs: {}, retryAfterSeconds: {}",
                properties.getMaxConcurrent(),
                properties.getAcquireTimeoutMs(),
                properties.getRetryAfterSeconds());
    }

    /**
     * 동시 업로드 제한 내에서 작업 실행
     *
     * @param task 실행할 작업 (Supplier)
     * @param <T> 반환 타입
     * @return 작업 결과
     * @throws TooManyRequestsException 타임아웃 내 permit 획득 실패 시
     */
    public <T> T executeWithLimit(Supplier<T> task) {
        boolean acquired = false;

        try {
            // permit 획득 시도 (타임아웃 적용)
            acquired = semaphore.tryAcquire(
                    properties.getAcquireTimeoutMs(),
                    TimeUnit.MILLISECONDS
            );

            if (!acquired) {
                // 타임아웃 - 429 응답
                log.warn("Upload limit exceeded - current permits: {}, waiting requests rejected",
                        semaphore.availablePermits());

                throw new TooManyRequestsException(
                        "서버가 바쁩니다. " + properties.getRetryAfterSeconds() + "초 후 다시 시도해주세요.",
                        properties.getRetryAfterSeconds()
                );
            }

            log.debug("Upload permit acquired - available permits: {}",
                    semaphore.availablePermits());

            // 작업 실행
            return task.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Upload interrupted", e);

        } finally {
            if (acquired) {
                semaphore.release();
                log.debug("Upload permit released - available permits: {}",
                        semaphore.availablePermits());
            }
        }
    }

    /**
     * 현재 사용 가능한 permit 수 조회 (모니터링용)
     */
    public int availablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * 대기 중인 스레드 수 조회 (모니터링용)
     */
    public int queueLength() {
        return semaphore.getQueueLength();
    }
}
