package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.Study;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * DICOM Study Instance UID로 조회
     *
     * @param studyInstanceUid DICOM Study UID
     * @return 검사 (없으면 empty)
     */
    Optional<Study> findByStudyInstanceUid(String studyInstanceUid);

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
