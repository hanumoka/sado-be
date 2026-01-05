package com.hanumoka.sado.minipacs.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanumoka.sado.common.exception.ResourceNotFoundException;
import com.hanumoka.sado.minipacs.domain.entity.*;
import com.hanumoka.sado.minipacs.domain.enums.FileCategory;
import com.hanumoka.sado.minipacs.domain.enums.FileStatus;
import com.hanumoka.sado.minipacs.domain.enums.ReferenceType;
import com.hanumoka.sado.minipacs.domain.parser.DicomMetadataExtractor;
import com.hanumoka.sado.minipacs.domain.repository.DicomMetadataRecordRepository;
import com.hanumoka.sado.minipacs.domain.repository.FileAssetRepository;
import com.hanumoka.sado.minipacs.domain.repository.InstanceRepository;
import com.hanumoka.sado.minipacs.domain.util.DicomFileValidator;
import com.hanumoka.sado.minipacs.storage.dto.DicomFileMetadata;
import com.hanumoka.sado.minipacs.storage.dto.StorageResult;
import com.hanumoka.sado.minipacs.storage.service.DicomStorageService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Instance Service
 *
 * DICOM Instance 생성, 조회 기능 제공
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class InstanceService {

    private final InstanceRepository instanceRepository;
    private final SeriesService seriesService;
    private final DicomStorageService dicomStorageService;
    private final EntityManager entityManager;

    // STOW-RS 구현을 위한 추가 의존성
    private final StudyService studyService;
    private final PatientService patientService;
    private final DicomMetadataRecordRepository dicomMetadataRecordRepository;
    private final FileAssetRepository fileAssetRepository;
    private final ObjectMapper objectMapper;

    /**
     * CRITICAL: Self-injection for AOP proxy access
     * Required for @Retryable to work correctly with @Transactional
     *
     * Pattern reference: FileAssetService.java lines 66-88
     */
    @Autowired
    @Lazy
    private InstanceService self;

    /**
     * Instance PK로 조회
     *
     * @param id Instance PK
     * @return Instance 엔티티
     * @throws ResourceNotFoundException Instance가 존재하지 않는 경우
     */
    public Instance findById(Long id) {
        return instanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Instance not found with id: " + id));
    }

    /**
     * DICOM SOP Instance UID로 조회
     *
     * @param sopInstanceUid DICOM SOP Instance UID (0008,0018)
     * @return Instance 엔티티 (Optional)
     */
    public Optional<Instance> findBySopInstanceUid(String sopInstanceUid) {
        return instanceRepository.findBySopInstanceUid(sopInstanceUid);
    }

    /**
     * Storage Path로 Instance 조회
     * 파일 중복 업로드 방지용 (같은 경로에 이미 저장된 파일이 있는지 확인)
     *
     * @param storagePath Storage Path (예: /data/dicom/2025/01/15/abc123.dcm)
     * @return Instance 엔티티 (Optional)
     */
    public Optional<Instance> findByStoragePath(String storagePath) {
        return instanceRepository.findByStoragePath(storagePath);
    }

    /**
     * 특정 시리즈의 모든 Instance 조회 (Instance Number 순)
     *
     * @param seriesId Series PK
     * @return Instance 목록
     */
    public List<Instance> findBySeriesId(Long seriesId) {
        // Series 존재 여부 확인
        seriesService.findById(seriesId);

        return instanceRepository.findBySeriesIdOrderByInstanceNumber(seriesId);
    }

    /**
     * 특정 시리즈의 특정 Instance Number 조회
     *
     * @param seriesId Series PK
     * @param instanceNumber Instance Number
     * @return Instance 엔티티 (Optional)
     */
    public Optional<Instance> findBySeriesIdAndInstanceNumber(Long seriesId, Integer instanceNumber) {
        return instanceRepository.findBySeriesIdAndInstanceNumber(seriesId, instanceNumber);
    }

    /**
     * 특정 시리즈의 Instance 개수 조회
     *
     * @param seriesId Series PK
     * @return Instance 개수
     */
    public long countBySeriesId(Long seriesId) {
        return instanceRepository.countBySeriesId(seriesId);
    }

    /**
     * Instance 생성 with Retry (Public API)
     *
     * CRITICAL: 역정규화 카운터 제거로 인한 변경 (2026-01-05)
     * - numberOfInstances 필드 제거 → Optimistic Lock 충돌 완전 제거
     * - @Retryable 유지 (향후 다른 충돌 가능성 대비)
     * - Study/Series의 @Version 제거 → 동시성 충돌 사라짐
     *
     * CRITICAL: Transaction Propagation Strategy
     * - @Transactional(propagation = Propagation.NOT_SUPPORTED)
     * - This method runs OUTSIDE any transaction (suspends inherited read-only)
     * - Each retry gets a completely fresh transaction via createInstanceInternal()
     *
     * Why NOT_SUPPORTED:
     * - Class has @Transactional(readOnly = true) → all methods inherit read-only
     * - Without NOT_SUPPORTED, this method inherits read-only transaction
     * - createInstanceInternal() would join the read-only transaction → INSERT fails
     * - NOT_SUPPORTED suspends inherited transaction → createInstanceInternal() gets fresh read-write transaction
     *
     * @param instance Instance 엔티티 (series 관계 설정 필요)
     * @return 저장된 Instance
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Retryable(
        retryFor = org.springframework.orm.ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2.0)
    )
    public Instance createInstanceWithRetry(Instance instance) {
        log.debug("Attempting to create instance: sopInstanceUid={} (may retry on conflict)",
                instance.getSopInstanceUid());

        // Call via self-proxy to ensure @Transactional works
        // and exception is thrown after transaction commit
        return self.createInstanceInternal(instance);
    }

    /**
     * Instance 생성 - Internal implementation
     *
     * @param instance Instance 엔티티 (series 관계 설정 필요)
     * @return 저장된 Instance
     */
    @Transactional
    Instance createInstanceInternal(Instance instance) {
        // Series 존재 여부 확인
        if (instance.getSeries() == null || instance.getSeries().getId() == null) {
            throw new IllegalArgumentException("Instance must have a series");
        }

        // Series 조회 (관리되는 엔티티)
        Series series = seriesService.findById(instance.getSeries().getId());

        // 파일 경로 중복 확인
        if (instance.getStoragePath() != null) {
            Optional<Instance> existingInstance = findByStoragePath(instance.getStoragePath());
            if (existingInstance.isPresent()) {
                throw new IllegalArgumentException("Instance with storagePath already exists: " + instance.getStoragePath());
            }
        }

        log.info("Creating new instance: sopInstanceUid={}, seriesId={}",
                instance.getSopInstanceUid(),
                series.getId());

        // 비즈니스 메서드 호출 (역정규화 필드 자동 업데이트)
        series.addInstance(instance);

        return instanceRepository.save(instance);

        // Transaction commits AFTER this method returns
        // If OptimisticLockingFailureException occurs during commit,
        // it propagates to createInstanceWithRetry() where @Retryable catches it
    }

    /**
     * Instance 생성 (Deprecated - use createInstanceWithRetry)
     *
     * @deprecated Use {@link #createInstanceWithRetry(Instance)} instead
     * @param instance Instance 엔티티
     * @return 저장된 Instance
     */
    @Deprecated
    @Transactional
    public Instance createInstance(Instance instance) {
        log.warn("DEPRECATED: createInstance() called. Use createInstanceWithRetry() for retry support.");
        return createInstanceInternal(instance);
    }

    /**
     * Instance 찾기 또는 생성
     *
     * DICOM C-STORE 수신 시 사용:
     * 1. SOP Instance UID로 기존 Instance 검색
     * 2. 없으면 새로운 Instance 생성
     *
     * @param sopInstanceUid DICOM SOP Instance UID
     * @param series 소속 시리즈
     * @param instanceNumber Instance Number
     * @param sopClassUid SOP Class UID
     * @param storagePath DICOM 파일 저장 경로 (SeaweedFS/S3)
     * @param fileSize 파일 크기 (bytes)
     * @return 찾거나 생성된 Instance
     */
    @Transactional
    public Instance findOrCreateInstance(
            String sopInstanceUid,
            Series series,
            Integer instanceNumber,
            String sopClassUid,
            String storagePath,
            Long fileSize) {

        // CRITICAL: Race Condition 해결
        // - 초기 검사 제거: TOCTOU (Time-of-Check Time-of-Use) 취약점 방지
        // - Insert-first 전략: DB Unique Constraint를 활용한 동시성 제어

        try {
            // 1. Instance 생성
            Instance newInstance = Instance.builder()
                    .series(series)
                    .sopInstanceUid(sopInstanceUid)
                    .sopClassUid(sopClassUid)
                    .instanceNumber(instanceNumber)
                    .storagePath(storagePath)
                    .fileSize(fileSize)
                    .build();

            log.info("Creating new instance from DICOM: sopInstanceUid={}, seriesId={}, storagePath={}",
                    sopInstanceUid,
                    series.getId(),
                    storagePath);

            // 2. 양방향 관계 설정 + 역정규화 필드 자동 업데이트
            series.addInstance(newInstance);

            // 3. DB 저장 (Unique Constraint 위반 시 예외 발생)
            return instanceRepository.saveAndFlush(newInstance);

        } catch (DataIntegrityViolationException e) {
            // Race condition 발생: 다른 스레드가 먼저 저장함
            log.warn("Race condition detected for instance: {}, refreshing series state", sopInstanceUid);

            // CRITICAL: series의 메모리 상태를 DB 상태로 되돌림
            // - series.addInstance()로 인한 instances 컬렉션 변경을 원복
            // - 트랜잭션이 커밋되면 잘못된 관계가 DB에 저장되는 것을 방지
            // - Note: numberOfInstances 카운터는 제거되었으므로 더 이상 원복할 필요 없음
            entityManager.refresh(series);

            // 이미 존재하는 Instance 조회 후 반환
            return instanceRepository.findBySopInstanceUid(sopInstanceUid)
                    .orElseThrow(() -> new IllegalStateException(
                            "Instance should exist after DataIntegrityViolationException"));
        }
    }

    /**
     * Instance 업데이트
     *
     * @param instance Instance 엔티티
     * @return 업데이트된 Instance
     */
    @Transactional
    public Instance updateInstance(Instance instance) {
        // 존재 여부 확인
        findById(instance.getId());

        log.info("Updating instance: id={}", instance.getId());
        return instanceRepository.save(instance);
    }

    /**
     * Instance 삭제
     *
     * CRITICAL: 트랜잭션 일관성 보장을 위한 삭제 순서 변경
     * <ol>
     *   <li>S3에서 DICOM 파일 먼저 삭제 (실패 시 예외 발생 → 트랜잭션 롤백)</li>
     *   <li>S3 삭제 성공 시에만 DB에서 Instance 삭제</li>
     * </ol>
     *
     * <p>이전 문제점:
     * - DB 먼저 삭제 → S3 삭제 실패 시 고아 파일 발생
     * - DB는 이미 커밋되어 복구 불가능
     *
     * <p>개선 효과:
     * - S3 삭제 실패 시 트랜잭션 롤백으로 DB 보존
     * - 데이터 일관성 보장
     *
     * @param id Instance PK
     */
    @Transactional
    public void deleteInstance(Long id) {
        Instance instance = findById(id);
        Series series = instance.getSeries();
        String storagePath = instance.getStoragePath();

        log.info("Deleting instance: id={}, sopInstanceUid={}, storagePath={}",
                id,
                instance.getSopInstanceUid(),
                storagePath);

        // 1. S3에서 DICOM 파일 먼저 삭제 (실패 시 예외 발생 → 트랜잭션 롤백)
        if (storagePath != null && !storagePath.isEmpty()) {
            dicomStorageService.deleteDicomFile(storagePath);
            log.info("Deleted S3 file: {}", storagePath);
        }

        // 2. S3 삭제 성공 시에만 DB에서 Instance 삭제
        // 비즈니스 메서드 호출 (역정규화 필드 자동 업데이트)
        if (series != null) {
            series.removeInstance(instance);
        }

        instanceRepository.delete(instance);
        log.info("Deleted instance from DB: id={}", id);
    }

    /**
     * DICOM 파일 업로드 (Service 계층 - DICOMweb STOW-RS 지원)
     *
     * <p>이 메서드는 DICOM 파일을 업로드하고 메타데이터를 자동으로 추출하여
     * Patient → Study → Series → Instance를 생성합니다.
     *
     * <p>흐름:
     * <ol>
     *   <li>DICOM 메타데이터 추출 (DCM4CHE)</li>
     *   <li>Patient findOrCreate (Pessimistic Lock)</li>
     *   <li>Study findOrCreate (Pessimistic Lock)</li>
     *   <li>Series findOrCreate (Pessimistic Lock)</li>
     *   <li>중복 파일 체크 (SOP Instance UID)</li>
     *   <li>SeaweedFS 업로드</li>
     *   <li>Instance 생성</li>
     *   <li>DicomMetadataRecord + FileAsset 생성</li>
     * </ol>
     *
     * <p>멱등성 보장: 같은 DICOM 파일 재업로드 시 기존 Instance 반환
     *
     * <p>보상 트랜잭션: S3 업로드 후 DB 저장 실패 시 S3 파일 자동 삭제
     *
     * @param fileBytes DICOM 파일 바이트 배열
     * @param originalFilename 원본 파일명 (FileAsset 저장용, null 가능)
     * @return 생성되거나 이미 존재하는 Instance
     * @throws IllegalArgumentException 파일 검증 실패 (확장자, DICOM 형식)
     * @throws IOException DICOM 메타데이터 파싱 실패
     * @throws RuntimeException S3 업로드 실패, DB 저장 실패 등
     */
    @Transactional
    public Instance uploadDicomFile(byte[] fileBytes, String originalFilename) throws IOException {
        log.info("uploadDicomFile - filename: {}, size: {} bytes", originalFilename, fileBytes.length);

        // 1. 파일 확장자 검증 (.dcm, .dicom, 확장자 없음만 허용)
        DicomFileValidator.validateFileExtension(originalFilename);

        // 2. DICOM magic number 검증 (128 byte offset에서 'DICM' 확인)
        DicomFileValidator.validateDicomMagicNumber(fileBytes);

        // 3. DICOM 메타데이터 추출
        DicomMetadataExtractor.DicomMetadata metadata =
                DicomMetadataExtractor.extract(new ByteArrayInputStream(fileBytes));

        log.info("DICOM metadata extracted: PatientID={}, StudyUID={}, SeriesUID={}, SOPInstanceUID={}",
                metadata.getPatientId(),
                metadata.getStudyInstanceUid(),
                metadata.getSeriesInstanceUid(),
                metadata.getSopInstanceUid());

        // 4. Patient findOrCreate (Pessimistic Lock 적용됨)
        Patient patient = patientService.findOrCreatePatient(
                metadata.getPatientId(),
                metadata.getIssuerOfPatientId(),
                metadata.getPatientName(),
                metadata.getPatientBirthDate(),
                metadata.getPatientSex()
        );

        log.debug("Patient resolved: id={}, dicomPatientId={}",
                patient.getId(), patient.getDicomPatientId());

        // 5. Study findOrCreate (Pessimistic Lock 적용됨)
        Study study = studyService.findOrCreateStudy(
                metadata.getStudyInstanceUid(),
                patient,
                metadata.getStudyDate(),
                metadata.getStudyDescription()
        );

        log.debug("Study resolved: id={}, studyInstanceUid={}",
                study.getId(), study.getStudyInstanceUid());

        // 6. Series findOrCreate (Pessimistic Lock 적용됨)
        Series series = seriesService.findOrCreateSeries(
                metadata.getSeriesInstanceUid(),
                study,
                metadata.getSeriesNumber(),
                metadata.getModality(),
                metadata.getSeriesDescription(),
                metadata.getBodyPartExamined()
        );

        log.debug("Series resolved: id={}, seriesInstanceUid={}, modality={}",
                series.getId(), series.getSeriesInstanceUid(), series.getModality());

        // 7. 중복 파일 체크 (멱등성 보장)
        Optional<Instance> existingInstance = findBySopInstanceUid(metadata.getSopInstanceUid());

        if (existingInstance.isPresent()) {
            Instance existing = existingInstance.get();
            log.info("Duplicate DICOM file upload detected - returning existing Instance: " +
                            "sopInstanceUid={}, instanceId={}, storagePath={}",
                    metadata.getSopInstanceUid(), existing.getId(), existing.getStoragePath());
            return existing;
        }

        // 8. SeaweedFS 업로드 + 보상 트랜잭션
        String s3Key = null;
        try {
            // 8-1. DicomFileMetadata 생성
            DicomFileMetadata dicomMetadata = DicomFileMetadata.builder()
                    .studyInstanceUid(metadata.getStudyInstanceUid())
                    .seriesInstanceUid(metadata.getSeriesInstanceUid())
                    .sopInstanceUid(metadata.getSopInstanceUid())
                    .sopClassUid(metadata.getSopClassUid())
                    .build();

            // 8-2. S3 업로드
            StorageResult storageResult = dicomStorageService.uploadDicomFile(
                    new ByteArrayInputStream(fileBytes),
                    dicomMetadata
            );

            s3Key = storageResult.getS3Key();

            log.debug("DICOM file uploaded to storage: s3Key={}, size={} bytes",
                    s3Key, storageResult.getFileSize());

            // 9. Instance 생성
            Instance instance = Instance.builder()
                    .series(series)
                    .sopInstanceUid(metadata.getSopInstanceUid())
                    .sopClassUid(metadata.getSopClassUid())
                    .instanceNumber(metadata.getInstanceNumber())
                    .imageRows(metadata.getImageRows())
                    .imageColumns(metadata.getImageColumns())
                    .numberOfFrames(metadata.getNumberOfFrames())
                    .storagePath(s3Key)
                    .fileSize((long) fileBytes.length)
                    .build();

            // 10. Instance DB 저장 (with retry support)
            Instance savedInstance = createInstanceWithRetry(instance);

            log.info("Instance created: id={}, sopInstanceUid={}, s3Key={}",
                    savedInstance.getId(),
                    savedInstance.getSopInstanceUid(),
                    s3Key);

            // 11. DicomMetadataRecord 생성
            DicomMetadataRecord metadataRecord = DicomMetadataRecord.builder()
                    .metadata(convertMetadataToJson(metadata))
                    .instanceId(savedInstance.getId())
                    .studyInstanceUid(metadata.getStudyInstanceUid())
                    .seriesInstanceUid(metadata.getSeriesInstanceUid())
                    .sopInstanceUid(metadata.getSopInstanceUid())
                    .filePath(s3Key)
                    .filename(originalFilename)
                    .fileSize((long) fileBytes.length)
                    .build();

            dicomMetadataRecordRepository.save(metadataRecord);

            log.debug("DicomMetadataRecord created: instanceId={}, sopInstanceUid={}",
                    savedInstance.getId(), metadata.getSopInstanceUid());

            // 12. FileAsset 생성
            FileAsset dicomFileAsset = FileAsset.builder()
                    .category(FileCategory.DICOM)
                    .referenceType(ReferenceType.INSTANCE)
                    .referenceId(savedInstance.getId())
                    .status(FileStatus.ACTIVE)
                    .fileName(originalFilename != null ?
                            originalFilename : metadata.getSopInstanceUid() + ".dcm")
                    .storagePath(s3Key)
                    .fileSize((long) fileBytes.length)
                    .mimeType("application/dicom")
                    .storageTier("HOT")
                    .build();

            fileAssetRepository.save(dicomFileAsset);

            log.debug("FileAsset created: id={}, instanceId={}, storagePath={}",
                    dicomFileAsset.getId(), savedInstance.getId(), s3Key);

            return savedInstance;

        } catch (Exception e) {
            // CRITICAL: Optimistic Lock 예외는 재시도 가능하므로 S3 파일 유지
            if (e instanceof ObjectOptimisticLockingFailureException) {
                log.warn("Optimistic lock failure - S3 file retained for retry: {}", s3Key);
                throw (ObjectOptimisticLockingFailureException) e;
            }

            // 보상 트랜잭션: S3 파일 삭제 (재시도 불가능한 예외)
            if (s3Key != null) {
                try {
                    log.warn("Rolling back S3 upload due to non-retryable error: {}", s3Key);
                    dicomStorageService.deleteDicomFile(s3Key);
                    log.info("S3 file deleted successfully on error: {}", s3Key);
                } catch (Exception deleteEx) {
                    log.error("Failed to delete orphan S3 file: {}. Will be cleaned up later.",
                            s3Key, deleteEx);
                }
            }

            // 원래 예외 재발생
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
            log.error("Failed to convert metadata to JSON", e);
            throw new RuntimeException("Failed to serialize DICOM metadata", e);
        }
    }
}
