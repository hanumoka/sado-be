package com.hanumoka.sado.minipacs.domain.service;

import com.hanumoka.sado.common.exception.ResourceNotFoundException;
import com.hanumoka.sado.common.tenant.TenantProvider;
import com.hanumoka.sado.minipacs.domain.entity.Patient;
import com.hanumoka.sado.minipacs.domain.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final TenantProvider tenantProvider;

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
     * </ul>
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

        // issuer NULL 정규화 (MySQL unique constraint 작동을 위해)
        String normalizedIssuer = (issuerOfPatientId != null && !issuerOfPatientId.isEmpty())
            ? issuerOfPatientId
            : "";

        // 1. MySQL Upsert 실행 (Exception 없음, 완전 원자적)
        Long tenantId = tenantProvider.getCurrentTenantId();
        patientRepository.upsertPatient(
            tenantId,
            dicomPatientId,
            normalizedIssuer,
            patientName,
            patientBirthDate,
            patientSex
        );

        // 2. LAST_INSERT_ID() 조회 (같은 커넥션 내에서 유효)
        Long patientId = patientRepository.getLastInsertId();

        log.debug("Upsert patient completed: dicomPatientId={}, resultId={}",
                dicomPatientId, patientId);

        // 3. 엔티티 반환 (1차 캐시 활용)
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalStateException(
                        "Patient should exist after upsert: " + dicomPatientId));
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
