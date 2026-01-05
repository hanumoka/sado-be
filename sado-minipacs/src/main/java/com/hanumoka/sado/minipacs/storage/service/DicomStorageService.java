package com.hanumoka.sado.minipacs.storage.service;

import com.hanumoka.sado.minipacs.storage.dto.DicomFileMetadata;
import com.hanumoka.sado.minipacs.storage.dto.StorageResult;

import java.io.InputStream;
import java.time.Duration;

/**
 * DICOM 파일 저장 서비스 인터페이스
 *
 * <p>DICOM 파일을 SeaweedFS S3 API를 통해 저장/조회/삭제합니다.
 *
 * <p>S3 Key 구조 (DICOMweb 표준):
 * <pre>
 * {bucket}/studies/{studyInstanceUid}/series/{seriesInstanceUid}/instances/{sopInstanceUid}.dcm
 * </pre>
 *
 * <p>구현체: {@link SeaweedFsS3StorageService}
 *
 * <p>설계 원칙:
 * <ul>
 *   <li>DICOMweb 표준 준수 - OHIF Viewer 호환성</li>
 *   <li>Pre-signed URL 지원 - Frontend 직접 다운로드</li>
 *   <li>InputStream 기반 - 범용성, 메모리 효율성</li>
 *   <li>인터페이스 분리 - 테스트 용이성, Storage 교체 가능성</li>
 * </ul>
 */
public interface DicomStorageService {

    /**
     * DICOM 파일 업로드 (추상화된 메타데이터 기반)
     *
     * <p>변경 사항 (2026-01-05):
     * <ul>
     *   <li>Controller가 Storage 경로를 결정하지 않음 (Separation of Concerns)</li>
     *   <li>DicomFileMetadata (추상화된 키)만 전달</li>
     *   <li>경로 생성은 StoragePathStrategy가 담당</li>
     *   <li>StorageResult (Storage가 결정한 경로) 반환</li>
     * </ul>
     *
     * <p>흐름:
     * <ol>
     *   <li>StoragePathStrategy.generatePath()로 경로 생성</li>
     *   <li>S3 PutObject 요청 생성 (Content-Type: application/dicom)</li>
     *   <li>InputStream → RequestBody 변환</li>
     *   <li>SeaweedFS에 업로드</li>
     *   <li>StorageResult 반환 (경로, 파일 크기 등)</li>
     * </ol>
     *
     * @param inputStream DICOM 파일 InputStream
     * @param metadata DICOM 파일 메타데이터 (추상화된 키)
     * @return StorageResult (Storage가 결정한 경로 및 메타데이터)
     * @throws com.hanumoka.sado.common.exception.BusinessException STORAGE_UPLOAD_FAILED - S3 업로드 실패 시
     */
    StorageResult uploadDicomFile(
            InputStream inputStream,
            DicomFileMetadata metadata
    );

    /**
     * DICOM 파일 업로드 (레거시)
     *
     * @deprecated Use {@link #uploadDicomFile(InputStream, DicomFileMetadata)} instead
     *
     * <p>S3 Key 생성 규칙 (DICOMweb 표준):
     * <pre>
     * studies/{studyInstanceUid}/series/{seriesInstanceUid}/instances/{sopInstanceUid}.dcm
     * </pre>
     *
     * <p>흐름:
     * <ol>
     *   <li>buildS3Key()로 S3 Key 생성</li>
     *   <li>S3 PutObject 요청 생성 (Content-Type: application/dicom)</li>
     *   <li>InputStream → RequestBody 변환</li>
     *   <li>SeaweedFS에 업로드</li>
     *   <li>S3 Key 반환</li>
     * </ol>
     *
     * @param studyInstanceUid Study Instance UID (0020,000D)
     * @param seriesInstanceUid Series Instance UID (0020,000E)
     * @param sopInstanceUid SOP Instance UID (0008,0018)
     * @param inputStream DICOM 파일 InputStream
     * @param contentLength 파일 크기 (bytes)
     * @return S3 Key (저장된 파일 경로)
     * @throws com.hanumoka.sado.common.exception.BusinessException STORAGE_UPLOAD_FAILED - S3 업로드 실패 시
     */
    @Deprecated
    String uploadDicomFile(
            String studyInstanceUid,
            String seriesInstanceUid,
            String sopInstanceUid,
            InputStream inputStream,
            long contentLength
    );

    /**
     * DICOM 파일 다운로드
     *
     * <p>흐름:
     * <ol>
     *   <li>S3 GetObject 요청 생성</li>
     *   <li>SeaweedFS에서 파일 조회</li>
     *   <li>ResponseInputStream 반환</li>
     * </ol>
     *
     * <p>주의: 호출자는 반환된 InputStream을 반드시 닫아야 합니다 (try-with-resources 권장)
     *
     * @param s3Key S3 Key (uploadDicomFile 반환값)
     * @return DICOM 파일 InputStream
     * @throws com.hanumoka.sado.common.exception.BusinessException FILE_NOT_FOUND - 파일이 존재하지 않을 때
     * @throws com.hanumoka.sado.common.exception.BusinessException STORAGE_DOWNLOAD_FAILED - S3 다운로드 실패 시
     */
    InputStream downloadDicomFile(String s3Key);

    /**
     * DICOM 파일 다운로드 (Resource 반환)
     *
     * <p>CRITICAL: 리소스 누수 방지
     * - Controller에서 ResponseEntity&lt;Resource&gt; 반환 시 사용
     * - Spring Framework가 Resource.close()를 자동 호출
     * - InputStream보다 안전한 리소스 관리
     *
     * <p>사용 예시:
     * <pre>{@code
     * // Controller
     * Resource resource = dicomStorageService.downloadDicomFileAsResource(s3Key);
     * return ResponseEntity.ok().body(resource);
     * }</pre>
     *
     * @param s3Key S3 Key (uploadDicomFile 반환값)
     * @return DICOM 파일 Resource (InputStreamResource)
     * @throws com.hanumoka.sado.common.exception.BusinessException FILE_NOT_FOUND - 파일이 존재하지 않을 때
     * @throws com.hanumoka.sado.common.exception.BusinessException STORAGE_DOWNLOAD_FAILED - S3 다운로드 실패 시
     */
    org.springframework.core.io.Resource downloadDicomFileAsResource(String s3Key);

    /**
     * 범용 파일 다운로드 (스트리밍)
     *
     * <p>DICOM뿐만 아니라 모든 FileAsset 타입의 파일을 다운로드합니다.
     * Backend File Proxy에서 사용됩니다.
     *
     * <p>특징:
     * <ul>
     *   <li>스트리밍 지원: 대용량 파일도 메모리 효율적으로 처리</li>
     *   <li>범용성: DICOM, AI 결과, Export 파일 등 모든 타입 지원</li>
     *   <li>감사 로그: FileProxyController에서 호출 전후 로그 기록</li>
     * </ul>
     *
     * <p>주의: 호출자는 반환된 InputStream을 반드시 닫아야 합니다
     *
     * @param s3Key S3 Key (FileAsset.storagePath)
     * @return 파일 InputStream
     * @throws com.hanumoka.sado.common.exception.BusinessException FILE_NOT_FOUND - 파일이 존재하지 않을 때
     * @throws com.hanumoka.sado.common.exception.BusinessException STORAGE_DOWNLOAD_FAILED - S3 다운로드 실패 시
     */
    InputStream downloadFileStream(String s3Key);

    /**
     * Pre-signed URL 생성
     *
     * <p>Frontend가 SeaweedFS에 직접 접근할 수 있는 임시 URL을 생성합니다.
     *
     * <p>보안:
     * <ul>
     *   <li>URL에 AWS Signature v4 포함 (인증 정보)</li>
     *   <li>만료 시간 설정 (기본 1시간)</li>
     *   <li>읽기 전용 (GetObject)</li>
     * </ul>
     *
     * <p>사용 예시:
     * <pre>
     * String url = dicomStorageService.getPresignedUrl(s3Key, Duration.ofHours(1));
     * // Frontend: fetch(url) → DICOM 파일 다운로드
     * </pre>
     *
     * @param s3Key S3 Key
     * @param validity URL 유효 시간 (예: Duration.ofHours(1))
     * @return Pre-signed URL (HTTP GET으로 파일 다운로드 가능)
     * @throws com.hanumoka.sado.common.exception.BusinessException STORAGE_DOWNLOAD_FAILED - Pre-signed URL 생성 실패 시
     */
    String getPresignedUrl(String s3Key, Duration validity);

    /**
     * DICOM 파일 삭제
     *
     * <p>주의: DICOM 원본 파일은 일반적으로 삭제하지 않습니다 (WORM 정책).
     * <p>이 메서드는 관리자 기능 또는 테스트용으로만 사용하세요.
     *
     * <p>S3 DeleteObject는 파일이 존재하지 않아도 성공 응답을 반환합니다 (멱등성).
     *
     * @param s3Key S3 Key
     * @throws com.hanumoka.sado.common.exception.BusinessException STORAGE_DELETE_FAILED - S3 삭제 실패 시
     */
    void deleteDicomFile(String s3Key);

    /**
     * S3 Key 생성 (DICOMweb 표준)
     *
     * <p>Utility 메서드: 다른 서비스에서 S3 Key를 생성할 때 사용
     *
     * <p>생성 규칙:
     * <pre>
     * studies/{studyInstanceUid}/series/{seriesInstanceUid}/instances/{sopInstanceUid}.dcm
     * </pre>
     *
     * <p>사용 예시:
     * <pre>
     * String s3Key = DicomStorageService.buildS3Key(
     *     "1.2.840.113619.2.55.3.12345",
     *     "1.2.840.113619.2.55.3.12345.1",
     *     "1.2.840.113619.2.55.3.12345.1.1"
     * );
     * // → "studies/1.2.840.113619.2.55.3.12345/series/1.2.840.113619.2.55.3.12345.1/instances/1.2.840.113619.2.55.3.12345.1.1.dcm"
     * </pre>
     *
     * @param studyInstanceUid Study Instance UID
     * @param seriesInstanceUid Series Instance UID
     * @param sopInstanceUid SOP Instance UID
     * @return S3 Key (studies/.../series/.../instances/....dcm)
     */
    static String buildS3Key(
            String studyInstanceUid,
            String seriesInstanceUid,
            String sopInstanceUid
    ) {
        return String.format(
                "studies/%s/series/%s/instances/%s.dcm",
                studyInstanceUid,
                seriesInstanceUid,
                sopInstanceUid
        );
    }
}
