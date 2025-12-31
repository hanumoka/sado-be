package com.hanumoka.sado.minipacs.storage.service;

import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.minipacs.exception.MiniPacsErrorCode;
import com.hanumoka.sado.minipacs.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * DicomStorageService Integration Tests
 *
 * <p>Mock S3Client 및 S3Presigner를 사용하여 DicomStorageService의 S3 API 연동 로직을 검증합니다.
 *
 * <p>테스트 전략:
 * <ul>
 *   <li>BaseIntegrationTest 제공 Mock 사용 (s3Client, s3Presigner)</li>
 *   <li>실제 Spring Bean 주입 (dicomStorageService)</li>
 *   <li>given-when-then 패턴</li>
 *   <li>9개 시나리오 (성공 6개, 예외 3개)</li>
 * </ul>
 */
@DisplayName("DicomStorageService Integration Tests")
class DicomStorageServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DicomStorageService dicomStorageService;

    /**
     * S3 Mock Bean Configuration
     *
     * <p>Spring Boot 권장 패턴: @TestConfiguration을 static inner class로 정의
     * <p>이렇게 하면 Spring이 자동으로 감지하고 Mock Bean을 주입합니다.
     *
     * <p>참고: https://reflectoring.io/spring-boot-testconfiguration/
     */
    @TestConfiguration
    static class S3MockConfig {
        @Bean
        @Primary
        public S3Client s3Client() {
            S3Client mockClient = Mockito.mock(S3Client.class);

            // SeaweedFsS3Config.createBucketIfNotExists() 메서드를 위한 기본 stub
            // 빈 버킷 목록을 반환하여 NullPointerException 방지
            ListBucketsResponse emptyBuckets = ListBucketsResponse.builder()
                    .buckets(java.util.Collections.emptyList())
                    .build();
            Mockito.when(mockClient.listBuckets()).thenReturn(emptyBuckets);

            return mockClient;
        }

        @Bean
        @Primary
        public S3Presigner s3Presigner() {
            return Mockito.mock(S3Presigner.class);
        }
    }

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3Presigner s3Presigner;

    /**
     * Reset Mocks before each test
     *
     * <p>공유된 Mock Bean의 상태를 초기화하여 테스트 간 독립성 보장
     * <p>listBuckets() stub은 다시 설정 (SeaweedFsS3Config.createBucketIfNotExists용)
     */
    @BeforeEach
    void resetMocks() {
        Mockito.reset(s3Client, s3Presigner);

        // SeaweedFsS3Config.createBucketIfNotExists() 를 위한 기본 stub 재설정
        ListBucketsResponse emptyBuckets = ListBucketsResponse.builder()
                .buckets(java.util.Collections.emptyList())
                .build();
        Mockito.when(s3Client.listBuckets()).thenReturn(emptyBuckets);
    }

    // 테스트 데이터 상수
    private static final String TEST_STUDY_UID = "1.2.840.113619.2.55.1.1762295501.65.1234567890.1";
    private static final String TEST_SERIES_UID = "1.2.840.113619.2.55.1.1762295501.65.1234567890.2";
    private static final String TEST_SOP_UID = "1.2.840.113619.2.55.1.1762295501.65.1234567890.3";
    private static final String EXPECTED_S3_KEY = "studies/" + TEST_STUDY_UID +
            "/series/" + TEST_SERIES_UID +
            "/instances/" + TEST_SOP_UID + ".dcm";
    private static final byte[] DUMMY_DICOM_DATA = "DICOM_FILE_CONTENT".getBytes();

    // ========== uploadDicomFile Tests ==========

    @Test
    @DisplayName("uploadDicomFile - 정상 업로드 시 S3 Key 반환 및 DICOMweb 형식 검증")
    void uploadDicomFile_Success_ReturnsS3Key() {
        // given: S3 PutObject 성공 Mock
        PutObjectResponse putObjectResponse = PutObjectResponse.builder().build();
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(putObjectResponse);

        // when: DICOM 파일 업로드
        InputStream inputStream = new ByteArrayInputStream(DUMMY_DICOM_DATA);
        String s3Key = dicomStorageService.uploadDicomFile(
                TEST_STUDY_UID,
                TEST_SERIES_UID,
                TEST_SOP_UID,
                inputStream,
                DUMMY_DICOM_DATA.length
        );

        // then: S3 Key 반환 및 DICOMweb 형식 검증
        assertThat(s3Key).isEqualTo(EXPECTED_S3_KEY);
        assertThat(s3Key).startsWith("studies/");
        assertThat(s3Key).contains("/series/");
        assertThat(s3Key).contains("/instances/");
        assertThat(s3Key).endsWith(".dcm");

        // then: S3 PutObject 호출 검증
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("uploadDicomFile - S3 업로드 실패 시 STORAGE_UPLOAD_FAILED 예외 발생")
    void uploadDicomFile_S3Exception_ThrowsStorageUploadFailed() {
        // given: S3 PutObject 실패 Mock
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willThrow(S3Exception.builder().message("S3 upload failed").build());

        // when & then: BusinessException 발생 검증
        InputStream inputStream = new ByteArrayInputStream(DUMMY_DICOM_DATA);

        assertThatThrownBy(() -> dicomStorageService.uploadDicomFile(
                TEST_STUDY_UID,
                TEST_SERIES_UID,
                TEST_SOP_UID,
                inputStream,
                DUMMY_DICOM_DATA.length
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DICOM 파일 업로드 실패")
                .extracting(ex -> ((BusinessException) ex).getApiCode())
                .isEqualTo(MiniPacsErrorCode.STORAGE_UPLOAD_FAILED);
    }

    // ========== downloadDicomFile Tests ==========

    @Test
    @DisplayName("downloadDicomFile - 정상 다운로드 시 InputStream 반환")
    void downloadDicomFile_Success_ReturnsInputStream() throws Exception {
        // given: S3 GetObject 성공 Mock
        ResponseInputStream<GetObjectResponse> responseInputStream =
                createMockResponseInputStream(DUMMY_DICOM_DATA);
        given(s3Client.getObject(any(GetObjectRequest.class)))
                .willReturn(responseInputStream);

        // when: DICOM 파일 다운로드
        InputStream result = dicomStorageService.downloadDicomFile(EXPECTED_S3_KEY);

        // then: InputStream 반환 및 내용 검증
        assertThat(result).isNotNull();
        byte[] content = result.readAllBytes();
        assertThat(content).isEqualTo(DUMMY_DICOM_DATA);

        // then: S3 GetObject 호출 검증
        verify(s3Client).getObject(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("downloadDicomFile - 파일 없음 시 FILE_NOT_FOUND 예외 발생")
    void downloadDicomFile_NoSuchKeyException_ThrowsFileNotFound() {
        // given: S3 GetObject NoSuchKeyException Mock
        given(s3Client.getObject(any(GetObjectRequest.class)))
                .willThrow(NoSuchKeyException.builder().message("Key not found").build());

        // when & then: BusinessException 발생 검증
        assertThatThrownBy(() -> dicomStorageService.downloadDicomFile(EXPECTED_S3_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("파일을 찾을 수 없습니다")
                .extracting(ex -> ((BusinessException) ex).getApiCode())
                .isEqualTo(MiniPacsErrorCode.FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("downloadDicomFile - S3 다운로드 실패 시 STORAGE_DOWNLOAD_FAILED 예외 발생")
    void downloadDicomFile_S3Exception_ThrowsStorageDownloadFailed() {
        // given: S3 GetObject 일반 S3Exception Mock
        given(s3Client.getObject(any(GetObjectRequest.class)))
                .willThrow(S3Exception.builder().message("S3 download failed").build());

        // when & then: BusinessException 발생 검증
        assertThatThrownBy(() -> dicomStorageService.downloadDicomFile(EXPECTED_S3_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DICOM 파일 다운로드 실패")
                .extracting(ex -> ((BusinessException) ex).getApiCode())
                .isEqualTo(MiniPacsErrorCode.STORAGE_DOWNLOAD_FAILED);
    }

    // ========== getPresignedUrl Tests ==========

    @Test
    @DisplayName("getPresignedUrl - URL 생성 성공 시 URL 반환 및 형식 검증")
    void getPresignedUrl_Success_ReturnsValidUrl() throws Exception {
        // given: S3Presigner 성공 Mock
        String expectedUrl = "http://localhost:10405/minipacs/studies/" + TEST_STUDY_UID +
                "/series/" + TEST_SERIES_UID +
                "/instances/" + TEST_SOP_UID + ".dcm" +
                "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=3600";

        // Mock PresignedGetObjectRequest
        PresignedGetObjectRequest presignedRequest = Mockito.mock(PresignedGetObjectRequest.class);
        Mockito.when(presignedRequest.url()).thenReturn(new URL(expectedUrl));

        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .willReturn(presignedRequest);

        // when: Pre-signed URL 생성
        String url = dicomStorageService.getPresignedUrl(EXPECTED_S3_KEY, Duration.ofHours(1));

        // then: URL 반환 및 형식 검증
        assertThat(url).isNotNull();
        assertThat(url).startsWith("http://");
        assertThat(url).contains("X-Amz-");

        // then: S3Presigner 호출 검증
        verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    @DisplayName("getPresignedUrl - URL 생성 실패 시 STORAGE_DOWNLOAD_FAILED 예외 발생")
    void getPresignedUrl_S3Exception_ThrowsStorageDownloadFailed() {
        // given: S3Presigner 실패 Mock
        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .willThrow(S3Exception.builder().message("Presigner failed").build());

        // when & then: BusinessException 발생 검증
        assertThatThrownBy(() ->
                dicomStorageService.getPresignedUrl(EXPECTED_S3_KEY, Duration.ofHours(1))
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Pre-signed URL 생성 실패")
                .extracting(ex -> ((BusinessException) ex).getApiCode())
                .isEqualTo(MiniPacsErrorCode.STORAGE_DOWNLOAD_FAILED);
    }

    // ========== deleteDicomFile Tests ==========

    @Test
    @DisplayName("deleteDicomFile - 정상 삭제 시 예외 없이 완료")
    void deleteDicomFile_Success_NoException() {
        // given: S3 DeleteObject 성공 Mock
        DeleteObjectResponse deleteObjectResponse = DeleteObjectResponse.builder().build();
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willReturn(deleteObjectResponse);

        // when & then: DICOM 파일 삭제 - 예외 없음
        assertThatNoException().isThrownBy(() ->
                dicomStorageService.deleteDicomFile(EXPECTED_S3_KEY)
        );

        // then: S3 DeleteObject 호출 검증
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));

        // Note: S3 DeleteObject는 파일이 존재하지 않아도 성공 응답을 반환합니다 (멱등성)
    }

    @Test
    @DisplayName("deleteDicomFile - S3 삭제 실패 시 STORAGE_DELETE_FAILED 예외 발생")
    void deleteDicomFile_S3Exception_ThrowsStorageDeleteFailed() {
        // given: S3 DeleteObject 실패 Mock
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willThrow(S3Exception.builder().message("S3 delete failed").build());

        // when & then: BusinessException 발생 검증
        assertThatThrownBy(() ->
                dicomStorageService.deleteDicomFile(EXPECTED_S3_KEY)
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DICOM 파일 삭제 실패")
                .extracting(ex -> ((BusinessException) ex).getApiCode())
                .isEqualTo(MiniPacsErrorCode.STORAGE_DELETE_FAILED);
    }

    // ========== Helper Methods ==========

    /**
     * Mock ResponseInputStream 생성 헬퍼 메서드
     *
     * <p>S3 GetObject 응답을 시뮬레이션하기 위한 ResponseInputStream 생성
     */
    private ResponseInputStream<GetObjectResponse> createMockResponseInputStream(byte[] data) {
        GetObjectResponse response = GetObjectResponse.builder().build();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        return new ResponseInputStream<>(response, inputStream);
    }
}
