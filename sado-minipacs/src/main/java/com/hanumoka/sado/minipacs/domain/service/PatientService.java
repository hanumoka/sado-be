package com.hanumoka.sado.minipacs.domain.service;

import com.hanumoka.sado.common.exception.ResourceNotFoundException;
import com.hanumoka.sado.minipacs.domain.entity.Patient;
import com.hanumoka.sado.minipacs.domain.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Patient Service
 *
 * 환자 생성, 조회, Identity Resolution 기능 제공
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class PatientService {

    private final PatientRepository patientRepository;

    /**
     * Patient PK로 조회
     *
     * @param id Patient PK
     * @return Patient 엔티티
     * @throws ResourceNotFoundException 환자가 존재하지 않는 경우
     */
    public Patient findById(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found with id: " + id));
    }

    /**
     * 전체 환자 목록 조회
     *
     * @return 모든 Patient 목록
     */
    public List<Patient> findAll() {
        return patientRepository.findAll();
    }

    /**
     * DICOM PatientID + Issuer로 조회
     *
     * @param dicomPatientId DICOM PatientID (0010,0020)
     * @param issuerOfPatientId Issuer of PatientID (0010,0021)
     * @return Patient 엔티티 (Optional)
     */
    public Optional<Patient> findByDicomPatientId(String dicomPatientId, String issuerOfPatientId) {
        return patientRepository.findByDicomPatientIdAndIssuerOfPatientId(dicomPatientId, issuerOfPatientId);
    }

    /**
     * EMR 환자 ID로 조회
     *
     * @param emrPatientId EMR 시스템의 환자 ID
     * @return Patient 엔티티 (Optional)
     */
    public Optional<Patient> findByEmrPatientId(String emrPatientId) {
        return patientRepository.findByEmrPatientId(emrPatientId);
    }

    /**
     * 환자 필터링 조회 (이름, 성별)
     *
     * <p>CRITICAL: OOM 방지
     * - findAll() + Stream 필터링 대신 DB 쿼리 활용
     * - null 파라미터는 무시 (동적 쿼리)
     * - 인덱스 활용으로 성능 개선
     *
     * @param name 환자 이름 (nullable, 부분 일치)
     * @param gender 성별 (nullable, M/F/O)
     * @return Patient 목록
     */
    public List<Patient> findByFilters(String name, String gender) {
        log.debug("Searching patients with filters: name={}, gender={}", name, gender);

        // "ALL" gender 필터는 null로 처리
        String genderFilter = (gender != null && "ALL".equals(gender)) ? null : gender;

        List<Patient> patients = patientRepository.findByFilters(name, genderFilter);

        log.debug("Found {} patients matching filters", patients.size());
        return patients;
    }

    /**
     * 환자 생성
     *
     * @param patient Patient 엔티티
     * @return 저장된 Patient
     */
    @Transactional
    public Patient createPatient(Patient patient) {
        log.info("Creating new patient: dicomPatientId={}, issuer={}",
                patient.getDicomPatientId(),
                patient.getIssuerOfPatientId());

        return patientRepository.save(patient);
    }

    /**
     * Identity Resolution: DICOM PatientID로 환자 찾기 또는 생성
     *
     * DICOM C-STORE 수신 시 사용:
     * 1. DICOM PatientID + Issuer로 기존 환자 검색
     * 2. 없으면 새로운 Patient 생성
     *
     * @param dicomPatientId DICOM PatientID
     * @param issuerOfPatientId Issuer of PatientID
     * @param patientName 환자 이름
     * @param patientBirthDate 생년월일
     * @param patientSex 성별
     * @return 찾거나 생성된 Patient
     */
    @Transactional
    public Patient findOrCreatePatient(
            String dicomPatientId,
            String issuerOfPatientId,
            String patientName,
            java.time.LocalDate patientBirthDate,
            String patientSex) {

        // 1. 기존 환자 검색
        Optional<Patient> existingPatient = findByDicomPatientId(dicomPatientId, issuerOfPatientId);

        if (existingPatient.isPresent()) {
            log.debug("Found existing patient: id={}, dicomPatientId={}",
                    existingPatient.get().getId(),
                    dicomPatientId);
            return existingPatient.get();
        }

        // 2. 새 환자 생성 시도
        try {
            Patient newPatient = Patient.builder()
                    .dicomPatientId(dicomPatientId)
                    .issuerOfPatientId(issuerOfPatientId)
                    .patientName(patientName)
                    .patientBirthDate(patientBirthDate)
                    .patientSex(patientSex)
                    .build();

            Patient saved = patientRepository.saveAndFlush(newPatient);
            log.info("Created new patient: id={}, dicomPatientId={}",
                    saved.getId(),
                    dicomPatientId);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Race condition 발생 - 다른 스레드가 먼저 저장함
            log.warn("Race condition detected for patient: {}/{}", dicomPatientId, issuerOfPatientId);
            return patientRepository
                    .findByDicomPatientIdAndIssuerOfPatientId(dicomPatientId, issuerOfPatientId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Patient should exist after DataIntegrityViolationException"));
        }
    }

    /**
     * 환자 업데이트
     *
     * @param patient Patient 엔티티
     * @return 업데이트된 Patient
     */
    @Transactional
    public Patient updatePatient(Patient patient) {
        // 존재 여부 확인
        findById(patient.getId());

        log.info("Updating patient: id={}", patient.getId());
        return patientRepository.save(patient);
    }

    /**
     * 환자 삭제
     *
     * @param id Patient PK
     */
    @Transactional
    public void deletePatient(Long id) {
        Patient patient = findById(id);

        log.info("Deleting patient: id={}, dicomPatientId={}",
                id,
                patient.getDicomPatientId());

        patientRepository.delete(patient);
    }
}
