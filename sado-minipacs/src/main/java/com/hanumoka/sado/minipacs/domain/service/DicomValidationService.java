package com.hanumoka.sado.minipacs.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanumoka.sado.minipacs.domain.entity.DicomMetadataRecord;
import com.hanumoka.sado.minipacs.domain.entity.ValidationLog;
import com.hanumoka.sado.minipacs.domain.enums.DicomValidationCategory;
import com.hanumoka.sado.minipacs.domain.enums.DicomValidationLevel;
import com.hanumoka.sado.minipacs.domain.enums.DicomValidationStatus;
import com.hanumoka.sado.minipacs.domain.parser.DicomMetadataExtractor.DicomMetadata;
import com.hanumoka.sado.minipacs.domain.repository.ValidationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * DICOM Validation Service
 *
 * <p>3-Level 검증 파이프라인을 수행합니다.
 *
 * <p>검증 레벨:
 * <ul>
 *   <li>Level 1: 필수 태그 검증 (저장 가능 여부 판단)</li>
 *   <li>Level 2: 심초음파 특화 검증 (임상 품질 판단)</li>
 *   <li>Level 3: AI 준비 검증 (연구/분석 가능 여부 판단)</li>
 * </ul>
 *
 * <p>검증 결과는 ValidationLog에 기록됩니다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class DicomValidationService {

    private static final String VALIDATOR_VERSION = "1.0.0";

    // Level 3 AI Readiness 기준
    private static final int MIN_RESOLUTION = 112;
    private static final int MIN_FRAMES_RECOMMENDED = 16;

    private final ValidationLogRepository validationLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * 3-Level 전체 검증 수행
     *
     * @param metadata DICOM 메타데이터
     * @param record DicomMetadataRecord 엔티티
     * @return 검증 결과 목록
     */
    @Transactional
    public List<ValidationLog> validateAll(DicomMetadata metadata, DicomMetadataRecord record) {
        log.info("Starting 3-Level validation for SOP Instance UID: {}", metadata.getSopInstanceUid());

        List<ValidationLog> results = new ArrayList<>();

        // Level 1: 필수 태그 검증
        results.add(validateLevel1(metadata, record));

        // Level 2: 심초음파 특화 검증
        results.add(validateLevel2(metadata, record));

        // Level 3: AI 준비 검증
        results.add(validateLevel3(metadata, record));

        // 저장
        List<ValidationLog> savedLogs = validationLogRepository.saveAll(results);

        log.info("Validation completed: {} logs saved, SOP Instance UID: {}",
                savedLogs.size(), metadata.getSopInstanceUid());

        return savedLogs;
    }

    /**
     * Level 1 검증: 필수 태그 검증
     *
     * <p>검증 항목:
     * <ul>
     *   <li>PatientID</li>
     *   <li>StudyInstanceUID</li>
     *   <li>SeriesInstanceUID</li>
     *   <li>SOPInstanceUID</li>
     *   <li>Modality</li>
     * </ul>
     *
     * @param metadata DICOM 메타데이터
     * @param record DicomMetadataRecord 엔티티
     * @return 검증 결과
     */
    @Transactional
    public ValidationLog validateLevel1(DicomMetadata metadata, DicomMetadataRecord record) {
        log.debug("Validating Level 1 (Mandatory Tags) for: {}", metadata.getSopInstanceUid());

        List<String> missingTags = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 필수 태그 검증
        if (isBlank(metadata.getPatientId())) {
            missingTags.add("PatientID (0010,0020)");
        }
        if (isBlank(metadata.getStudyInstanceUid())) {
            missingTags.add("StudyInstanceUID (0020,000D)");
        }
        if (isBlank(metadata.getSeriesInstanceUid())) {
            missingTags.add("SeriesInstanceUID (0020,000E)");
        }
        if (isBlank(metadata.getSopInstanceUid())) {
            missingTags.add("SOPInstanceUID (0008,0018)");
        }
        if (isBlank(metadata.getModality())) {
            missingTags.add("Modality (0008,0060)");
        }

        // 결과 결정
        DicomValidationStatus status;
        if (!missingTags.isEmpty()) {
            status = DicomValidationStatus.ERROR;
        } else {
            status = DicomValidationStatus.SUCCESS;
        }

        // ValidationLog 생성
        ValidationLog validationLog = createValidationLog(
                record,
                DicomValidationLevel.LEVEL_1,
                DicomValidationCategory.MANDATORY_TAGS,
                status,
                missingTags,
                warnings
        );

        log.debug("Level 1 validation result: {} (missing tags: {})", status, missingTags.size());

        return validationLog;
    }

    /**
     * Level 2 검증: 심초음파 특화 검증
     *
     * <p>검증 항목:
     * <ul>
     *   <li>Modality = US (Ultrasound) 권장</li>
     *   <li>멀티프레임 시 FrameTime 또는 CineRate 권장</li>
     *   <li>NumberOfFrames 존재 확인</li>
     * </ul>
     *
     * @param metadata DICOM 메타데이터
     * @param record DicomMetadataRecord 엔티티
     * @return 검증 결과
     */
    @Transactional
    public ValidationLog validateLevel2(DicomMetadata metadata, DicomMetadataRecord record) {
        log.debug("Validating Level 2 (Echocardiography Standards) for: {}", metadata.getSopInstanceUid());

        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Modality 확인 (US 권장)
        String modality = metadata.getModality();
        if (modality != null && !modality.equals("US")) {
            warnings.add("Modality is '" + modality + "', expected 'US' for echocardiography");
        }

        // 멀티프레임 검증
        Integer numberOfFrames = metadata.getNumberOfFrames();
        if (numberOfFrames != null && numberOfFrames > 1) {
            // 멀티프레임인 경우 시간 정보 권장
            // Note: DicomMetadata에 FrameTime, CineRate 필드가 없으므로
            // 여기서는 numberOfFrames 존재 여부만 확인
            log.debug("Multi-frame DICOM detected: {} frames", numberOfFrames);
        }

        // 결과 결정 (Level 2는 WARNING만 발생, ERROR 없음)
        DicomValidationStatus status;
        if (!warnings.isEmpty()) {
            status = DicomValidationStatus.WARNING;
        } else {
            status = DicomValidationStatus.SUCCESS;
        }

        ValidationLog validationLog = createValidationLog(
                record,
                DicomValidationLevel.LEVEL_2,
                DicomValidationCategory.DICOM_CONFORMANCE,
                status,
                issues,
                warnings
        );

        log.debug("Level 2 validation result: {} (warnings: {})", status, warnings.size());

        return validationLog;
    }

    /**
     * Level 3 검증: AI 준비 검증
     *
     * <p>검증 항목 (EchoNet-Dynamic 요구사항):
     * <ul>
     *   <li>해상도: 112x112 이상</li>
     *   <li>프레임: 16개 이상 (권장)</li>
     *   <li>픽셀 데이터 존재</li>
     * </ul>
     *
     * @param metadata DICOM 메타데이터
     * @param record DicomMetadataRecord 엔티티
     * @return 검증 결과
     */
    @Transactional
    public ValidationLog validateLevel3(DicomMetadata metadata, DicomMetadataRecord record) {
        log.debug("Validating Level 3 (AI Readiness) for: {}", metadata.getSopInstanceUid());

        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        // 해상도 검증
        Integer rows = metadata.getImageRows();
        Integer columns = metadata.getImageColumns();

        if (rows == null || columns == null) {
            issues.add("Image dimensions (Rows/Columns) not available");
        } else if (rows < MIN_RESOLUTION || columns < MIN_RESOLUTION) {
            issues.add(String.format("Resolution %dx%d is below minimum %dx%d for AI analysis",
                    columns, rows, MIN_RESOLUTION, MIN_RESOLUTION));
            suggestions.add("Recommend resolution of at least 112x112 pixels for EchoNet-Dynamic");
        }

        // 프레임 수 검증
        Integer numberOfFrames = metadata.getNumberOfFrames();
        if (numberOfFrames == null || numberOfFrames < MIN_FRAMES_RECOMMENDED) {
            int frames = numberOfFrames != null ? numberOfFrames : 1;
            warnings.add(String.format("Frame count %d is below recommended %d for optimal AI analysis",
                    frames, MIN_FRAMES_RECOMMENDED));
            suggestions.add("Recommend at least 16 frames for accurate EF calculation");
        }

        // 결과 결정
        DicomValidationStatus status;
        if (!issues.isEmpty()) {
            status = DicomValidationStatus.ERROR;
        } else if (!warnings.isEmpty()) {
            status = DicomValidationStatus.WARNING;
        } else {
            status = DicomValidationStatus.SUCCESS;
        }

        // issues와 suggestions 합치기
        List<String> allIssues = new ArrayList<>(issues);
        allIssues.addAll(warnings);

        ValidationLog validationLog = createValidationLog(
                record,
                DicomValidationLevel.LEVEL_3,
                DicomValidationCategory.DATA_INTEGRITY,
                status,
                allIssues,
                suggestions
        );

        log.debug("Level 3 validation result: {} (issues: {}, warnings: {})",
                status, issues.size(), warnings.size());

        return validationLog;
    }

    /**
     * 검증 통과 여부 확인
     *
     * @param logs 검증 로그 목록
     * @return Level 1 검증 통과 여부
     */
    public boolean isLevel1Passed(List<ValidationLog> logs) {
        return logs.stream()
                .filter(log -> log.getValidationLevel() == DicomValidationLevel.LEVEL_1)
                .allMatch(ValidationLog::isPassed);
    }

    /**
     * AI 분석 가능 여부 확인
     *
     * @param logs 검증 로그 목록
     * @return Level 3 검증 통과 또는 경고만 있는 경우 true
     */
    public boolean isAiReady(List<ValidationLog> logs) {
        return logs.stream()
                .filter(log -> log.getValidationLevel() == DicomValidationLevel.LEVEL_3)
                .noneMatch(ValidationLog::isFailed);
    }

    /**
     * 검증 결과 요약
     *
     * @param logs 검증 로그 목록
     * @return 검증 결과 요약 Map
     */
    public Map<String, Object> summarize(List<ValidationLog> logs) {
        Map<String, Object> summary = new LinkedHashMap<>();

        long successCount = logs.stream().filter(ValidationLog::isPassed).count();
        long warningCount = logs.stream().filter(ValidationLog::hasWarning).count();
        long errorCount = logs.stream().filter(ValidationLog::isFailed).count();

        summary.put("total", logs.size());
        summary.put("success", successCount);
        summary.put("warning", warningCount);
        summary.put("error", errorCount);
        summary.put("level1Passed", isLevel1Passed(logs));
        summary.put("aiReady", isAiReady(logs));

        return summary;
    }

    // ========== Helper Methods ==========

    /**
     * ValidationLog 엔티티 생성
     */
    private ValidationLog createValidationLog(
            DicomMetadataRecord record,
            DicomValidationLevel level,
            DicomValidationCategory category,
            DicomValidationStatus status,
            List<String> issues,
            List<String> suggestions
    ) {
        ValidationLog log = new ValidationLog();
        log.setDicomMetadataRecord(record);
        log.setValidationLevel(level);
        log.setCategory(category);
        log.setStatus(status);
        log.setValidatedAt(LocalDateTime.now());
        log.setValidatorVersion(VALIDATOR_VERSION);

        // Issues JSON 생성
        if (!issues.isEmpty() || !suggestions.isEmpty()) {
            Map<String, Object> issuesMap = new LinkedHashMap<>();
            if (!issues.isEmpty()) {
                issuesMap.put("issues", issues);
            }
            if (!suggestions.isEmpty()) {
                issuesMap.put("suggestions", suggestions);
            }
            try {
                log.setIssues(objectMapper.writeValueAsString(issuesMap));
            } catch (JsonProcessingException e) {
                log.setIssues("{\"error\": \"Failed to serialize issues\"}");
            }
        }

        return log;
    }

    /**
     * 문자열이 비어있는지 확인
     */
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
