package com.hanumoka.sado.minipacs.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비동기 처리 설정
 *
 * <p>DICOM 사전 렌더링 등 비동기 작업을 위한 Executor 설정.
 *
 * <p>설정값:
 * <ul>
 *   <li>CorePoolSize: 4 (기본 스레드 수)</li>
 *   <li>MaxPoolSize: 8 (최대 스레드 수)</li>
 *   <li>QueueCapacity: 500 (대기 큐 크기)</li>
 *   <li>RejectedHandler: CallerRunsPolicy (큐 초과 시 호출 스레드에서 실행)</li>
 * </ul>
 *
 * <p>Multi-Resolution 기능으로 프레임당 작업량 증가 (4파일: raw, 512 PNG, 256 JPEG, 128 JPEG)
 * <p>CallerRunsPolicy: 큐 초과 시 TaskRejectedException 대신 호출 스레드에서 실행
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    /**
     * 사전 렌더링 전용 Executor
     *
     * <p>PreRenderingService에서 @Async("preRenderingExecutor")로 사용.
     *
     * <p>특징:
     * <ul>
     *   <li>CPU 집약적 작업 (이미지 렌더링)</li>
     *   <li>I/O 작업 (S3 업로드)</li>
     *   <li>메모리 사용량 높음</li>
     * </ul>
     *
     * <p>따라서 스레드 수를 적게 유지합니다.
     */
    @Bean(name = "preRenderingExecutor")
    public Executor preRenderingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 기본 스레드 수 (항상 유지) - Multi-Resolution으로 작업량 증가
        executor.setCorePoolSize(4);

        // 최대 스레드 수 - 동시 처리량 향상
        executor.setMaxPoolSize(8);

        // 대기 큐 크기 (큐가 가득 차면 MaxPoolSize까지 스레드 생성)
        executor.setQueueCapacity(500);

        // 스레드 이름 접두사 (로그 추적용)
        executor.setThreadNamePrefix("PreRender-");

        // ⭐ CRITICAL: CallerRunsPolicy 적용
        // - 큐가 가득 차면 예외 대신 호출 스레드에서 직접 실행
        // - 업로드 응답이 느려질 수 있지만 TaskRejectedException 방지
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 스레드 풀 종료 시 대기 중인 작업 처리
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 종료 대기 시간 (초)
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("PreRendering Executor initialized: corePoolSize={}, maxPoolSize={}, queueCapacity={}, rejectionPolicy=CallerRunsPolicy",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }
}
