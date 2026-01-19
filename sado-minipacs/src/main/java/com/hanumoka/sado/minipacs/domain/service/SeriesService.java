package com.hanumoka.sado.minipacs.domain.service;

import com.hanumoka.sado.common.exception.ResourceNotFoundException;
import com.hanumoka.sado.common.util.EntityFinder;
import com.hanumoka.sado.common.tenant.TenantProvider;
import com.hanumoka.sado.common.util.UuidV7Generator;
import com.hanumoka.sado.minipacs.domain.entity.Series;
import com.hanumoka.sado.minipacs.domain.entity.Study;
import com.hanumoka.sado.minipacs.domain.repository.SeriesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Series Service
 *
 * 시리즈(Series) 생성, 조회 기능 제공
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class SeriesService {

    private final SeriesRepository seriesRepository;
    private final StudyService studyService;
    private final TenantProvider tenantProvider;
    private final CacheManager cacheManager;

    /**
     * 전체 Series 조회
     *
     * @return 모든 Series 목록
     */
    public List<Series> findAll() {
        return seriesRepository.findAll();
    }

    /**
     * 필터 조건으로 Series 조회
     *
     * @param modality Modality 필터 (optional)
     * @param studyId Study ID 필터 (optional)
     * @return 필터링된 Series 목록
     */
    public List<Series> findByFilters(String modality, Long studyId) {
        log.debug("Finding series with filters: modality={}, studyId={}", modality, studyId);
        return seriesRepository.findByFilters(modality, studyId);
    }

    /**
     * Series PK로 조회
     *
     * @param id Series PK
     * @return Series 엔티티
     * @throws ResourceNotFoundException Series가 존재하지 않는 경우
     */
    public Series findById(Long id) {
        return EntityFinder.findById(seriesRepository, id, "Series");
    }

    /**
     * DICOM Series Instance UID로 조회
     *
     * <p>캐시 적용: QIDO-RS 조회 시 반복 쿼리 방지
     *
     * @param seriesInstanceUid DICOM Series Instance UID (0020,000E)
     * @return Series 엔티티 (Optional)
     */
    @Cacheable(value = "series", key = "#seriesInstanceUid", unless = "#result == null || !#result.present")
    public Optional<Series> findBySeriesInstanceUid(String seriesInstanceUid) {
        log.debug("Cache miss - querying DB for seriesInstanceUid={}", seriesInstanceUid);
        return seriesRepository.findBySeriesInstanceUid(seriesInstanceUid);
    }

    /**
     * 특정 검사(Study)의 모든 시리즈 조회 (시리즈 번호순)
     *
     * @param studyId Study PK
     * @return Series 목록
     */
    public List<Series> findByStudyId(Long studyId) {
        // Study 존재 여부 확인
        studyService.findById(studyId);

        return seriesRepository.findByStudyIdOrderBySeriesNumber(studyId);
    }

    /**
     * 특정 검사의 특정 Modality 시리즈 조회
     *
     * @param studyId Study PK
     * @param modality Modality (US, CT, MR 등)
     * @return Series 목록
     */
    public List<Series> findByStudyIdAndModality(Long studyId, String modality) {
        // Study 존재 여부 확인
        studyService.findById(studyId);

        return seriesRepository.findByStudyIdAndModality(studyId, modality);
    }

    /**
     * 특정 검사의 시리즈 개수 조회
     *
     * @param studyId Study PK
     * @return 시리즈 개수
     */
    public long countByStudyId(Long studyId) {
        return seriesRepository.countByStudyId(studyId);
    }

    /**
     * Series 생성
     *
     * @param series Series 엔티티 (study 관계 설정 필요)
     * @return 저장된 Series
     */
    @Transactional
    public Series createSeries(Series series) {
        // Study 존재 여부 확인
        if (series.getStudy() == null || series.getStudy().getId() == null) {
            throw new IllegalArgumentException("Series must have a study");
        }

        // Study 조회 (관리되는 엔티티)
        Study study = studyService.findById(series.getStudy().getId());

        log.info("Creating new series: seriesInstanceUid={}, studyId={}",
                series.getSeriesInstanceUid(),
                study.getId());

        // 비즈니스 메서드 호출 (역정규화 필드 자동 업데이트)
        study.addSeries(series);

        return seriesRepository.save(series);
    }

    /**
     * Series 찾기 또는 생성
     *
     * <p>변경 사항 (2026-01-05): MySQL Native Query Upsert 패턴
     * <ul>
     *   <li>Insert-First + Exception Flow Control 제거</li>
     *   <li>MySQL ON DUPLICATE KEY UPDATE로 원자적 Upsert</li>
     *   <li>LAST_INSERT_ID(id) 트릭으로 기존/신규 ID 모두 반환</li>
     *   <li>Exception 없음 → Hibernate Session 오염 없음</li>
     *   <li>REQUIRES_NEW 불필요 → 상위 트랜잭션과 자연스럽게 통합</li>
     * </ul>
     *
     * <p>동작 원리:
     * <ol>
     *   <li>MySQL Upsert 실행 (INSERT 또는 UPDATE)</li>
     *   <li>LAST_INSERT_ID()로 ID 조회 (신규: auto_increment, 기존: 설정된 id)</li>
     *   <li>findById()로 엔티티 반환 (1차 캐시 활용)</li>
     * </ol>
     *
     * <p>이전 방식의 문제점 (Insert-First):
     * <ul>
     *   <li>DataIntegrityViolationException 발생 시 Session 오염</li>
     *   <li>REQUIRES_NEW가 EntityManager를 격리하지 못함 (ThreadLocal 공유)</li>
     *   <li>detach()/clear() 후에도 "null identifier" 오류 발생</li>
     *   <li>study.addSeries() 호출 후 실패 시 메모리 상태 불일치</li>
     * </ul>
     *
     * @param seriesInstanceUid DICOM Series Instance UID
     * @param study 소속 검사
     * @param seriesNumber 시리즈 번호
     * @param modality Modality (US, CT, MR 등)
     * @param seriesDescription 시리즈 설명
     * @param bodyPartExamined 검사 부위
     * @return 찾거나 생성된 Series
     */
    @Transactional
    public Series findOrCreateSeries(
            String seriesInstanceUid,
            Study study,
            Integer seriesNumber,
            String modality,
            String seriesDescription,
            String bodyPartExamined) {

        // 1. MySQL Upsert 실행 (Exception 없음, 완전 원자적)
        Long tenantId = tenantProvider.getCurrentTenantId();
        String uuid = UuidV7Generator.generateString();
        seriesRepository.upsertSeries(
            tenantId,
            uuid,
            seriesInstanceUid,
            study.getId(),
            seriesNumber,
            modality,
            seriesDescription,
            bodyPartExamined
        );

        // 2. LAST_INSERT_ID() 조회 (같은 커넥션 내에서 유효)
        Long seriesId = seriesRepository.getLastInsertId();

        log.debug("Upsert series completed: seriesInstanceUid={}, modality={}, resultId={}",
                seriesInstanceUid, modality, seriesId);

        // 3. 엔티티 반환 (1차 캐시 활용)
        return seriesRepository.findById(seriesId)
                .orElseThrow(() -> new IllegalStateException(
                        "Series should exist after upsert: " + seriesInstanceUid));
    }

    /**
     * Series 업데이트
     *
     * @param series Series 엔티티
     * @return 업데이트된 Series
     */
    @Transactional
    @CacheEvict(value = "series", key = "#series.seriesInstanceUid")
    public Series updateSeries(Series series) {
        // 존재 여부 확인
        findById(series.getId());

        log.info("Updating series: id={}, evicting cache for uid={}", series.getId(), series.getSeriesInstanceUid());
        return seriesRepository.save(series);
    }

    /**
     * Series 삭제
     *
     * <p>캐시 무효화: 삭제 시 해당 UID의 캐시 즉시 삭제
     *
     * @param id Series PK
     */
    @Transactional
    public void deleteSeries(Long id) {
        Series series = findById(id);
        Study study = series.getStudy();
        String seriesInstanceUid = series.getSeriesInstanceUid();

        log.info("Deleting series: id={}, seriesInstanceUid={}",
                id,
                seriesInstanceUid);

        // 비즈니스 메서드 호출 (역정규화 필드 자동 업데이트)
        if (study != null) {
            study.removeSeries(series);
        }

        seriesRepository.delete(series);

        // 캐시 즉시 무효화
        Cache cache = cacheManager.getCache("series");
        if (cache != null) {
            cache.evict(seriesInstanceUid);
            log.debug("Cache evicted for seriesInstanceUid={}", seriesInstanceUid);
        }
    }
}
