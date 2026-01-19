package com.hanumoka.sado.minipacs.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * DICOM 파일 업로드 서비스 (STOW-RS 지원)
 *
 * <p>InstanceService에서 분리된 업로드 전용 서비스입니다.
 * SRP(Single Responsibility Principle)를 준수하여:
 * <ul>
 *   <li>InstanceService: Instance CRUD 담당</li>
 *   <li>DicomUploadService: DICOM 파일 업로드 오케스트레이션 담당</li>
 * </ul>
 *
 * <p>책임:
 * <ol>
 *   <li>DICOM 파일 검증 (확장자, magic number)</li>
 *   <li>DICOM 메타데이터 추출</li>
 *   <li>Patient/Study/Series 계층 구조 생성</li>
 *   <li>S3 스토리지 업로드</li>
 *   <li>Instance, DicomMetadataRecord, FileAsset 생성</li>
 *   <li>사전 렌더링 트리거</li>
 *   <li>보상 트랜잭션 처리</li>
 * </ol>
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class DicomUploadService {

    // DICOM 계층 구조 서비스
    private final PatientService patientService;
    private final StudyService studyService;
    private final SeriesService seriesService;

    // Instance 관련
    private final InstanceService instanceService;
    private final InstanceRepository instanceRepository;

    // 스토리지 및 메타데이터
    private final DicomStorageService dicomStorageService;
    private final DicomMetadataRecordRepository dicomMetadataRecordRepository;
    private final FileAssetRepository fileAssetRepository;
    private final ObjectMapper objectMapper;

    // 사전 렌더링
    private final PreRenderingService preRenderingService;

    /**
     * DICOM 파일 업로드 (DICOMweb STOW-RS 지원)
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
        Optional<Instance> existingInstance = instanceService.findBySopInstanceUid(metadata.getSopInstanceUid());

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
            // 8-1. DicomFileMetadata 생성 (fileSize 포함 - 스트리밍 업로드용)
            DicomFileMetadata dicomMetadata = DicomFileMetadata.builder()
                    .studyInstanceUid(metadata.getStudyInstanceUid())
                    .seriesInstanceUid(metadata.getSeriesInstanceUid())
                    .sopInstanceUid(metadata.getSopInstanceUid())
                    .sopClassUid(metadata.getSopClassUid())
                    .fileSize((long) fileBytes.length)
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
                    // Pixel Data Metadata (WADO-RS BulkData 지원)
                    .transferSyntaxUid(metadata.getTransferSyntaxUid())
                    .bitsAllocated(metadata.getBitsAllocated())
                    .bitsStored(metadata.getBitsStored())
                    .highBit(metadata.getHighBit())
                    .pixelRepresentation(metadata.getPixelRepresentation())
                    .photometricInterpretation(metadata.getPhotometricInterpretation())
                    .samplesPerPixel(metadata.getSamplesPerPixel())
                    .storagePath(s3Key)
                    .fileSize((long) fileBytes.length)
                    .build();

            // 10. Instance DB 저장 (같은 트랜잭션 내 - 트랜잭션 통합)
            // CRITICAL: createInstanceWithRetry() 대신 직접 저장
            // - 역정규화 카운터(numberOfInstances) 제거로 Optimistic Lock 충돌 사라짐
            // - 같은 트랜잭션 내에서 Instance, MetadataRecord, FileAsset 함께 커밋/롤백
            // - Orphan Instance 문제 해결 (2026-01-05)
            series.addInstance(instance);
            Instance savedInstance = instanceRepository.save(instance);

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

            // 13. 비동기 사전 렌더링 트리거 (업로드 응답에 영향 없음)
            // PENDING 상태로 설정
            savedInstance.setTranscodingStatus(Instance.TranscodingStatus.PENDING);
            instanceRepository.save(savedInstance);

            // CRITICAL: 트랜잭션 커밋 후에 비동기 작업 실행
            // - @Transactional 메서드에서 바로 @Async 호출 시, 비동기 스레드가
            //   메인 트랜잭션 커밋 전에 DB를 조회하여 Instance not found 에러 발생
            // - TransactionSynchronizationManager.afterCommit()을 사용하여
            //   트랜잭션이 커밋된 후에만 비동기 작업이 시작되도록 보장
            final Long instanceId = savedInstance.getId();
            final String studyUid = metadata.getStudyInstanceUid();
            final String seriesUid = metadata.getSeriesInstanceUid();

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            log.info("Transaction committed, triggering pre-rendering: instanceId={}",
                                    instanceId);
                            preRenderingService.preRenderAsync(instanceId, studyUid, seriesUid);
                        }
                    }
            );

            log.info("Pre-rendering scheduled for after commit: instanceId={}, sopInstanceUid={}",
                    savedInstance.getId(), savedInstance.getSopInstanceUid());

            return savedInstance;

        } catch (Exception e) {
            // 보상 트랜잭션: S3 파일 삭제
            // - 트랜잭션 롤백 시 DB 변경사항은 자동 롤백되지만
            // - S3에 업로드된 파일은 수동으로 삭제해야 함
            if (s3Key != null) {
                try {
                    log.warn("Rolling back S3 upload due to error: {}", s3Key);
                    dicomStorageService.deleteDicomFile(s3Key);
                    log.info("S3 file deleted successfully on rollback: {}", s3Key);
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
