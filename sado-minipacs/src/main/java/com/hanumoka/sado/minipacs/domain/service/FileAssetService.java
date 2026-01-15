package com.hanumoka.sado.minipacs.domain.service;

import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.common.exception.ResourceNotFoundException;
import com.hanumoka.sado.common.util.EntityFinder;
import com.hanumoka.sado.common.tenant.TenantContext;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import com.hanumoka.sado.minipacs.domain.entity.FileAsset;
import com.hanumoka.sado.minipacs.domain.enums.FileCategory;
import com.hanumoka.sado.minipacs.domain.enums.FileStatus;
import com.hanumoka.sado.minipacs.domain.enums.ReferenceType;
import com.hanumoka.sado.minipacs.domain.repository.FileAssetRepository;
import com.hanumoka.sado.minipacs.infrastructure.config.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * FileAsset Service
 *
 * <p>DICOM 외 부수 파일(AI 결과, 리포트, Export 등) 관리
 *
 * <p>주요 기능:
 * <ul>
 *   <li>파일 업로드 (S3 + DB 저장)</li>
 *   <li>파일 다운로드 (S3 조회)</li>
 *   <li>파일 메타데이터 조회</li>
 *   <li>파일 삭제 (Soft Delete)</li>
 * </ul>
 *
 * <p>S3 경로 구조:
 * <pre>
 * assets/{category}/{referenceType}/{referenceId}/{uuid}_{filename}
 * </pre>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class FileAssetService {

    private final FileAssetRepository fileAssetRepository;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    /**
     * CRITICAL: Self-injection for AOP proxy access
     *
     * <p>Self-invocation 문제 해결:
     * - 같은 클래스 내 메서드 호출 시 AOP 프록시 우회
     * - @Transactional 등 AOP 기능이 작동하지 않음
     * - self를 통해 호출하면 프록시를 거쳐 AOP 작동
     *
     * <p>@Lazy 필수:
     * - Spring Boot 4.x에서 순환 참조 방지
     * - 지연 주입으로 Bean 생성 순서 문제 해결
     *
     * <p>사용 예:
     * <pre>{@code
     * // ❌ 작동 안 함 (직접 호출)
     * updateLastAccessedAt(id);
     *
     * // ✅ 작동함 (프록시 통해 호출)
     * self.updateLastAccessedAt(id);
     * }</pre>
     */
    @Autowired
    @Lazy
    private FileAssetService self;

    /**
     * FileAsset PK로 조회
     *
     * @param id FileAsset PK
     * @return FileAsset 엔티티
     * @throws ResourceNotFoundException FileAsset이 존재하지 않는 경우
     */
    public FileAsset findById(Long id) {
        return EntityFinder.findById(fileAssetRepository, id, "FileAsset");
    }

    /**
     * 참조 엔티티로 파일 목록 조회
     *
     * @param referenceType 참조 엔티티 타입
     * @param referenceId 참조 엔티티 ID
     * @return 파일 목록 (최신순)
     */
    public List<FileAsset> findByReference(ReferenceType referenceType, Long referenceId) {
        return fileAssetRepository.findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                referenceType, referenceId);
    }

    /**
     * 참조 엔티티 + 카테고리로 파일 목록 조회
     *
     * @param referenceType 참조 엔티티 타입
     * @param referenceId 참조 엔티티 ID
     * @param category 파일 카테고리
     * @return 파일 목록
     */
    public List<FileAsset> findByReferenceAndCategory(
            ReferenceType referenceType,
            Long referenceId,
            FileCategory category
    ) {
        return fileAssetRepository.findByReferenceTypeAndReferenceIdAndCategory(
                referenceType, referenceId, category);
    }

    /**
     * 파일 업로드
     *
     * <p>S3에 파일을 업로드하고 메타데이터를 DB에 저장합니다.
     *
     * @param file 업로드할 파일
     * @param category 파일 카테고리
     * @param referenceType 참조 엔티티 타입
     * @param referenceId 참조 엔티티 ID
     * @return 저장된 FileAsset
     * @throws BusinessException 파일 업로드 실패 시
     */
    @Transactional
    public FileAsset uploadFile(
            MultipartFile file,
            FileCategory category,
            ReferenceType referenceType,
            Long referenceId
    ) {
        String originalFilename = file.getOriginalFilename() != null ?
                file.getOriginalFilename() : "unnamed";

        // 1. S3 Key 생성
        String s3Key = buildS3Key(category, referenceType, referenceId, originalFilename);

        log.info("Uploading file: category={}, referenceType={}, referenceId={}, filename={}, s3Key={}",
                category, referenceType, referenceId, originalFilename, s3Key);

        String checksum = null;

        try {
            // CRITICAL: InputStream 재사용 불가 문제 해결
            // MultipartFile.getInputStream()은 재사용 보장 안 됨
            // → byte array로 한 번 읽어서 재사용
            byte[] fileBytes = file.getBytes();

            // 2. 체크섬 계산 (MD5)
            checksum = calculateChecksum(new ByteArrayInputStream(fileBytes));

            // 3. S3에 업로드
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(fileBytes));

            log.info("File uploaded to S3 successfully: s3Key={}", s3Key);

        } catch (IOException e) {
            log.error("Failed to read file: {}", originalFilename, e);
            throw new BusinessException(MiniPacsErrorCode.FILE_READ_FAILED,
                    "파일 읽기 실패: " + originalFilename);

        } catch (S3Exception e) {
            log.error("S3 upload failed: s3Key={}", s3Key, e);
            throw new BusinessException(MiniPacsErrorCode.STORAGE_UPLOAD_FAILED,
                    "파일 업로드 실패: " + s3Key);
        }

        // 4. FileAsset 엔티티 생성 및 저장
        FileAsset fileAsset = new FileAsset();
        fileAsset.setCategory(category);
        fileAsset.setReferenceType(referenceType);
        fileAsset.setReferenceId(referenceId);
        fileAsset.setFileName(originalFilename);
        fileAsset.setStoragePath(s3Key);
        fileAsset.setFileSize(file.getSize());
        fileAsset.setMimeType(file.getContentType());
        fileAsset.setChecksum(checksum);
        fileAsset.setStatus(FileStatus.ACTIVE);
        fileAsset.setStorageTier("HOT");

        // TTL 설정 (카테고리별)
        fileAsset.setExpiresAt(calculateExpiresAt(category));

        FileAsset saved = fileAssetRepository.save(fileAsset);
        log.info("FileAsset saved: id={}, s3Key={}", saved.getId(), s3Key);

        return saved;
    }

    /**
     * 파일 다운로드 InputStream 반환
     *
     * <p>주의: 호출자는 반환된 InputStream을 반드시 닫아야 합니다.
     *
     * @param id FileAsset PK
     * @return 파일 InputStream
     * @throws BusinessException 파일 다운로드 실패 시
     */
    public InputStream downloadFile(Long id) {
        FileAsset fileAsset = findById(id);

        log.info("Downloading file: id={}, s3Key={}", id, fileAsset.getStoragePath());

        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(fileAsset.getStoragePath())
                    .build();

            InputStream inputStream = s3Client.getObject(getRequest);

            // 마지막 접근 시간 업데이트 (self-injection으로 AOP 프록시 통과)
            self.updateLastAccessedAt(id);

            log.info("File downloaded successfully: id={}", id);
            return inputStream;

        } catch (NoSuchKeyException e) {
            log.error("File not found in S3: s3Key={}", fileAsset.getStoragePath(), e);
            throw new BusinessException(MiniPacsErrorCode.FILE_NOT_FOUND,
                    "파일을 찾을 수 없습니다: " + fileAsset.getStoragePath());

        } catch (S3Exception e) {
            log.error("S3 download failed: s3Key={}", fileAsset.getStoragePath(), e);
            throw new BusinessException(MiniPacsErrorCode.STORAGE_DOWNLOAD_FAILED,
                    "파일 다운로드 실패: " + fileAsset.getStoragePath());
        }
    }

    /**
     * Pre-signed URL 생성
     *
     * @param id FileAsset PK
     * @param validity URL 유효 시간
     * @return Pre-signed URL
     */
    public String getPresignedUrl(Long id, Duration validity) {
        FileAsset fileAsset = findById(id);

        log.info("Generating pre-signed URL: id={}, validity={}", id, validity);

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(fileAsset.getStoragePath())
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(validity)
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            String url = presignedRequest.url().toString();

            // 마지막 접근 시간 업데이트 (self-injection으로 AOP 프록시 통과)
            self.updateLastAccessedAt(id);

            log.info("Pre-signed URL generated: id={}", id);
            return url;

        } catch (S3Exception e) {
            log.error("Pre-signed URL generation failed: id={}", id, e);
            throw new BusinessException(MiniPacsErrorCode.STORAGE_DOWNLOAD_FAILED,
                    "Pre-signed URL 생성 실패");
        }
    }

    /**
     * 파일 삭제 (Soft Delete)
     *
     * <p>FileAsset 상태를 DELETED로 변경합니다.
     * <p>물리 파일 삭제는 FileAssetCleanupScheduler에서 처리합니다.
     *
     * @param id FileAsset PK
     */
    @Transactional
    public void deleteFile(Long id) {
        FileAsset fileAsset = findById(id);

        log.info("Deleting file (soft): id={}, s3Key={}", id, fileAsset.getStoragePath());

        fileAsset.markDeleted();
        fileAssetRepository.save(fileAsset);

        log.info("File marked as deleted: id={}", id);
    }

    // ========== Helper Methods ==========

    /**
     * S3 Key 생성
     *
     * <p>경로 구조: tenant-{tenantId}/assets/{category}/{referenceType}/{referenceId}/{uuid}_{filename}
     *
     * <p>멀티테넌시 격리:
     * <ul>
     *   <li>TenantContext에서 현재 요청의 테넌트 ID를 조회</li>
     *   <li>테넌트 ID를 경로 프리픽스로 사용하여 테넌트별 파일 격리</li>
     * </ul>
     */
    private String buildS3Key(
            FileCategory category,
            ReferenceType referenceType,
            Long referenceId,
            String filename
    ) {
        // 테넌트 ID 조회 (멀티테넌시 격리)
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("TenantContext is not set. Cannot generate storage path without tenant isolation.");
        }

        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String safeFilename = sanitizeFilename(filename);

        return String.format("tenant-%d/assets/%s/%s/%d/%s_%s",
                tenantId,
                category.name().toLowerCase(),
                referenceType.name().toLowerCase(),
                referenceId,
                uuid,
                safeFilename);
    }

    /**
     * 파일명 정규화 (특수문자 제거)
     */
    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * MD5 체크섬 계산
     *
     * <p><strong>CRITICAL - 리소스 관리 책임</strong>:
     * 이 메서드는 inputStream의 소유권을 가져가지 않습니다.
     * 호출자는 반드시 inputStream을 닫아야 합니다.
     *
     * <p>현재 사용 방식:
     * <ul>
     *   <li>{@code uploadFile()}: ByteArrayInputStream 사용 (close 불필요)</li>
     *   <li>향후 변경 시: 호출자가 try-with-resources로 관리 필요</li>
     * </ul>
     *
     * @param inputStream 체크섬을 계산할 입력 스트림
     * @return MD5 체크섬 (hex 문자열), 실패 시 null
     * @apiNote 호출자는 반드시 try-with-resources 또는 finally 블록에서
     *          inputStream을 닫아야 합니다. 그렇지 않으면 리소스가 누수됩니다.
     */
    private String calculateChecksum(InputStream inputStream) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }

            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (NoSuchAlgorithmException | IOException e) {
            log.warn("Failed to calculate checksum", e);
            return null;
        }
    }

    /**
     * 카테고리별 TTL 계산
     */
    private LocalDateTime calculateExpiresAt(FileCategory category) {
        return switch (category) {
            case EXPORT -> LocalDateTime.now().plusDays(7);       // 7일
            case SYSTEM -> LocalDateTime.now().plusDays(90);      // 90일
            case AI_RESULT, CLINICAL_DOC, DICOM -> null;          // 영구 보관
        };
    }

    /**
     * 마지막 접근 시간 업데이트
     *
     * <p>CRITICAL: 이 메서드는 반드시 self를 통해 호출해야 합니다!
     * <pre>{@code
     * // ❌ 작동 안 함 (AOP 프록시 우회, @Transactional 무시됨)
     * updateLastAccessedAt(id);
     *
     * // ✅ 올바른 사용 (AOP 프록시 통과, @Transactional 작동)
     * self.updateLastAccessedAt(id);
     * }</pre>
     *
     * <p>이유:
     * - 같은 클래스 내부에서 메서드 호출 시 Spring AOP 프록시를 거치지 않음
     * - @Transactional 등 AOP 기반 기능이 작동하지 않음
     * - self를 통해 호출하면 프록시를 거쳐 정상 작동
     *
     * @param id FileAsset PK
     */
    @Transactional
    public void updateLastAccessedAt(Long id) {
        fileAssetRepository.findById(id).ifPresent(fileAsset -> {
            fileAsset.setLastAccessedAt(LocalDateTime.now());
            fileAssetRepository.save(fileAsset);
        });
    }
}
