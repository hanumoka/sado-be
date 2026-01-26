# 24. DICOM 검증 파이프라인 가이드

> **작성일**: 2025-12-28
> **목적**: 3단계 DICOM 검증 파이프라인 전체 구현 코드
> **우선순위**: CRITICAL (Week 4 필수)

---

## 1. Executive Summary

### 1.1 검증 파이프라인 개요

```
┌──────────────────────────────────────────────────────────┐
│                  DICOM Ingest Pipeline                   │
└──────────────────────────────────────────────────────────┘
                          │
                          ▼
        ┌─────────────────────────────────┐
        │ Level 1: Basic Validator        │
        │ - 필수 태그 검증                │
        │ - SOP Instance UID 중복 확인    │
        └──────────┬──────────────────────┘
                   │ PASS
                   ▼
        ┌─────────────────────────────────┐
        │ Level 2: Echo Validator          │
        │ - US Modality 검증              │
        │ - 멀티프레임 확인                │
        │ - FrameTime/CineRate 검증       │
        └──────────┬──────────────────────┘
                   │ PASS
                   ▼
        ┌─────────────────────────────────┐
        │ Level 3: Identity Validator      │
        │ - PatientID 매핑 시도           │
        │ - Issuer 검증                   │
        │ - TrustLevel 판정               │
        └──────────┬──────────────────────┘
                   │
                   ▼
        ┌─────────────────────────────────┐
        │ ValidationLog 저장               │
        │ TrustLevel 할당                 │
        └─────────────────────────────────┘
```

### 1.2 TrustLevel 판정 기준

| TrustLevel | 조건 | 사용 가능 기능 |
|------------|------|---------------|
| **VERIFIED** | PatientID + Issuer 일치, EMR 매핑 성공 | 전체 (진단, 보고서) |
| **RESEARCH** | 검증 통과, EMR 매핑 실패 | AI 분석만 |
| **EDUCATIONAL** | 익명화 데이터 (PatientID = ANON) | 조회만 |
| **UNVERIFIED** | Level 1/2 검증 실패 | 조회만 (읽기 전용) |

---

## 2. 도메인 모델

### 2.1 ValidationResult

```java
// ValidationResult.java
@Data
@Builder
public class ValidationResult {
    private ValidationLevel level;
    private ValidationStatus status;
    private List<ValidationError> errors;
    private List<ValidationWarning> warnings;
    private Map<String, Object> metadata;

    public boolean hasCriticalErrors() {
        return errors.stream()
            .anyMatch(e -> e.getSeverity() == Severity.CRITICAL);
    }

    public boolean isPassed() {
        return status == ValidationStatus.PASS;
    }
}

public enum ValidationLevel {
    LEVEL1_BASIC,
    LEVEL2_ECHO,
    LEVEL3_IDENTITY
}

public enum ValidationStatus {
    PASS,
    PASS_WITH_WARNINGS,
    FAIL
}

// ValidationError.java
@Data
@Builder
public class ValidationError {
    private String code;
    private Severity severity;
    private String message;
    private String tagPath;  // e.g., "PatientID", "SequenceOfUltrasoundRegions[0].RegionLocationMinX0"
}

public enum Severity {
    CRITICAL,  // 저장 거부
    ERROR,     // 저장 가능, TrustLevel 하락
    WARNING    // 저장 가능, 로그만
}
```

### 2.2 ValidationLog Entity

```java
// ValidationLogEntity.java
@Entity
@Table(name = "validation_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "sop_instance_uid", nullable = false, length = 128)
    private String sopInstanceUid;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_level", nullable = false)
    private ValidationLevel validationLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false)
    private ValidationStatus validationStatus;

    @Convert(converter = JsonConverter.class)
    @Column(name = "errors", columnDefinition = "JSON")
    private List<ValidationError> errors;

    @Convert(converter = JsonConverter.class)
    @Column(name = "warnings", columnDefinition = "JSON")
    private List<ValidationWarning> warnings;

    @Column(name = "validated_at", nullable = false)
    private Instant validatedAt;

    @Index
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

---

## 3. Level 1: Basic Validator

### 3.1 필수 태그 검증

```java
// Level1BasicValidator.java
@Component
@Slf4j
public class Level1BasicValidator implements DicomValidator {

    private static final List<Integer> REQUIRED_TAGS = List.of(
        Tag.SOPInstanceUID,
        Tag.SOPClassUID,
        Tag.StudyInstanceUID,
        Tag.SeriesInstanceUID,
        Tag.PatientID
    );

    @Override
    public ValidationResult validate(Attributes dcm, ValidationContext context) {
        List<ValidationError> errors = new ArrayList<>();

        // 1. 필수 태그 검증
        for (int tag : REQUIRED_TAGS) {
            if (!dcm.contains(tag) || dcm.getString(tag) == null) {
                errors.add(ValidationError.builder()
                    .code("MISSING_REQUIRED_TAG")
                    .severity(Severity.CRITICAL)
                    .message("Required tag missing: " + TagUtils.toString(tag))
                    .tagPath(TagUtils.toString(tag))
                    .build());
            }
        }

        // 2. SOPInstanceUID 중복 검증
        String sopInstanceUid = dcm.getString(Tag.SOPInstanceUID);
        if (sopInstanceUid != null && isDuplicate(sopInstanceUid, context.getTenantId())) {
            errors.add(ValidationError.builder()
                .code("DUPLICATE_SOP_INSTANCE_UID")
                .severity(Severity.CRITICAL)
                .message("SOPInstanceUID already exists: " + sopInstanceUid)
                .tagPath("SOPInstanceUID")
                .build());
        }

        // 3. UID 형식 검증
        validateUidFormat(dcm, Tag.SOPInstanceUID, "SOPInstanceUID", errors);
        validateUidFormat(dcm, Tag.StudyInstanceUID, "StudyInstanceUID", errors);
        validateUidFormat(dcm, Tag.SeriesInstanceUID, "SeriesInstanceUID", errors);

        ValidationStatus status = errors.isEmpty() ? ValidationStatus.PASS : ValidationStatus.FAIL;

        return ValidationResult.builder()
            .level(ValidationLevel.LEVEL1_BASIC)
            .status(status)
            .errors(errors)
            .warnings(List.of())
            .build();
    }

    private void validateUidFormat(Attributes dcm, int tag, String tagName, List<ValidationError> errors) {
        String uid = dcm.getString(tag);
        if (uid != null && !isValidUid(uid)) {
            errors.add(ValidationError.builder()
                .code("INVALID_UID_FORMAT")
                .severity(Severity.CRITICAL)
                .message(tagName + " format invalid: " + uid)
                .tagPath(tagName)
                .build());
        }
    }

    private boolean isValidUid(String uid) {
        // UID format: 0-9 and '.' only, max 64 chars
        return uid.matches("^[0-9.]{1,64}$") && !uid.startsWith(".") && !uid.endsWith(".");
    }

    private boolean isDuplicate(String sopInstanceUid, String tenantId) {
        // Repository 조회
        return instanceRepository.existsByTenantIdAndSopInstanceUid(tenantId, sopInstanceUid);
    }
}
```

---

## 4. Level 2: Echo Validator

### 4.1 심초음파 전용 검증

```java
// Level2EchoValidator.java
@Component
@Slf4j
public class Level2EchoValidator implements DicomValidator {

    @Override
    public ValidationResult validate(Attributes dcm, ValidationContext context) {
        List<ValidationError> errors = new ArrayList<>();
        List<ValidationWarning> warnings = new ArrayList<>();

        // 1. Modality 검증
        String modality = dcm.getString(Tag.Modality);
        if (!"US".equals(modality)) {
            errors.add(ValidationError.builder()
                .code("UNSUPPORTED_MODALITY")
                .severity(Severity.CRITICAL)
                .message("Only US (Ultrasound) modality supported, got: " + modality)
                .tagPath("Modality")
                .build());
            return buildResult(ValidationStatus.FAIL, errors, warnings);
        }

        // 2. 멀티프레임 확인
        int numberOfFrames = dcm.getInt(Tag.NumberOfFrames, 1);
        boolean isMultiframe = numberOfFrames > 1;

        if (isMultiframe) {
            validateMultiframe(dcm, errors, warnings);
        }

        // 3. FrameTime 또는 CineRate 검증 (멀티프레임 전용)
        if (isMultiframe) {
            validateFrameRate(dcm, numberOfFrames, errors, warnings);
        }

        // 4. ImageType 검증
        String[] imageType = dcm.getStrings(Tag.ImageType);
        if (imageType == null || imageType.length == 0) {
            warnings.add(ValidationWarning.builder()
                .code("MISSING_IMAGE_TYPE")
                .message("ImageType tag missing (recommended for US)")
                .tagPath("ImageType")
                .build());
        }

        ValidationStatus status = errors.isEmpty() ?
            (warnings.isEmpty() ? ValidationStatus.PASS : ValidationStatus.PASS_WITH_WARNINGS) :
            ValidationStatus.FAIL;

        return buildResult(status, errors, warnings);
    }

    private void validateMultiframe(Attributes dcm, List<ValidationError> errors, List<ValidationWarning> warnings) {
        // SequenceOfUltrasoundRegions 검증 (선택사항이지만 권장)
        Sequence regions = dcm.getSequence(Tag.SequenceOfUltrasoundRegions);
        if (regions == null || regions.isEmpty()) {
            warnings.add(ValidationWarning.builder()
                .code("MISSING_US_REGIONS")
                .message("SequenceOfUltrasoundRegions missing (recommended for multiframe US)")
                .tagPath("SequenceOfUltrasoundRegions")
                .build());
        } else {
            validateUltrasoundRegions(regions, errors, warnings);
        }
    }

    private void validateFrameRate(Attributes dcm, int numberOfFrames, List<ValidationError> errors, List<ValidationWarning> warnings) {
        Double frameTime = dcm.getDouble(Tag.FrameTime, null);  // ms per frame
        Double cineRate = dcm.getDouble(Tag.CineRate, null);    // frames per second

        if (frameTime == null && cineRate == null) {
            warnings.add(ValidationWarning.builder()
                .code("MISSING_FRAME_RATE")
                .message("Neither FrameTime nor CineRate found (recommended for multiframe)")
                .tagPath("FrameTime/CineRate")
                .build());
            return;
        }

        // FrameTime 검증 (0 < frameTime < 10000ms)
        if (frameTime != null) {
            if (frameTime <= 0 || frameTime > 10000) {
                errors.add(ValidationError.builder()
                    .code("INVALID_FRAME_TIME")
                    .severity(Severity.ERROR)
                    .message("FrameTime out of range: " + frameTime + "ms (expected 0-10000)")
                    .tagPath("FrameTime")
                    .build());
            }
        }

        // CineRate 검증 (0 < cineRate <= 200 fps)
        if (cineRate != null) {
            if (cineRate <= 0 || cineRate > 200) {
                errors.add(ValidationError.builder()
                    .code("INVALID_CINE_RATE")
                    .severity(Severity.ERROR)
                    .message("CineRate out of range: " + cineRate + " fps (expected 0-200)")
                    .tagPath("CineRate")
                    .build());
            }
        }

        // 일관성 검증 (둘 다 있으면 일치 확인)
        if (frameTime != null && cineRate != null) {
            double calculatedCineRate = 1000.0 / frameTime;
            if (Math.abs(calculatedCineRate - cineRate) > 0.1) {
                warnings.add(ValidationWarning.builder()
                    .code("FRAME_RATE_MISMATCH")
                    .message(String.format("FrameTime (%.2f fps) and CineRate (%.2f fps) mismatch",
                        calculatedCineRate, cineRate))
                    .tagPath("FrameTime/CineRate")
                    .build());
            }
        }
    }

    private void validateUltrasoundRegions(Sequence regions, List<ValidationError> errors, List<ValidationWarning> warnings) {
        for (int i = 0; i < regions.size(); i++) {
            Attributes region = regions.get(i);

            // RegionLocationMinX0, RegionLocationMinY0 필수
            if (!region.contains(Tag.RegionLocationMinX0)) {
                errors.add(ValidationError.builder()
                    .code("MISSING_REGION_LOCATION")
                    .severity(Severity.ERROR)
                    .message("RegionLocationMinX0 missing in region[" + i + "]")
                    .tagPath("SequenceOfUltrasoundRegions[" + i + "].RegionLocationMinX0")
                    .build());
            }

            if (!region.contains(Tag.RegionLocationMinY0)) {
                errors.add(ValidationError.builder()
                    .code("MISSING_REGION_LOCATION")
                    .severity(Severity.ERROR)
                    .message("RegionLocationMinY0 missing in region[" + i + "]")
                    .tagPath("SequenceOfUltrasoundRegions[" + i + "].RegionLocationMinY0")
                    .build());
            }
        }
    }

    private ValidationResult buildResult(ValidationStatus status, List<ValidationError> errors, List<ValidationWarning> warnings) {
        return ValidationResult.builder()
            .level(ValidationLevel.LEVEL2_ECHO)
            .status(status)
            .errors(errors)
            .warnings(warnings)
            .build();
    }
}
```

---

## 5. Level 3: Identity Validator

### 5.1 환자 식별 및 TrustLevel 판정

```java
// Level3IdentityValidator.java
@Component
@RequiredArgsConstructor
@Slf4j
public class Level3IdentityValidator implements DicomValidator {

    private final PatientMatchingService patientMatchingService;
    private final EmrAdapterService emrAdapterService;

    @Override
    public ValidationResult validate(Attributes dcm, ValidationContext context) {
        List<ValidationError> errors = new ArrayList<>();
        List<ValidationWarning> warnings = new ArrayList<>();
        Map<String, Object> metadata = new HashMap<>();

        String patientId = dcm.getString(Tag.PatientID);
        String issuerOfPatientId = dcm.getString(Tag.IssuerOfPatientID);

        // 1. 익명화 데이터 확인
        if ("ANON".equalsIgnoreCase(patientId) || "ANONYMOUS".equalsIgnoreCase(patientId)) {
            metadata.put("trustLevel", TrustLevel.EDUCATIONAL);
            metadata.put("reason", "Anonymous data");
            warnings.add(ValidationWarning.builder()
                .code("ANONYMOUS_DATA")
                .message("Anonymous patient data detected")
                .tagPath("PatientID")
                .build());

            return buildResult(ValidationStatus.PASS_WITH_WARNINGS, errors, warnings, metadata);
        }

        // 2. Issuer 검증
        if (issuerOfPatientId == null || issuerOfPatientId.isBlank()) {
            warnings.add(ValidationWarning.builder()
                .code("MISSING_ISSUER")
                .message("IssuerOfPatientID missing (recommended for identity matching)")
                .tagPath("IssuerOfPatientID")
                .build());
        }

        // 3. EMR 매핑 시도
        TrustLevel trustLevel;
        try {
            PatientMatchingResult matchingResult = patientMatchingService.match(
                context.getTenantId(),
                patientId,
                issuerOfPatientId,
                dcm
            );

            if (matchingResult.isMatched()) {
                trustLevel = TrustLevel.VERIFIED;
                metadata.put("applicationPatientId", matchingResult.getApplicationPatientId());
                metadata.put("matchScore", matchingResult.getScore());
            } else {
                trustLevel = TrustLevel.RESEARCH;
                metadata.put("matchFailureReason", matchingResult.getFailureReason());
                warnings.add(ValidationWarning.builder()
                    .code("EMR_MATCHING_FAILED")
                    .message("Patient not found in EMR: " + matchingResult.getFailureReason())
                    .tagPath("PatientID")
                    .build());
            }

        } catch (Exception e) {
            log.error("EMR matching failed", e);
            trustLevel = TrustLevel.RESEARCH;
            metadata.put("matchError", e.getMessage());
            warnings.add(ValidationWarning.builder()
                .code("EMR_MATCHING_ERROR")
                .message("EMR matching service error: " + e.getMessage())
                .tagPath("PatientID")
                .build());
        }

        metadata.put("trustLevel", trustLevel);

        ValidationStatus status = errors.isEmpty() ?
            (warnings.isEmpty() ? ValidationStatus.PASS : ValidationStatus.PASS_WITH_WARNINGS) :
            ValidationStatus.FAIL;

        return buildResult(status, errors, warnings, metadata);
    }

    private ValidationResult buildResult(ValidationStatus status,
                                          List<ValidationError> errors,
                                          List<ValidationWarning> warnings,
                                          Map<String, Object> metadata) {
        return ValidationResult.builder()
            .level(ValidationLevel.LEVEL3_IDENTITY)
            .status(status)
            .errors(errors)
            .warnings(warnings)
            .metadata(metadata)
            .build();
    }
}

// TrustLevel enum
public enum TrustLevel {
    VERIFIED,      // EMR 매핑 성공
    RESEARCH,      // 검증 통과, EMR 매핑 실패
    EDUCATIONAL,   // 익명화 데이터
    UNVERIFIED     // 검증 실패
}
```

---

## 6. 통합 파이프라인

### 6.1 DicomValidationService

```java
// DicomValidationService.java
@Service
@RequiredArgsConstructor
@Slf4j
public class DicomValidationService {

    private final Level1BasicValidator level1Validator;
    private final Level2EchoValidator level2Validator;
    private final Level3IdentityValidator level3Validator;
    private final ValidationLogRepository validationLogRepository;

    @Transactional
    public ValidationPipelineResult validateFull(Attributes dcm, String tenantId) {
        ValidationContext context = ValidationContext.builder()
            .tenantId(tenantId)
            .build();

        List<ValidationResult> results = new ArrayList<>();
        TrustLevel trustLevel = TrustLevel.UNVERIFIED;

        // Level 1: Basic
        ValidationResult level1 = level1Validator.validate(dcm, context);
        results.add(level1);
        saveValidationLog(dcm, tenantId, level1);

        if (!level1.isPassed()) {
            log.warn("Level 1 validation failed for {}", dcm.getString(Tag.SOPInstanceUID));
            return buildPipelineResult(results, TrustLevel.UNVERIFIED);
        }

        // Level 2: Echo
        ValidationResult level2 = level2Validator.validate(dcm, context);
        results.add(level2);
        saveValidationLog(dcm, tenantId, level2);

        if (!level2.isPassed()) {
            log.warn("Level 2 validation failed for {}", dcm.getString(Tag.SOPInstanceUID));
            return buildPipelineResult(results, TrustLevel.UNVERIFIED);
        }

        // Level 3: Identity
        ValidationResult level3 = level3Validator.validate(dcm, context);
        results.add(level3);
        saveValidationLog(dcm, tenantId, level3);

        // TrustLevel 추출
        if (level3.getMetadata() != null) {
            trustLevel = (TrustLevel) level3.getMetadata().get("trustLevel");
        }

        return buildPipelineResult(results, trustLevel != null ? trustLevel : TrustLevel.RESEARCH);
    }

    private void saveValidationLog(Attributes dcm, String tenantId, ValidationResult result) {
        ValidationLogEntity log = ValidationLogEntity.builder()
            .tenantId(tenantId)
            .sopInstanceUid(dcm.getString(Tag.SOPInstanceUID))
            .validationLevel(result.getLevel())
            .validationStatus(result.getStatus())
            .errors(result.getErrors())
            .warnings(result.getWarnings())
            .validatedAt(Instant.now())
            .createdAt(Instant.now())
            .build();

        validationLogRepository.save(log);
    }

    private ValidationPipelineResult buildPipelineResult(List<ValidationResult> results, TrustLevel trustLevel) {
        return ValidationPipelineResult.builder()
            .results(results)
            .trustLevel(trustLevel)
            .overallStatus(determineOverallStatus(results))
            .build();
    }

    private ValidationStatus determineOverallStatus(List<ValidationResult> results) {
        boolean hasErrors = results.stream().anyMatch(r -> !r.getErrors().isEmpty());
        if (hasErrors) {
            return ValidationStatus.FAIL;
        }

        boolean hasWarnings = results.stream().anyMatch(r -> !r.getWarnings().isEmpty());
        return hasWarnings ? ValidationStatus.PASS_WITH_WARNINGS : ValidationStatus.PASS;
    }
}
```

---

## 7. 사용 예시

### 7.1 Ingest Pipeline에서 사용

```java
// IngestProcessor.java
@Component
@RequiredArgsConstructor
public class IngestProcessor {

    private final DicomValidationService validationService;
    private final DicomStorageService storageService;

    public void process(IngestMessage message) {
        File tempFile = new File(message.getTempFilePath());
        Attributes dcm = DicomUtils.read(tempFile);

        // 전체 검증
        ValidationPipelineResult validation = validationService.validateFull(
            dcm,
            message.getTenantId()
        );

        if (validation.getOverallStatus() == ValidationStatus.FAIL) {
            log.error("DICOM validation failed: {}", validation.getResults());
            throw new DicomValidationException("Validation failed");
        }

        // TrustLevel과 함께 저장
        storageService.store(dcm, tempFile, validation.getTrustLevel());

        log.info("DICOM stored with TrustLevel: {}", validation.getTrustLevel());
    }
}
```

---

## 8. 테스트 코드

```java
// Level1BasicValidatorTest.java
@SpringBootTest
class Level1BasicValidatorTest {

    @Autowired
    private Level1BasicValidator validator;

    @Test
    void 필수_태그_누락_시_실패() {
        Attributes dcm = new Attributes();
        dcm.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4");
        // StudyInstanceUID 누락

        ValidationContext context = ValidationContext.builder()
            .tenantId("test-tenant")
            .build();

        ValidationResult result = validator.validate(dcm, context);

        assertThat(result.getStatus()).isEqualTo(ValidationStatus.FAIL);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getCode()).isEqualTo("MISSING_REQUIRED_TAG");
    }

    @Test
    void 정상_DICOM_검증_성공() {
        Attributes dcm = new Attributes();
        dcm.setString(Tag.SOPInstanceUID, VR.UI, "1.2.840.113619.2.1.1.1");
        dcm.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.3.1");
        dcm.setString(Tag.StudyInstanceUID, VR.UI, "1.2.840.113619.2.1.1.2");
        dcm.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.840.113619.2.1.1.3");
        dcm.setString(Tag.PatientID, VR.LO, "P12345");

        ValidationContext context = ValidationContext.builder()
            .tenantId("test-tenant")
            .build();

        ValidationResult result = validator.validate(dcm, context);

        assertThat(result.getStatus()).isEqualTo(ValidationStatus.PASS);
        assertThat(result.getErrors()).isEmpty();
    }
}
```

---

## 9. 참고 자료

- **DICOM 원본 보존**: `21_DICOM_원본_보존_정책.md`
- **POC 통합**: `13-5_통합_단계별_계획.md` § 13.7
- **07 최종 계획**: `07_최종_구현_계획.md` Week 4

---

**작성일**: 2025-12-28
**다음 리뷰**: Week 4 검증 구현 후
