package com.hanumoka.sado.minipacs.domain.entity;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * DICOM Storage Layer - 검증 로그 엔티티
 * DICOM 메타데이터 검증 결과 기록 (Level 1/2/3)
 */
@Entity
@Table(name = "validation_log")
@Getter
@Setter
@NoArgsConstructor
public class ValidationLog extends TenantAwareEntity {

    // ========== DicomMetadataRecord 관계 ==========

    /**
     * 검증 대상 DICOM 메타데이터
     * N개의 검증 로그 → 1개의 DicomMetadataRecord
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dicom_metadata_record_id", nullable = false)
    private DicomMetadataRecord dicomMetadataRecord;

    // ========== 검증 정보 ==========

    /**
     * Validation Level
     * - LEVEL_1: 기본 DICOM 필수 태그 검증
     * - LEVEL_2: 심초음파 특화 검증
     * - LEVEL_3: AI 준비 검증 (EchoNet 요구사항)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "validation_level", nullable = false, length = 16)
    private ValidationLevel validationLevel;

    /**
     * Validation Result
     * - PASS: 검증 통과
     * - WARNING: 경고 (저장 허용, 관리자 알림)
     * - FAIL: 실패 (저장 거부 또는 기능 제한)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 16)
    private ValidationResult result;

    /**
     * Issues (JSON)
     * 검증 결과 상세 (실패/경고 이유)
     *
     * 예시:
     * {
     *   "missing_tags": ["FrameTime", "CineRate"],
     *   "warnings": ["FrameTime and CineRate both missing"],
     *   "suggestions": ["Provide FrameTime or CineRate for video playback"]
     * }
     */
    @Column(name = "issues", columnDefinition = "JSON")
    private String issues;

    /**
     * Validated At
     * 검증 실행 시간
     */
    @Column(name = "validated_at", nullable = false)
    private LocalDateTime validatedAt;

    /**
     * Validator Version
     * 검증 로직 버전 (추적성)
     * 예: "1.0.0", "2024-12-27"
     */
    @Column(name = "validator_version", length = 32)
    private String validatorVersion;

    // ========== Enum 정의 ==========

    /**
     * 검증 레벨
     */
    public enum ValidationLevel {
        /**
         * Level 1: 기본 DICOM 검증
         * 필수 태그 존재 확인
         * - PatientID, StudyInstanceUID, SeriesInstanceUID, SOPInstanceUID
         * - SOPClassUID, Modality
         *
         * 실패 시: 저장 거부 (Status 0xA700)
         */
        LEVEL_1,

        /**
         * Level 2: 심초음파 특화 검증
         * 2025년 DICOM 표준 반영
         * - SOP Class: US (Ultrasound) 확인
         * - 멀티프레임 시 FrameTime or CineRate 권장
         * - NumberOfFrames, FrameIncrementPointer
         *
         * 경고 시: 저장 허용 + 관리자 알림
         */
        LEVEL_2,

        /**
         * Level 3: AI 준비 검증
         * EchoNet-Dynamic 요구사항
         * - 해상도: 112x112 이상
         * - 프레임: 16개 이상 (권장)
         * - 픽셀 데이터 존재
         *
         * 실패 시: 저장 허용 (Trust Level = RESEARCH, AI 분석 스킵)
         */
        LEVEL_3
    }

    /**
     * 검증 결과
     */
    public enum ValidationResult {
        /**
         * 검증 통과
         */
        PASS,

        /**
         * 경고 (저장 허용, 관리자 알림)
         */
        WARNING,

        /**
         * 실패 (저장 거부 또는 기능 제한)
         */
        FAIL
    }

    // ========== 비즈니스 메서드 ==========

    /**
     * 검증 통과 여부
     */
    public boolean isPassed() {
        return result == ValidationResult.PASS;
    }

    /**
     * 검증 실패 여부
     */
    public boolean isFailed() {
        return result == ValidationResult.FAIL;
    }

    /**
     * 경고 여부
     */
    public boolean hasWarning() {
        return result == ValidationResult.WARNING;
    }
}
