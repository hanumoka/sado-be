package com.hanumoka.sado.minipacs.controller;

import com.hanumoka.sado.common.dto.ApiResponse;
import com.hanumoka.sado.minipacs.domain.entity.Patient;
import com.hanumoka.sado.minipacs.domain.entity.Study;
import com.hanumoka.sado.minipacs.domain.service.PatientService;
import com.hanumoka.sado.minipacs.domain.service.StudyService;
import com.hanumoka.sado.minipacs.dto.request.CreatePatientRequest;
import com.hanumoka.sado.minipacs.dto.request.UpdatePatientRequest;
import com.hanumoka.sado.minipacs.dto.response.PatientResponse;
import com.hanumoka.sado.minipacs.dto.response.StudyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Patient REST API Controller
 *
 * DICOM 환자 정보 관리 API
 */
@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@Slf4j
public class PatientController {

    private final PatientService patientService;
    private final StudyService studyService;

    /**
     * 환자 목록 조회
     * GET /api/patients
     */
    @GetMapping
    public ApiResponse<List<PatientResponse>> getAllPatients(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String gender) {
        log.info("GET /api/patients?name={}&gender={}", name, gender);

        // CRITICAL: OOM 방지 - DB 쿼리로 필터링 (findAll() + Stream 제거)
        List<Patient> patients = patientService.findByFilters(name, gender);

        List<PatientResponse> response = patients.stream()
                .map(this::toResponse)
                .toList();

        return ApiResponse.success(response);
    }

    /**
     * 환자 생성
     * POST /api/patients
     */
    @PostMapping
    public ApiResponse<PatientResponse> createPatient(@Valid @RequestBody CreatePatientRequest request) {
        Patient patient = toEntity(request);
        Patient savedPatient = patientService.createPatient(patient);
        PatientResponse response = toResponse(savedPatient);
        return ApiResponse.success(response);
    }

    /**
     * 환자 조회
     * GET /api/patients/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<PatientResponse> getPatient(@PathVariable Long id) {
        log.info("GET /api/patients/{}", id);
        Patient patient = patientService.findById(id);
        PatientResponse response = toResponse(patient);
        return ApiResponse.success(response);
    }

    /**
     * 환자 수정
     * PUT /api/patients/{id}
     */
    @PutMapping("/{id}")
    public ApiResponse<PatientResponse> updatePatient(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePatientRequest request) {
        log.info("PUT /api/patients/{}", id);
        Patient patient = patientService.findById(id);
        updateEntity(patient, request);
        Patient updatedPatient = patientService.updatePatient(patient);
        PatientResponse response = toResponse(updatedPatient);
        return ApiResponse.success(response);
    }

    /**
     * 환자 삭제
     * DELETE /api/patients/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePatient(@PathVariable Long id) {
        log.info("DELETE /api/patients/{}", id);
        patientService.deletePatient(id);
        return ApiResponse.success();
    }

    /**
     * 환자의 검사 목록 조회
     * GET /api/patients/{id}/studies
     */
    @GetMapping("/{id}/studies")
    public ApiResponse<List<StudyResponse>> getPatientStudies(@PathVariable Long id) {
        log.info("GET /api/patients/{}/studies", id);
        List<Study> studies = studyService.findByPatientId(id);
        List<StudyResponse> response = studies.stream()
                .map(this::toStudyResponse)
                .toList();
        return ApiResponse.success(response);
    }

    /**
     * CreatePatientRequest → Patient Entity 변환
     */
    private Patient toEntity(CreatePatientRequest request) {
        Patient patient = new Patient();
        patient.setDicomPatientId(request.getDicomPatientId());
        patient.setIssuerOfPatientId(request.getIssuerOfPatientId());
        patient.setPatientName(request.getPatientName());
        patient.setPatientBirthDate(request.getPatientBirthDate());
        patient.setPatientSex(request.getPatientSex());
        patient.setEmrPatientId(request.getEmrPatientId());
        return patient;
    }

    /**
     * UpdatePatientRequest → Patient Entity 변환
     */
    private void updateEntity(Patient patient, UpdatePatientRequest request) {
        if (request.getDicomPatientId() != null) {
            patient.setDicomPatientId(request.getDicomPatientId());
        }
        if (request.getIssuerOfPatientId() != null) {
            patient.setIssuerOfPatientId(request.getIssuerOfPatientId());
        }
        if (request.getPatientName() != null) {
            patient.setPatientName(request.getPatientName());
        }
        if (request.getPatientBirthDate() != null) {
            patient.setPatientBirthDate(request.getPatientBirthDate());
        }
        if (request.getPatientSex() != null) {
            patient.setPatientSex(request.getPatientSex());
        }
        if (request.getEmrPatientId() != null) {
            patient.setEmrPatientId(request.getEmrPatientId());
        }
        if (request.getMatchingConfidence() != null) {
            patient.setMatchingConfidence(request.getMatchingConfidence());
        }
        if (request.getMatchingStatus() != null) {
            patient.setMatchingStatus(
                    Patient.MatchingStatus.valueOf(request.getMatchingStatus())
            );
        }
    }

    /**
     * Patient Entity → PatientResponse 변환
     *
     * <p>CHANGED (2026-01-05): 통계 필드 제거
     * <ul>
     *   <li>studiesCount, lastStudyDate 필드 제거됨</li>
     *   <li>필요시 studyRepository.countByPatientId()로 실시간 조회</li>
     *   <li>Deadlock 방지 + 메모리 정합성 보장</li>
     * </ul>
     */
    private PatientResponse toResponse(Patient patient) {
        return PatientResponse.builder()
                .id(patient.getId())
                .dicomPatientId(patient.getDicomPatientId())
                .issuerOfPatientId(patient.getIssuerOfPatientId())
                .issuerTypeCode(patient.getIssuerTypeCode())
                .patientName(patient.getPatientName())
                .patientBirthDate(patient.getPatientBirthDate())
                .patientSex(patient.getPatientSex())
                .emrPatientId(patient.getEmrPatientId())
                .matchingConfidence(patient.getMatchingConfidence())
                .matchingStatus(patient.getMatchingStatus() != null ?
                        patient.getMatchingStatus().name() : null)
                // studiesCount, lastStudyDate 제거 (2026-01-05)
                // 필요시: studyRepository.countByPatientId(patient.getId())
                .createdAt(patient.getCreatedAt())
                .updatedAt(patient.getUpdatedAt())
                .tenantId(patient.getTenantId())
                .build();
    }

    /**
     * Study Entity → StudyResponse 변환
     */
    private StudyResponse toStudyResponse(Study study) {
        return StudyResponse.builder()
                .id(study.getId())
                .patientId(study.getPatient().getId())
                .studyInstanceUid(study.getStudyInstanceUid())
                .studyDate(study.getStudyDate())
                .studyDescription(study.getStudyDescription())
                .numberOfSeries(study.getNumberOfSeries())
                .numberOfInstances(study.getNumberOfInstances())
                .createdAt(study.getCreatedAt())
                .updatedAt(study.getUpdatedAt())
                .tenantId(study.getTenantId())
                .build();
    }
}
