package com.hanumoka.sado.minipacs.infrastructure.config;

import com.hanumoka.sado.common.tenant.TenantContextTaskDecorator;
import lombok.RequiredArgsConstructor;
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
 * <p>설정값은 application.yml에서 관리합니다.
 *
 * <p>설정 항목:
 * <ul>
 *   <li>async.pre-rendering.* - 사전 렌더링 스레드 풀</li>
 *   <li>async.s3-upload.* - S3 업로드 스레드 풀</li>
 * </ul>
 *
 * <p>RejectedHandler: CallerRunsPolicy (큐 초과 시 호출 스레드에서 실행)
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
@Slf4j
public class AsyncConfig {

    private final AsyncProperties asyncProperties;

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
     */
    @Bean(name = "preRenderingExecutor")
    public Executor preRenderingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        AsyncProperties.PoolConfig config = asyncProperties.getPreRendering();

        executor.setCorePoolSize(config.getCorePoolSize());
        executor.setMaxPoolSize(config.getMaxPoolSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setThreadNamePrefix("PreRender-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // TenantContext 비동기 전파 (멀티테넌시 지원)
        executor.setTaskDecorator(new TenantContextTaskDecorator());

        executor.initialize();

        log.info("PreRendering Executor initialized: corePoolSize={}, maxPoolSize={}, queueCapacity={}, rejectionPolicy=CallerRunsPolicy, taskDecorator=TenantContextTaskDecorator",
                config.getCorePoolSize(), config.getMaxPoolSize(), config.getQueueCapacity());

        return executor;
    }

    /**
     * S3 업로드 전용 Executor
     *
     * <p>PreRenderingService에서 S3 업로드를 병렬화하기 위해 사용.
     *
     * <p>특징:
     * <ul>
     *   <li>I/O 바운드 작업 (네트워크 대기)</li>
     *   <li>프레임당 최대 7개 업로드 (raw, compressed, png, 4 cine jpeg)</li>
     * </ul>
     */
    @Bean(name = "s3UploadExecutor")
    public Executor s3UploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        AsyncProperties.PoolConfig config = asyncProperties.getS3Upload();

        executor.setCorePoolSize(config.getCorePoolSize());
        executor.setMaxPoolSize(config.getMaxPoolSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setThreadNamePrefix("S3Upload-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);

        // TenantContext 비동기 전파 (멀티테넌시 지원)
        executor.setTaskDecorator(new TenantContextTaskDecorator());

        executor.initialize();

        log.info("S3 Upload Executor initialized: corePoolSize={}, maxPoolSize={}, queueCapacity={}, taskDecorator=TenantContextTaskDecorator",
                config.getCorePoolSize(), config.getMaxPoolSize(), config.getQueueCapacity());

        return executor;
    }
}
