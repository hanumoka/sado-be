package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.Patient;
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
 * Patient Repository
 *
 * 멀티테넌시: Hibernate Filter 자동 적용 (TenantAwareEntity 상속)
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

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
     * @param dicomPatientId DICOM Patient ID (0010,0020)
     * @param issuer Issuer of Patient ID (0010,0021), 빈 문자열로 정규화
     * @param name 환자 이름
     * @param birthDate 생년월일
     * @param sex 성별 (M/F/O)
     */
    @Modifying
    @Query(value = """
        INSERT INTO patient (tenant_id, dicom_patient_id, issuer_of_patient_id,
                            patient_name, patient_birth_date, patient_sex,
                            created_at, updated_at)
        VALUES (:tenantId, :dicomPatientId, :issuer, :name, :birthDate, :sex, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
            id = LAST_INSERT_ID(id),
            patient_name = COALESCE(:name, patient_name),
            patient_birth_date = COALESCE(:birthDate, patient_birth_date),
            patient_sex = COALESCE(:sex, patient_sex),
            updated_at = NOW()
        """, nativeQuery = true)
    void upsertPatient(
        @Param("tenantId") Long tenantId,
        @Param("dicomPatientId") String dicomPatientId,
        @Param("issuer") String issuer,
        @Param("name") String name,
        @Param("birthDate") LocalDate birthDate,
        @Param("sex") String sex
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
     * DICOM PatientID + Issuer로 환자 조회
     *
     * @param dicomPatientId DICOM PatientID
     * @param issuerOfPatientId Issuer of PatientID
     * @return 환자 (없으면 empty)
     */
    Optional<Patient> findByDicomPatientIdAndIssuerOfPatientId(
            String dicomPatientId,
            String issuerOfPatientId
    );

    /**
     * DICOM PatientID + Issuer로 환자 조회 (Pessimistic Write Lock)
     *
     * <p><strong>@Deprecated (2026-01-05)</strong> - Insert-first 패턴으로 변경됨
     *
     * <p>변경 이유:
     * <ul>
     *   <li>Pessimistic Lock은 기존 행만 보호, INSERT 시 Gap Lock Deadlock 발생</li>
     *   <li>3파일 동시 업로드 시 2개 Deadlock (ErrorCode: 1213)</li>
     *   <li>PatientService.findOrCreatePatient()에서 Insert-first 패턴으로 변경</li>
     *   <li>InstanceService.findOrCreateInstance() 패턴으로 통일</li>
     * </ul>
     *
     * <p>새로운 방식:
     * <ul>
     *   <li>INSERT 먼저 시도 (Lock 없이 빠른 실행)</li>
     *   <li>Race Condition 시 DataIntegrityViolationException</li>
     *   <li>Exception 발생 시 기존 Patient SELECT</li>
     *   <li>Deadlock 완전 제거, 66% 병렬 처리 가능</li>
     * </ul>
     *
     * @param dicomPatientId DICOM PatientID
     * @param issuerOfPatientId Issuer of PatientID
     * @return 환자 (없으면 empty), Lock과 함께 반환
     * @deprecated Insert-first 패턴 사용, Lock 메서드 불필요
     */
    @Deprecated(since = "2026-01-05", forRemoval = false)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Patient p WHERE p.dicomPatientId = :dicomPatientId AND p.issuerOfPatientId = :issuerOfPatientId")
    Optional<Patient> findByDicomPatientIdWithLock(
            @Param("dicomPatientId") String dicomPatientId,
            @Param("issuerOfPatientId") String issuerOfPatientId
    );

    /**
     * EMR 환자 ID로 조회
     *
     * @param emrPatientId EMR 시스템의 환자 ID
     * @return 환자 (없으면 empty)
     */
    Optional<Patient> findByEmrPatientId(String emrPatientId);

    /**
     * 환자 필터링 조회 (이름, 성별)
     *
     * <p>CRITICAL: 전체 데이터 메모리 로드 방지 (OOM 방지)
     * - findAll() + Stream 필터링 대신 DB 쿼리 활용
     * - 인덱스 사용으로 성능 개선
     *
     * <p>동적 쿼리 (null 파라미터는 무시):
     * <ul>
     *   <li>name: patientName 부분 일치 (대소문자 무시)</li>
     *   <li>gender: patientSex 완전 일치</li>
     * </ul>
     *
     * @param name 환자 이름 (nullable, 부분 일치)
     * @param gender 성별 (nullable, M/F/O)
     * @return Patient 목록
     */
    @Query("SELECT p FROM Patient p " +
           "WHERE (:name IS NULL OR LOWER(p.patientName) LIKE LOWER(CONCAT('%', :name, '%'))) " +
           "AND (:gender IS NULL OR p.patientSex = :gender)")
    List<Patient> findByFilters(
            @Param("name") String name,
            @Param("gender") String gender
    );
}
