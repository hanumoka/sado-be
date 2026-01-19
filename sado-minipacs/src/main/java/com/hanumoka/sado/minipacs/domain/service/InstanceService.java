package com.hanumoka.sado.minipacs.domain.service;

import static com.hanumoka.sado.common.util.StringUtils.hasText;
import com.hanumoka.sado.common.util.EntityFinder;
import com.hanumoka.sado.common.exception.ResourceNotFoundException;
import com.hanumoka.sado.minipacs.domain.entity.Instance;
import com.hanumoka.sado.minipacs.domain.entity.Series;
import com.hanumoka.sado.minipacs.domain.repository.InstanceRepository;
import com.hanumoka.sado.minipacs.storage.service.DicomStorageService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

/**
 * Instance Service
 *
 * <p>DICOM Instance CRUD 기능 제공
 *
 * <p>SRP 분리 (2026-01-19):
 * <ul>
 *   <li>InstanceService: Instance CRUD 담당 (본 클래스)</li>
 *   <li>DicomUploadService: DICOM 파일 업로드 담당</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class InstanceService {

    private final InstanceRepository instanceRepository;
    private final SeriesService seriesService;
    private final DicomStorageService dicomStorageService;
    private final EntityManager entityManager;
    private final CacheManager cacheManager;

    /**
     * CRITICAL: Self-injection for AOP proxy access
     * Required for @Retryable to work correctly with @Transactional
     *
     * Pattern reference: FileAssetService.java lines 66-88
     */
    @Autowired
    @Lazy
    private InstanceService self;

    /**
     * Instance PK로 조회
     *
     * @param id Instance PK
     * @return Instance 엔티티
     * @throws ResourceNotFoundException Instance가 존재하지 않는 경우
     */
    public Instance findById(Long id) {
        return EntityFinder.findById(instanceRepository, id, "Instance");
    }

    /**
     * DICOM SOP Instance UID로 조회
     *
     * <p>캐시 적용: WADO-RS 조회 시 반복 쿼리 방지
     *
     * @param sopInstanceUid DICOM SOP Instance UID (0008,0018)
     * @return Instance 엔티티 (Optional)
     */
    @Cacheable(value = "instances", key = "#sopInstanceUid", unless = "#result == null")
    public Optional<Instance> findBySopInstanceUid(String sopInstanceUid) {
        log.debug("Cache miss - querying DB for sopInstanceUid={}", sopInstanceUid);
        return instanceRepository.findBySopInstanceUid(sopInstanceUid);
    }

    /**
     * 전체 Instance 조회
     *
     * @return 모든 Instance 목록
     */
    public List<Instance> findAll() {
        return instanceRepository.findAll();
    }

    /**
     * Instance 필터링 조회
     *
     * <p>동적 쿼리로 null 파라미터는 무시됩니다.
     * <ul>
     *   <li>seriesId: Series ID 완전 일치</li>
     *   <li>studyId: Study ID 완전 일치 (Series JOIN 통해)</li>
     *   <li>sopInstanceUid: SOP Instance UID 부분 일치</li>
     *   <li>storageTier: Storage Tier 완전 일치 (HOT/WARM/COLD)</li>
     * </ul>
     *
     * @param seriesId Series ID (nullable)
     * @param studyId Study ID (nullable)
     * @param sopInstanceUid SOP Instance UID (nullable, 부분 일치)
     * @param storageTier Storage Tier 문자열 (nullable, HOT/WARM/COLD)
     * @return Instance 목록
     */
    public List<Instance> findByFilters(Long seriesId, Long studyId,
            String sopInstanceUid, String storageTier) {
        log.debug("Finding instances with filters: seriesId={}, studyId={}, sopInstanceUid={}, storageTier={}",
                seriesId, studyId, sopInstanceUid, storageTier);

        // storageTier String -> Enum 변환 (null-safe)
        Instance.StorageTier storageTierEnum = null;
        if (hasText(storageTier)) {
            try {
                storageTierEnum = Instance.StorageTier.valueOf(storageTier.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid storageTier value: {}, ignoring filter", storageTier);
                // 잘못된 값은 무시하고 null로 처리
            }
        }

        List<Instance> instances = instanceRepository.findByFilters(
                seriesId, studyId, sopInstanceUid, storageTierEnum);

        log.debug("Found {} instances matching filters", instances.size());
        return instances;
    }

    /**
     * Instance 필터링 조회 (페이지네이션)
     *
     * <p>동적 쿼리로 null 파라미터는 무시됩니다.
     * <ul>
     *   <li>seriesId: Series ID 완전 일치</li>
     *   <li>studyId: Study ID 완전 일치 (Series JOIN 통해)</li>
     *   <li>sopInstanceUid: SOP Instance UID 부분 일치</li>
     *   <li>storageTier: Storage Tier 완전 일치 (HOT/WARM/COLD)</li>
     * </ul>
     *
     * @param seriesId Series ID (nullable)
     * @param studyId Study ID (nullable)
     * @param sopInstanceUid SOP Instance UID (nullable, 부분 일치)
     * @param storageTier Storage Tier 문자열 (nullable, HOT/WARM/COLD)
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기
     * @return Instance 페이지
     */
    public Page<Instance> findByFiltersWithPagination(Long seriesId, Long studyId,
            String sopInstanceUid, String storageTier, int page, int size) {
        log.debug("Finding instances with filters (paginated): seriesId={}, studyId={}, " +
                "sopInstanceUid={}, storageTier={}, page={}, size={}",
                seriesId, studyId, sopInstanceUid, storageTier, page, size);

        // storageTier String -> Enum 변환 (null-safe)
        Instance.StorageTier storageTierEnum = null;
        if (hasText(storageTier)) {
            try {
                storageTierEnum = Instance.StorageTier.valueOf(storageTier.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid storageTier value: {}, ignoring filter", storageTier);
            }
        }

        // Pageable 생성 (createdAt 내림차순 정렬)
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Instance> instances = instanceRepository.findByFiltersWithPagination(
                seriesId, studyId, sopInstanceUid, storageTierEnum, pageable);

        log.debug("Found {} instances matching filters (page {}/{})",
                instances.getTotalElements(), page + 1, instances.getTotalPages());
        return instances;
    }

    /**
     * Storage Path로 Instance 조회
     * 파일 중복 업로드 방지용 (같은 경로에 이미 저장된 파일이 있는지 확인)
     *
     * @param storagePath Storage Path (예: /data/dicom/2025/01/15/abc123.dcm)
     * @return Instance 엔티티 (Optional)
     */
    public Optional<Instance> findByStoragePath(String storagePath) {
        return instanceRepository.findByStoragePath(storagePath);
    }

    /**
     * 특정 시리즈의 모든 Instance 조회 (Instance Number 순)
     *
     * @param seriesId Series PK
     * @return Instance 목록
     */
    public List<Instance> findBySeriesId(Long seriesId) {
        // Series 존재 여부 확인
        seriesService.findById(seriesId);

        return instanceRepository.findBySeriesIdOrderByInstanceNumber(seriesId);
    }

    /**
     * 특정 시리즈의 특정 Instance Number 조회
     *
     * @param seriesId Series PK
     * @param instanceNumber Instance Number
     * @return Instance 엔티티 (Optional)
     */
    public Optional<Instance> findBySeriesIdAndInstanceNumber(Long seriesId, Integer instanceNumber) {
        return instanceRepository.findBySeriesIdAndInstanceNumber(seriesId, instanceNumber);
    }

    /**
     * 특정 시리즈의 Instance 개수 조회
     *
     * @param seriesId Series PK
     * @return Instance 개수
     */
    public long countBySeriesId(Long seriesId) {
        return instanceRepository.countBySeriesId(seriesId);
    }

    /**
     * Study Instance UID로 모든 Instance 조회 (N+1 쿼리 방지)
     *
     * <p>WADO-RS Study 메타데이터 API에서 사용합니다.
     * 단일 쿼리로 Study의 모든 Instance를 조회하여 N+1 문제를 해결합니다.
     *
     * <p>성능 개선:
     * <ul>
     *   <li>기존: 1 (Study) + N (Series) + M (Instance) 쿼리 → 5-10초</li>
     *   <li>개선: 1 쿼리 → 500ms 이하 (90% 단축)</li>
     * </ul>
     *
     * @param studyInstanceUid Study Instance UID
     * @return Instance 목록 (Series, Study eager loading, SeriesNumber/InstanceNumber 정렬)
     */
    public List<Instance> findAllByStudyInstanceUid(String studyInstanceUid) {
        log.debug("Finding all instances by studyInstanceUid: {} (single query)", studyInstanceUid);
        List<Instance> instances = instanceRepository.findAllByStudyInstanceUid(studyInstanceUid);
        log.debug("Found {} instances for study {} (single query)", instances.size(), studyInstanceUid);
        return instances;
    }

    /**
     * Series Instance UID로 모든 Instance 조회 (N+1 쿼리 방지)
     *
     * <p>WADO-RS Series 메타데이터 API에서 사용합니다.
     * JOIN FETCH로 Series, Study를 eager loading하여 N+1 쿼리를 방지합니다.
     *
     * @param seriesInstanceUid Series Instance UID
     * @return Instance 목록 (Series, Study eager loading, InstanceNumber 정렬)
     */
    public List<Instance> findAllBySeriesInstanceUid(String seriesInstanceUid) {
        log.debug("Finding all instances by seriesInstanceUid: {} (single query)", seriesInstanceUid);
        List<Instance> instances = instanceRepository.findAllBySeriesInstanceUid(seriesInstanceUid);
        log.debug("Found {} instances for series {} (single query)", instances.size(), seriesInstanceUid);
        return instances;
    }

    /**
     * Instance 생성 with Retry (Legacy API)
     *
     * <p><b>NOTE (2026-01-05):</b> 이 메서드는 더 이상 uploadDicomFile()에서 사용되지 않습니다.
     * <ul>
     *   <li>역정규화 카운터(numberOfInstances) 제거로 Optimistic Lock 충돌 사라짐</li>
     *   <li>uploadDicomFile()에서 직접 instanceRepository.save() 호출로 트랜잭션 통합</li>
     *   <li>같은 트랜잭션 내에서 Instance, MetadataRecord, FileAsset 함께 커밋/롤백</li>
     * </ul>
     *
     * <p>외부 호출자가 있을 수 있으므로 유지하되, 새 코드에서는
     * 트랜잭션 내에서 직접 series.addInstance() + instanceRepository.save() 호출 권장
     *
     * @param instance Instance 엔티티 (series 관계 설정 필요)
     * @return 저장된 Instance
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Retryable(
        retryFor = org.springframework.orm.ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2.0)
    )
    public Instance createInstanceWithRetry(Instance instance) {
        log.debug("Attempting to create instance: sopInstanceUid={} (may retry on conflict)",
                instance.getSopInstanceUid());

        // Call via self-proxy to ensure @Transactional works
        // and exception is thrown after transaction commit
        return self.createInstanceInternal(instance);
    }

    /**
     * Instance 생성 - Internal implementation
     *
     * @param instance Instance 엔티티 (series 관계 설정 필요)
     * @return 저장된 Instance
     */
    @Transactional
    Instance createInstanceInternal(Instance instance) {
        // Series 존재 여부 확인
        if (instance.getSeries() == null || instance.getSeries().getId() == null) {
            throw new IllegalArgumentException("Instance must have a series");
        }

        // Series 조회 (관리되는 엔티티)
        Series series = seriesService.findById(instance.getSeries().getId());

        // 파일 경로 중복 확인
        if (instance.getStoragePath() != null) {
            Optional<Instance> existingInstance = findByStoragePath(instance.getStoragePath());
            if (existingInstance.isPresent()) {
                throw new IllegalArgumentException("Instance with storagePath already exists: " + instance.getStoragePath());
            }
        }

        log.info("Creating new instance: sopInstanceUid={}, seriesId={}",
                instance.getSopInstanceUid(),
                series.getId());

        // 비즈니스 메서드 호출 (역정규화 필드 자동 업데이트)
        series.addInstance(instance);

        return instanceRepository.save(instance);

        // Transaction commits AFTER this method returns
        // If OptimisticLockingFailureException occurs during commit,
        // it propagates to createInstanceWithRetry() where @Retryable catches it
    }

    /**
     * Instance 생성 (Deprecated - use createInstanceWithRetry)
     *
     * @deprecated Use {@link #createInstanceWithRetry(Instance)} instead
     * @param instance Instance 엔티티
     * @return 저장된 Instance
     */
    @Deprecated
    @Transactional
    public Instance createInstance(Instance instance) {
        log.warn("DEPRECATED: createInstance() called. Use createInstanceWithRetry() for retry support.");
        return createInstanceInternal(instance);
    }

    /**
     * Instance 찾기 또는 생성
     *
     * DICOM C-STORE 수신 시 사용:
     * 1. SOP Instance UID로 기존 Instance 검색
     * 2. 없으면 새로운 Instance 생성
     *
     * @param sopInstanceUid DICOM SOP Instance UID
     * @param series 소속 시리즈
     * @param instanceNumber Instance Number
     * @param sopClassUid SOP Class UID
     * @param storagePath DICOM 파일 저장 경로 (SeaweedFS/S3)
     * @param fileSize 파일 크기 (bytes)
     * @return 찾거나 생성된 Instance
     */
    @Transactional
    public Instance findOrCreateInstance(
            String sopInstanceUid,
            Series series,
            Integer instanceNumber,
            String sopClassUid,
            String storagePath,
            Long fileSize) {

        // CRITICAL: Race Condition 해결
        // - 초기 검사 제거: TOCTOU (Time-of-Check Time-of-Use) 취약점 방지
        // - Insert-first 전략: DB Unique Constraint를 활용한 동시성 제어

        try {
            // 1. Instance 생성
            Instance newInstance = Instance.builder()
                    .series(series)
                    .sopInstanceUid(sopInstanceUid)
                    .sopClassUid(sopClassUid)
                    .instanceNumber(instanceNumber)
                    .storagePath(storagePath)
                    .fileSize(fileSize)
                    .build();

            log.info("Creating new instance from DICOM: sopInstanceUid={}, seriesId={}, storagePath={}",
                    sopInstanceUid,
                    series.getId(),
                    storagePath);

            // 2. 양방향 관계 설정 + 역정규화 필드 자동 업데이트
            series.addInstance(newInstance);

            // 3. DB 저장 (Unique Constraint 위반 시 예외 발생)
            return instanceRepository.saveAndFlush(newInstance);

        } catch (DataIntegrityViolationException e) {
            // Race condition 발생: 다른 스레드가 먼저 저장함
            log.warn("Race condition detected for instance: {}, refreshing series state", sopInstanceUid);

            // CRITICAL: series의 메모리 상태를 DB 상태로 되돌림
            // - series.addInstance()로 인한 instances 컬렉션 변경을 원복
            // - 트랜잭션이 커밋되면 잘못된 관계가 DB에 저장되는 것을 방지
            // - Note: numberOfInstances 카운터는 제거되었으므로 더 이상 원복할 필요 없음
            entityManager.refresh(series);

            // 이미 존재하는 Instance 조회 후 반환
            return instanceRepository.findBySopInstanceUid(sopInstanceUid)
                    .orElseThrow(() -> new IllegalStateException(
                            "Instance should exist after DataIntegrityViolationException"));
        }
    }

    /**
     * Instance 업데이트
     *
     * @param instance Instance 엔티티
     * @return 업데이트된 Instance
     */
    @Transactional
    @CacheEvict(value = "instances", key = "#instance.sopInstanceUid")
    public Instance updateInstance(Instance instance) {
        // 존재 여부 확인
        findById(instance.getId());

        log.info("Updating instance: id={}, evicting cache for uid={}", instance.getId(), instance.getSopInstanceUid());
        return instanceRepository.save(instance);
    }

    /**
     * Instance 삭제
     *
     * CRITICAL: 트랜잭션 일관성 보장을 위한 삭제 순서 변경
     * <ol>
     *   <li>S3에서 DICOM 파일 먼저 삭제 (실패 시 예외 발생 → 트랜잭션 롤백)</li>
     *   <li>S3 삭제 성공 시에만 DB에서 Instance 삭제</li>
     * </ol>
     *
     * <p>이전 문제점:
     * - DB 먼저 삭제 → S3 삭제 실패 시 고아 파일 발생
     * - DB는 이미 커밋되어 복구 불가능
     *
     * <p>개선 효과:
     * - S3 삭제 실패 시 트랜잭션 롤백으로 DB 보존
     * - 데이터 일관성 보장
     *
     * @param id Instance PK
     */
    @Transactional
    public void deleteInstance(Long id) {
        Instance instance = findById(id);
        Series series = instance.getSeries();
        String storagePath = instance.getStoragePath();
        String sopInstanceUid = instance.getSopInstanceUid();

        log.info("Deleting instance: id={}, sopInstanceUid={}, storagePath={}",
                id,
                sopInstanceUid,
                storagePath);

        // 1. S3에서 DICOM 파일 먼저 삭제 (실패 시 예외 발생 → 트랜잭션 롤백)
        if (hasText(storagePath)) {
            dicomStorageService.deleteDicomFile(storagePath);
            log.info("Deleted S3 file: {}", storagePath);
        }

        // 2. S3 삭제 성공 시에만 DB에서 Instance 삭제
        // 비즈니스 메서드 호출 (역정규화 필드 자동 업데이트)
        if (series != null) {
            series.removeInstance(instance);
        }

        instanceRepository.delete(instance);
        log.info("Deleted instance from DB: id={}", id);

        // 3. 캐시 즉시 무효화
        Cache cache = cacheManager.getCache("instances");
        if (cache != null) {
            cache.evict(sopInstanceUid);
            log.debug("Cache evicted for sopInstanceUid={}", sopInstanceUid);
        }
    }
}
