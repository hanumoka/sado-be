package com.hanumoka.sado.minipacs.storage.service;

import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import com.hanumoka.sado.minipacs.infrastructure.config.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.time.Duration;

/**
 * SeaweedFS S3 API 기반 DICOM 파일 저장 서비스
 *
 * <p>AWS SDK for Java v2를 사용하여 SeaweedFS S3 API와 통신합니다.
 *
 * <p>주요 기능:
 * <ul>
 *   <li>DICOM 파일 업로드 (PutObject)</li>
 *   <li>DICOM 파일 다운로드 (GetObject)</li>
 *   <li>Pre-signed URL 생성 (S3Presigner)</li>
 *   <li>DICOM 파일 삭제 (DeleteObject)</li>
 * </ul>
 *
 * <p>DICOMweb 표준 준수:
 * <pre>
 * S3 Key: studies/{studyUid}/series/{seriesUid}/instances/{sopUid}.dcm
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SeaweedFsS3StorageService implements DicomStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    /**
     * DICOM 파일 업로드
     *
     * <p>DICOMweb 표준 경로로 S3에 업로드합니다.
     *
     * @param studyInstanceUid Study Instance UID
     * @param seriesInstanceUid Series Instance UID
     * @param sopInstanceUid SOP Instance UID
     * @param inputStream DICOM 파일 InputStream
     * @param contentLength 파일 크기 (bytes)
     * @return S3 Key (저장된 파일 경로)
     * @throws BusinessException STORAGE_UPLOAD_FAILED - S3 업로드 실패 시
     */
    @Override
    public String uploadDicomFile(
            String studyInstanceUid,
            String seriesInstanceUid,
            String sopInstanceUid,
            InputStream inputStream,
            long contentLength
    ) {
        // 1. S3 Key 생성 (DICOMweb 표준)
        String s3Key = DicomStorageService.buildS3Key(
                studyInstanceUid,
                seriesInstanceUid,
                sopInstanceUid
        );

        log.info("Uploading DICOM file: bucket={}, s3Key={}, size={} bytes",
                s3Properties.getBucket(), s3Key, contentLength);

        try {
            // 2. S3 PutObject 요청 생성
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(s3Key)
                    .contentType("application/dicom")
                    .contentLength(contentLength)
                    .build();

            // 3. SeaweedFS에 업로드
            s3Client.putObject(putRequest, RequestBody.fromInputStream(inputStream, contentLength));

            log.info("DICOM file uploaded successfully: s3Key={}", s3Key);
            return s3Key;

        } catch (S3Exception e) {
            log.error("S3 upload failed: bucket={}, s3Key={}, studyUid={}, seriesUid={}, sopUid={}",
                    s3Properties.getBucket(), s3Key, studyInstanceUid, seriesInstanceUid, sopInstanceUid, e);

            throw new BusinessException(
                    MiniPacsErrorCode.STORAGE_UPLOAD_FAILED,
                    String.format("DICOM 파일 업로드 실패: %s", s3Key)
            );
        }
    }

    /**
     * DICOM 파일 다운로드
     *
     * <p>S3에서 파일을 조회하고 InputStream을 반환합니다.
     *
     * <p>주의: 호출자는 반환된 InputStream을 반드시 닫아야 합니다.
     *
     * @param s3Key S3 Key
     * @return DICOM 파일 InputStream
     * @throws BusinessException FILE_NOT_FOUND - 파일이 존재하지 않을 때
     * @throws BusinessException STORAGE_DOWNLOAD_FAILED - S3 다운로드 실패 시
     */
    @Override
    public InputStream downloadDicomFile(String s3Key) {
        log.info("Downloading DICOM file: bucket={}, s3Key={}", s3Properties.getBucket(), s3Key);

        try {
            // 1. S3 GetObject 요청 생성
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(s3Key)
                    .build();

            // 2. SeaweedFS에서 파일 조회 (InputStream 반환)
            InputStream inputStream = s3Client.getObject(getRequest);

            log.info("DICOM file downloaded successfully: s3Key={}", s3Key);
            return inputStream;

        } catch (NoSuchKeyException e) {
            log.error("DICOM file not found: bucket={}, s3Key={}", s3Properties.getBucket(), s3Key, e);

            throw new BusinessException(
                    MiniPacsErrorCode.FILE_NOT_FOUND,
                    String.format("파일을 찾을 수 없습니다: %s", s3Key)
            );

        } catch (S3Exception e) {
            log.error("S3 download failed: bucket={}, s3Key={}", s3Properties.getBucket(), s3Key, e);

            throw new BusinessException(
                    MiniPacsErrorCode.STORAGE_DOWNLOAD_FAILED,
                    String.format("DICOM 파일 다운로드 실패: %s", s3Key)
            );
        }
    }

    /**
     * Pre-signed URL 생성
     *
     * <p>Frontend가 SeaweedFS에 직접 접근할 수 있는 임시 URL을 생성합니다.
     *
     * <p>보안:
     * <ul>
     *   <li>AWS Signature v4 포함 (URL 파라미터로 인증)</li>
     *   <li>만료 시간 설정 (validity)</li>
     *   <li>읽기 전용 (GetObject)</li>
     * </ul>
     *
     * @param s3Key S3 Key
     * @param validity URL 유효 시간 (예: Duration.ofHours(1))
     * @return Pre-signed URL
     * @throws BusinessException STORAGE_DOWNLOAD_FAILED - Pre-signed URL 생성 실패 시
     */
    @Override
    public String getPresignedUrl(String s3Key, Duration validity) {
        log.info("Generating pre-signed URL: bucket={}, s3Key={}, validity={}",
                s3Properties.getBucket(), s3Key, validity);

        try {
            // 1. GetObject 요청 생성
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(s3Key)
                    .build();

            // 2. Pre-signed URL 요청 생성
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(validity)
                    .getObjectRequest(getObjectRequest)
                    .build();

            // 3. Pre-signed URL 생성
            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            String url = presignedRequest.url().toString();

            log.info("Pre-signed URL generated successfully: s3Key={}, url={}", s3Key, url);
            return url;

        } catch (S3Exception e) {
            log.error("Pre-signed URL generation failed: bucket={}, s3Key={}",
                    s3Properties.getBucket(), s3Key, e);

            throw new BusinessException(
                    MiniPacsErrorCode.STORAGE_DOWNLOAD_FAILED,
                    String.format("Pre-signed URL 생성 실패: %s", s3Key)
            );
        }
    }

    /**
     * DICOM 파일 삭제
     *
     * <p>S3에서 파일을 삭제합니다.
     *
     * <p>참고: S3 DeleteObject는 파일이 존재하지 않아도 성공 응답을 반환합니다 (멱등성).
     *
     * @param s3Key S3 Key
     * @throws BusinessException STORAGE_DELETE_FAILED - S3 삭제 실패 시
     */
    @Override
    public void deleteDicomFile(String s3Key) {
        log.info("Deleting DICOM file: bucket={}, s3Key={}", s3Properties.getBucket(), s3Key);

        try {
            // 1. S3 DeleteObject 요청 생성
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(s3Key)
                    .build();

            // 2. SeaweedFS에서 파일 삭제
            s3Client.deleteObject(deleteRequest);

            log.info("DICOM file deleted successfully: s3Key={}", s3Key);

        } catch (S3Exception e) {
            log.error("S3 delete failed: bucket={}, s3Key={}", s3Properties.getBucket(), s3Key, e);

            throw new BusinessException(
                    MiniPacsErrorCode.STORAGE_DELETE_FAILED,
                    String.format("DICOM 파일 삭제 실패: %s", s3Key)
            );
        }
    }
}
