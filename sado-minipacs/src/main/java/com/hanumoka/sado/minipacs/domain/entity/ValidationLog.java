package com.hanumoka.sado.minipacs.domain.entity;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import com.hanumoka.sado.minipacs.domain.enums.DicomValidationCategory;
import com.hanumoka.sado.minipacs.domain.enums.DicomValidationLevel;
import com.hanumoka.sado.minipacs.domain.enums.DicomValidationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 *  📚 ValidationLog 엔티티의 역할 완벽 가이드
 *
 *   🎯 핵심 개념: "DICOM 파일 검증 이력 추적"
 *
 *   ValidationLog는 DICOM 파일이 업로드될 때 거치는 검증 과정의 이력을 기록합니다.
 *
 *   ---
 *   🔄 DICOM C-STORE 수신 플로우
 *
 *   1. DICOM 파일 수신 (C-STORE)
 *       ↓
 *   2. 🔍 검증 단계 시작
 *       ↓
 *       ├─ [검증 1] DICOM Conformance 검증 → ValidationLog 생성
 *       ├─ [검증 2] 필수 Tag 검증 → ValidationLog 생성
 *       ├─ [검증 3] 데이터 무결성 검증 → ValidationLog 생성
 *       └─ [검증 4] 중복 확인 → ValidationLog 생성
 *       ↓
 *   3. 모든 검증 통과?
 *       ├─ YES → Instance 생성 + DicomMetadataRecord 생성
 *       └─ NO → 거부 (에러 로그만 남김)
 *
 *   ---
 *   📊 실제 시나리오 예시
 *
 *   시나리오 1: 정상 파일 업로드 ✅
 *
 *   업로드: 초음파 이미지 1개 (heart-001.dcm)
 *
 *   검증 과정:
 *   [검증 1] DICOM_CONFORMANCE
 *     - Transfer Syntax 확인: 1.2.840.10008.1.2.1 (지원됨)
 *     - 결과: SUCCESS ✅
 *     → ValidationLog 1개 생성
 *
 *   [검증 2] MANDATORY_TAGS
 *     - Patient ID: P12345 ✅
 *     - Study Instance UID: 1.2.840... ✅
 *     - SOP Instance UID: 1.2.840... ✅
 *     - 결과: SUCCESS ✅
 *     → ValidationLog 1개 생성
 *
 *   [검증 3] DATA_INTEGRITY
 *     - Pixel Data 손상 여부: 정상 ✅
 *     - 결과: SUCCESS ✅
 *     → ValidationLog 1개 생성
 *
 *   [검증 4] DUPLICATE_CHECK
 *     - SOP Instance UID 중복: 없음 ✅
 *     - 결과: SUCCESS ✅
 *     → ValidationLog 1개 생성
 *
 *   → Instance 1개 생성 (instance_id = 100)
 *   → ValidationLog 4개 생성 (모두 instance_id = 100 FK)
 *
 *   생성된 ValidationLog 테이블:
 *   | id  | instance_id | validation_type     | validation_result | error_message | created_at          |
 *   |-----|-------------|---------------------|-------------------|---------------|---------------------|
 *   | 1   | 100         | DICOM_CONFORMANCE   | SUCCESS           | NULL          | 2025-01-15 10:00:01 |
 *   | 2   | 100         | MANDATORY_TAGS      | SUCCESS           | NULL          | 2025-01-15 10:00:02 |
 *   | 3   | 100         | DATA_INTEGRITY      | SUCCESS           | NULL          | 2025-01-15 10:00:03 |
 *   | 4   | 100         | DUPLICATE_CHECK     | SUCCESS           | NULL          | 2025-01-15 10:00:04 |
 *
 *   ---
 *   시나리오 2: 필수 Tag 누락 ❌
 *
 *   업로드: 잘못된 파일 (broken-001.dcm)
 *
 *   검증 과정:
 *   [검증 1] DICOM_CONFORMANCE
 *     - Transfer Syntax 확인: 정상 ✅
 *     - 결과: SUCCESS ✅
 *     → ValidationLog 1개 생성
 *
 *   [검증 2] MANDATORY_TAGS
 *     - Patient ID: P12345 ✅
 *     - Study Instance UID: 누락 ❌
 *     - 결과: ERROR ❌
 *     - 에러 메시지: "Required Tag (0020,000D) Study Instance UID is missing"
 *     → ValidationLog 1개 생성 (ERROR)
 *
 *   → 검증 실패! Instance 생성 안 됨
 *   → 파일 거부 (C-STORE 응답: FAILURE)
 *
 *   생성된 ValidationLog 테이블:
 *   | id  | instance_id | validation_type     | validation_result | error_message                                    | created_at          |
 *   |-----|-------------|---------------------|-------------------|--------------------------------------------------|---------------------|
 *   | 5   | NULL        | DICOM_CONFORMANCE   | SUCCESS           | NULL
 *                              | 2025-01-15 10:05:01 |
 *   | 6   | NULL        | MANDATORY_TAGS      | ERROR             | Required Tag (0020,000D) Study Instance UID is missing | 2025-01-15 10:05:02 |
 *
 *   주목: instance_id = NULL (Instance가 생성되지 않았으므로)
 *
 *   ---
 *   시나리오 3: 경고 (WARNING) ⚠️
 *
 *   업로드: Tag 일부 누락 (optional-missing.dcm)
 *
 *   검증 과정:
 *   [검증 1] DICOM_CONFORMANCE
 *     - 결과: SUCCESS ✅
 *
 *   [검증 2] MANDATORY_TAGS
 *     - 필수 Tag: 모두 있음 ✅
 *     - Optional Tag (Patient's Age): 누락 ⚠️
 *     - 결과: WARNING ⚠️
 *     - 경고 메시지: "Optional Tag (0010,1010) Patient's Age is missing"
 *     → ValidationLog 1개 생성 (WARNING)
 *
 *   [검증 3] DATA_INTEGRITY
 *     - 결과: SUCCESS ✅
 *
 *   [검증 4] DUPLICATE_CHECK
 *     - 결과: SUCCESS ✅
 *
 *   → WARNING이지만 처리 가능 → Instance 생성 ✅
 *   → ValidationLog 4개 생성 (1개 WARNING, 3개 SUCCESS)
 *
 *   생성된 ValidationLog 테이블:
 *   | id  | instance_id | validation_type     | validation_result | error_message                                | created_at          |
 *   |-----|-------------|---------------------|-------------------|----------------------------------------------|---------------------|
 *   | 7   | 101         | DICOM_CONFORMANCE   | SUCCESS           | NULL
 *                          | 2025-01-15 10:10:01 |
 *   | 8   | 101         | MANDATORY_TAGS      | WARNING           | Optional Tag (0010,1010) Patient's Age is missing | 2025-01-15 10:10:02 |
 *   | 9   | 101         | DATA_INTEGRITY      | SUCCESS           | NULL
 *                          | 2025-01-15 10:10:03 |
 *   | 10  | 101         | DUPLICATE_CHECK     | SUCCESS           | NULL                                         | 2025-01-15 10:10:04 |
 *
 *   ---
 *   🎯 ValidationLog의 활용 방법
 *
 *   1️⃣ 업로드 실패 원인 추적
 *
 *   문제: "왜 이 DICOM 파일이 거부되었나요?"
 *
 *   해결:
 *   // instance_id = NULL이고 ERROR인 로그 조회
 *   List<ValidationLog> errorLogs = validationLogRepository
 *       .findByValidationResult(ValidationResult.ERROR);
 *
 *   // 결과: "Required Tag (0020,000D) Study Instance UID is missing"
 *
 *   ---
 *   2️⃣ 특정 Instance의 검증 이력 조회
 *
 *   문제: "이 이미지는 어떤 검증을 거쳤나요?"
 *
 *   해결:
 *   // Instance ID = 100의 검증 이력
 *   List<ValidationLog> logs = validationLogRepository
 *       .findByInstanceIdOrderByCreatedAtDesc(100L);
 *
 *   // 결과: 4개 로그 (DICOM_CONFORMANCE, MANDATORY_TAGS, DATA_INTEGRITY, DUPLICATE_CHECK)
 *   // 모두 SUCCESS
 *
 *   ---
 *   3️⃣ 데이터 품질 모니터링
 *
 *   문제: "최근 1주일간 얼마나 많은 파일이 검증 실패했나요?"
 *
 *   해결:
 *   // 최근 1주일 ERROR 로그
 *   LocalDateTime startDate = LocalDateTime.now().minusWeeks(1);
 *   LocalDateTime endDate = LocalDateTime.now();
 *
 *   List<ValidationLog> errorLogs = validationLogRepository
 *       .findByCreatedAtBetween(startDate, endDate)
 *       .stream()
 *       .filter(log -> log.getValidationResult() == ValidationResult.ERROR)
 *       .toList();
 *
 *   // 결과: 15개 ERROR 로그
 *   // → 데이터 품질 문제 파악
 *
 *   ---
 *   4️⃣ 특정 검증 타입의 실패 분석
 *
 *   문제: "필수 Tag 검증 실패가 얼마나 많나요?"
 *
 *   해결:
 *   // MANDATORY_TAGS + ERROR 조합 조회
 *   List<ValidationLog> mandatoryTagErrors = validationLogRepository
 *       .findByValidationTypeAndValidationResult(
 *           ValidationType.MANDATORY_TAGS,
 *           ValidationResult.ERROR
 *       );
 *
 *   // 결과: 8개 로그
 *   // → 어떤 Tag가 자주 누락되는지 분석 가능
 *
 *   ---
 *   💡 ValidationLog vs Instance 관계
 *
 *   관계 정리
 *
 *   ValidationLog (N) → Instance (1)
 *                        (Optional FK)
 *
 *   경우의 수:
 *   1. 검증 성공: Instance 생성 → ValidationLog.instance_id = 100
 *   2. 검증 실패: Instance 생성 안 됨 → ValidationLog.instance_id = NULL
 *   3. 검증 경고: Instance 생성 → ValidationLog.instance_id = 100
 *
 *   DB 제약조건:
 *   - instance_id: NULL 허용 (검증 실패 시)
 *   - ON DELETE SET NULL: Instance 삭제 시 FK를 NULL로 변경
 *
 *   ---
 *   🎓 정리
 *
 *   ValidationLog의 역할
 *
 *   1. 감사 추적: DICOM 파일 검증 과정의 모든 단계 기록
 *   2. 에러 추적: 업로드 실패 원인 분석
 *   3. 품질 관리: 데이터 품질 모니터링, 통계
 *   4. 디버깅: 특정 파일의 검증 이력 확인
 *
 *   생성 시점
 *
 *   - C-STORE 수신 시 각 검증 단계마다 생성
 *   - 검증 성공/실패/경고 모두 기록
 *
 *   주요 필드
 *
 *   - validationType: 어떤 검증을 했는지 (DICOM_CONFORMANCE, MANDATORY_TAGS 등)
 *   - validationResult: 결과는 무엇인지 (SUCCESS, WARNING, ERROR)
 *   - errorMessage: 실패/경고 시 상세 메시지
 *   - instanceId: 검증 성공 시 생성된 Instance FK (실패 시 NULL)
 */

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
     * Validation Level (검증 수준)
     * "어느 수준의 검증을 수행했는가?"
     *
     * - LEVEL_1: 기본 DICOM 필수 태그 검증 (저장 가능 여부 판단)
     * - LEVEL_2: 심초음파 특화 검증 (임상 품질 판단)
     * - LEVEL_3: AI 준비 검증 (연구/분석 가능 여부 판단)
     *
     * 예: 하나의 파일에 대해 LEVEL_1, LEVEL_2, LEVEL_3 검증을 각각 수행하면
     *     ValidationLog 3개가 생성됨 (각각 다른 validationLevel)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "validation_level", nullable = false, length = 16)
    private DicomValidationLevel validationLevel;

    /**
     * Validation Category (검증 카테고리)
     * "어떤 종류의 검증을 수행했는가?"
     *
     * - DICOM_CONFORMANCE: DICOM 표준 준수 여부
     * - MANDATORY_TAGS: 필수 태그 존재 여부
     * - DATA_INTEGRITY: 데이터 무결성 (Pixel Data 손상 등)
     * - DUPLICATE_CHECK: SOP Instance UID 중복 확인
     *
     * 예: LEVEL_1 검증 시 MANDATORY_TAGS, DATA_INTEGRITY 검증을 각각 수행하면
     *     ValidationLog 2개가 생성됨 (같은 validationLevel, 다른 category)
     *
     * 관계: validationLevel + category 조합으로 구체적인 검증 정의
     * 예: (LEVEL_1, MANDATORY_TAGS) = "기본 필수 태그 검증"
     *     (LEVEL_2, DICOM_CONFORMANCE) = "심초음파 DICOM 표준 준수 검증"
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "validation_category", nullable = false, length = 32)
    private DicomValidationCategory category;

    /**
     * Validation Status (검증 상태)
     * - SUCCESS: 검증 통과
     * - WARNING: 경고 (저장 허용, 관리자 알림)
     * - ERROR: 실패 (저장 거부 또는 기능 제한)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 16)
    private DicomValidationStatus status;

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

    // ========== 비즈니스 메서드 ==========

    /**
     * 검증 통과 여부
     */
    public boolean isPassed() {
        return status == DicomValidationStatus.SUCCESS;
    }

    /**
     * 검증 실패 여부
     */
    public boolean isFailed() {
        return status == DicomValidationStatus.ERROR;
    }

    /**
     * 경고 여부
     */
    public boolean hasWarning() {
        return status == DicomValidationStatus.WARNING;
    }
}
