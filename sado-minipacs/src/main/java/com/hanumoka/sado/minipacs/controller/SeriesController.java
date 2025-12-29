package com.hanumoka.sado.minipacs.controller;

import com.hanumoka.sado.common.dto.ApiResponse;
import com.hanumoka.sado.minipacs.domain.entity.Instance;
import com.hanumoka.sado.minipacs.domain.entity.Series;
import com.hanumoka.sado.minipacs.domain.entity.Study;
import com.hanumoka.sado.minipacs.domain.service.InstanceService;
import com.hanumoka.sado.minipacs.domain.service.SeriesService;
import com.hanumoka.sado.minipacs.domain.service.StudyService;
import com.hanumoka.sado.minipacs.dto.request.CreateSeriesRequest;
import com.hanumoka.sado.minipacs.dto.request.UpdateSeriesRequest;
import com.hanumoka.sado.minipacs.dto.response.InstanceResponse;
import com.hanumoka.sado.minipacs.dto.response.SeriesResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Series REST API Controller
 *
 * DICOM 시리즈 정보 관리 API
 */
@RestController
@RequestMapping("/api/series")
@RequiredArgsConstructor
@Slf4j
public class SeriesController {

    private final SeriesService seriesService;
    private final StudyService studyService;
    private final InstanceService instanceService;

    /**
     * 시리즈 생성
     * POST /api/series
     */
    @PostMapping
    public ApiResponse<SeriesResponse> createSeries(@RequestBody CreateSeriesRequest request) {
        log.info("POST /api/series - studyId: {}", request.getStudyId());

        // 1. DTO → Entity 변환
        Series series = toEntity(request);

        // 2. Service 호출
        Series savedSeries = seriesService.createSeries(series);

        // 3. Entity → Response DTO 변환
        SeriesResponse response = toResponse(savedSeries);

        // 4. 성공 응답 반환
        return ApiResponse.success(response);
    }

    /**
     * 시리즈 조회
     * GET /api/series/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<SeriesResponse> getSeries(@PathVariable Long id) {
        log.info("GET /api/series/{}", id);

        // 1. Service 호출
        Series series = seriesService.findById(id);

        // 2. Entity → Response DTO 변환
        SeriesResponse response = toResponse(series);

        // 3. 성공 응답 반환
        return ApiResponse.success(response);
    }

    /**
     * 시리즈 수정
     * PUT /api/series/{id}
     */
    @PutMapping("/{id}")
    public ApiResponse<SeriesResponse> updateSeries(
            @PathVariable Long id,
            @RequestBody UpdateSeriesRequest request) {
        log.info("PUT /api/series/{}", id);

        // 1. 기존 시리즈 조회
        Series series = seriesService.findById(id);

        // 2. Request DTO로 Entity 업데이트 (부분 업데이트)
        updateEntity(series, request);

        // 3. Service 호출
        Series updatedSeries = seriesService.updateSeries(series);

        // 4. Entity → Response DTO 변환
        SeriesResponse response = toResponse(updatedSeries);

        // 5. 성공 응답 반환
        return ApiResponse.success(response);
    }

    /**
     * 시리즈 삭제
     * DELETE /api/series/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSeries(@PathVariable Long id) {
        log.info("DELETE /api/series/{}", id);

        // 1. Service 호출
        seriesService.deleteSeries(id);

        // 2. 성공 응답 반환 (데이터 없음)
        return ApiResponse.success();
    }

    /**
     * 시리즈의 영상 목록 조회
     * GET /api/series/{id}/instances
     */
    @GetMapping("/{id}/instances")
    public ApiResponse<List<InstanceResponse>> getSeriesInstances(@PathVariable Long id) {
        log.info("GET /api/series/{}/instances", id);

        // 1. Service 호출
        List<Instance> instances = instanceService.findBySeriesId(id);

        // 2. Entity → Response DTO 변환
        List<InstanceResponse> response = instances.stream()
                .map(this::toInstanceResponse)
                .collect(Collectors.toList());

        // 3. 성공 응답 반환
        return ApiResponse.success(response);
    }

    // ========== Helper Methods: Entity ↔ DTO 변환 ==========

    /**
     * CreateSeriesRequest → Series Entity 변환
     */
    private Series toEntity(CreateSeriesRequest request) {
        // Study 조회
        Study study = studyService.findById(request.getStudyId());

        Series series = new Series();
        series.setStudy(study);
        series.setSeriesInstanceUid(request.getSeriesInstanceUid());
        series.setModality(request.getModality());
        series.setSeriesDescription(request.getSeriesDescription());
        series.setBodyPartExamined(request.getBodyPartExamined());
        series.setManufacturer(request.getManufacturer());
        series.setManufacturerModelName(request.getManufacturerModelName());
        series.setSeriesNumber(request.getSeriesNumber());
        return series;
    }

    /**
     * UpdateSeriesRequest → Series Entity 변환 (기존 Entity 업데이트)
     */
    private void updateEntity(Series series, UpdateSeriesRequest request) {
        if (request.getSeriesInstanceUid() != null) {
            series.setSeriesInstanceUid(request.getSeriesInstanceUid());
        }
        if (request.getModality() != null) {
            series.setModality(request.getModality());
        }
        if (request.getSeriesDescription() != null) {
            series.setSeriesDescription(request.getSeriesDescription());
        }
        if (request.getBodyPartExamined() != null) {
            series.setBodyPartExamined(request.getBodyPartExamined());
        }
        if (request.getManufacturer() != null) {
            series.setManufacturer(request.getManufacturer());
        }
        if (request.getManufacturerModelName() != null) {
            series.setManufacturerModelName(request.getManufacturerModelName());
        }
        if (request.getSeriesNumber() != null) {
            series.setSeriesNumber(request.getSeriesNumber());
        }
    }

    /**
     * Series Entity → SeriesResponse 변환
     */
    private SeriesResponse toResponse(Series series) {
        return SeriesResponse.builder()
                .id(series.getId())
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

    /**
     * Instance Entity → InstanceResponse 변환
     */
    private InstanceResponse toInstanceResponse(Instance instance) {
        return InstanceResponse.builder()
                .id(instance.getId())
                .seriesId(instance.getSeries().getId())
                .sopInstanceUid(instance.getSopInstanceUid())
                .sopClassUid(instance.getSopClassUid())
                .rows(instance.getImageRows())
                .columns(instance.getImageColumns())
                .numberOfFrames(instance.getNumberOfFrames())
                .frameRate(instance.getFrameRate())
                .frameRateSource(instance.getFrameRateSource())
                .instanceNumber(instance.getInstanceNumber())
                .storagePath(instance.getStoragePath())
                .fileSize(instance.getFileSize())
                .transcodingStatus(instance.getTranscodingStatus() != null ?
                    instance.getTranscodingStatus().name() : null)
                .thumbnailPath(instance.getThumbnailPath())
                .videoPath(instance.getVideoPath())
                .storageTier(instance.getStorageTier() != null ?
                    instance.getStorageTier().name() : null)
                .createdAt(instance.getCreatedAt())
                .updatedAt(instance.getUpdatedAt())
                .tenantId(instance.getTenantId())
                .build();
    }
}
