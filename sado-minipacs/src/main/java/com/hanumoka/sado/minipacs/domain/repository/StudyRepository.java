package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.Study;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Study Repository
 *
 * 멀티테넌시: Hibernate Filter 자동 적용
 */
@Repository
public interface StudyRepository extends JpaRepository<Study, Long> {

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
     * @param studyInstanceUid DICOM Study Instance UID
     * @param patientId 환자 FK
     * @param studyDate 검사 날짜
     * @param studyDescription 검사 설명
     */
    @Modifying
    @Query(value = """
        INSERT INTO study (tenant_id, study_instance_uid, patient_id,
                          study_date, study_description, created_at, updated_at)
        VALUES (:tenantId, :studyUid, :patientId, :studyDate, :description, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
            id = LAST_INSERT_ID(id),
            study_date = COALESCE(:studyDate, study_date),
            study_description = COALESCE(:description, study_description),
            updated_at = NOW()
        """, nativeQuery = true)
    void upsertStudy(
        @Param("tenantId") Long tenantId,
        @Param("studyUid") String studyInstanceUid,
        @Param("patientId") Long patientId,
        @Param("studyDate") LocalDate studyDate,
        @Param("description") String studyDescription
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
     * DICOM Study Instance UID로 조회
     *
     * @param studyInstanceUid DICOM Study UID
     * @return 검사 (없으면 empty)
     */
    Optional<Study> findByStudyInstanceUid(String studyInstanceUid);

    /**
     * DICOM Study Instance UID로 조회 (Pessimistic Write Lock)
     *
     * <p><strong>@Deprecated (2026-01-05)</strong> - Insert-first 패턴으로 변경됨
     *
     * <p>변경 이유:
     * <ul>
     *   <li>Pessimistic Lock은 기존 행만 보호, INSERT 시 Gap Lock Deadlock 발생</li>
     *   <li>3파일 동시 업로드 시 2개 Deadlock (ErrorCode: 1213)</li>
     *   <li>StudyService.findOrCreateStudy()에서 Insert-first 패턴으로 변경</li>
     *   <li>InstanceService.findOrCreateInstance() 패턴으로 통일</li>
     * </ul>
     *
     * <p>새로운 방식:
     * <ul>
     *   <li>INSERT 먼저 시도 (Lock 없이 빠른 실행)</li>
     *   <li>Race Condition 시 DataIntegrityViolationException</li>
     *   <li>Exception 발생 시 기존 Study SELECT</li>
     *   <li>Deadlock 완전 제거, 66% 병렬 처리 가능</li>
     * </ul>
     *
     * @param studyInstanceUid DICOM Study UID
     * @return 검사 (없으면 empty), Lock과 함께 반환
     * @deprecated Insert-first 패턴 사용, Lock 메서드 불필요
     */
    @Deprecated(since = "2026-01-05", forRemoval = false)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Study s WHERE s.studyInstanceUid = :studyInstanceUid")
    Optional<Study> findByStudyInstanceUidWithLock(@Param("studyInstanceUid") String studyInstanceUid);

    /**
     * 특정 환자의 모든 검사 조회
     *
     * @param patientId 환자 PK
     * @return 검사 목록 (최신순)
     */
    List<Study> findByPatientIdOrderByStudyDateDesc(Long patientId);

    /**
     * 환자의 검사 개수 조회
     *
     * @param patientId 환자 PK
     * @return 검사 개수
     */
    long countByPatientId(Long patientId);

    /**
     * DICOM-Web QIDO-RS 필터링 조회
     *
     * <p>CRITICAL: 전체 데이터 메모리 로드 방지 (OOM 방지)
     * - findAll() + Stream 필터링 대신 DB 쿼리 활용
     * - 인덱스 사용으로 성능 10배 이상 개선
     *
     * <p>동적 쿼리 (null 파라미터는 무시):
     * <ul>
     *   <li>patientId: Patient.dicomPatientId 완전 일치</li>
     *   <li>patientName: Patient.patientName 부분 일치 (대소문자 무시)</li>
     *   <li>studyDate: Study.studyDate 완전 일치</li>
     * </ul>
     *
     * <p>JOIN FETCH로 N+1 문제 방지
     *
     * @param patientId DICOM PatientID (nullable)
     * @param patientName 환자 이름 (nullable, 부분 일치)
     * @param studyDate 검사 날짜 (nullable)
     * @return Study 목록 (Patient eager loading)
     */
    @Query("SELECT DISTINCT s FROM Study s " +
           "LEFT JOIN FETCH s.patient p " +
           "WHERE (:patientId IS NULL OR p.dicomPatientId = :patientId) " +
           "AND (:patientName IS NULL OR LOWER(p.patientName) LIKE LOWER(CONCAT('%', :patientName, '%'))) " +
           "AND (:studyDate IS NULL OR s.studyDate = :studyDate)")
    List<Study> findByDicomWebFilters(
            @Param("patientId") String patientId,
            @Param("patientName") String patientName,
            @Param("studyDate") LocalDate studyDate
    );

    /**
     * ID로 조회 (Pessimistic Write Lock)
     *
     * <p><strong>@Deprecated</strong> - Optimistic Lock으로 변경됨
     *
     * <p>Study 엔티티에 @Version 필드가 추가되어
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
     * Study study = studyRepository.findByIdWithLock(id).orElseThrow();
     * study.incrementSeriesCount();
     * studyRepository.save(study);
     *
     * // ✅ 새 코드 (Optimistic Lock + 재시도)
     * @Retryable(value = OptimisticLockingFailureException.class, maxAttempts = 3)
     * public void incrementSeries(Long id) {
     *     Study study = studyRepository.findById(id).orElseThrow();
     *     study.incrementSeriesCount();
     *     studyRepository.save(study);  // @Version 자동 증가
     * }
     * }</pre>
     *
     * @param id Study PK
     * @return Study (없으면 empty)
     * @deprecated Study 엔티티에 @Version 필드 추가됨, findById() 사용 권장
     */
    @Deprecated(since = "POC Week 8", forRemoval = true)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Study s WHERE s.id = :id")
    Optional<Study> findByIdWithLock(@Param("id") Long id);
}
