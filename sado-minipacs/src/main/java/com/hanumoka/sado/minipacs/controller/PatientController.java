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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

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
     * 환자 생성
     * POST /api/patients
     */
    @PostMapping
    public ApiResponse<PatientResponse> createPatient(@RequestBody CreatePatientRequest request) {
        // 1. DTO → Entity 변환
        Patient patient = toEntity(request);

        // 2. Service 호출
        Patient savedPatient = patientService.createPatient(patient);

        // 3. Entity → Response DTO 변환
        PatientResponse response = toResponse(savedPatient);

        // 4. 성공 응답 반환
        return ApiResponse.success(response);

    }

    /**
     * 환자 조회
     * GET /api/patients/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<PatientResponse> getPatient(@PathVariable Long id) {
        log.info("GET /api/patients/{}", id);

        // 1. Service 호출
        Patient patient = patientService.findById(id);

        // 2. Entity → Response DTO 변환
        PatientResponse response = toResponse(patient);

        // 3. 성공 응답 반환
        return ApiResponse.success(response);
    }

    /**
     * 환자 수정
     * PUT /api/patients/{id}
     */
    @PutMapping("/{id}")
    public ApiResponse<PatientResponse> updatePatient(
            @PathVariable Long id,
            @RequestBody UpdatePatientRequest request) {
        log.info("PUT /api/patients/{}", id);

        // 1. 기존 환자 조회
        Patient patient = patientService.findById(id);

        // 2. Request DTO로 Entity 업데이트 (부분 업데이트)
        updateEntity(patient, request);

        // 3. Service 호출
        Patient updatedPatient = patientService.updatePatient(patient);

        // 4. Entity → Response DTO 변환
        PatientResponse response = toResponse(updatedPatient);

        // 5. 성공 응답 반환
        return ApiResponse.success(response);
    }

    /**
     * 환자 삭제
     * DELETE /api/patients/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePatient(@PathVariable Long id) {
        log.info("DELETE /api/patients/{}", id);

        // 1. Service 호출
        patientService.deletePatient(id);

        // 2. 성공 응답 반환 (데이터 없음)
        return ApiResponse.success();
    }

    /**
     * 환자의 검사 목록 조회
     * GET /api/patients/{id}/studies
     */
    @GetMapping("/{id}/studies")
    public ApiResponse<List<StudyResponse>> getPatientStudies(@PathVariable Long id) {
        log.info("GET /api/patients/{}/studies", id);

        // 1. Service 호출
        List<Study> studies = studyService.findByPatientId(id);

        // 2. Entity → Response DTO 변환
        List<StudyResponse> response = studies.stream()
                .map(this::toStudyResponse)
                .collect(Collectors.toList());

        // 3. 성공 응답 반환
        return ApiResponse.success(response);
    }

    // ========== Helper Methods: Entity ↔ DTO 변환 ==========

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
     * UpdatePatientRequest → Patient Entity 변환 (기존 Entity 업데이트)
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
