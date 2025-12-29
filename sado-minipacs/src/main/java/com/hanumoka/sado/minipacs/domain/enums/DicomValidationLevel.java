package com.hanumoka.sado.minipacs.domain.enums;

/**
 * DICOM 검증 레벨
 *
 * <p>검증 수준을 나타내며, 각 레벨은 점진적으로 엄격한 검증을 수행합니다.
 *
 * <p>사용 예시:
 * <ul>
 *   <li>LEVEL_1: 기본 DICOM 필수 태그 검증 (저장 가능 여부 판단)</li>
 *   <li>LEVEL_2: 심초음파 특화 검증 (임상 품질 판단)</li>
 *   <li>LEVEL_3: AI 준비 검증 (연구/분석 가능 여부 판단)</li>
 * </ul>
 */
public enum DicomValidationLevel {

    /**
     * Level 1: 기본 DICOM 검증
     *
     * <p>필수 태그 존재 확인:
     * <ul>
     *   <li>PatientID, StudyInstanceUID, SeriesInstanceUID, SOPInstanceUID</li>
     *   <li>SOPClassUID, Modality</li>
     * </ul>
     *
     * <p>실패 시: 저장 거부 (Status 0xA700)
     */
    LEVEL_1,

    /**
     * Level 2: 심초음파 특화 검증
     *
     * <p>2025년 DICOM 표준 반영:
     * <ul>
     *   <li>SOP Class: US (Ultrasound) 확인</li>
     *   <li>멀티프레임 시 FrameTime or CineRate 권장</li>
     *   <li>NumberOfFrames, FrameIncrementPointer</li>
     * </ul>
     *
     * <p>경고 시: 저장 허용 + 관리자 알림
     */
    LEVEL_2,

    /**
     * Level 3: AI 준비 검증
     *
     * <p>EchoNet-Dynamic 요구사항:
     * <ul>
     *   <li>해상도: 112x112 이상</li>
     *   <li>프레임: 16개 이상 (권장)</li>
     *   <li>픽셀 데이터 존재</li>
     * </ul>
     *
     * <p>실패 시: 저장 허용 (Trust Level = RESEARCH, AI 분석 스킵)
     */
    LEVEL_3
}
