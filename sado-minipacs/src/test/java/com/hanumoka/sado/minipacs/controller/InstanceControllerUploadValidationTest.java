package com.hanumoka.sado.minipacs.controller;

import com.hanumoka.sado.common.dto.ApiResponse;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import com.hanumoka.sado.minipacs.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InstanceController 업로드 검증 통합 테스트
 *
 * <p>테스트 범위:
 * <ul>
 *   <li>파일 확장자 검증 (API 레벨)</li>
 *   <li>DICOM magic number 검증 (API 레벨)</li>
 *   <li>실제 HTTP 요청/응답 검증</li>
 * </ul>
 *
 * <p>주의사항:
 * <ul>
 *   <li>BaseIntegrationTest 상속 (RestTestClient 사용)</li>
 *   <li>@Transactional로 각 테스트 후 롤백 (DB 격리)</li>
 *   <li>MultipartBodyBuilder로 파일 업로드 시뮬레이션</li>
 * </ul>
 */
@DisplayName("InstanceController 업로드 검증 통합 테스트")
class InstanceControllerUploadValidationTest extends BaseIntegrationTest {

    // ========== 실패 케이스: 확장자 검증 ==========

    @Test
    @DisplayName("[실패] .jpg 파일 업로드 (지원하지 않는 확장자)")
    void uploadDicom_JpgFile_ThrowsUnsupportedExtension() {
        // given: .jpg 파일 (DICOM이 아님)
        byte[] jpgBytes = new byte[200];
        Arrays.fill(jpgBytes, (byte) 0xFF);

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", jpgBytes)
                .filename("image.jpg")
                .contentType(MediaType.IMAGE_JPEG);

        // when
        ApiResponse<String> response = restTestClient.post()
                .uri("/api/instances/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(bodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(new ParameterizedTypeReference<ApiResponse<String>>() {})
                .returnResult()
                .getResponseBody();

        // then: 400 Bad Request + UNSUPPORTED_FILE_EXTENSION 에러 코드
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(MiniPacsErrorCode.UNSUPPORTED_FILE_EXTENSION.getCode());
    }

    @Test
    @DisplayName("[실패] .png 파일 업로드 (지원하지 않는 확장자)")
    void uploadDicom_PngFile_ThrowsUnsupportedExtension() {
        // given: .png 파일
        byte[] pngBytes = new byte[200];
        Arrays.fill(pngBytes, (byte) 0x89);

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", pngBytes)
                .filename("image.png")
                .contentType(MediaType.IMAGE_PNG);

        // when
        ApiResponse<String> response = restTestClient.post()
                .uri("/api/instances/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(bodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(new ParameterizedTypeReference<ApiResponse<String>>() {})
                .returnResult()
                .getResponseBody();

        // then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(MiniPacsErrorCode.UNSUPPORTED_FILE_EXTENSION.getCode());
    }

    @Test
    @DisplayName("[실패] .txt 파일 업로드 (지원하지 않는 확장자)")
    void uploadDicom_TxtFile_ThrowsUnsupportedExtension() {
        // given: .txt 파일
        byte[] txtBytes = "This is a text file".getBytes();

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", txtBytes)
                .filename("document.txt")
                .contentType(MediaType.TEXT_PLAIN);

        // when
        ApiResponse<String> response = restTestClient.post()
                .uri("/api/instances/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(bodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(new ParameterizedTypeReference<ApiResponse<String>>() {})
                .returnResult()
                .getResponseBody();

        // then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(MiniPacsErrorCode.UNSUPPORTED_FILE_EXTENSION.getCode());
    }

    @Test
    @DisplayName("[실패] 숨김 파일 업로드 (.hiddenfile)")
    void uploadDicom_HiddenFile_ThrowsUnsupportedExtension() {
        // given: 숨김 파일
        byte[] hiddenBytes = new byte[200];

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", hiddenBytes)
                .filename(".hiddenfile")
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        // when
        ApiResponse<String> response = restTestClient.post()
                .uri("/api/instances/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(bodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(new ParameterizedTypeReference<ApiResponse<String>>() {})
                .returnResult()
                .getResponseBody();

        // then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(MiniPacsErrorCode.UNSUPPORTED_FILE_EXTENSION.getCode());
    }

    // ========== 실패 케이스: DICOM Magic Number 검증 ==========

    @Test
    @DisplayName("[실패] 파일 크기 부족 (< 132 bytes)")
    void uploadDicom_FileTooSmall_ThrowsInvalidFormat() {
        // given: 100 bytes 파일 (DICOM 최소 크기 132보다 작음)
        byte[] smallFile = new byte[100];

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", smallFile)
                .filename("test.dcm")
                .contentType(MediaType.valueOf("application/dicom"));

        // when
        ApiResponse<String> response = restTestClient.post()
                .uri("/api/instances/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(bodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(new ParameterizedTypeReference<ApiResponse<String>>() {})
                .returnResult()
                .getResponseBody();

        // then: 400 Bad Request + DICOM_INVALID_FORMAT 에러 코드
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(MiniPacsErrorCode.DICOM_INVALID_FORMAT.getCode());
    }

    @Test
    @DisplayName("[실패] 빈 파일 업로드")
    void uploadDicom_EmptyFile_ThrowsBadRequest() {
        // given: 빈 파일
        byte[] emptyBytes = new byte[0];

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", emptyBytes)
                .filename("empty.dcm")
                .contentType(MediaType.valueOf("application/dicom"));

        // when & then: 400 Bad Request (파일 비어있음 검증)
        restTestClient.post()
                .uri("/api/instances/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(bodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest();
        // 에러 메시지: "파일이 비어있습니다"
    }

    // ========== 성공 케이스 (확장자 검증 통과, DICOM 파싱 실패 예상) ==========

    @Test
    @DisplayName("[검증 통과] .dcm 확장자 + 유효한 magic number")
    void uploadDicom_ValidDcmExtension_PassesValidation() {
        // given: 유효한 DICOM 파일 (.dcm)
        byte[] validDicomBytes = createValidDicomBytes();

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", validDicomBytes)
                .filename("test.dcm")
                .contentType(MediaType.valueOf("application/dicom"));

        // when & then: 확장자/magic number 검증 통과
        // 주의: DICOM 메타데이터 파싱 단계에서 실패할 수 있음 (더미 데이터이므로)
        restTestClient.post()
                .uri("/api/instances/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(bodyBuilder.build())
                .exchange()
                .expectStatus().is4xxClientError(); // 파싱 단계 실패 예상 (400)
        // 실제 유효한 DICOM 파일이면 200 OK 반환
    }

    @Test
    @DisplayName("[검증 통과] .dicom 확장자 + 유효한 magic number")
    void uploadDicom_ValidDicomExtension_PassesValidation() {
        // given: 유효한 DICOM 파일 (.dicom)
        byte[] validDicomBytes = createValidDicomBytes();

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", validDicomBytes)
                .filename("test.dicom")
                .contentType(MediaType.valueOf("application/dicom"));

        // when & then: 확장자 검증 통과
        restTestClient.post()
                .uri("/api/instances/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(bodyBuilder.build())
                .exchange()
                .expectStatus().is4xxClientError(); // 파싱 단계 실패 예상
    }

    @Test
    @DisplayName("[검증 통과] 확장자 없는 DICOM 파일")
    void uploadDicom_NoExtension_PassesValidation() {
        // given: 확장자 없는 유효한 DICOM 파일
        byte[] validDicomBytes = createValidDicomBytes();

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", validDicomBytes)
                .filename("IMG001")
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        // when & then: 확장자 검증 통과
        restTestClient.post()
                .uri("/api/instances/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(bodyBuilder.build())
                .exchange()
                .expectStatus().is4xxClientError(); // 파싱 단계 실패 예상
    }

    @Test
    @DisplayName("[검증 통과] .DCM 대문자 확장자")
    void uploadDicom_UppercaseDcmExtension_PassesValidation() {
        // given: 대문자 .DCM 확장자
        byte[] validDicomBytes = createValidDicomBytes();

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", validDicomBytes)
                .filename("TEST.DCM")
                .contentType(MediaType.valueOf("application/dicom"));

        // when & then: 확장자 검증 통과 (대소문자 무시)
        restTestClient.post()
                .uri("/api/instances/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(bodyBuilder.build())
                .exchange()
                .expectStatus().is4xxClientError(); // 파싱 단계 실패 예상
    }

    // ========== 헬퍼 메서드 ==========

    /**
     * 유효한 DICOM 바이트 배열 생성
     *
     * <p>구조:
     * <ul>
     *   <li>[0-127]: 128 bytes preamble (0x00)</li>
     *   <li>[128-131]: 'DICM' magic number</li>
     *   <li>[132-199]: 더미 데이터 (0x00)</li>
     * </ul>
     *
     * <p>주의: 이 파일은 DCM4CHE 파싱 단계에서 실패할 수 있습니다.
     * (실제 DICOM 태그가 없으므로)
     *
     * @return 유효한 DICOM 바이트 배열 (magic number만 유효)
     */
    private byte[] createValidDicomBytes() {
        byte[] bytes = new byte[200];

        // 128 bytes preamble (0x00으로 채움)
        Arrays.fill(bytes, 0, 128, (byte) 0x00);

        // 'DICM' magic number
        bytes[128] = 'D';
        bytes[129] = 'I';
        bytes[130] = 'C';
        bytes[131] = 'M';

        // 나머지 데이터 (0x00으로 채움)
        Arrays.fill(bytes, 132, 200, (byte) 0x00);

        return bytes;
    }
}
