package com.hanumoka.sado.minipacs.domain.enums;

/**
 * DICOM 검증 상태
 *
 * <p>검증 결과를 나타내며, 각 상태는 처리 가능 여부를 결정합니다.
 *
 * <p>사용 예시:
 * <pre>
 * {@code
 * ValidationLog log = new ValidationLog();
 * log.setStatus(DicomValidationStatus.SUCCESS);
 *
 * if (log.getStatus() == DicomValidationStatus.ERROR) {
 *     // 검증 실패 처리
 * }
 * }
 * </pre>
 */
public enum DicomValidationStatus {

    /**
     * 검증 성공
     *
     * <p>모든 검증 항목을 통과했으며, 정상적으로 처리 가능합니다.
     */
    SUCCESS,

    /**
     * 경고 (처리는 가능하지만 주의 필요)
     *
     * <p>검증은 통과했지만 개선이 필요한 항목이 있습니다.
     *
     * <p>예시:
     * <ul>
     *   <li>Optional Tag 누락</li>
     *   <li>권장 값과 다른 설정</li>
     *   <li>데이터 품질 저하</li>
     * </ul>
     *
     * <p>처리: 저장 허용 + 관리자 알림
     */
    WARNING,

    /**
     * 검증 실패 (처리 불가)
     *
     * <p>필수 검증 항목을 통과하지 못했으며, 처리할 수 없습니다.
     *
     * <p>예시:
     * <ul>
     *   <li>필수 Tag 누락</li>
     *   <li>데이터 손상</li>
     *   <li>DICOM 표준 위반</li>
     * </ul>
     *
     * <p>처리: 저장 거부 또는 기능 제한
     */
    ERROR
}
