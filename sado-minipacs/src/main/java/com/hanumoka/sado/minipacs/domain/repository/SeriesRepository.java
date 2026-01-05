package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.Series;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Series Repository
 *
 * 멀티테넌시: Hibernate Filter 자동 적용
 */
@Repository
public interface SeriesRepository extends JpaRepository<Series, Long> {

    // ========== MySQL Upsert (2026-01-05) ==========

    /**
     * MySQL Upsert: INSERT 또는 기존 ID 반환
     *
     * <p>동시성 문제 해결:
     * <ul>
     *   <li>Insert-First 패턴의 Exception Flow Control 제거</li>
     *   <li>Hibernate Session 오염 방지</li>
     *   <li>완전 원자적 연산 보장</li>
     * </ul>
     *
     * <p>ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id) 트릭:
     * <ul>
     *   <li>중복 시 UPDATE를 실행하지만 실제 변경 최소화</li>
     *   <li>LAST_INSERT_ID(id)로 기존 ID를 반환값으로 설정</li>
     *   <li>COALESCE로 기존 값 보존 (새 값이 null이면)</li>
     * </ul>
     *
     * @param tenantId 테넌트 ID
     * @param seriesInstanceUid DICOM Series Instance UID
     * @param studyId 검사 FK
     * @param seriesNumber 시리즈 번호
     * @param modality 모달리티 (CT, MR, US 등)
     * @param seriesDescription 시리즈 설명
     * @param bodyPartExamined 촬영 부위
     */
    @Modifying
    @Query(value = """
        INSERT INTO series (tenant_id, series_instance_uid, study_id,
                           series_number, modality, series_description,
                           body_part_examined, created_at, updated_at)
        VALUES (:tenantId, :seriesUid, :studyId, :seriesNumber, :modality,
                :description, :bodyPart, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
            id = LAST_INSERT_ID(id),
            series_number = COALESCE(:seriesNumber, series_number),
            modality = COALESCE(:modality, modality),
            series_description = COALESCE(:description, series_description),
            body_part_examined = COALESCE(:bodyPart, body_part_examined),
            updated_at = NOW()
        """, nativeQuery = true)
    void upsertSeries(
        @Param("tenantId") Long tenantId,
        @Param("seriesUid") String seriesInstanceUid,
        @Param("studyId") Long studyId,
        @Param("seriesNumber") Integer seriesNumber,
        @Param("modality") String modality,
        @Param("description") String seriesDescription,
        @Param("bodyPart") String bodyPartExamined
    );

    /**
     * LAST_INSERT_ID() 값 조회
     *
     * <p>주의사항:
     * <ul>
     *   <li>MySQL 커넥션별로 유지됨</li>
     *   <li>같은 트랜잭션 내에서만 유효</li>
     *   <li>Upsert 후 즉시 호출해야 함</li>
     * </ul>
     *
     * @return INSERT된 ID 또는 ON DUPLICATE KEY로 설정된 기존 ID
     */
    @Query(value = "SELECT LAST_INSERT_ID()", nativeQuery = true)
    Long getLastInsertId();

    // ========== 기존 메서드 ==========

    /**
     * DICOM Series Instance UID로 조회
     *
     * @param seriesInstanceUid DICOM Series UID
     * @return 시리즈 (없으면 empty)
     */
    Optional<Series> findBySeriesInstanceUid(String seriesInstanceUid);

    /**
     * DICOM Series Instance UID로 조회 (Pessimistic Write Lock)
     *
     * <p><strong>@Deprecated (2026-01-05)</strong> - Insert-first 패턴으로 변경됨
     *
     * <p>변경 이유:
     * <ul>
     *   <li>Pessimistic Lock은 기존 행만 보호, INSERT 시 Gap Lock Deadlock 발생</li>
     *   <li>3파일 동시 업로드 시 2개 Deadlock (ErrorCode: 1213)</li>
     *   <li>SeriesService.findOrCreateSeries()에서 Insert-first 패턴으로 변경</li>
     *   <li>InstanceService.findOrCreateInstance() 패턴으로 통일</li>
     * </ul>
     *
     * <p>새로운 방식:
     * <ul>
     *   <li>INSERT 먼저 시도 (Lock 없이 빠른 실행)</li>
     *   <li>Race Condition 시 DataIntegrityViolationException</li>
     *   <li>Exception 발생 시 study 메모리 상태 되돌림 (entityManager.refresh)</li>
     *   <li>Deadlock 완전 제거, 66% 병렬 처리 가능</li>
     * </ul>
     *
     * @param seriesInstanceUid DICOM Series UID
     * @return 시리즈 (없으면 empty), Lock과 함께 반환
     * @deprecated Insert-first 패턴 사용, Lock 메서드 불필요
     */
    @Deprecated(since = "2026-01-05", forRemoval = false)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Series s WHERE s.seriesInstanceUid = :seriesInstanceUid")
    Optional<Series> findBySeriesInstanceUidWithLock(@Param("seriesInstanceUid") String seriesInstanceUid);

    /**
     * 특정 검사의 모든 시리즈 조회
     *
     * @param studyId 검사 PK
     * @return 시리즈 목록 (시리즈 번호순)
     */
    List<Series> findByStudyIdOrderBySeriesNumber(Long studyId);

    /**
     * 특정 검사의 특정 Modality 시리즈 조회
     *
     * @param studyId 검사 PK
     * @param modality Modality (US, CT, MR 등)
     * @return 시리즈 목록
     */
    List<Series> findByStudyIdAndModality(Long studyId, String modality);

    /**
     * 검사의 시리즈 개수 조회
     *
     * @param studyId 검사 PK
     * @return 시리즈 개수
     */
    long countByStudyId(Long studyId);

    /**
     * ID로 조회 (Pessimistic Write Lock)
     *
     * <p><strong>@Deprecated</strong> - Optimistic Lock으로 변경됨
     *
     * <p>Series 엔티티에 @Version 필드가 추가되어
     * Optimistic Lock을 자동으로 사용합니다.
     * 이 메서드 대신 findById()를 사용하세요.
     *
     * <p>변경 이유:
     * <ul>
     *   <li>Pessimistic Lock: Deadlock 위험, 성능 오버헤드</li>
     *   <li>Optimistic Lock: OptimisticLockException 발생 시 재시도로 해결</li>
     * </ul>
     *
     * <p>마이그레이션 가이드:
     * <pre>{@code
     * // ❌ 기존 코드 (Pessimistic Lock)
     * Series series = seriesRepository.findByIdWithLock(id).orElseThrow();
     * series.incrementInstanceCount();
     * seriesRepository.save(series);
     *
     * // ✅ 새 코드 (Optimistic Lock + 재시도)
     * @Retryable(value = OptimisticLockingFailureException.class, maxAttempts = 3)
     * public void incrementInstance(Long id) {
     *     Series series = seriesRepository.findById(id).orElseThrow();
     *     series.incrementInstanceCount();
     *     seriesRepository.save(series);  // @Version 자동 증가
     * }
     * }</pre>
     *
     * @param id Series PK
     * @return Series (없으면 empty)
     * @deprecated Series 엔티티에 @Version 필드 추가됨, findById() 사용 권장
     */
    @Deprecated(since = "POC Week 8", forRemoval = true)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Series s WHERE s.id = :id")
    Optional<Series> findByIdWithLock(@Param("id") Long id);
}
