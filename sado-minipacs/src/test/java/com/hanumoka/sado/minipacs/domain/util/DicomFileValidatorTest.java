package com.hanumoka.sado.minipacs.domain.util;

import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DicomFileValidator 유닛 테스트
 *
 * <p>테스트 범위:
 * <ul>
 *   <li>파일 확장자 검증</li>
 *   <li>DICOM Magic Number 검증</li>
 *   <li>전체 검증</li>
 * </ul>
 */
@DisplayName("DicomFileValidator 테스트")
class DicomFileValidatorTest {

    // ========== 파일 확장자 검증 테스트 ==========

    @Nested
    @DisplayName("validateFileExtension() 테스트")
    class ValidateFileExtensionTest {

        @Test
        @DisplayName("[성공] .dcm 확장자 파일")
        void validateFileExtension_DcmFile_Success() {
            // when & then
            assertThatCode(() -> DicomFileValidator.validateFileExtension("test.dcm"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("[성공] .dicom 확장자 파일")
        void validateFileExtension_DicomFile_Success() {
            // when & then
            assertThatCode(() -> DicomFileValidator.validateFileExtension("test.dicom"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("[성공] .DCM 확장자 파일 (대문자)")
        void validateFileExtension_UppercaseDcmFile_Success() {
            // when & then
            assertThatCode(() -> DicomFileValidator.validateFileExtension("TEST.DCM"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("[성공] .DICOM 확장자 파일 (대문자)")
        void validateFileExtension_UppercaseDicomFile_Success() {
            // when & then
            assertThatCode(() -> DicomFileValidator.validateFileExtension("TEST.DICOM"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("[성공] 확장자 없는 파일 (IMG001)")
        void validateFileExtension_NoExtensionFile_Success() {
            // when & then
            assertThatCode(() -> DicomFileValidator.validateFileExtension("IMG001"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("[성공] 확장자 없는 파일 (SCAN_001)")
        void validateFileExtension_NoExtensionFileWithUnderscore_Success() {
            // when & then
            assertThatCode(() -> DicomFileValidator.validateFileExtension("SCAN_001"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("[실패] .jpg 파일")
        void validateFileExtension_JpgFile_ThrowsException() {
            // when & then
            assertThatThrownBy(() -> DicomFileValidator.validateFileExtension("image.jpg"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("apiCode", MiniPacsErrorCode.UNSUPPORTED_FILE_EXTENSION);
        }

        @Test
        @DisplayName("[실패] .png 파일")
        void validateFileExtension_PngFile_ThrowsException() {
            // when & then
            assertThatThrownBy(() -> DicomFileValidator.validateFileExtension("image.png"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("apiCode", MiniPacsErrorCode.UNSUPPORTED_FILE_EXTENSION);
        }

        @Test
        @DisplayName("[실패] .txt 파일")
        void validateFileExtension_TxtFile_ThrowsException() {
            // when & then
            assertThatThrownBy(() -> DicomFileValidator.validateFileExtension("document.txt"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("apiCode", MiniPacsErrorCode.UNSUPPORTED_FILE_EXTENSION);
        }

        @Test
        @DisplayName("[실패] 숨김 파일 (.hiddenfile)")
        void validateFileExtension_HiddenFile_ThrowsException() {
            // when & then
            assertThatThrownBy(() -> DicomFileValidator.validateFileExtension(".hiddenfile"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("apiCode", MiniPacsErrorCode.UNSUPPORTED_FILE_EXTENSION);
        }

        @Test
        @DisplayName("[실패] null 파일명")
        void validateFileExtension_NullFilename_ThrowsException() {
            // when & then
            assertThatThrownBy(() -> DicomFileValidator.validateFileExtension(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("apiCode", MiniPacsErrorCode.UNSUPPORTED_FILE_EXTENSION);
        }

        @Test
        @DisplayName("[실패] 빈 문자열 파일명")
        void validateFileExtension_EmptyFilename_ThrowsException() {
            // when & then
            assertThatThrownBy(() -> DicomFileValidator.validateFileExtension(""))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("apiCode", MiniPacsErrorCode.UNSUPPORTED_FILE_EXTENSION);
        }
    }

    // ========== DICOM Magic Number 검증 테스트 ==========

    @Nested
    @DisplayName("validateDicomMagicNumber() 테스트")
    class ValidateDicomMagicNumberTest {

        @Test
        @DisplayName("[성공] 유효한 DICOM magic number")
        void validateDicomMagicNumber_ValidMagicNumber_Success() {
            // given: 128 bytes preamble + 'DICM' + dummy data
            byte[] validDicomBytes = createValidDicomBytes();

            // when & then
            assertThatCode(() -> DicomFileValidator.validateDicomMagicNumber(validDicomBytes))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("[경고] magic number 없는 파일 (오래된 DICOM 호환)")
        void validateDicomMagicNumber_NoMagicNumber_LogsWarning() {
            // given: 132 bytes 파일, magic number 없음
            byte[] oldDicomBytes = new byte[132];
            Arrays.fill(oldDicomBytes, (byte) 0x00);

            // when & then: 예외 발생하지 않음 (경고만 출력)
            assertThatCode(() -> DicomFileValidator.validateDicomMagicNumber(oldDicomBytes))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("[실패] 파일 크기 부족 (< 132 bytes)")
        void validateDicomMagicNumber_FileTooSmall_ThrowsException() {
            // given: 100 bytes 파일 (최소 크기 132보다 작음)
            byte[] smallFile = new byte[100];

            // when & then
            assertThatThrownBy(() -> DicomFileValidator.validateDicomMagicNumber(smallFile))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("apiCode", MiniPacsErrorCode.DICOM_INVALID_FORMAT);
        }

        @Test
        @DisplayName("[실패] null 바이트 배열")
        void validateDicomMagicNumber_NullBytes_ThrowsException() {
            // when & then
            assertThatThrownBy(() -> DicomFileValidator.validateDicomMagicNumber(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("apiCode", MiniPacsErrorCode.DICOM_INVALID_FORMAT);
        }

        @Test
        @DisplayName("[실패] 빈 바이트 배열")
        void validateDicomMagicNumber_EmptyBytes_ThrowsException() {
            // given
            byte[] emptyBytes = new byte[0];

            // when & then
            assertThatThrownBy(() -> DicomFileValidator.validateDicomMagicNumber(emptyBytes))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("apiCode", MiniPacsErrorCode.DICOM_INVALID_FORMAT);
        }

        @Test
        @DisplayName("[성공] 정확히 132 bytes (최소 크기)")
        void validateDicomMagicNumber_ExactMinimumSize_Success() {
            // given: 정확히 132 bytes
            byte[] minimumSizeFile = createValidDicomBytes(132);

            // when & then
            assertThatCode(() -> DicomFileValidator.validateDicomMagicNumber(minimumSizeFile))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("[실패] 잘못된 magic number ('ABCD')")
        void validateDicomMagicNumber_WrongMagicNumber_LogsWarning() {
            // given: 128 bytes preamble + 'ABCD' (잘못된 magic number)
            byte[] wrongMagicBytes = new byte[200];
            wrongMagicBytes[128] = 'A';
            wrongMagicBytes[129] = 'B';
            wrongMagicBytes[130] = 'C';
            wrongMagicBytes[131] = 'D';

            // when & then: 경고만 출력, 예외 발생 안 함 (호환성)
            assertThatCode(() -> DicomFileValidator.validateDicomMagicNumber(wrongMagicBytes))
                .doesNotThrowAnyException();
        }
    }

    // ========== 전체 검증 테스트 ==========

    @Nested
    @DisplayName("validate() 테스트")
    class ValidateTest {

        @Test
        @DisplayName("[성공] 유효한 DICOM 파일 (test.dcm)")
        void validate_ValidDicomFile_Success() {
            // given
            String filename = "test.dcm";
            byte[] validDicomBytes = createValidDicomBytes();

            // when & then
            assertThatCode(() -> DicomFileValidator.validate(filename, validDicomBytes))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("[성공] 유효한 DICOM 파일 (test.dicom)")
        void validate_ValidDicomFileWithDicomExtension_Success() {
            // given
            String filename = "test.dicom";
            byte[] validDicomBytes = createValidDicomBytes();

            // when & then
            assertThatCode(() -> DicomFileValidator.validate(filename, validDicomBytes))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("[성공] 유효한 DICOM 파일 (확장자 없음)")
        void validate_ValidDicomFileNoExtension_Success() {
            // given
            String filename = "IMG001";
            byte[] validDicomBytes = createValidDicomBytes();

            // when & then
            assertThatCode(() -> DicomFileValidator.validate(filename, validDicomBytes))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("[실패] 잘못된 확장자 (.jpg)")
        void validate_InvalidExtension_ThrowsException() {
            // given
            String filename = "image.jpg";
            byte[] validDicomBytes = createValidDicomBytes();

            // when & then: 확장자 검증 단계에서 실패
            assertThatThrownBy(() -> DicomFileValidator.validate(filename, validDicomBytes))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("apiCode", MiniPacsErrorCode.UNSUPPORTED_FILE_EXTENSION);
        }

        @Test
        @DisplayName("[실패] 파일 크기 부족")
        void validate_FileTooSmall_ThrowsException() {
            // given
            String filename = "test.dcm";
            byte[] smallFile = new byte[100];

            // when & then: magic number 검증 단계에서 실패
            assertThatThrownBy(() -> DicomFileValidator.validate(filename, smallFile))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("apiCode", MiniPacsErrorCode.DICOM_INVALID_FORMAT);
        }
    }

    // ========== 헬퍼 메서드 ==========

    /**
     * 유효한 DICOM 바이트 배열 생성 (기본 크기: 200 bytes)
     *
     * <p>구조:
     * <ul>
     *   <li>[0-127]: 128 bytes preamble (0x00)</li>
     *   <li>[128-131]: 'DICM' magic number</li>
     *   <li>[132-199]: 더미 데이터 (0x00)</li>
     * </ul>
     *
     * @return 유효한 DICOM 바이트 배열
     */
    private byte[] createValidDicomBytes() {
        return createValidDicomBytes(200);
    }

    /**
     * 유효한 DICOM 바이트 배열 생성 (지정 크기)
     *
     * @param totalSize 전체 크기 (최소 132 bytes)
     * @return 유효한 DICOM 바이트 배열
     */
    private byte[] createValidDicomBytes(int totalSize) {
        if (totalSize < 132) {
            throw new IllegalArgumentException("DICOM 파일은 최소 132 bytes 이상이어야 합니다");
        }

        byte[] bytes = new byte[totalSize];

        // 128 bytes preamble (0x00으로 채움)
        Arrays.fill(bytes, 0, 128, (byte) 0x00);

        // 'DICM' magic number
        bytes[128] = 'D';
        bytes[129] = 'I';
        bytes[130] = 'C';
        bytes[131] = 'M';

        // 나머지 데이터 (0x00으로 채움)
        if (totalSize > 132) {
            Arrays.fill(bytes, 132, totalSize, (byte) 0x00);
        }

        return bytes;
    }
}
