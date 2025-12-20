# 🔧 Redis 활용 요구사항

> 🔧 **문서 역할**: Redis 분산락/캐싱 상세 명세 (최종 문서)
>
> 📅 **작성일**: 2025-12-20
>
> 🎯 **적용 시기**: Week 14 (Redis 마스터)
>
> 🔗 **연결**: `07_최종_구현_계획.md` Week 14 섹션

---

## 1. 기술 스택

### Redis
- **버전**: Redis 7.x
- **클라이언트**: Redisson (Java 분산락 클라이언트)
- **배포**: Docker Compose (개발), Redis Cluster (프로덕션 고려)

### Redisson 선택 이유
| 항목 | Redisson | Lettuce + 직접 구현 |
|------|----------|---------------------|
| **분산락 구현** | 내장 지원 | 수동 구현 필요 |
| **Lock 종류** | Fair Lock, Read/Write Lock, Semaphore | 기본 Lock만 |
| **자동 갱신** | Watch Dog 자동 갱신 | 수동 구현 필요 |
| **학습 가치** | ⭐⭐⭐⭐⭐ (프로덕션 레벨) | ⭐⭐⭐ (기본 이해) |

**결정**: Redisson 사용 (프로덕션 레벨 분산락 학습)

---

## 2. Redis 활용 시나리오

### 2.1 분산락 (Distributed Lock)

#### 시나리오 1: DICOM 파일 동시 업로드 방지
**문제 상황**:
- 동일한 Study에 대해 여러 클라이언트가 동시에 DICOM 파일 업로드
- Study 메타데이터 중복 생성 또는 데이터 불일치 발생 가능

**해결 방안**:
```java
// MiniPACS 모듈 - DicomUploadService.java
public void uploadDicom(String studyId, List<DicomFile> files) {
    String lockKey = "study:upload:" + studyId;
    RLock lock = redisson.getLock(lockKey);

    try {
        // 최대 10초 대기, 30초 후 자동 해제
        boolean acquired = lock.tryLock(10, 30, TimeUnit.SECONDS);
        if (!acquired) {
            throw new LockAcquisitionException("Study is being uploaded by another user");
        }

        // Study 업로드 로직
        // 1. Study 존재 여부 확인
        // 2. DICOM 파일 SeaweedFS 저장
        // 3. Study 메타데이터 업데이트

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Lock interrupted", e);
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

**적용 모듈**: `sado-minipacs` (minipacs-api)
**학습 목표**: Redisson RLock 사용법, Watch Dog 메커니즘

---

#### 시나리오 2: AI 분석 중복 실행 방지
**문제 상황**:
- 동일한 Study에 대해 여러 Orchestrator 인스턴스가 동시에 AI 분석 요청
- Triton 서버 리소스 낭비, 중복 결과 저장

**해결 방안**:
```java
// Orchestrator 모듈 - AnalysisWorkflow.java
@WorkflowMethod
public AnalysisResult analyzeStudy(String studyId) {
    String lockKey = "analysis:running:" + studyId;
    RLock lock = redisson.getLock(lockKey);

    try {
        // 즉시 획득 실패 시 예외 (이미 분석 중)
        boolean acquired = lock.tryLock(0, 600, TimeUnit.SECONDS); // 10분 타임아웃
        if (!acquired) {
            throw new AnalysisAlreadyRunningException("Analysis is already running for study: " + studyId);
        }

        // Temporal Workflow 실행
        // 1. Triton 서버에 분석 요청
        // 2. 분석 결과 대기
        // 3. 결과 저장

    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

**적용 모듈**: `sado-orchestrator` (orchestrator-workflow)
**학습 목표**: 분산 환경에서 중복 실행 방지 패턴

---

#### 시나리오 3: Study 상태 변경 동시성 제어
**문제 상황**:
- Study 상태: UPLOADED → ANALYZING → COMPLETED → ARCHIVED
- 여러 서비스가 동시에 상태 변경 시도 (예: Orchestrator + BFF)

**해결 방안**:
```java
// Common 모듈 - StudyStateManager.java
public void updateStudyState(String studyId, StudyState newState) {
    String lockKey = "study:state:" + studyId;
    RLock lock = redisson.getLock(lockKey);

    try {
        lock.lock(5, TimeUnit.SECONDS);

        Study study = studyRepository.findById(studyId)
            .orElseThrow(() -> new StudyNotFoundException(studyId));

        // 상태 전환 검증
        if (!study.canTransitionTo(newState)) {
            throw new InvalidStateTransitionException(
                "Cannot transition from " + study.getState() + " to " + newState
            );
        }

        study.setState(newState);
        studyRepository.save(study);

    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

**적용 모듈**: `sado-common` (common-core)
**학습 목표**: 상태 머신 패턴 + 분산락

---

### 2.2 캐싱 (Caching)

#### 시나리오 4: Study 메타데이터 캐싱
**문제 상황**:
- 프론트엔드에서 Study 목록 조회 API 빈번한 호출
- PostgreSQL에서 JSONB 쿼리는 상대적으로 느림

**해결 방안**:
```java
// BFF 모듈 - StudyQueryService.java
@Cacheable(value = "study:metadata", key = "#studyId")
public StudyMetadataDto getStudyMetadata(String studyId) {
    // Cache Miss 시 MiniPACS 호출
    return miniPacsClient.getStudyMetadata(studyId);
}

@CacheEvict(value = "study:metadata", key = "#studyId")
public void invalidateStudyCache(String studyId) {
    // Study 상태 변경 시 캐시 무효화
}
```

**캐시 설정**:
```java
// redis-config 모듈
@Bean
public RedisCacheConfiguration studyMetadataCacheConfig() {
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofMinutes(10))  // 10분 TTL
        .serializeValuesWith(
            RedisSerializationContext.SerializationPair.fromSerializer(
                new GenericJackson2JsonRedisSerializer()
            )
        );
}
```

**적용 모듈**: `sado-bff` (bff-api), `sado-infrastructure` (redis-config)
**학습 목표**: Spring Cache Abstraction, Cache Aside 패턴

---

#### 시나리오 5: Triton 분석 결과 캐싱
**문제 상황**:
- 동일한 DICOM 파일에 대해 재분석 요청 가능
- Triton 분석은 비용이 높음 (GPU 리소스)

**해결 방안**:
```java
// Orchestrator 모듈 - AnalysisService.java
public AnalysisResult requestAnalysis(String studyId, String fileHash) {
    String cacheKey = "analysis:result:" + fileHash;

    // 캐시 확인
    RBucket<AnalysisResult> cached = redisson.getBucket(cacheKey);
    if (cached.isExists()) {
        log.info("Analysis result found in cache for file: {}", fileHash);
        return cached.get();
    }

    // Cache Miss: Triton 서버에 분석 요청
    AnalysisResult result = tritonClient.analyze(studyId);

    // 결과 캐싱 (24시간 TTL)
    cached.set(result, 24, TimeUnit.HOURS);

    return result;
}
```

**적용 모듈**: `sado-orchestrator` (orchestrator-workflow)
**학습 목표**: RBucket 활용, 비용 절감 전략

---

### 2.3 Rate Limiting

#### 시나리오 6: API Gateway Rate Limiting
**문제 상황**:
- DDoS 공격 또는 클라이언트 버그로 인한 과도한 API 호출
- 서버 리소스 고갈 방지 필요

**해결 방안**:
```java
// Gateway 모듈 - RateLimitFilter.java
@Component
public class RateLimitFilter implements GatewayFilter {

    @Autowired
    private RedissonClient redisson;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String userId = extractUserId(exchange);
        String rateLimiterKey = "ratelimit:user:" + userId;

        RRateLimiter limiter = redisson.getRateLimiter(rateLimiterKey);
        limiter.trySetRate(RateType.OVERALL, 100, 1, RateIntervalUnit.MINUTES); // 100 req/min

        if (!limiter.tryAcquire(1)) {
            return onError(exchange, "Rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS);
        }

        return chain.filter(exchange);
    }
}
```

**설정**:
- 사용자당 100 req/min
- IP당 500 req/min (인증 전)

**적용 모듈**: `sado-gateway`
**학습 목표**: RRateLimiter, API 보호 전략

---

## 3. Redis 아키텍처 설계

### 3.1 개발 환경
```yaml
# docker-compose.yml
redis:
  image: redis:7-alpine
  ports:
    - "6379:6379"
  volumes:
    - redis-data:/data
  command: redis-server --appendonly yes
```

### 3.2 키 네이밍 규칙
```
{서비스}:{타입}:{식별자}

예시:
- study:upload:12345         # MiniPACS 업로드 락
- analysis:running:67890      # Orchestrator 분석 락
- study:state:12345           # Study 상태 변경 락
- study:metadata:12345        # Study 메타데이터 캐시
- analysis:result:abc123      # 분석 결과 캐시
- ratelimit:user:user123      # 사용자별 Rate Limit
```

### 3.3 TTL 전략
| 키 타입 | TTL | 이유 |
|--------|-----|------|
| 분산락 | 30초 ~ 10분 | 작업 예상 소요 시간 기반 |
| Study 메타데이터 | 10분 | 자주 변경되지 않음 |
| 분석 결과 | 24시간 | 재분석 가능성 낮음 |
| Rate Limit | 1분 | 슬라이딩 윈도우 |

### 3.4 메모리 관리
- **Max Memory**: 2GB (개발), 8GB (프로덕션)
- **Eviction Policy**: `allkeys-lru` (LRU로 자동 삭제)

---

## 4. 모듈별 Redis 의존성

### Infrastructure 모듈 (redis-config)
**책임**:
- RedissonClient Bean 생성
- 공통 Redis 설정 (연결, 직렬화)
- Lock, Cache, RateLimiter 헬퍼 메서드

```java
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
            .setAddress("redis://localhost:6379")
            .setConnectionPoolSize(10)
            .setConnectionMinimumIdleSize(5);
        return Redisson.create(config);
    }

    @Bean
    public DistributedLockHelper lockHelper(RedissonClient redisson) {
        return new DistributedLockHelper(redisson);
    }
}
```

### MiniPACS 모듈
**의존성**:
- `sado-infrastructure:redis-config`

**활용**:
- DICOM 업로드 분산락
- Study 상태 변경 분산락

### Orchestrator 모듈
**의존성**:
- `sado-infrastructure:redis-config`

**활용**:
- AI 분석 중복 실행 방지 분산락
- Triton 분석 결과 캐싱

### BFF 모듈
**의존성**:
- `sado-infrastructure:redis-config`

**활용**:
- Study 메타데이터 캐싱
- Spring Cache Abstraction

### Gateway 모듈
**의존성**:
- `sado-infrastructure:redis-config`

**활용**:
- Rate Limiting (RRateLimiter)

---

## 5. 학습 목표 (Week 14)

### 학습 내용
1. **Redis 아키텍처**
   - 데이터 구조 (String, Hash, List, Set, Sorted Set)
   - Persistence (RDB, AOF)
   - Replication & Sentinel

2. **Redisson 분산락**
   - RLock vs Fair Lock vs Read/Write Lock
   - Watch Dog 자동 갱신 메커니즘
   - Lock 타임아웃 전략

3. **캐싱 전략**
   - Cache Aside vs Write Through vs Write Behind
   - TTL 설계 원칙
   - Cache Stampede 방지

4. **Rate Limiting 알고리즘**
   - Token Bucket vs Leaky Bucket vs Fixed Window

### 실습 체크리스트
- [ ] Redis Docker Compose 추가
- [ ] `redis-config` 모듈에 RedissonClient 설정
- [ ] MiniPACS: DICOM 업로드 분산락 구현
- [ ] Orchestrator: AI 분석 중복 실행 방지
- [ ] BFF: Study 메타데이터 캐싱
- [ ] Gateway: Rate Limiting 구현
- [ ] Redis 메모리 사용량 모니터링 (Grafana)

### 산출물
- Redis 분산락 및 캐싱 구현 완료
- `docs/learning/week14-redis.md` (학습 노트)
- Grafana 대시보드: Redis 메트릭

---

## 6. 프로덕션 고려사항

### 6.1 Redis Cluster (선택)
- **Phase 5** (Week 16)에 Redis Cluster 구성 학습 (선택)
- 단일 Redis 노드 장애 시 복원력 확보

### 6.2 모니터링
- **메트릭**:
  - Lock 획득 실패율
  - Cache Hit Rate
  - Rate Limit 초과 횟수
  - Redis 메모리 사용량

### 6.3 백업
- RDB Snapshot: 매일 자동 백업
- AOF: Append-only 파일로 데이터 복구

---

## 7. 참고 문서

- **Redisson 공식 문서**: https://github.com/redisson/redisson/wiki
- **Redis 공식 문서**: https://redis.io/docs/
- **분산락 패턴**: https://redis.io/docs/manual/patterns/distributed-locks/
- **Spring Cache Abstraction**: https://docs.spring.io/spring-framework/reference/integration/cache.html

---

## 결론

Redis는 다음 3가지 주요 용도로 활용됩니다:

1. **분산락**: 동시성 제어 (DICOM 업로드, AI 분석 중복 방지)
2. **캐싱**: 성능 최적화 (Study 메타데이터, 분석 결과)
3. **Rate Limiting**: API 보호 (DDoS 방어)

**학습 우선순위**:
1. Week 14: Redisson 분산락 완전 이해
2. Week 14: Spring Cache Abstraction 활용
3. Week 15: Redis 모니터링 (Grafana 연동)

**최종 목표**: Redis를 프로덕션 레벨로 활용할 수 있는 능력 확보 ⭐⭐⭐⭐
