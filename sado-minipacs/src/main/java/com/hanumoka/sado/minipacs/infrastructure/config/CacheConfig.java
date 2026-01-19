package com.hanumoka.sado.minipacs.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 캐시 설정
 *
 * <p>DICOMweb API 성능 향상을 위한 로컬 캐시 설정입니다.
 *
 * <p>캐시 대상:
 * <ul>
 *   <li>studies - Study UID 기반 조회 (QIDO-RS)</li>
 *   <li>series - Series UID 기반 조회 (QIDO-RS)</li>
 *   <li>instances - Instance UID 기반 조회 (WADO-RS)</li>
 * </ul>
 *
 * <p>캐시 정책:
 * <ul>
 *   <li>만료 시간: 10분 (TTL)</li>
 *   <li>최대 엔트리: 1000개</li>
 *   <li>무효화: 엔티티 수정/삭제 시 @CacheEvict</li>
 * </ul>
 *
 * <p>주의사항:
 * <ul>
 *   <li>멀티 인스턴스 배포 시 분산 캐시(Redis) 고려 필요</li>
 *   <li>현재는 단일 인스턴스 POC용 로컬 캐시</li>
 * </ul>
 */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {

    /**
     * 캐시 만료 시간 (분)
     */
    private static final long CACHE_TTL_MINUTES = 10;

    /**
     * 캐시 최대 엔트리 수
     */
    private static final long CACHE_MAX_SIZE = 1000;

    /**
     * Caffeine CacheManager 빈 등록
     *
     * <p>모든 캐시에 동일한 설정 적용:
     * <ul>
     *   <li>expireAfterWrite: 쓰기 후 10분 만료</li>
     *   <li>maximumSize: 최대 1000개 엔트리</li>
     *   <li>recordStats: 통계 기록 (모니터링용)</li>
     * </ul>
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
                .maximumSize(CACHE_MAX_SIZE)
                .recordStats()  // 캐시 통계 기록 (hit/miss ratio 등)
        );

        // 캐시 이름 사전 등록 (선택사항 - 동적 생성도 가능)
        cacheManager.setCacheNames(java.util.List.of(
                "studies",      // Study UID 조회
                "series",       // Series UID 조회
                "instances"     // Instance (SOP Instance UID) 조회
        ));

        log.info("Caffeine CacheManager initialized: ttl={}min, maxSize={}, caches={}",
                CACHE_TTL_MINUTES, CACHE_MAX_SIZE, cacheManager.getCacheNames());

        return cacheManager;
    }
}
