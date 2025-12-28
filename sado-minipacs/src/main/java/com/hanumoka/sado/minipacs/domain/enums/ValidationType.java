package com.hanumoka.sado.minipacs.domain.enums;

/**
 * DICOM 검증 타입
 */
public enum ValidationType {

    /**
     * DICOM Conformance 검증
     * - DICOM 표준 준수 여부
     * - Transfer Syntax 지원 여부
     */
    DICOM_CONFORMANCE,

    /**
     * 필수 DICOM Tag 검증
     * - Patient ID, Study Instance UID 등
     * - Type 1 Tags 누락 확인
     */
    MANDATORY_TAGS,

    /**
     * 데이터 무결성 검증
     * - 이미지 데이터 손상 여부
     * - Pixel Data 유효성
     */
    DATA_INTEGRITY,

    /**
     * 중복 검증
     * - SOP Instance UID 중복 확인
     */
    DUPLICATE_CHECK
}

