package com.hanumoka.sado.common.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskDecorator;

/**
 * 비동기 스레드에 TenantContext를 전파하는 TaskDecorator
 *
 * <p>Spring의 @Async 메서드는 별도 스레드에서 실행되므로
 * ThreadLocal 기반의 TenantContext 값이 전파되지 않습니다.
 * 이 데코레이터를 사용하면 부모 스레드의 TenantContext를
 * 자식 스레드에 자동으로 복사합니다.
 *
 * <p>사용법:
 * <pre>
 * {@code
 * @Bean
 * public Executor asyncExecutor() {
 *     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *     executor.setTaskDecorator(new TenantContextTaskDecorator());
 *     executor.initialize();
 *     return executor;
 * }
 * }
 * </pre>
 *
 * <p>동작 흐름:
 * <ol>
 *   <li>부모 스레드: TenantContext.getCurrentTenantId() 캡처</li>
 *   <li>자식 스레드 시작 시: TenantContext.setCurrentTenantId() 호출</li>
 *   <li>작업 완료 후: TenantContext.clear() 호출 (메모리 누수 방지)</li>
 * </ol>
 *
 * @see TenantContext
 */
@Slf4j
public class TenantContextTaskDecorator implements TaskDecorator {

    /**
     * Runnable을 래핑하여 TenantContext를 전파합니다.
     *
     * @param runnable 원본 Runnable
     * @return TenantContext가 전파되는 래핑된 Runnable
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        // 1. 부모 스레드에서 현재 TenantContext 값 캡처
        Long tenantId = TenantContext.getCurrentTenantId();

        return () -> {
            try {
                // 2. 자식 스레드에서 TenantContext 설정
                if (tenantId != null) {
                    TenantContext.setCurrentTenantId(tenantId);
                    log.trace("TenantContext propagated to async thread: tenantId={}, thread={}",
                            tenantId, Thread.currentThread().getName());
                }

                // 3. 원본 작업 실행
                runnable.run();

            } finally {
                // 4. 작업 완료 후 TenantContext 정리 (메모리 누수 방지)
                TenantContext.clear();
                log.trace("TenantContext cleared in async thread: thread={}",
                        Thread.currentThread().getName());
            }
        };
    }
}
