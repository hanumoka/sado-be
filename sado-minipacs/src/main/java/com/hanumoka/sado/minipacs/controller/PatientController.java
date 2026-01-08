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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Patient REST API Controller
 *
 * DICOM 환자 정보 관리 API
 */
@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@Slf4j
@Validated
public class PatientController {

    private final PatientService patientService;
    private final StudyService studyService;

    /**
     * 환자 목록 조회
     * GET /api/patients
     *
     * <p>N+1 문제 방지: 배치 쿼리로 모든 환자의 studiesCount, lastStudyDate 조회
     */
    @GetMapping
    public ApiResponse<List<PatientResponse>> getAllPatients(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String gender) {
        log.info("GET /api/patients?name={}&gender={}", name, gender);

        // CRITICAL: OOM 방지 - DB 쿼리로 필터링 (findAll() + Stream 제거)
        List<Patient> patients = patientService.findByFilters(name, gender);

        // N+1 방지: 배치 쿼리로 모든 환자의 통계 조회
        List<Long> patientIds = patients.stream()
                .map(Patient::getId)
                .toList();
        Map<Long, StudyService.StudyStats> statsMap = studyService.getStudyStatsByPatientIds(patientIds);

        List<PatientResponse> response = patients.stream()
                .map(patient -> toResponseWithStats(patient, statsMap.get(patient.getId())))
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
     *
     * <p>단일 환자 조회: 개별 쿼리로 studiesCount, lastStudyDate 조회
     */
    @GetMapping("/{id}")
    public ApiResponse<PatientResponse> getPatient(@PathVariable Long id) {
        log.info("GET /api/patients/{}", id);
        Patient patient = patientService.findById(id);

        // 단일 조회: 개별 쿼리로 통계 조회
        long studiesCount = studyService.countByPatientId(id);
        LocalDate lastStudyDate = studyService.getLastStudyDate(id);
        StudyService.StudyStats stats = new StudyService.StudyStats((int) studiesCount, lastStudyDate);

        PatientResponse response = toResponseWithStats(patient, stats);
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
     * Patient Entity → PatientResponse 변환 (통계 없이)
     *
     * <p>CREATE, UPDATE 응답에서 사용 (통계 조회 불필요)
     */
    private PatientResponse toResponse(Patient patient) {
        return toResponseWithStats(patient, null);
    }

    /**
     * Patient Entity → PatientResponse 변환 (통계 포함)
     *
     * <p>CHANGED (2026-01-05): 통계 필드 동적 조회
     * <ul>
     *   <li>studiesCount, lastStudyDate를 동적으로 조회</li>
     *   <li>목록 조회: 배치 쿼리로 N+1 방지</li>
     *   <li>단일 조회: 개별 쿼리로 조회</li>
     * </ul>
     *
     * @param patient 환자 엔티티
     * @param stats 검사 통계 (null이면 0, null로 설정)
     */
    private PatientResponse toResponseWithStats(Patient patient, StudyService.StudyStats stats) {
        return PatientResponse.builder()
                .id(patient.getId())
                .uuid(patient.getUuid())
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
                .studiesCount(stats != null ? stats.studiesCount() : 0)
                .lastStudyDate(stats != null ? stats.lastStudyDate() : null)
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
                .uuid(study.getUuid())
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
