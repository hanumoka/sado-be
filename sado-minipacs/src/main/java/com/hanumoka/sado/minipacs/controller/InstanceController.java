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
import com.hanumoka.sado.minipacs.dto.request.CreateInstanceRequest;
import com.hanumoka.sado.minipacs.dto.request.UpdateInstanceRequest;
import com.hanumoka.sado.minipacs.dto.response.InstanceResponse;
import com.hanumoka.sado.minipacs.storage.dto.FileAccessResponse;
import com.hanumoka.sado.minipacs.storage.strategy.StorageAccessStrategy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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
    @PostMapping("/upload")
    public ApiResponse<InstanceResponse> uploadDicom(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        log.info("POST /api/instances/upload - filename: {}, size: {} bytes",
                file.getOriginalFilename(), file.getSize());

        // 1. 파일 검증
        if (file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다");
        }

        // 2. 파일을 byte[]로 읽기 (InputStream 재사용 방지)
        byte[] fileBytes = file.getBytes();

        // 2-1. 파일 확장자 검증 (.dcm, .dicom, 확장자 없음만 허용)
        DicomFileValidator.validateFileExtension(file.getOriginalFilename());

        // 2-2. DICOM magic number 검증 (128 byte offset에서 'DICM' 확인)
        DicomFileValidator.validateDicomMagicNumber(fileBytes);

        // 3. DICOM 메타데이터 추출
        DicomMetadataExtractor.DicomMetadata metadata =
                DicomMetadataExtractor.extract(new java.io.ByteArrayInputStream(fileBytes));

        log.info("DICOM metadata extracted: PatientID={}, StudyUID={}, SeriesUID={}, SOPInstanceUID={}",
                metadata.getPatientId(),
                metadata.getStudyInstanceUid(),
                metadata.getSeriesInstanceUid(),
                metadata.getSopInstanceUid());

        // 4. Patient findOrCreate
        Patient patient = patientService.findOrCreatePatient(
                metadata.getPatientId(),
                metadata.getIssuerOfPatientId(),
                metadata.getPatientName(),
                metadata.getPatientBirthDate(),
                metadata.getPatientSex()
        );

        log.info("Patient resolved: id={}, dicomPatientId={}",
                patient.getId(), patient.getDicomPatientId());

        // 5. Study findOrCreate
        Study study = studyService.findOrCreateStudy(
                metadata.getStudyInstanceUid(),
                patient,
                metadata.getStudyDate(),
                metadata.getStudyDescription()
        );

        log.info("Study resolved: id={}, studyInstanceUid={}",
                study.getId(), study.getStudyInstanceUid());

        // 6. Series findOrCreate
        Series series = seriesService.findOrCreateSeries(
                metadata.getSeriesInstanceUid(),
                study,
                metadata.getSeriesNumber(),
                metadata.getModality(),
                metadata.getSeriesDescription(),
                metadata.getBodyPartExamined()
        );

        log.info("Series resolved: id={}, seriesInstanceUid={}, modality={}",
                series.getId(), series.getSeriesInstanceUid(), series.getModality());

        // 7. SeaweedFS 업로드 (DICOMweb 표준 경로) + 보상 트랜잭션
        String s3Key = null;
        try {
            // 7-1. S3 업로드 (byte[]를 InputStream으로 변환)
            s3Key = dicomStorageService.uploadDicomFile(
                    study.getStudyInstanceUid(),
                    series.getSeriesInstanceUid(),
                    metadata.getSopInstanceUid(),
                    new java.io.ByteArrayInputStream(fileBytes),
                    fileBytes.length
            );

            log.info("DICOM file uploaded to storage: s3Key={}, originalFilename={}",
                    s3Key, file.getOriginalFilename());

            // 8. Instance 생성 (Builder 패턴)
            Instance instance = Instance.builder()
                    .series(series)
                    .sopInstanceUid(metadata.getSopInstanceUid())
                    .sopClassUid(metadata.getSopClassUid())
                    .instanceNumber(metadata.getInstanceNumber())
                    .imageRows(metadata.getImageRows())
                    .imageColumns(metadata.getImageColumns())
                    .numberOfFrames(metadata.getNumberOfFrames())
                    .storagePath(s3Key)  // S3 Key를 storagePath로 저장 (DICOMweb 표준 경로)
                    .fileSize((long) fileBytes.length)  // byte[] 길이 사용 (Long으로 캐스팅)
                    .build();

            // 9. InstanceService로 DB 저장
            Instance savedInstance = instanceService.createInstance(instance);

            log.info("Instance created: id={}, sopInstanceUid={}, s3Key={}, seriesId={}",
                    savedInstance.getId(),
                    savedInstance.getSopInstanceUid(),
                    s3Key,
                    series.getId());

            // 9. DicomMetadataRecord 생성 (WORM 정책)
            DicomMetadataRecord metadataRecord = DicomMetadataRecord.builder()
                    .metadata(convertMetadataToJson(metadata))
                    .instanceId(savedInstance.getId())
                    .studyInstanceUid(metadata.getStudyInstanceUid())
                    .seriesInstanceUid(metadata.getSeriesInstanceUid())
                    .sopInstanceUid(metadata.getSopInstanceUid())
                    .filePath(s3Key)
                    .filename(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .build();

            dicomMetadataRecordRepository.save(metadataRecord);

            log.info("DicomMetadataRecord created: instanceId={}, sopInstanceUid={}",
                    savedInstance.getId(), metadata.getSopInstanceUid());

            // 10. FileAsset 생성 (DICOM 파일 메타데이터 관리)
            FileAsset dicomFileAsset = FileAsset.builder()
                    .category(FileCategory.DICOM)
                    .referenceType(ReferenceType.INSTANCE)
                    .referenceId(savedInstance.getId())
                    .status(FileStatus.ACTIVE)
                    .fileName(file.getOriginalFilename() != null ?
                            file.getOriginalFilename() : metadata.getSopInstanceUid() + ".dcm")
                    .storagePath(s3Key)
                    .fileSize(file.getSize())
                    .mimeType("application/dicom")
                    // DICOM 파일은 영구 보관 (expiresAt = null)
                    .storageTier("HOT")
                    .build();

            fileAssetRepository.save(dicomFileAsset);

            log.info("FileAsset created: id={}, instanceId={}, storagePath={}",
                    dicomFileAsset.getId(), savedInstance.getId(), s3Key);

            // 11. Entity → Response DTO 변환
            InstanceResponse response = toResponse(savedInstance);

            // 12. 성공 응답 반환
            return ApiResponse.success(response);

        } catch (Exception e) {
            // 보상 트랜잭션: S3 파일 삭제 (DB 저장 실패 시)
            if (s3Key != null) {
                try {
                    log.warn("Rolling back S3 upload due to error: {}", s3Key);
                    dicomStorageService.deleteDicomFile(s3Key);
                    log.info("S3 file deleted successfully: {}", s3Key);
                } catch (Exception deleteEx) {
                    log.error("Failed to delete orphan S3 file: {}. Will be cleaned up later.",
                            s3Key, deleteEx);
                }
            }
            // 원래 예외 재발생
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new RuntimeException("Failed to upload DICOM file", e);
        }
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
