package com.hanumoka.sado.minipacs.controller;

import com.hanumoka.sado.common.dto.ApiResponse;
import com.hanumoka.sado.minipacs.domain.entity.Instance;
import com.hanumoka.sado.minipacs.domain.entity.Series;
import com.hanumoka.sado.minipacs.domain.service.InstanceService;
import com.hanumoka.sado.minipacs.domain.service.SeriesService;
import com.hanumoka.sado.minipacs.dto.request.CreateInstanceRequest;
import com.hanumoka.sado.minipacs.dto.request.UpdateInstanceRequest;
import com.hanumoka.sado.minipacs.dto.response.InstanceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Instance REST API Controller
 *
 * DICOM 영상 정보 관리 API
 */
@RestController
@RequestMapping("/api/instances")
@RequiredArgsConstructor
@Slf4j
public class InstanceController {

    private final InstanceService instanceService;
    private final SeriesService seriesService;

    /**
     * 영상 생성
     * POST /api/instances
     */
    @PostMapping
    public ApiResponse<InstanceResponse> createInstance(@RequestBody CreateInstanceRequest request) {
        log.info("POST /api/instances - seriesId: {}", request.getSeriesId());

        // 1. DTO → Entity 변환
        Instance instance = toEntity(request);

        // 2. Service 호출
        Instance savedInstance = instanceService.createInstance(instance);

        // 3. Entity → Response DTO 변환
        InstanceResponse response = toResponse(savedInstance);

        // 4. 성공 응답 반환
        return ApiResponse.success(response);
    }

    /**
     * 영상 조회
     * GET /api/instances/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<InstanceResponse> getInstance(@PathVariable Long id) {
        log.info("GET /api/instances/{}", id);

        // 1. Service 호출
        Instance instance = instanceService.findById(id);

        // 2. Entity → Response DTO 변환
        InstanceResponse response = toResponse(instance);

        // 3. 성공 응답 반환
        return ApiResponse.success(response);
    }

    /**
     * 영상 수정
     * PUT /api/instances/{id}
     */
    @PutMapping("/{id}")
    public ApiResponse<InstanceResponse> updateInstance(
            @PathVariable Long id,
            @RequestBody UpdateInstanceRequest request) {
        log.info("PUT /api/instances/{}", id);

        // 1. 기존 영상 조회
        Instance instance = instanceService.findById(id);

        // 2. Request DTO로 Entity 업데이트 (부분 업데이트)
        updateEntity(instance, request);

        // 3. Service 호출
        Instance updatedInstance = instanceService.updateInstance(instance);

        // 4. Entity → Response DTO 변환
        InstanceResponse response = toResponse(updatedInstance);

        // 5. 성공 응답 반환
        return ApiResponse.success(response);
    }

    /**
     * 영상 삭제
     * DELETE /api/instances/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteInstance(@PathVariable Long id) {
        log.info("DELETE /api/instances/{}", id);

        // 1. Service 호출
        instanceService.deleteInstance(id);

        // 2. 성공 응답 반환 (데이터 없음)
        return ApiResponse.success();
    }

    // ========== Helper Methods: Entity ↔ DTO 변환 ==========

    /**
     * CreateInstanceRequest → Instance Entity 변환
     */
    private Instance toEntity(CreateInstanceRequest request) {
        // Series 조회
        Series series = seriesService.findById(request.getSeriesId());

        Instance instance = new Instance();
        instance.setSeries(series);
        instance.setSopInstanceUid(request.getSopInstanceUid());
        instance.setSopClassUid(request.getSopClassUid());
        instance.setRows(request.getRows());
        instance.setColumns(request.getColumns());
        instance.setNumberOfFrames(request.getNumberOfFrames());
        instance.setFrameRate(request.getFrameRate());
        instance.setFrameRateSource(request.getFrameRateSource());
        instance.setInstanceNumber(request.getInstanceNumber());
        instance.setStoragePath(request.getStoragePath());
        instance.setFileSize(request.getFileSize());
        return instance;
    }

    /**
     * UpdateInstanceRequest → Instance Entity 변환 (기존 Entity 업데이트)
     */
    private void updateEntity(Instance instance, UpdateInstanceRequest request) {
        if (request.getSopInstanceUid() != null) {
            instance.setSopInstanceUid(request.getSopInstanceUid());
        }
        if (request.getSopClassUid() != null) {
            instance.setSopClassUid(request.getSopClassUid());
        }
        if (request.getRows() != null) {
            instance.setRows(request.getRows());
        }
        if (request.getColumns() != null) {
            instance.setColumns(request.getColumns());
        }
        if (request.getNumberOfFrames() != null) {
            instance.setNumberOfFrames(request.getNumberOfFrames());
        }
        if (request.getFrameRate() != null) {
            instance.setFrameRate(request.getFrameRate());
        }
        if (request.getFrameRateSource() != null) {
            instance.setFrameRateSource(request.getFrameRateSource());
        }
        if (request.getInstanceNumber() != null) {
            instance.setInstanceNumber(request.getInstanceNumber());
        }
        if (request.getStoragePath() != null) {
            instance.setStoragePath(request.getStoragePath());
        }
        if (request.getFileSize() != null) {
            instance.setFileSize(request.getFileSize());
        }
        if (request.getTranscodingStatus() != null) {
            instance.setTranscodingStatus(
                Instance.TranscodingStatus.valueOf(request.getTranscodingStatus())
            );
        }
        if (request.getThumbnailPath() != null) {
            instance.setThumbnailPath(request.getThumbnailPath());
        }
        if (request.getVideoPath() != null) {
            instance.setVideoPath(request.getVideoPath());
        }
        if (request.getStorageTier() != null) {
            instance.setStorageTier(
                Instance.StorageTier.valueOf(request.getStorageTier())
            );
        }
    }

    /**
     * Instance Entity → InstanceResponse 변환
     */
    private InstanceResponse toResponse(Instance instance) {
        return InstanceResponse.builder()
                .id(instance.getId())
                .seriesId(instance.getSeries().getId())
                .sopInstanceUid(instance.getSopInstanceUid())
                .sopClassUid(instance.getSopClassUid())
                .rows(instance.getRows())
                .columns(instance.getColumns())
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
