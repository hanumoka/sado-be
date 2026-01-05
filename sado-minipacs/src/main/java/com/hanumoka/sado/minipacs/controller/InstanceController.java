package com.hanumoka.sado.minipacs.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanumoka.sado.common.dto.ApiResponse;
import com.hanumoka.sado.minipacs.domain.entity.DicomMetadataRecord;
import com.hanumoka.sado.minipacs.domain.entity.FileAsset;
import com.hanumoka.sado.minipacs.domain.entity.Instance;
import com.hanumoka.sado.minipacs.domain.entity.Patient;
import com.hanumoka.sado.minipacs.domain.entity.Series;
import com.hanumoka.sado.minipacs.domain.entity.Study;
import com.hanumoka.sado.minipacs.domain.enums.FileCategory;
import com.hanumoka.sado.minipacs.domain.enums.FileStatus;
import com.hanumoka.sado.minipacs.domain.enums.ReferenceType;
import com.hanumoka.sado.minipacs.domain.parser.DicomMetadataExtractor;
import com.hanumoka.sado.minipacs.domain.repository.DicomMetadataRecordRepository;
import com.hanumoka.sado.minipacs.domain.util.DicomFileValidator;
import com.hanumoka.sado.minipacs.domain.repository.FileAssetRepository;
import com.hanumoka.sado.minipacs.domain.service.InstanceService;
import com.hanumoka.sado.minipacs.domain.service.PatientService;
import com.hanumoka.sado.minipacs.domain.service.SeriesService;
import com.hanumoka.sado.minipacs.domain.service.StudyService;
import com.hanumoka.sado.minipacs.storage.service.DicomStorageService;
import com.hanumoka.sado.minipacs.storage.dto.DicomFileMetadata;
import com.hanumoka.sado.minipacs.storage.dto.StorageResult;
import com.hanumoka.sado.minipacs.dto.request.CreateInstanceRequest;
import com.hanumoka.sado.minipacs.dto.request.UpdateInstanceRequest;
import com.hanumoka.sado.minipacs.dto.response.InstanceResponse;
import com.hanumoka.sado.minipacs.storage.dto.FileAccessResponse;
import com.hanumoka.sado.minipacs.storage.strategy.StorageAccessStrategy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

/**
 * Instance REST API Controller
 * <p>
 * DICOM 영상 정보 관리 API
 */
@RestController
@RequestMapping("/api/instances")
@RequiredArgsConstructor
@Slf4j
public class InstanceController {

    private final InstanceService instanceService;
    private final SeriesService seriesService;
    private final StudyService studyService;
    private final PatientService patientService;
    private final DicomStorageService dicomStorageService;
    private final DicomMetadataRecordRepository dicomMetadataRecordRepository;
    private final FileAssetRepository fileAssetRepository;
    private final ObjectMapper objectMapper;

    private final StorageAccessStrategy storageAccessStrategy; // Storage 접근 전략 (pre-signed url or backed proxy)

    /**
     * 영상 생성
     * POST /api/instances
     */
    @PostMapping
    public ApiResponse<InstanceResponse> createInstance(@Valid @RequestBody CreateInstanceRequest request) {
        log.info("POST /api/instances - seriesId: {}", request.getSeriesId());

        // 1. DTO → Entity 변환
        Instance instance = toEntity(request);

        // 2. Service 호출 (with retry support)
        Instance savedInstance = instanceService.createInstanceWithRetry(instance);

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
            @Valid @RequestBody UpdateInstanceRequest request) {
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

    /**
     * DICOM 파일 다운로드 URL 조회
     * GET /api/instances/{id}/download
     *
     * <p>이 API는 Frontend가 DICOM 파일을 다운로드할 수 있는 URL을 반환합니다.
     *
     * <p>Week 4-8 POC:
     * <ul>
     *   <li>Pre-signed URL 반환 (Frontend가 SeaweedFS에 직접 접근)</li>
     *   <li>만료 시간: 1시간</li>
     *   <li>보안 수준: 학습 환경</li>
     * </ul>
     *
     * <p>Week 9+ 프로덕션 (KingArthur 통합):
     * <ul>
     *   <li>Backend Proxy URL 반환 (Frontend가 Backend 경유)</li>
     *   <li>만료 시간: 없음 (Per-Request JWT 검증)</li>
     *   <li>보안 수준: HIPAA/GDPR 준수</li>
     * </ul>
     *
     * <p>전환 방법: application.yml에서 storage.access-strategy 변경만
     *
     * @param id Instance ID
     * @return 파일 접근 방법 (Pre-signed URL or Backend Proxy URL)
     */
    @GetMapping("/{id}/download")
    public ApiResponse<FileAccessResponse> getDownloadUrl(@PathVariable Long id) {
        log.info("GET /api/instances/{}/download", id);

        // 1. Instance 조회
        Instance instance = instanceService.findById(id);

        // 2. fileId 확인 (FileAsset 연관 관계에서 조회)
        // TODO: Instance에 fileId 필드 또는 FileAsset 연관 관계 추가 필요
        // 임시로 storagePath를 fileId로 사용 (Week 4-5 구현 완료 시 수정)
        String fileId = instance.getStoragePath();

        if (fileId == null || fileId.isEmpty()) {
            throw new IllegalStateException(
                    "Instance has no file stored. instanceId=" + id
            );
        }

        // 3. Tenant ID, User ID 추출
        // TODO: Spring Security @AuthenticationPrincipal로 실제 User 정보 받기
        // POC에서는 임시로 instance.getTenantId() 사용
        Long tenantId = instance.getTenantId();
        Long userId = 1L; // TODO: 실제 User ID로 교체 (Week 12+ Keycloak 연동 시)

        // 4. StorageAccessStrategy로 파일 접근 URL 생성
        // - Pre-signed URL 방식: S3Presigner로 서명된 URL 생성 (1시간 유효)
        // - Backend Proxy 방식: Proxy 엔드포인트 반환 (/api/files/{fileId}/proxy)
        FileAccessResponse fileAccess = storageAccessStrategy.getFileAccess(
                fileId,
                userId,
                tenantId
        );

        log.info("File access generated: method={}, url={}, instanceId={}",
                fileAccess.getMethod(), fileAccess.getUrl(), id);

        // 5. 성공 응답 반환
        return ApiResponse.success(fileAccess);
    }

    /**
     * DICOM 파일 업로드 (메타데이터 자동 파싱)
     * POST /api/instances/upload
     *
     * <p>이 API는 DICOM 파일을 업로드하고 메타데이터를 자동으로 추출하여
     * Patient → Study → Series → Instance를 생성합니다.
     *
     * <p>흐름:
     * <ol>
     *   <li>MultipartFile 검증 (파일 존재 여부, 크기)</li>
     *   <li>DICOM 메타데이터 추출 (DCM4CHE)</li>
     *   <li>Patient findOrCreate (PatientID + Issuer 기준)</li>
     *   <li>Study findOrCreate (StudyInstanceUID 기준)</li>
     *   <li>Series findOrCreate (SeriesInstanceUID 기준)</li>
     *   <li>SeaweedFS 업로드 (UUID v7 생성)</li>
     *   <li>Instance 생성 (SOPInstanceUID + fileId)</li>
     *   <li>성공 응답 반환</li>
     * </ol>
     *
     * <p>멱등성 보장:
     * <ul>
     *   <li>같은 DICOM 파일 재업로드 시 중복 생성 방지</li>
     *   <li>Patient, Study, Series는 UID 기준으로 재사용</li>
     * </ul>
     *
     * @param file DICOM 파일 (MultipartFile)
     * @return 생성된 Instance 정보
     * @throws IllegalArgumentException 파일이 비어있거나 DICOM 형식이 아닌 경우
     * @throws IOException DICOM 파싱 실패 시
     * @throws com.hanumoka.sado.common.exception.BusinessException 파일 업로드 실패 시
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    @PostMapping("/upload")
    public ApiResponse<InstanceResponse> uploadDicom(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        log.warn("DEPRECATED API called: POST /api/instances/upload. " +
                 "Please migrate to DICOMweb STOW-RS: POST /dicomweb/studies");

        // 비즈니스 로직은 InstanceService.uploadDicomFile()로 이동됨
        byte[] fileBytes = file.getBytes();
        Instance savedInstance = instanceService.uploadDicomFile(
            fileBytes,
            file.getOriginalFilename()
        );

        return ApiResponse.success(toResponse(savedInstance));
    }

    /**
     * DicomMetadata → JSON 문자열 변환
     */
    private String convertMetadataToJson(DicomMetadataExtractor.DicomMetadata metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to convert metadata to JSON, using empty object", e);
            return "{}";
        }
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
        instance.setImageRows(request.getRows());
        instance.setImageColumns(request.getColumns());
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
            instance.setImageRows(request.getRows());
        }
        if (request.getColumns() != null) {
            instance.setImageColumns(request.getColumns());
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
        // storageUri 생성 (Pre-signed URL)
        String storageUri = "";  // null 대신 빈 문자열 초기화
        if (instance.getStoragePath() != null && !instance.getStoragePath().isEmpty()) {
            try {
                Long tenantId = instance.getTenantId();
                Long userId = 1L; // TODO: 실제 User ID (Week 12+)

                FileAccessResponse fileAccess = storageAccessStrategy.getFileAccess(
                    instance.getStoragePath(), userId, tenantId
                );
                storageUri = fileAccess.getUrl();
            } catch (Exception e) {
                log.warn("Failed to generate storage URI for instance {}, using fallback: {}",
                         instance.getId(), e.getMessage());
                storageUri = "";  // 예외 발생 시 빈 문자열 (null 방지)
            }
        }

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
                .storageUri(storageUri)  // Pre-signed URL 추가
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
