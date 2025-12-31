package com.hanumoka.sado.minipacs.domain.service;

import com.hanumoka.sado.common.exception.ResourceNotFoundException;
import com.hanumoka.sado.minipacs.domain.entity.Patient;
import com.hanumoka.sado.minipacs.domain.entity.Study;
import com.hanumoka.sado.minipacs.domain.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Study Service
 *
 * 검사(Study) 생성, 조회 기능 제공
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class StudyService {

    private final StudyRepository studyRepository;
    private final PatientService patientService;

    /**
     * Study PK로 조회
     *
     * @param id Study PK
     * @return Study 엔티티
     * @throws ResourceNotFoundException Study가 존재하지 않는 경우
     */
    public Study findById(Long id) {
        return studyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Study not found with id: " + id));
    }

    /**
     * DICOM Study Instance UID로 조회
     *
     * @param studyInstanceUid DICOM Study Instance UID (0020,000D)
     * @return Study 엔티티 (Optional)
     */
    public Optional<Study> findByStudyInstanceUid(String studyInstanceUid) {
        return studyRepository.findByStudyInstanceUid(studyInstanceUid);
    }

    /**
     * 특정 환자의 모든 검사 조회 (최신순)
     *
     * @param patientId Patient PK
     * @return Study 목록
     */
    public List<Study> findByPatientId(Long patientId) {
        // Patient 존재 여부 확인
        patientService.findById(patientId);

        return studyRepository.findByPatientIdOrderByStudyDateDesc(patientId);
    }

    /**
     * 특정 환자의 검사 개수 조회
     *
     * @param patientId Patient PK
     * @return 검사 개수
     */
    public long countByPatientId(Long patientId) {
        return studyRepository.countByPatientId(patientId);
    }

    /**
     * Study 생성
     *
     * @param study Study 엔티티 (patient 관계 설정 필요)
     * @return 저장된 Study
     */
    @Transactional
    public Study createStudy(Study study) {
        // Patient 존재 여부 확인
        if (study.getPatient() == null || study.getPatient().getId() == null) {
            throw new IllegalArgumentException("Study must have a patient");
        }

        patientService.findById(study.getPatient().getId());

        log.info("Creating new study: studyInstanceUid={}, patientId={}",
                study.getStudyInstanceUid(),
                study.getPatient().getId());

        return studyRepository.save(study);
    }

    /**
     * Study 찾기 또는 생성
     *
     * DICOM C-STORE 수신 시 사용:
     * 1. Study Instance UID로 기존 Study 검색
     * 2. 없으면 새로운 Study 생성
     *
     * @param studyInstanceUid DICOM Study Instance UID
     * @param patient 소속 환자
     * @param studyDate 검사 날짜
     * @param studyDescription 검사 설명
     * @return 찾거나 생성된 Study
     */
    @Transactional
    public Study findOrCreateStudy(
            String studyInstanceUid,
            Patient patient,
            java.time.LocalDate studyDate,
            String studyDescription) {

        // 1. 기존 Study 검색
        Optional<Study> existingStudy = findByStudyInstanceUid(studyInstanceUid);

        if (existingStudy.isPresent()) {
            log.info("Found existing study: id={}, studyInstanceUid={}",
                    existingStudy.get().getId(),
                    studyInstanceUid);
            return existingStudy.get();
        }

        // 2. 새 Study 생성 (Builder 패턴)
        Study newStudy = Study.builder()
                .patient(patient)
                .studyInstanceUid(studyInstanceUid)
                .studyDate(studyDate)
                .studyDescription(studyDescription)
                .build();

        log.info("Creating new study from DICOM: studyInstanceUid={}, patientId={}",
                studyInstanceUid,
                patient.getId());

        return studyRepository.save(newStudy);
    }

    /**
     * Study 업데이트
     *
     * @param study Study 엔티티
     * @return 업데이트된 Study
     */
    @Transactional
    public Study updateStudy(Study study) {
        // 존재 여부 확인
        findById(study.getId());

        log.info("Updating study: id={}", study.getId());
        return studyRepository.save(study);
    }

    /**
     * Study 삭제
     *
     * @param id Study PK
     */
    @Transactional
    public void deleteStudy(Long id) {
        Study study = findById(id);

        log.info("Deleting study: id={}, studyInstanceUid={}",
                id,
                study.getStudyInstanceUid());

        studyRepository.delete(study);
    }
}
