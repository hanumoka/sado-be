package com.hanumoka.sado.minipacs.controller;

import com.hanumoka.sado.common.dto.ApiResponse;
import com.hanumoka.sado.minipacs.domain.entity.Patient;
import com.hanumoka.sado.minipacs.domain.entity.Series;
import com.hanumoka.sado.minipacs.domain.entity.Study;
import com.hanumoka.sado.minipacs.domain.service.PatientService;
import com.hanumoka.sado.minipacs.domain.service.SeriesService;
import com.hanumoka.sado.minipacs.domain.service.StudyService;
import com.hanumoka.sado.minipacs.dto.request.CreateStudyRequest;
import com.hanumoka.sado.minipacs.dto.request.UpdateStudyRequest;
import com.hanumoka.sado.minipacs.dto.response.SeriesResponse;
import com.hanumoka.sado.minipacs.dto.response.StudyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Study REST API Controller
 *
 * DICOM 검사 정보 관리 API
 */
@RestController
@RequestMapping("/api/studies")
@RequiredArgsConstructor
@Slf4j
@Validated
public class StudyController {

    private final StudyService studyService;
    private final PatientService patientService;
    private final SeriesService seriesService;

    /**
     * 검사 목록 조회
     * GET /api/studies
     *
     * <p>필터링 파라미터 (모두 optional):
     * <ul>
     *   <li>patientId: DICOM Patient ID로 필터링</li>
     *   <li>patientName: 환자 이름으로 필터링 (부분 일치)</li>
     *   <li>studyDate: 검사 날짜로 필터링 (YYYY-MM-DD)</li>
     * </ul>
     */
    @GetMapping
    public ApiResponse<List<StudyResponse>> getAllStudies(
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String patientName,
            @RequestParam(required = false) LocalDate studyDate) {
        log.info("GET /api/studies?patientId={}&patientName={}&studyDate={}",
                patientId, patientName, studyDate);

        List<Study> studies = studyService.findByDicomWebFilters(patientId, patientName, studyDate);

        List<StudyResponse> response = studies.stream()
                .map(this::toResponse)
                .toList();

        return ApiResponse.success(response);
    }

    /**
     * 검사 생성
     * POST /api/studies
     */
    @PostMapping
    public ApiResponse<StudyResponse> createStudy(@Valid @RequestBody CreateStudyRequest request) {
        log.info("POST /api/studies - patientId: {}", request.getPatientId());

        // 1. DTO → Entity 변환
        Study study = toEntity(request);

        // 2. Service 호출
        Study savedStudy = studyService.createStudy(study);

        // 3. Entity → Response DTO 변환
        StudyResponse response = toResponse(savedStudy);

        // 4. 성공 응답 반환
        return ApiResponse.success(response);
    }

    /**
     * 검사 조회
     * GET /api/studies/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<StudyResponse> getStudy(@PathVariable Long id) {
        log.info("GET /api/studies/{}", id);

        // 1. Service 호출
        Study study = studyService.findById(id);

        // 2. Entity → Response DTO 변환
        StudyResponse response = toResponse(study);

        // 3. 성공 응답 반환
        return ApiResponse.success(response);
    }

    /**
     * 검사 수정
     * PUT /api/studies/{id}
     */
    @PutMapping("/{id}")
    public ApiResponse<StudyResponse> updateStudy(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStudyRequest request) {
        log.info("PUT /api/studies/{}", id);

        // 1. 기존 검사 조회
        Study study = studyService.findById(id);

        // 2. Request DTO로 Entity 업데이트 (부분 업데이트)
        updateEntity(study, request);

        // 3. Service 호출
        Study updatedStudy = studyService.updateStudy(study);

        // 4. Entity → Response DTO 변환
        StudyResponse response = toResponse(updatedStudy);

        // 5. 성공 응답 반환
        return ApiResponse.success(response);
    }

    /**
     * 검사 삭제
     * DELETE /api/studies/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteStudy(@PathVariable Long id) {
        log.info("DELETE /api/studies/{}", id);

        // 1. Service 호출
        studyService.deleteStudy(id);

        // 2. 성공 응답 반환 (데이터 없음)
        return ApiResponse.success();
    }

    /**
     * 검사의 시리즈 목록 조회
     * GET /api/studies/{id}/series
     */
    @GetMapping("/{id}/series")
    public ApiResponse<List<SeriesResponse>> getStudySeries(@PathVariable Long id) {
        log.info("GET /api/studies/{}/series", id);

        // 1. Service 호출
        List<Series> seriesList = seriesService.findByStudyId(id);

        // 2. Entity → Response DTO 변환
        List<SeriesResponse> response = seriesList.stream()
                .map(this::toSeriesResponse)
                .toList();

        // 3. 성공 응답 반환
        return ApiResponse.success(response);
    }

    // ========== Helper Methods: Entity ↔ DTO 변환 ==========

    /**
     * CreateStudyRequest → Study Entity 변환
     */
    private Study toEntity(CreateStudyRequest request) {
        // Patient 조회
        Patient patient = patientService.findById(request.getPatientId());

        Study study = new Study();
        study.setPatient(patient);
        study.setStudyInstanceUid(request.getStudyInstanceUid());
        study.setStudyDate(request.getStudyDate());
        study.setStudyDescription(request.getStudyDescription());
        return study;
    }

    /**
     * UpdateStudyRequest → Study Entity 변환 (기존 Entity 업데이트)
     */
    private void updateEntity(Study study, UpdateStudyRequest request) {
        if (request.getStudyInstanceUid() != null) {
            study.setStudyInstanceUid(request.getStudyInstanceUid());
        }
        if (request.getStudyDate() != null) {
            study.setStudyDate(request.getStudyDate());
        }
        if (request.getStudyDescription() != null) {
            study.setStudyDescription(request.getStudyDescription());
        }
    }

    /**
     * Study Entity → StudyResponse 변환
     */
    private StudyResponse toResponse(Study study) {
        return StudyResponse.builder()
                .id(study.getId())
                .uuid(study.getUuid())
                .patientId(study.getPatient().getId())
                .patientName(study.getPatient().getPatientName())
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

    /**
     * Series Entity → SeriesResponse 변환
     */
    private SeriesResponse toSeriesResponse(Series series) {
        return SeriesResponse.builder()
                .id(series.getId())
                .uuid(series.getUuid())
                .studyId(series.getStudy().getId())
                .seriesInstanceUid(series.getSeriesInstanceUid())
                .modality(series.getModality())
                .seriesDescription(series.getSeriesDescription())
                .bodyPartExamined(series.getBodyPartExamined())
                .manufacturer(series.getManufacturer())
                .manufacturerModelName(series.getManufacturerModelName())
                .seriesNumber(series.getSeriesNumber())
                .numberOfInstances(series.getNumberOfInstances())
                .createdAt(series.getCreatedAt())
                .updatedAt(series.getUpdatedAt())
                .tenantId(series.getTenantId())
                .build();
    }
}
