package com.hanumoka.sado.minipacs.domain.repository;

import com.hanumoka.sado.minipacs.domain.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
