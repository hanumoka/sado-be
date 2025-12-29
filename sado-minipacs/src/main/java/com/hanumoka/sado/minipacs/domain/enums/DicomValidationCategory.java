package com.hanumoka.sado.minipacs.domain.enums;

/**
 * DICOM 검증 카테고리
 *
 * <p>검증 종류를 나타내며, 각 카테고리는 특정 검증 항목을 담당합니다.
 *
 * <p>사용 예시:
 * <pre>
 * {@code
 * ValidationLog log = new ValidationLog();
 * log.setCategory(DicomValidationCategory.MANDATORY_TAGS);
 * log.setStatus(DicomValidationStatus.SUCCESS);
 * }
 * </pre>
 */
public enum DicomValidationCategory {

    /**
     * DICOM Conformance 검증
     *
     * <p>검증 항목:
     * <ul>
     *   <li>DICOM 표준 준수 여부</li>
     *   <li>Transfer Syntax 지원 여부</li>
     *   <li>SOP Class UID 유효성</li>
     * </ul>
     */
    DICOM_CONFORMANCE,

    /**
     * 필수 DICOM Tag 검증
     *
     * <p>검증 항목:
     * <ul>
     *   <li>Patient ID, Study Instance UID 등 필수 태그 존재</li>
     *   <li>Type 1 Tags 누락 확인</li>
     *   <li>필수 속성 값 유효성 검증</li>
     * </ul>
     */
    MANDATORY_TAGS,

    /**
     * 데이터 무결성 검증
     *
     * <p>검증 항목:
     * <ul>
     *   <li>이미지 데이터 손상 여부</li>
     *   <li>Pixel Data 유효성</li>
     *   <li>파일 해시 검증</li>
     * </ul>
     */
    DATA_INTEGRITY,

    /**
     * 중복 검증
     *
     * <p>검증 항목:
     * <ul>
     *   <li>SOP Instance UID 중복 확인</li>
     *   <li>파일 해시 중복 확인</li>
     * </ul>
     */
    DUPLICATE_CHECK
}
