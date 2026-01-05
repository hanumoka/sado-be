package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Patient Repository
 *
 * 멀티테넌시: Hibernate Filter 자동 적용 (TenantAwareEntity 상속)
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

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
