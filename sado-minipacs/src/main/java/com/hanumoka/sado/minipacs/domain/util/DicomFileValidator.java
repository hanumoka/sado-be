package com.hanumoka.sado.minipacs.domain.util;

import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

/**
 * DICOM 파일 검증 유틸리티
 *
 * <p>업로드된 파일이 유효한 DICOM 파일인지 검증합니다.
 *
 * <p>검증 항목:
 * <ul>
 *   <li>파일 확장자: .dcm, .dicom, 확장자 없음만 허용</li>
 *   <li>DICOM Magic Number: 128 byte offset에서 'DICM' 확인</li>
 * </ul>
 *
 * <p>사용 예시:
 * <pre>{@code
 * // 확장자만 검증
 * DicomFileValidator.validateFileExtension("test.dcm");
 *
 * // Magic number만 검증
 * byte[] fileBytes = file.getBytes();
 * DicomFileValidator.validateDicomMagicNumber(fileBytes);
 *
 * // 전체 검증
 * DicomFileValidator.validate("test.dcm", fileBytes);
 * }</pre>
 */
@Slf4j
public class DicomFileValidator {

    /**
     * DICOM 파일 허용 확장자 목록
     */
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(".dcm", ".dicom");

    /**
     * DICOM Magic Number: 'DICM' (4 bytes)
     */
    private static final byte[] DICOM_MAGIC_NUMBER = {'D', 'I', 'C', 'M'};

    /**
     * DICOM Preamble 크기: 128 bytes
     * <p>DICOM 파일은 128 bytes preamble 다음에 'DICM' magic number가 위치합니다.
     */
    private static final int DICOM_PREAMBLE_LENGTH = 128;

    /**
     * DICOM 파일 최소 크기: 132 bytes (128 preamble + 4 magic number)
     */
    private static final int DICOM_MIN_FILE_SIZE = DICOM_PREAMBLE_LENGTH + DICOM_MAGIC_NUMBER.length;

    /**
     * 파일 확장자 검증
     *
     * <p>허용 확장자:
     * <ul>
     *   <li>.dcm</li>
     *   <li>.dicom</li>
     *   <li>확장자 없음 (예: IMG001, SCAN_001)</li>
     * </ul>
     *
     * <p>거부 대상:
     * <ul>
     *   <li>숨김 파일 (.hiddenfile)</li>
     *   <li>기타 확장자 (.jpg, .png, .txt 등)</li>
     * </ul>
     *
     * @param filename 검증할 파일명
     * @throws BusinessException UNSUPPORTED_FILE_EXTENSION - 지원하지 않는 확장자
     */
    public static void validateFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            log.error("파일명이 null 또는 비어있습니다");
            throw new BusinessException(
                MiniPacsErrorCode.UNSUPPORTED_FILE_EXTENSION,
                "파일명이 비어있습니다"
            );
        }

        String lowercaseFilename = filename.toLowerCase();

        // 1. 허용 확장자 체크 (.dcm, .dicom)
        for (String extension : ALLOWED_EXTENSIONS) {
            if (lowercaseFilename.endsWith(extension)) {
                log.debug("파일 확장자 검증 성공: {}", filename);
                return;
            }
        }

        // 2. 확장자 없는 파일 체크 (숨김 파일 제외)
        boolean hasNoExtension = !lowercaseFilename.contains(".");
        boolean isHiddenFile = lowercaseFilename.startsWith(".");

        if (hasNoExtension && !isHiddenFile) {
            log.debug("확장자 없는 DICOM 파일로 인식: {}", filename);
            return;
        }

        // 3. 검증 실패
        log.error("지원하지 않는 파일 확장자: {}", filename);
        throw new BusinessException(
            MiniPacsErrorCode.UNSUPPORTED_FILE_EXTENSION,
            "파일: " + filename
        );
    }

    /**
     * DICOM Magic Number 검증
     *
     * <p>DICOM 파일 구조:
     * <pre>
     * [0-127]   : 128 bytes Preamble (임의의 데이터)
     * [128-131] : 'DICM' Magic Number (4 bytes)
     * [132-...] : DICOM 데이터
     * </pre>
     *
     * <p>주의사항:
     * <ul>
     *   <li>오래된 DICOM 파일은 magic number가 없을 수 있음</li>
     *   <li>magic number 없어도 경고만 출력 (DCM4CHE가 추가 검증)</li>
     * </ul>
     *
     * @param fileBytes 검증할 파일 바이트
     * @throws BusinessException DICOM_INVALID_FORMAT - 파일 크기 부족
     */
    public static void validateDicomMagicNumber(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length < DICOM_MIN_FILE_SIZE) {
            log.error("파일 크기가 DICOM 최소 크기({})보다 작습니다: {} bytes",
                DICOM_MIN_FILE_SIZE, fileBytes == null ? 0 : fileBytes.length);
            throw new BusinessException(
                MiniPacsErrorCode.DICOM_INVALID_FORMAT,
                "파일 크기가 너무 작습니다 (최소 " + DICOM_MIN_FILE_SIZE + " bytes)"
            );
        }

        // 128 byte offset에서 'DICM' 확인
        boolean hasMagicNumber = true;
        for (int i = 0; i < DICOM_MAGIC_NUMBER.length; i++) {
            if (fileBytes[DICOM_PREAMBLE_LENGTH + i] != DICOM_MAGIC_NUMBER[i]) {
                hasMagicNumber = false;
                break;
            }
        }

        if (!hasMagicNumber) {
            // 오래된 DICOM 파일 호환성: magic number 없어도 경고만 출력
            log.warn("DICOM magic number가 없습니다. 오래된 DICOM 파일일 수 있습니다. DCM4CHE가 추가 검증합니다.");

            // 엄격한 검증이 필요한 경우 아래 주석 해제:
            // throw new BusinessException(
            //     MiniPacsErrorCode.DICOM_INVALID_FORMAT,
            //     "DICOM magic number가 없습니다"
            // );
        } else {
            log.debug("DICOM magic number 검증 성공");
        }
    }

    /**
     * DICOM 파일 전체 검증
     *
     * <p>확장자 + Magic Number 검증을 모두 수행합니다.
     *
     * @param filename 파일명
     * @param fileBytes 파일 바이트
     * @throws BusinessException UNSUPPORTED_FILE_EXTENSION - 지원하지 않는 확장자
     * @throws BusinessException DICOM_INVALID_FORMAT - 유효하지 않은 DICOM 형식
     */
    public static void validate(String filename, byte[] fileBytes) {
        log.info("DICOM 파일 검증 시작: {}", filename);

        // 1. 확장자 검증
        validateFileExtension(filename);

        // 2. Magic Number 검증
        validateDicomMagicNumber(fileBytes);

        log.info("DICOM 파일 검증 완료: {}", filename);
    }
}
