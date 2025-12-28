package com.hanumoka.sado.minipacs.domain.enums;

/**
 * DICOM 검증 결과
 */
public enum ValidationResult {

    /**
     * 검증 성공
     */
    SUCCESS,

    /**
     * 경고 (처리는 가능하지만 주의 필요)
     * 예: Optional Tag 누락, 권장 값과 다름
     */
    WARNING,

    /**
     * 검증 실패 (처리 불가)
     * 예: 필수 Tag 누락, 데이터 손상
     */
    ERROR
}
