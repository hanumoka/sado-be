# 25. Identity Resolution 가이드 (환자 식별 알고리즘)

> **작성일**: 2025-12-28
> **목적**: PatientID 매핑 및 EMR 연동 알고리즘 구현
> **우선순위**: HIGH (Week 3-4 필요)

---

## 1. Executive Summary

### 1.1 문제 정의

**심초음파 DICOM의 환자 식별 문제**:
- PatientID는 **병원/장비마다 다름** (e.g., 삼성병원: "P12345", 서울병원: "SEOUL-12345")
- **Issuer of PatientID**가 없거나 불일치 (표준 미준수)
- **EMR 시스템 환자 ID**와 매핑 필요 (진단 보고서 연동)

**해결 방안**: **Identity Resolution (Patient Matching)**

---

## 2. 아키텍처 개요

```
┌──────────────────────────────────────────────────────────┐
│                DICOM Upload (C-STORE/STOW-RS)            │
└────────────────────┬─────────────────────────────────────┘
                     │
                     ▼
        ┌────────────────────────────┐
        │ PatientMatchingService     │
        │ - Exact Match (ID + Issuer)│
        │ - Fuzzy Match (Name + DOB) │
        └────────────┬───────────────┘
                     │
         ┌───────────┴───────────┐
         │                       │
         ▼                       ▼
┌────────────────┐      ┌────────────────┐
│ Internal DB    │      │ EMR Adapter    │
│ (이전 매핑)    │      │ (HL7 FHIR)     │
└────────────────┘      └────────────────┘
         │                       │
         └───────────┬───────────┘
                     ▼
        ┌────────────────────────────┐
        │ PatientIdentityMapping     │
        │ - dicomPatientId          │
        │ - applicationPatientId    │
        │ - trustScore (0.0-1.0)    │
        └────────────────────────────┘
```

---

## 3. 도메인 모델

### 3.1 PatientIdentityMapping Entity

```java
// PatientIdentityMappingEntity.java
@Entity
@Table(name = "patient_identity_mappings",
    indexes = {
        @Index(name = "idx_tenant_dicom_patient", columnList = "tenant_id, dicom_patient_id, issuer_of_patient_id"),
        @Index(name = "idx_application_patient_id", columnList = "application_patient_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_dicom_issuer", columnNames = {"tenant_id", "dicom_patient_id", "issuer_of_patient_id"})
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientIdentityMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    // DICOM Patient ID
    @Column(name = "dicom_patient_id", nullable = false, length = 64)
    private String dicomPatientId;

    @Column(name = "issuer_of_patient_id", length = 64)
    private String issuerOfPatientId;

    // Application Patient ID (EMR)
    @Column(name = "application_patient_id", length = 64)
    private String applicationPatientId;

    // Trust Score (0.0 = 불확실, 1.0 = 확실)
    @Column(name = "trust_score", nullable = false)
    private Double trustScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "matching_method", nullable = false, length = 32)
    private MatchingMethod matchingMethod;

    // 매칭에 사용된 추가 정보 (JSON)
    @Convert(converter = JsonConverter.class)
    @Column(name = "matching_metadata", columnDefinition = "JSON")
    private Map<String, Object> matchingMetadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "verified_by", length = 64)
    private String verifiedBy;  // 수동 검증자 (의사/관리자)
}

public enum MatchingMethod {
    EXACT_MATCH,        // ID + Issuer 정확히 일치
    FUZZY_MATCH_HIGH,   // 이름 + 생년월일 + 성별 일치 (신뢰도 0.9+)
    FUZZY_MATCH_MEDIUM, // 이름 + 생년월일 일치 (신뢰도 0.7-0.9)
    MANUAL_VERIFICATION // 수동 매핑 (신뢰도 1.0)
}
```

### 3.2 PatientMatchingResult

```java
// PatientMatchingResult.java
@Data
@Builder
public class PatientMatchingResult {
    private boolean matched;
    private String applicationPatientId;
    private Double score;  // 0.0-1.0
    private MatchingMethod matchingMethod;
    private String failureReason;
    private Map<String, Object> metadata;
}
```

---

## 4. PatientMatchingService 구현

### 4.1 핵심 알고리즘

```java
// PatientMatchingService.java
@Service
@RequiredArgsConstructor
@Slf4j
public class PatientMatchingService {

    private final PatientIdentityMappingRepository mappingRepository;
    private final EmrAdapterService emrAdapter;

    public PatientMatchingResult match(String tenantId, String dicomPatientId, String issuerOfPatientId, Attributes dcm) {

        // 1. 기존 매핑 확인 (캐시)
        Optional<PatientIdentityMappingEntity> cached = mappingRepository.findByTenantIdAndDicomPatientIdAndIssuerOfPatientId(
            tenantId, dicomPatientId, issuerOfPatientId
        );

        if (cached.isPresent()) {
            PatientIdentityMappingEntity mapping = cached.get();
            return PatientMatchingResult.builder()
                .matched(true)
                .applicationPatientId(mapping.getApplicationPatientId())
                .score(mapping.getTrustScore())
                .matchingMethod(mapping.getMatchingMethod())
                .metadata(Map.of("source", "cache"))
                .build();
        }

        // 2. Exact Match (EMR에서 ID + Issuer로 조회)
        PatientMatchingResult exactMatch = tryExactMatch(tenantId, dicomPatientId, issuerOfPatientId);
        if (exactMatch.isMatched()) {
            saveMapping(tenantId, dicomPatientId, issuerOfPatientId, exactMatch);
            return exactMatch;
        }

        // 3. Fuzzy Match (이름 + 생년월일 + 성별)
        String patientName = dcm.getString(Tag.PatientName);
        String patientBirthDate = dcm.getString(Tag.PatientBirthDate);  // YYYYMMDD
        String patientSex = dcm.getString(Tag.PatientSex);

        if (patientName != null && patientBirthDate != null) {
            PatientMatchingResult fuzzyMatch = tryFuzzyMatch(tenantId, patientName, patientBirthDate, patientSex);
            if (fuzzyMatch.isMatched() && fuzzyMatch.getScore() >= 0.7) {
                saveMapping(tenantId, dicomPatientId, issuerOfPatientId, fuzzyMatch);
                return fuzzyMatch;
            }
        }

        // 4. 매칭 실패
        return PatientMatchingResult.builder()
            .matched(false)
            .failureReason("No matching patient found in EMR")
            .build();
    }

    private PatientMatchingResult tryExactMatch(String tenantId, String dicomPatientId, String issuerOfPatientId) {
        try {
            // EMR Adapter를 통한 조회
            Optional<EmrPatient> emrPatient = emrAdapter.findByPatientIdAndIssuer(tenantId, dicomPatientId, issuerOfPatientId);

            if (emrPatient.isPresent()) {
                return PatientMatchingResult.builder()
                    .matched(true)
                    .applicationPatientId(emrPatient.get().getId())
                    .score(1.0)
                    .matchingMethod(MatchingMethod.EXACT_MATCH)
                    .metadata(Map.of("method", "exact", "issuer", issuerOfPatientId != null ? issuerOfPatientId : "null"))
                    .build();
            }

        } catch (Exception e) {
            log.error("Exact match failed", e);
        }

        return PatientMatchingResult.builder().matched(false).build();
    }

    private PatientMatchingResult tryFuzzyMatch(String tenantId, String patientName, String patientBirthDate, String patientSex) {
        try {
            List<EmrPatient> candidates = emrAdapter.searchByDemographics(tenantId, patientName, patientBirthDate, patientSex);

            if (candidates.isEmpty()) {
                return PatientMatchingResult.builder().matched(false).build();
            }

            // Fuzzy Matching Score 계산
            EmrPatient bestMatch = null;
            double bestScore = 0.0;

            for (EmrPatient candidate : candidates) {
                double score = calculateSimilarity(patientName, patientBirthDate, patientSex, candidate);
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = candidate;
                }
            }

            if (bestScore >= 0.7) {
                MatchingMethod method = bestScore >= 0.9 ? MatchingMethod.FUZZY_MATCH_HIGH : MatchingMethod.FUZZY_MATCH_MEDIUM;

                return PatientMatchingResult.builder()
                    .matched(true)
                    .applicationPatientId(bestMatch.getId())
                    .score(bestScore)
                    .matchingMethod(method)
                    .metadata(Map.of(
                        "method", "fuzzy",
                        "candidateCount", candidates.size(),
                        "nameScore", calculateNameSimilarity(patientName, bestMatch.getName())
                    ))
                    .build();
            }

        } catch (Exception e) {
            log.error("Fuzzy match failed", e);
        }

        return PatientMatchingResult.builder().matched(false).build();
    }

    private double calculateSimilarity(String dicomName, String dicomDOB, String dicomSex, EmrPatient candidate) {
        double nameScore = calculateNameSimilarity(dicomName, candidate.getName());
        double dobScore = dicomDOB.equals(candidate.getBirthDate()) ? 1.0 : 0.0;
        double sexScore = (dicomSex != null && dicomSex.equals(candidate.getSex())) ? 1.0 : 0.0;

        // 가중 평균: 이름 50%, 생년월일 40%, 성별 10%
        return nameScore * 0.5 + dobScore * 0.4 + sexScore * 0.1;
    }

    private double calculateNameSimilarity(String dicomName, String emrName) {
        // DICOM Patient Name: "Family^Given^Middle"
        // EMR Name: "Given Family" or "Family Given"

        String normalizedDicom = normalizeName(dicomName);
        String normalizedEmr = normalizeName(emrName);

        // Levenshtein Distance 또는 Jaro-Winkler
        return jaroWinklerSimilarity(normalizedDicom, normalizedEmr);
    }

    private String normalizeName(String name) {
        if (name == null) return "";

        // DICOM: "홍^길동" or "Hong^Gildong"
        // → "홍길동" or "HongGildong"
        return name.replaceAll("[\\^\\s]+", "").toLowerCase();
    }

    // Jaro-Winkler Similarity (0.0 ~ 1.0)
    private double jaroWinklerSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;

        // Apache Commons Text 사용
        return new JaroWinklerSimilarity().apply(s1, s2);
    }

    private void saveMapping(String tenantId, String dicomPatientId, String issuerOfPatientId, PatientMatchingResult result) {
        PatientIdentityMappingEntity mapping = PatientIdentityMappingEntity.builder()
            .tenantId(tenantId)
            .dicomPatientId(dicomPatientId)
            .issuerOfPatientId(issuerOfPatientId)
            .applicationPatientId(result.getApplicationPatientId())
            .trustScore(result.getScore())
            .matchingMethod(result.getMatchingMethod())
            .matchingMetadata(result.getMetadata())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        mappingRepository.save(mapping);
        log.info("Saved patient mapping: {} -> {} (score: {})",
            dicomPatientId, result.getApplicationPatientId(), result.getScore());
    }
}
```

---

## 5. EMR Adapter 패턴

### 5.1 인터페이스 정의

```java
// EmrAdapterService.java (Interface)
public interface EmrAdapterService {
    Optional<EmrPatient> findByPatientIdAndIssuer(String tenantId, String patientId, String issuer);
    List<EmrPatient> searchByDemographics(String tenantId, String name, String birthDate, String sex);
}

// EmrPatient.java (DTO)
@Data
@Builder
public class EmrPatient {
    private String id;           // EMR 환자 ID
    private String name;         // "홍길동" or "Hong Gildong"
    private String birthDate;    // "19900101"
    private String sex;          // "M", "F", "O"
    private String issuer;       // Issuer of Patient ID
    private Map<String, Object> metadata;
}
```

### 5.2 FHIR Adapter 구현

```java
// FhirEmrAdapter.java (HL7 FHIR 기반 EMR 연동)
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "emr.adapter.type", havingValue = "fhir")
public class FhirEmrAdapter implements EmrAdapterService {

    private final FhirClient fhirClient;

    @Override
    public Optional<EmrPatient> findByPatientIdAndIssuer(String tenantId, String patientId, String issuer) {
        try {
            // FHIR Patient 조회: GET /Patient?identifier={issuer}|{patientId}
            Bundle bundle = fhirClient.search()
                .forResource(Patient.class)
                .where(Patient.IDENTIFIER.exactly().systemAndCode(issuer, patientId))
                .returnBundle(Bundle.class)
                .execute();

            if (bundle.getEntry().isEmpty()) {
                return Optional.empty();
            }

            Patient fhirPatient = (Patient) bundle.getEntryFirstRep().getResource();
            return Optional.of(toEmrPatient(fhirPatient));

        } catch (Exception e) {
            log.error("FHIR Patient search failed", e);
            return Optional.empty();
        }
    }

    @Override
    public List<EmrPatient> searchByDemographics(String tenantId, String name, String birthDate, String sex) {
        try {
            Bundle bundle = fhirClient.search()
                .forResource(Patient.class)
                .where(Patient.NAME.matches().value(name))
                .and(Patient.BIRTHDATE.exactly().day(birthDate))
                .and(Patient.GENDER.exactly().code(sex.toLowerCase()))
                .returnBundle(Bundle.class)
                .execute();

            return bundle.getEntry().stream()
                .map(entry -> toEmrPatient((Patient) entry.getResource()))
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("FHIR demographic search failed", e);
            return List.of();
        }
    }

    private EmrPatient toEmrPatient(Patient fhirPatient) {
        String familyName = fhirPatient.getNameFirstRep().getFamily();
        String givenName = fhirPatient.getNameFirstRep().getGivenAsSingleString();
        String fullName = givenName + " " + familyName;

        return EmrPatient.builder()
            .id(fhirPatient.getIdElement().getIdPart())
            .name(fullName)
            .birthDate(fhirPatient.getBirthDateElement().getValueAsString().replace("-", ""))
            .sex(fhirPatient.getGender().toCode().toUpperCase())
            .issuer(extractIssuer(fhirPatient))
            .build();
    }

    private String extractIssuer(Patient fhirPatient) {
        return fhirPatient.getIdentifierFirstRep().getSystem();
    }
}
```

### 5.3 Mock Adapter 구현 (테스트용)

```java
// MockEmrAdapter.java
@Service
@ConditionalOnProperty(name = "emr.adapter.type", havingValue = "mock", matchIfMissing = true)
public class MockEmrAdapter implements EmrAdapterService {

    private static final Map<String, EmrPatient> MOCK_PATIENTS = Map.of(
        "P12345", EmrPatient.builder()
            .id("EMR-001")
            .name("홍길동")
            .birthDate("19900101")
            .sex("M")
            .issuer("SAMSUNG")
            .build(),
        "P67890", EmrPatient.builder()
            .id("EMR-002")
            .name("김영희")
            .birthDate("19850315")
            .sex("F")
            .issuer("SEOUL")
            .build()
    );

    @Override
    public Optional<EmrPatient> findByPatientIdAndIssuer(String tenantId, String patientId, String issuer) {
        EmrPatient patient = MOCK_PATIENTS.get(patientId);
        if (patient != null && (issuer == null || patient.getIssuer().equals(issuer))) {
            return Optional.of(patient);
        }
        return Optional.empty();
    }

    @Override
    public List<EmrPatient> searchByDemographics(String tenantId, String name, String birthDate, String sex) {
        return MOCK_PATIENTS.values().stream()
            .filter(p -> normalizeName(p.getName()).contains(normalizeName(name)))
            .filter(p -> p.getBirthDate().equals(birthDate))
            .filter(p -> sex == null || p.getSex().equals(sex))
            .collect(Collectors.toList());
    }

    private String normalizeName(String name) {
        return name.replaceAll("[\\s\\^]+", "").toLowerCase();
    }
}
```

---

## 6. 설정

```yaml
# application.yml
emr:
  adapter:
    type: mock  # mock | fhir | hl7v2
    fhir:
      server-url: http://localhost:8080/fhir
      timeout-seconds: 5
    cache:
      enabled: true
      ttl-seconds: 3600  # 1시간
```

---

## 7. 테스트 코드

```java
// PatientMatchingServiceTest.java
@SpringBootTest
class PatientMatchingServiceTest {

    @Autowired
    private PatientMatchingService matchingService;

    @Test
    void Exact_Match_성공() {
        Attributes dcm = new Attributes();
        dcm.setString(Tag.PatientID, VR.LO, "P12345");
        dcm.setString(Tag.IssuerOfPatientID, VR.LO, "SAMSUNG");

        PatientMatchingResult result = matchingService.match("test-tenant", "P12345", "SAMSUNG", dcm);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getApplicationPatientId()).isEqualTo("EMR-001");
        assertThat(result.getScore()).isEqualTo(1.0);
        assertThat(result.getMatchingMethod()).isEqualTo(MatchingMethod.EXACT_MATCH);
    }

    @Test
    void Fuzzy_Match_이름_생년월일_일치() {
        Attributes dcm = new Attributes();
        dcm.setString(Tag.PatientID, VR.LO, "UNKNOWN");
        dcm.setString(Tag.PatientName, VR.PN, "홍^길동");
        dcm.setString(Tag.PatientBirthDate, VR.DA, "19900101");
        dcm.setString(Tag.PatientSex, VR.CS, "M");

        PatientMatchingResult result = matchingService.match("test-tenant", "UNKNOWN", null, dcm);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getApplicationPatientId()).isEqualTo("EMR-001");
        assertThat(result.getScore()).isGreaterThan(0.7);
        assertThat(result.getMatchingMethod()).isIn(MatchingMethod.FUZZY_MATCH_HIGH, MatchingMethod.FUZZY_MATCH_MEDIUM);
    }

    @Test
    void 매칭_실패() {
        Attributes dcm = new Attributes();
        dcm.setString(Tag.PatientID, VR.LO, "NONEXISTENT");

        PatientMatchingResult result = matchingService.match("test-tenant", "NONEXISTENT", null, dcm);

        assertThat(result.isMatched()).isFalse();
        assertThat(result.getFailureReason()).isNotNull();
    }
}
```

---

## 8. 운영 가이드

### 8.1 수동 매핑 (관리자 UI)

매칭 실패 시 관리자가 수동으로 매핑:

```java
@RestController
@RequestMapping("/api/admin/patient-mapping")
@RequiredArgsConstructor
public class PatientMappingAdminController {

    private final PatientIdentityMappingRepository mappingRepository;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> createManualMapping(@RequestBody ManualMappingRequest request) {
        PatientIdentityMappingEntity mapping = PatientIdentityMappingEntity.builder()
            .tenantId(request.getTenantId())
            .dicomPatientId(request.getDicomPatientId())
            .issuerOfPatientId(request.getIssuerOfPatientId())
            .applicationPatientId(request.getApplicationPatientId())
            .trustScore(1.0)
            .matchingMethod(MatchingMethod.MANUAL_VERIFICATION)
            .verifiedBy(getCurrentAdmin())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        mappingRepository.save(mapping);
        return ResponseEntity.ok().build();
    }
}
```

---

## 9. 참고 자료

- **DICOM 검증**: `24_DICOM_검증_파이프라인_가이드.md`
- **POC 통합**: `13-2_아키텍처_통합_계획.md`
- **HL7 FHIR**: https://www.hl7.org/fhir/

---

**작성일**: 2025-12-28
**다음 리뷰**: Week 3-4 Identity 구현 후
