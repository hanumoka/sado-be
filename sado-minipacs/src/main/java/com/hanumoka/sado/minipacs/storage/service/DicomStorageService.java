package com.hanumoka.sado.minipacs.storage.service;

import com.github.f4b6a3.uuid.UuidCreator;
import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.minipacs.exception.MiniPacsErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;

/**
 * DICOM 파일 Storage 서비스
 *
 * <p>SeaweedFS S3 API를 사용하여 DICOM 파일을 업로드/다운로드/삭제합니다.
 *
 * <p>파일 식별자: UUID v7 (타임스탬프 기반, 정렬 가능, 높은 DB 성능)
 *
 * <p>예외 처리: BusinessException + MiniPacsErrorCode 패턴
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DicomStorageService {

    private final S3Client s3Client;

    @Value("${seaweedfs.s3.bucket}")
    private String bucket;

    /**
     * DICOM 파일 업로드
     *
     * <p>흐름:
     * <ol>
     *   <li>UUID v7 생성 (UuidCreator.getTimeOrderedEpoch())</li>
     *   <li>MultipartFile → byte[] 변환</li>
     *   <li>S3 PutObject 요청 생성 (Content-Type: application/dicom)</li>
     *   <li>SeaweedFS에 업로드</li>
     *   <li>fileId 반환</li>
     * </ol>
     *
     * @param file 업로드할 DICOM 파일 (MultipartFile)
     * @return fileId UUID v7 형식의 파일 식별자
     * @throws BusinessException FILE_READ_FAILED - 파일 읽기 실패 시
     * @throws BusinessException STORAGE_UPLOAD_FAILED - S3 업로드 실패 시
     */
    public String uploadDicomFile(MultipartFile file) {
        // UUID v7 생성 (타임스탬프 기반, 정렬 가능)
        String fileId = UuidCreator.getTimeOrderedEpoch().toString();

        log.info("Uploading DICOM file: originalFilename={}, fileId={}, size={} bytes",
                file.getOriginalFilename(), fileId, file.getSize());

        try {
            // MultipartFile을 byte[]로 변환
            byte[] dicomBytes = file.getBytes();

            // S3 PutObject 요청 생성
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileId)
                    .contentType("application/dicom")
                    .contentLength((long) dicomBytes.length)
                    .build();

            // SeaweedFS에 업로드
            s3Client.putObject(putRequest, RequestBody.fromBytes(dicomBytes));

            log.info("DICOM file uploaded successfully: fileId={}", fileId);
            return fileId;

        } catch (IOException e) {
            log.error("Failed to read MultipartFile: fileId={}, originalFilename={}",
                    fileId, file.getOriginalFilename(), e);

            throw new BusinessException(
                    MiniPacsErrorCode.FILE_READ_FAILED,
                    "파일 읽기 실패: " + file.getOriginalFilename()
            );

        } catch (S3Exception e) {
            log.error("S3 upload failed: fileId={}, bucket={}, originalFilename={}",
                    fileId, bucket, file.getOriginalFilename(), e);

            throw new BusinessException(
                    MiniPacsErrorCode.STORAGE_UPLOAD_FAILED,
                    "파일 업로드 실패: " + file.getOriginalFilename()
            );
        }
    }

    /**
     * DICOM 파일 다운로드
     *
     * <p>흐름:
     * <ol>
     *   <li>S3 GetObject 요청 생성</li>
     *   <li>SeaweedFS에서 파일 조회</li>
     *   <li>ResponseBytes → byte[] 변환</li>
     *   <li>byte[] 반환</li>
     * </ol>
     *
     * @param fileId 파일 식별자 (UUID v7)
     * @return DICOM 파일 바이트 배열
     * @throws BusinessException FILE_NOT_FOUND - 파일이 존재하지 않을 때
     * @throws BusinessException STORAGE_DOWNLOAD_FAILED - S3 다운로드 실패 시
     */
    public byte[] downloadDicomFile(String fileId) {
        log.info("Downloading DICOM file: fileId={}", fileId);

        try {
            // S3 GetObject 요청 생성
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileId)
                    .build();

            // SeaweedFS에서 파일 조회
            ResponseBytes<GetObjectResponse> responseBytes =
                    s3Client.getObjectAsBytes(getRequest);

            byte[] dicomBytes = responseBytes.asByteArray();

            log.info("DICOM file downloaded successfully: fileId={}, size={} bytes",
                    fileId, dicomBytes.length);

            return dicomBytes;

        } catch (NoSuchKeyException e) {
            log.error("DICOM file not found: fileId={}, bucket={}", fileId, bucket, e);

            throw new BusinessException(
                    MiniPacsErrorCode.FILE_NOT_FOUND,
                    "파일을 찾을 수 없습니다: " + fileId
            );

        } catch (S3Exception e) {
            log.error("S3 download failed: fileId={}, bucket={}", fileId, bucket, e);

            throw new BusinessException(
                    MiniPacsErrorCode.STORAGE_DOWNLOAD_FAILED,
                    "파일 다운로드 실패: " + fileId
            );
        }
    }

    /**
     * DICOM 파일 삭제
     *
     * <p>흐름:
     * <ol>
     *   <li>S3 DeleteObject 요청 생성</li>
     *   <li>SeaweedFS에서 파일 삭제</li>
     * </ol>
     *
     * <p>참고: S3 DeleteObject는 파일이 존재하지 않아도 성공 응답을 반환합니다 (멱등성)
     *
     * @param fileId 파일 식별자 (UUID v7)
     * @throws BusinessException STORAGE_DELETE_FAILED - S3 삭제 실패 시
     */
    public void deleteDicomFile(String fileId) {
        log.info("Deleting DICOM file: fileId={}", fileId);

        try {
            // S3 DeleteObject 요청 생성
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileId)
                    .build();

            // SeaweedFS에서 파일 삭제
            s3Client.deleteObject(deleteRequest);

            log.info("DICOM file deleted successfully: fileId={}", fileId);

        } catch (S3Exception e) {
            log.error("S3 delete failed: fileId={}, bucket={}", fileId, bucket, e);

            throw new BusinessException(
                    MiniPacsErrorCode.STORAGE_DELETE_FAILED,
                    "파일 삭제 실패: " + fileId
            );
        }
    }
}
