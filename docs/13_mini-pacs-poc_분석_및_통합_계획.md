# 13. Mini PACS PoC 분석 및 통합 계획

> 📋 **문서 역할**: 기존 mini-pacs-poc 프로젝트 분석 및 sado-be 통합 계획
>
> 📅 **작성일**: 2025-12-20
>
> 🎯 **목표**: mini-pacs-poc의 검증된 기능을 sado-be에 통합하여 개발 기간 단축
>
> 🔗 **연결**: `07_최종_구현_계획.md` 보완 문서

---

## 1. Executive Summary

### 1.1 mini-pacs-poc 프로젝트 개요

| 항목 | 내용 |
|------|------|
| **프로젝트명** | Mini PACS PoC (Picture Archiving and Communication System) |
| **목적** | 심장초음파 전문 PACS 플랫폼 기술 검증 |
| **완성도** | 90% (Phase 4: 기술 심화 학습 진행 중) |
| **코드 규모** | 65개 Java 파일, 30+ 문서 파일 |
| **기술 스택** | Spring Boot 3.4.0, Java 21, dcm4che 5.29.1, PostgreSQL 15, Redis 7, SeaweedFS 3.71 |
| **주요 기능** | DICOMWeb API, 멀티프레임 처리, 트랜스코딩, 스토리지 티어링, 멀티테넌시 |
| **라이선스** | 모두 상업적 사용 가능 (Apache 2.0, MIT, BSD, MPL 1.1) |

### 1.2 통합 가치

✅ **즉시 사용 가능한 코드**: 65개 파일, 90% 완성도
✅ **검증된 아키텍처**: 엔터프라이즈 레벨 설계
✅ **완벽한 문서화**: 30+ 학습 문서, API 명세, 고급 기능 가이드
✅ **시간 절약**: 예상 80% 코드 재사용 가능 (4-6주 통합 예상)
✅ **표준 준수**: DICOM, DICOMWeb 완벽 준수
✅ **학습 자료**: 6주 학습 로드맵 + 예제 코드

### 1.3 통합 권장 사항

**전략**: **선택적 통합 + 학습 중심 접근**

1. **즉시 통합** (1-2주):
   - 도메인 모델 (Entity, Repository)
   - DICOM Info Record 클래스
   - 공통 유틸리티 (TenantContext, DicomDateTimeUtils)
   - 데이터베이스 스키마

2. **참고 후 학습 구현** (3-4주):
   - DICOM 처리 서비스 (메타데이터 추출, 파싱)
   - 썸네일/비디오 트랜스코딩
   - 저장소 연동 (SeaweedFS)

3. **DICOMWeb API 이관** (2-3주):
   - QIDO-RS, WADO-RS, STOW-RS 컨트롤러
   - DicomJsonBuilder
   - Extended API

4. **고급 기능 선택 적용** (2-3주):
   - 스토리지 티어링 (선택)
   - 이벤트 처리 (Redis Stream)
   - 트랜스코딩 워커

**예상 총 기간**: 8-12주 (병렬 작업 시 6-8주 가능)

---

## 2. 기술 스택 비교 분석

### 2.1 현재 sado-be vs mini-pacs-poc

| 기술 | sado-be 계획 | mini-pacs-poc 검증 | 통합 결정 |
|------|--------------|---------------------|----------|
| **Framework** | Spring Boot 4.0.1 | Spring Boot 3.4.0 | ✅ 4.0.1 유지 (하위호환) |
| **Java** | Java 21 | Java 21 | ✅ 일치 |
| **RDB** | MySQL 8.0 | PostgreSQL 15 | ⚠️ **MySQL 유지** (계획 준수) |
| **DICOM** | 미정 | dcm4che 5.29.1 | ✅ **dcm4che 채택** |
| **Storage** | SeaweedFS | SeaweedFS 3.71 | ✅ 일치 |
| **Cache/MQ** | Redis + Kafka | Redis 7 (Stream) | ⚠️ **Kafka 유지**, Redis 추가 |
| **Workflow** | Temporal | 미사용 | ✅ Temporal 유지 (계획대로) |
| **Auth** | Keycloak | 미구현 (계획) | ✅ Keycloak 진행 (Week 12) |

### 2.2 핵심 기술 결정

#### DICOM 라이브러리: dcm4che 5.29.1 채택

**선택 이유**:
- 업계 표준 (DICOM 표준 99% 준수)
- 활발한 유지보수 (2024년 최신 버전)
- MPL 1.1 라이선스 (라이브러리 사용 시 상업 안전)
- mini-pacs-poc에서 검증 완료

**의존성 추가**:
```gradle
dependencies {
    implementation 'org.dcm4che:dcm4che-core:5.29.1'
    implementation 'org.dcm4che:dcm4che-net:5.29.1'
    implementation 'org.dcm4che:dcm4che-imageio:5.29.1'
    implementation 'org.dcm4che:dcm4che-image:5.29.1'
}
```

#### Database: MySQL 8.0 유지

**이유**:
- 기존 계획 준수 (JPA + MyBatis 혼용 전략)
- PostgreSQL → MySQL 변환 필요
  - JSONB → JSON
  - TEXT → LONGTEXT
  - BIGSERIAL → BIGINT AUTO_INCREMENT

#### Messaging: Kafka + Redis 병행

**전략**:
- **Kafka**: 도메인 이벤트, 서비스 간 통신 (기존 계획)
- **Redis Stream**: DICOM 처리 파이프라인 (mini-pacs-poc 패턴)

**이유**:
- Kafka: 영구 이벤트 저장, 재처리 용이
- Redis Stream: 실시간 파이프라인, 경량, 빠른 처리

---

## 3. 아키텍처 통합 계획

### 3.1 최종 모듈 구조 (7개 → 8개)

```
sado-be/
├── sado-common/               # 1. 공통 모듈
│   ├── common-core/          # 기존 유틸
│   ├── common-event/         # 이벤트 스키마
│   ├── common-security/      # 보안 + Tenant
│   └── common-dicom/         # ⭐ NEW: DICOM Info Record, Utils
│
├── sado-infrastructure/       # 2. 인프라 모듈
│   ├── kafka-config/
│   ├── redis-config/
│   ├── temporal-config/
│   └── storage-config/       # SeaweedFS
│
├── sado-gateway/              # 3. API Gateway
│
├── sado-minipacs/             # 4. DICOM 파일 관리 ⭐ 대폭 확장
│   ├── minipacs-domain/      # Entity, Repository
│   ├── minipacs-processing/  # ⭐ NEW: DICOM 파싱, 썸네일, 트랜스코딩
│   ├── minipacs-storage/     # SeaweedFS 연동
│   └── minipacs-api/         # DICOMWeb + Extended API
│
├── sado-orchestrator/         # 5. 워크플로우
│
├── sado-bff/                  # 6. Backend For Frontend
│
├── sado-dicom-gateway/        # 7. ⭐ NEW: DICOM 수신 (C-STORE SCP)
│   └── dicom-scp/            # dcm4che C-STORE SCP
│
└── sado-viewer/               # 8. ⭐ NEW (Optional): DICOM Viewer
    └── ohif-integration/      # OHIF Viewer 통합
```

### 3.2 DICOM 처리 파이프라인

```
[DICOM 입력 소스]
  ├─ 초음파 장비 (DICOM C-STORE) ──▶ sado-dicom-gateway
  ├─ 웹 업로드 (HTTP)            ──▶ sado-minipacs API
  └─ 파일 감시 (NAS/FTP)         ──▶ sado-dicom-gateway
           │
           ▼
   ┌──────────────────────┐
   │  Redis Stream        │
   │  "dicom:ingest"      │
   └──────────┬───────────┘
              ▼
   ┌──────────────────────────────┐
   │  minipacs-processing         │
   │  - DICOM 태그 파싱           │
   │  - 중복 체크 (분산락)        │
   │  - 데이터 검증               │
   └──────────┬───────────────────┘
              │ Kafka: "study.uploaded"
              ▼
   ┌──────────────────────────────┐
   │  minipacs-storage            │
   │  - SeaweedFS 업로드          │
   │  - MySQL 메타데이터 저장     │
   └──────────┬───────────────────┘
              │ Redis Stream: "dicom:storage"
              ▼
   ┌──────────────────────────────┐
   │  Transcoding Worker          │
   │  - 썸네일 생성 (JPEG)        │
   │  - 비디오 생성 (MP4)         │
   └──────────┬───────────────────┘
              │ Kafka: "study.ready"
              ▼
   ┌──────────────────────────────┐
   │  sado-orchestrator           │
   │  - AI 분석 워크플로우        │
   │  - Temporal Saga             │
   └──────────────────────────────┘
```

### 3.3 계층 구조 (mini-pacs-poc 패턴 적용)

```
┌─────────────────────────────────────────────┐
│  API Layer (DICOMWeb + Extended)            │
│  - QIDO-RS, WADO-RS, STOW-RS                │
│  - Instance API, Study API                  │
└─────────────┬───────────────────────────────┘
              ▼
┌─────────────────────────────────────────────┐
│  Service Layer                              │
│  - DicomStorageService                      │
│  - DicomProcessingService                   │
│  - ThumbnailGeneratorService                │
│  - VideoTranscodingService                  │
└─────────────┬───────────────────────────────┘
              ▼
┌─────────────────────────────────────────────┐
│  Domain Layer                               │
│  - Entity (Patient, Study, Series, Instance)│
│  - Repository                               │
└─────────────┬───────────────────────────────┘
              ▼
┌──────────────┬──────────────────────────────┐
│  Storage     │  Metadata DB                 │
│  SeaweedFS   │  MySQL 8.0                   │
└──────────────┴──────────────────────────────┘
```

---

## 4. 도메인 모델 통합 계획

### 4.1 Entity 설계 (mini-pacs-poc 기반, MySQL 변환)

#### PatientEntity (환자)

**변경 사항**:
- PostgreSQL TEXT → MySQL LONGTEXT
- JSONB → JSON
- TenantAwareEntity 상속 (기존 멀티테넌시 통합)

```java
@Entity
@Table(name = "patients")
public class PatientEntity extends TenantAwareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "LONGTEXT")
    private String dicomPatientId;             // DICOM 원본 (0010,0020)

    @Column(columnDefinition = "LONGTEXT")
    private String dicomPatientName;           // (0010,0010)

    private String dicomPatientBirthDate;      // YYYYMMDD
    private String dicomPatientSex;            // M/F/O

    // 동물 환자 지원
    private String species;                    // (0010,2201) Dog, Cat, etc.
    private String breed;                      // (0010,2292)

    // 정제된 값 (검색 최적화)
    private String normalizedName;             // 소문자 변환
    private LocalDate birthDate;               // 파싱된 날짜

    // 중복 방지
    @Column(length = 64)
    private String contentHash;                // SHA-256

    // Optimistic Locking
    @Version
    private Long version;

    // Auditing
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // tenant_id는 TenantAwareEntity에서 상속
}
```

#### StudyEntity (검사)

```java
@Entity
@Table(name = "studies")
public class StudyEntity extends TenantAwareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 논리적 FK (물리적 FK 제약 없음)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id",
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private PatientEntity patient;

    @Column(columnDefinition = "LONGTEXT", unique = true)
    private String studyInstanceUid;           // (0020,000D)

    private String dicomStudyDate;             // YYYYMMDD
    private LocalDate studyDate;               // 파싱된 날짜

    @Column(columnDefinition = "LONGTEXT")
    private String studyDescription;           // (0008,1030)

    @Column(columnDefinition = "LONGTEXT")
    private String accessionNumber;            // (0008,0050)

    // 통계 (역정규화)
    private Integer numberOfSeries = 0;
    private Integer numberOfInstances = 0;

    // 중복 방지
    @Column(length = 64)
    private String contentHash;

    @Version
    private Long version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### SeriesEntity (시리즈)

```java
@Entity
@Table(name = "series")
public class SeriesEntity extends TenantAwareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_id",
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private StudyEntity study;

    @Column(columnDefinition = "LONGTEXT", unique = true)
    private String seriesInstanceUid;          // (0020,000E)

    private String modality;                   // (0008,0060) US, CT, MR
    private String bodyPartExamined;           // (0018,0015)

    @Column(columnDefinition = "LONGTEXT")
    private String seriesDescription;          // (0008,103E)

    private String manufacturer;               // (0008,0070)
    private String manufacturerModelName;      // (0008,1090)

    private Integer numberOfInstances = 0;

    @Column(length = 64)
    private String contentHash;

    @Version
    private Long version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### InstanceEntity (개별 영상) ⭐ 심장초음파 특화

```java
@Entity
@Table(name = "instances")
public class InstanceEntity extends TenantAwareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id",
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private SeriesEntity series;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_id",
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private StudyEntity study;                 // 조회 성능 최적화

    @Column(columnDefinition = "LONGTEXT", unique = true)
    private String sopInstanceUid;             // (0008,0018)

    @Column(columnDefinition = "LONGTEXT")
    private String sopClassUid;                // (0008,0016)

    // 이미지 기본 정보
    private Integer rows;                      // (0028,0010)
    private Integer columns;                   // (0028,0011)
    private String photometricInterpretation;  // (0028,0004)

    // 심장초음파 멀티프레임 ⭐
    private Integer numberOfFrames = 1;        // (0028,0008)
    private Double frameTime;                  // (0018,1063) ms
    private Integer cineRate;                  // (0018,0040) fps
    private Integer heartRate;                 // (0018,1088) bpm

    // 저장 정보
    @Column(columnDefinition = "LONGTEXT")
    private String storagePath;                // SeaweedFS 경로
    private Long fileSize;                     // bytes

    // 트랜스코딩 상태 ⭐
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private TranscodingStatus transcodingStatus = TranscodingStatus.NONE;

    @Column(columnDefinition = "LONGTEXT")
    private String thumbnailPath;              // 썸네일 경로

    @Column(columnDefinition = "LONGTEXT")
    private String videoPath;                  // MP4 경로

    @Column(columnDefinition = "LONGTEXT")
    private String framesPath;                 // 프레임 추출 경로

    // MySQL JSON 타입 ⭐
    @Column(columnDefinition = "JSON")
    private String ultrasoundRegions;          // UltrasoundRegion[] (픽셀→물리 변환)

    @Column(columnDefinition = "JSON")
    private String extendedMetadata;           // 확장 메타데이터

    // 스토리지 티어링 ⭐ (선택 기능)
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private StorageTier storageTier = StorageTier.HOT;

    private LocalDateTime lastAccessedAt;
    private Integer accessCount = 0;
    private Boolean tierLocked = false;

    // 중복 방지
    @Column(length = 64)
    private String contentHash;

    @Version
    private Long version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 비즈니스 메서드
    public boolean isMultiFrame() {
        return numberOfFrames != null && numberOfFrames > 1;
    }

    public void startTranscoding() {
        this.transcodingStatus = TranscodingStatus.PROCESSING;
    }

    public void completeTranscoding(String thumbnailPath, String framesPath, String videoPath) {
        this.transcodingStatus = TranscodingStatus.COMPLETED;
        this.thumbnailPath = thumbnailPath;
        this.framesPath = framesPath;
        this.videoPath = videoPath;
    }

    public void failTranscoding(String errorMessage) {
        this.transcodingStatus = TranscodingStatus.FAILED;
        // errorMessage는 extendedMetadata에 저장
    }

    public void recordAccess() {
        this.lastAccessedAt = LocalDateTime.now();
        this.accessCount++;
    }

    public void changeTier(StorageTier newTier, String newPath) {
        if (!this.tierLocked) {
            this.storageTier = newTier;
            this.storagePath = newPath;
        }
    }
}
```

### 4.2 Enum 정의

```java
public enum TranscodingStatus {
    NONE,           // 단일 프레임 또는 트랜스코딩 불필요
    PENDING,        // 트랜스코딩 대기
    PROCESSING,     // 진행 중
    COMPLETED,      // 완료
    FAILED          // 실패
}

public enum StorageTier {
    HOT,            // 최근 30일, 빠른 접근
    WARM,           // 30-90일, 중간 속도
    COLD            // 90일 이상, 느림 (Archive)
}
```

### 4.3 DICOM Info Record (DTO)

**common-dicom 모듈에 위치**:

```java
// 최상위 레코드
public record DicomMetadata(
    PatientInfo patientInfo,
    StudyInfo studyInfo,
    SeriesInfo seriesInfo,
    InstanceInfo instanceInfo,
    ImageInfo imageInfo,
    List<UltrasoundRegion> ultrasoundRegions,
    DicomTypeInfo typeInfo
) {}

// Patient 정보
public record PatientInfo(
    String patientId,
    String patientName,
    String patientBirthDate,
    String patientSex,
    String species,              // 동물
    String breed                 // 품종
) {}

// Study 정보
public record StudyInfo(
    String studyInstanceUid,
    String studyDate,
    String studyTime,
    String studyDescription,
    String accessionNumber
) {}

// Series 정보
public record SeriesInfo(
    String seriesInstanceUid,
    String modality,
    String seriesDescription,
    String bodyPartExamined,
    String manufacturer,
    String manufacturerModelName
) {}

// Instance 정보
public record InstanceInfo(
    String sopInstanceUid,
    String sopClassUid,
    Integer instanceNumber
) {}

// Image 정보 (심장초음파 특화)
public record ImageInfo(
    Integer rows,
    Integer columns,
    Integer numberOfFrames,
    Integer cineRate,
    Double frameTime,
    Integer heartRate,
    String photometricInterpretation,
    Integer bitsAllocated,
    Integer bitsStored,
    Integer samplesPerPixel
) {
    public double recommendedFrameRate() {
        if (cineRate != null && cineRate > 0) return cineRate;
        if (frameTime != null && frameTime > 0) return 1000.0 / frameTime;
        return 30.0;  // 기본값
    }

    public boolean isMultiFrame() {
        return numberOfFrames != null && numberOfFrames > 1;
    }
}

// UltrasoundRegion (픽셀→물리 변환)
public record UltrasoundRegion(
    String regionName,           // "LA", "LV", etc.
    Integer regionNumber,
    Double physicalDeltaX,       // cm per pixel
    Double physicalDeltaY,
    Integer referencePointX,
    Integer referencePointY,
    String unitsX,               // "cm"
    String unitsY                // "cm/s"
) {}

// DICOM Type 정보
public record DicomTypeInfo(
    boolean isMultiFrame,
    boolean isCompressed,
    boolean isColor,
    String transferSyntaxUid
) {}
```

---

## 5. 데이터베이스 스키마 (MySQL 변환)

### 5.1 DDL (init-db/01-init-schema.sql)

```sql
-- patients
CREATE TABLE patients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,

    dicom_patient_id LONGTEXT,
    dicom_patient_name LONGTEXT,
    dicom_patient_birth_date VARCHAR(32),
    dicom_patient_sex VARCHAR(16),

    species VARCHAR(64),
    breed VARCHAR(128),

    normalized_name VARCHAR(256),
    birth_date DATE,

    content_hash VARCHAR(64),
    version BIGINT DEFAULT 0,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_patients_tenant (tenant_id),
    INDEX idx_patients_name (normalized_name),
    INDEX idx_patients_birth_date (birth_date),
    UNIQUE KEY uk_patients_tenant_hash (tenant_id, content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- studies
CREATE TABLE studies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    patient_id BIGINT NOT NULL,

    study_instance_uid LONGTEXT NOT NULL,
    dicom_study_date VARCHAR(32),
    study_date DATE,
    study_description LONGTEXT,
    accession_number LONGTEXT,

    number_of_series INT DEFAULT 0,
    number_of_instances INT DEFAULT 0,

    content_hash VARCHAR(64),
    version BIGINT DEFAULT 0,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_studies_tenant (tenant_id),
    INDEX idx_studies_patient (patient_id),
    INDEX idx_studies_date (study_date),
    UNIQUE KEY uk_studies_tenant_hash (tenant_id, content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- series
CREATE TABLE series (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    study_id BIGINT NOT NULL,

    series_instance_uid LONGTEXT NOT NULL,
    modality VARCHAR(16),
    series_description LONGTEXT,
    body_part_examined VARCHAR(64),
    manufacturer VARCHAR(128),
    manufacturer_model_name VARCHAR(128),

    number_of_instances INT DEFAULT 0,

    content_hash VARCHAR(64),
    version BIGINT DEFAULT 0,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_series_tenant (tenant_id),
    INDEX idx_series_study (study_id),
    INDEX idx_series_modality (modality),
    UNIQUE KEY uk_series_tenant_hash (tenant_id, content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- instances
CREATE TABLE instances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    series_id BIGINT NOT NULL,
    study_id BIGINT NOT NULL,

    sop_instance_uid LONGTEXT NOT NULL,
    sop_class_uid LONGTEXT,

    rows INT,
    columns INT,
    photometric_interpretation VARCHAR(64),

    number_of_frames INT DEFAULT 1,
    frame_time DOUBLE,
    cine_rate INT,
    heart_rate INT,

    storage_path LONGTEXT,
    file_size BIGINT,

    transcoding_status VARCHAR(32) DEFAULT 'NONE',
    thumbnail_path LONGTEXT,
    video_path LONGTEXT,
    frames_path LONGTEXT,

    ultrasound_regions JSON,
    extended_metadata JSON,

    storage_tier VARCHAR(16) DEFAULT 'HOT',
    last_accessed_at TIMESTAMP,
    access_count INT DEFAULT 0,
    tier_locked BOOLEAN DEFAULT FALSE,

    content_hash VARCHAR(64),
    version BIGINT DEFAULT 0,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_instances_tenant (tenant_id),
    INDEX idx_instances_series (series_id),
    INDEX idx_instances_study (study_id),
    INDEX idx_instances_transcoding (transcoding_status),
    INDEX idx_instances_tier (tenant_id, storage_tier, last_accessed_at),
    UNIQUE KEY uk_instances_tenant_hash (tenant_id, content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 5.2 PostgreSQL vs MySQL 변환 요약

| PostgreSQL | MySQL 8.0 | 비고 |
|-----------|-----------|------|
| TEXT | LONGTEXT | 최대 4GB |
| JSONB | JSON | MySQL 8.0: JSON 검색 지원 |
| BIGSERIAL | BIGINT AUTO_INCREMENT | - |
| TIMESTAMP WITH TIME ZONE | TIMESTAMP | MySQL 8.0.19+ 지원 |
| UNIQUE (tenant_id, dicom_patient_id) | UNIQUE KEY uk_xxx (tenant_id, content_hash) | content_hash 사용 |

---

## 6. API 설계 (DICOMWeb + Extended)

### 6.1 DICOMWeb 표준 API (QIDO-RS, WADO-RS, STOW-RS)

**mini-pacs-poc 컨트롤러 직접 이관** (Spring Boot 4.0.1 호환 확인 필요)

#### QIDO-RS (검색)

```java
@RestController
@RequestMapping("/dicom-web")
public class QidoRsController {

    @GetMapping("/studies")
    public ResponseEntity<List<Map<String, Object>>> searchStudies(
        @RequestParam(required = false) String PatientID,
        @RequestParam(required = false) String StudyDate,
        @RequestParam(defaultValue = "100") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        // DICOM JSON Model 응답
        // application/dicom+json
    }

    @GetMapping("/studies/{studyUid}/series")
    public ResponseEntity<List<Map<String, Object>>> searchSeries(
        @PathVariable String studyUid,
        @RequestParam(required = false) String Modality
    ) {
        // ...
    }

    @GetMapping("/studies/{studyUid}/series/{seriesUid}/instances")
    public ResponseEntity<List<Map<String, Object>>> searchInstances(
        @PathVariable String studyUid,
        @PathVariable String seriesUid
    ) {
        // ...
    }
}
```

#### WADO-RS (조회)

```java
@RestController
@RequestMapping("/dicom-web")
public class WadoRsController {

    @GetMapping(value = "/studies/{studyUid}/series/{seriesUid}/instances/{instanceUid}",
                produces = "application/dicom")
    public ResponseEntity<byte[]> retrieveInstance(
        @PathVariable String studyUid,
        @PathVariable String seriesUid,
        @PathVariable String instanceUid
    ) {
        // DICOM 바이너리 반환
    }

    @GetMapping("/studies/{studyUid}/series/{seriesUid}/instances/{instanceUid}/rendered")
    public ResponseEntity<byte[]> retrieveRenderedInstance(
        @PathVariable String instanceUid
    ) {
        // JPEG 렌더링 이미지
    }

    @GetMapping("/studies/{studyUid}/series/{seriesUid}/instances/{instanceUid}/thumbnail")
    public ResponseEntity<byte[]> retrieveThumbnail(
        @PathVariable String instanceUid
    ) {
        // 썸네일 (256x256 JPEG)
    }
}
```

#### STOW-RS (저장)

```java
@RestController
@RequestMapping("/dicom-web")
public class StowRsController {

    @PostMapping(value = "/studies",
                 consumes = "application/dicom")
    public ResponseEntity<Map<String, Object>> storeInstances(
        @RequestBody byte[] dicomData
    ) {
        // DICOM 파일 저장
        // 응답: 저장 결과 + SOPInstanceUID
    }
}
```

### 6.2 Extended API (비표준 확장)

#### Instance API

```java
@RestController
@RequestMapping("/api/v1/instances")
public class InstanceController {

    @GetMapping("/{sopInstanceUid}")
    public InstanceDetailResponse getInstanceDetail(@PathVariable String sopInstanceUid) {
        // Instance 상세 정보 (확장 메타데이터 포함)
    }

    @GetMapping("/{sopInstanceUid}/cine-info")
    public CineInfoResponse getCineInfo(@PathVariable String sopInstanceUid) {
        // Cine 재생 정보 (fps, frameTime, numberOfFrames)
    }

    @GetMapping("/{sopInstanceUid}/frames/{frameNumber}")
    public ResponseEntity<byte[]> getFrame(
        @PathVariable String sopInstanceUid,
        @PathVariable int frameNumber  // 1-based
    ) {
        // 특정 프레임 JPEG
    }

    @GetMapping("/{sopInstanceUid}/thumbnail")
    public ResponseEntity<byte[]> getThumbnail(@PathVariable String sopInstanceUid) {
        // 썸네일 (중간 프레임 또는 첫 프레임)
    }

    @GetMapping("/{sopInstanceUid}/ultrasound-regions")
    public List<UltrasoundRegion> getUltrasoundRegions(@PathVariable String sopInstanceUid) {
        // 픽셀→물리 변환 정보
    }

    @GetMapping("/{sopInstanceUid}/video")
    public ResponseEntity<byte[]> getVideo(@PathVariable String sopInstanceUid) {
        // MP4 비디오 (멀티프레임만)
    }

    @GetMapping("/{sopInstanceUid}/video/available")
    public VideoAvailabilityResponse checkVideoAvailability(@PathVariable String sopInstanceUid) {
        // 비디오 변환 가능 여부 + 트랜스코딩 상태
    }
}
```

#### Study API

```java
@RestController
@RequestMapping("/api/v1/studies")
public class StudyController {

    @GetMapping("/{studyUid}/summary")
    public StudySummaryResponse getStudySummary(@PathVariable String studyUid) {
        // Study 요약 (Series 개수, Instance 개수, 환자 정보)
    }

    @GetMapping("/{studyUid}/instances")
    public List<InstanceBasicInfo> getInstances(@PathVariable String studyUid) {
        // Instance 목록 (경량)
    }

    @GetMapping("/{studyUid}/instances/detail")
    public List<InstanceDetailResponse> getInstancesDetail(@PathVariable String studyUid) {
        // Instance 상세 목록 (Cine 정보, 트랜스코딩 상태 포함)
    }
}
```

---

## 7. 통합 단계별 계획

### 7.1 Phase 1: 도메인 모델 통합 (Week 2-3, 1-2주)

**목표**: mini-pacs-poc의 검증된 도메인 모델을 sado-be에 이관

#### 작업 항목

1. **Entity 클래스 이관** (1일)
   - [ ] `PatientEntity.java` (PostgreSQL → MySQL 변환)
   - [ ] `StudyEntity.java`
   - [ ] `SeriesEntity.java`
   - [ ] `InstanceEntity.java`
   - [ ] `TranscodingStatus.java`, `StorageTier.java` (Enum)
   - [ ] `TenantAwareEntity.java` 상속 적용

2. **Repository 인터페이스 이관** (1일)
   - [ ] `PatientRepository.java`
   - [ ] `StudyRepository.java`
   - [ ] `SeriesRepository.java`
   - [ ] `InstanceRepository.java`
   - [ ] Custom Repository (DICOM UID 검색, 중복 체크)

3. **DICOM Info Record 추가** (1일)
   - [ ] `common-dicom` 모듈 생성
   - [ ] `DicomMetadata.java`
   - [ ] `PatientInfo.java`, `StudyInfo.java`, `SeriesInfo.java`, `InstanceInfo.java`
   - [ ] `ImageInfo.java` (심장초음파 특화)
   - [ ] `UltrasoundRegion.java`

4. **데이터베이스 스키마** (1일)
   - [ ] `init-db/01-init-schema.sql` 작성
   - [ ] Docker Compose MySQL 설정
   - [ ] JPA DDL 검증 (ddl-auto: validate)

5. **공통 유틸리티** (반일)
   - [ ] `TenantContext.java` (기존 멀티테넌시와 통합)
   - [ ] `DicomDateTimeUtils.java` (YYYYMMDD → LocalDate)

#### 산출물

- `sado-minipacs/minipacs-domain/` 모듈 완성
- `sado-common/common-dicom/` 모듈 생성
- MySQL 스키마 DDL
- Entity 단위 테스트 (JPA 저장/조회)

### 7.2 Phase 2: DICOM 처리 서비스 (Week 3-5, 2-3주)

**목표**: DICOM 파일 파싱, 메타데이터 추출, 썸네일 생성 학습 및 구현

#### 작업 항목

1. **dcm4che 의존성 추가 및 학습** (2일)
   - [ ] build.gradle에 dcm4che 추가
   - [ ] mini-pacs-poc 문서 학습: `learning/03-DICOM-처리.md`
   - [ ] dcm4che 예제 코드 실습

2. **DicomMetadataExtractor 구현** (3일)
   - [ ] mini-pacs-poc 참고: `processing/util/DicomMetadataExtractor.java`
   - [ ] DICOM 태그 파싱 (Patient, Study, Series, Instance)
   - [ ] ImageInfo 추출 (numberOfFrames, cineRate, frameTime)
   - [ ] UltrasoundRegion 추출 (Sequence 0018,6011)
   - [ ] 단위 테스트 (샘플 DICOM 파일)

3. **ThumbnailGeneratorService 구현** (3일)
   - [ ] mini-pacs-poc 참고: `processing/service/ThumbnailGeneratorService.java`
   - [ ] dcm4che + JAI ImageIO로 프레임 추출
   - [ ] JPEG 인코딩 (quality: 0.8)
   - [ ] 멀티프레임: 중간 프레임 선택
   - [ ] 단위 테스트

4. **VideoTranscodingService 구현** (4일, 선택)
   - [ ] mini-pacs-poc 참고: `processing/service/VideoTranscodingService.java`
   - [ ] Jcodec로 MP4 인코딩
   - [ ] 프레임 추출 + 비디오 생성
   - [ ] 단위 테스트 (멀티프레임 DICOM)

5. **DicomProcessingService 구현** (2일)
   - [ ] mini-pacs-poc 참고: `processing/service/DicomProcessingService.java`
   - [ ] DICOM 검증 (표준 준수 확인)
   - [ ] 중복 체크 (content_hash)
   - [ ] Redis 분산락 연동

#### 산출물

- `sado-minipacs/minipacs-processing/` 모듈 완성
- DICOM 파일 파싱 → DicomMetadata 변환 파이프라인
- 썸네일 생성 기능
- (선택) MP4 비디오 생성 기능

### 7.3 Phase 3: 저장소 연동 (Week 5-6, 1-2주)

**목표**: SeaweedFS 연동, MySQL 메타데이터 저장

#### 작업 항목

1. **SeaweedFS 설정** (1일)
   - [ ] Docker Compose에 SeaweedFS 추가
   - [ ] `sado-infrastructure/storage-config/` 모듈 설정

2. **DicomStorageService 구현** (3일)
   - [ ] mini-pacs-poc 참고: `storage/service/DicomStorageService.java`
   - [ ] DICOM 파일 SeaweedFS 업로드
   - [ ] MySQL 메타데이터 저장 (트랜잭션)
   - [ ] 계층 구조 관리 (Patient → Study → Series → Instance)
   - [ ] 통합 테스트

3. **Redis Stream 이벤트 발행** (2일)
   - [ ] 이벤트: `dicom:ingest`, `dicom:storage`
   - [ ] Publisher 구현
   - [ ] Consumer 기본 구조

#### 산출물

- `sado-minipacs/minipacs-storage/` 모듈 완성
- DICOM 업로드 → SeaweedFS 저장 → MySQL 메타데이터 저장 파이프라인
- Redis Stream 이벤트 발행

### 7.4 Phase 4: DICOMWeb API (Week 6-8, 2-3주)

**목표**: DICOMWeb 표준 API 구현

#### 작업 항목

1. **QIDO-RS 컨트롤러** (3일)
   - [ ] mini-pacs-poc 이관: `api/dicomweb/QidoRsController.java`
   - [ ] DICOM JSON Model Builder 이관
   - [ ] 검색 쿼리 (PatientID, StudyDate, Modality)
   - [ ] 페이징 (limit, offset)
   - [ ] Tenant 필터링 자동 적용
   - [ ] 통합 테스트

2. **WADO-RS 컨트롤러** (3일)
   - [ ] mini-pacs-poc 이관: `api/dicomweb/WadoRsController.java`
   - [ ] DICOM 바이너리 조회
   - [ ] Rendered 이미지 (JPEG)
   - [ ] Thumbnail
   - [ ] 통합 테스트

3. **STOW-RS 컨트롤러** (2일)
   - [ ] mini-pacs-poc 이관: `api/dicomweb/StowRsController.java`
   - [ ] DICOM 업로드
   - [ ] 응답 형식 (DICOM JSON)
   - [ ] 통합 테스트

4. **DicomJsonBuilder** (1일)
   - [ ] mini-pacs-poc 이관: `api/dicomweb/DicomJsonBuilder.java`
   - [ ] Entity → DICOM JSON Model 변환
   - [ ] 단위 테스트

#### 산출물

- DICOMWeb API 완성 (QIDO-RS, WADO-RS, STOW-RS)
- OHIF Viewer 연동 테스트

### 7.5 Phase 5: Extended API (Week 8-9, 1-2주)

**목표**: 비표준 확장 API 구현

#### 작업 항목

1. **Instance API** (3일)
   - [ ] mini-pacs-poc 이관: `api/rest/InstanceController.java`
   - [ ] Instance 상세 조회
   - [ ] Cine 정보
   - [ ] 프레임 JPEG
   - [ ] UltrasoundRegion
   - [ ] 비디오 조회

2. **Study API** (2일)
   - [ ] mini-pacs-poc 이관: `api/rest/StudyController.java`
   - [ ] Study 요약
   - [ ] Instance 목록

3. **Swagger API 문서** (1일)
   - [ ] OpenAPI 3.0 설정
   - [ ] API 문서 자동 생성

#### 산출물

- Extended API 완성
- API 문서 (Swagger UI)

### 7.6 Phase 6: 고급 기능 (Week 10-12, 선택, 2-3주)

**목표**: 트랜스코딩, 스토리지 티어링, DICOM SCP 수신

#### 작업 항목

1. **TranscodingWorker** (4일, 선택)
   - [ ] Redis Stream Consumer
   - [ ] 비동기 트랜스코딩 (썸네일 + MP4)
   - [ ] 트랜스코딩 상태 관리

2. **TieringScheduler** (2일, 선택)
   - [ ] 스케줄러 (매일 새벽 2시)
   - [ ] Hot → Warm → Cold 자동 이관
   - [ ] SeaweedFS 티어링 연동

3. **DICOM SCP (C-STORE)** (3일, 선택)
   - [ ] `sado-dicom-gateway` 모듈 생성
   - [ ] dcm4che C-STORE SCP
   - [ ] 초음파 장비 연동 테스트

#### 산출물

- (선택) 트랜스코딩 워커
- (선택) 스토리지 티어링
- (선택) DICOM SCP 수신

---

## 8. 예상 일정 및 리소스

### 8.1 타임라인

```
Week 2-3:  Phase 1 - 도메인 모델 통합
Week 3-5:  Phase 2 - DICOM 처리 서비스
Week 5-6:  Phase 3 - 저장소 연동
Week 6-8:  Phase 4 - DICOMWeb API
Week 8-9:  Phase 5 - Extended API
Week 10-12: Phase 6 - 고급 기능 (선택)
```

**총 예상 기간**: 8-10주 (고급 기능 포함 시 12주)

### 8.2 병렬 작업 가능 영역

- Phase 2와 Phase 3 일부 병렬 (DICOM 처리 학습 + SeaweedFS 설정)
- Phase 4와 Phase 5 병렬 (DICOMWeb API + Extended API)

**병렬 작업 시**: 6-8주 가능

### 8.3 리소스 요구사항

| 역할 | 인원 | 기간 |
|------|------|------|
| Backend 개발자 | 1명 | 8-10주 |
| 학습 시간 | 주 20시간 | - |

---

## 9. 위험 요소 및 완화 전략

### 9.1 기술적 위험

| 위험 | 발생 확률 | 영향도 | 완화 전략 |
|------|----------|--------|---------|
| **dcm4che 학습 곡선** | 중 | 중 | mini-pacs-poc 문서 + 예제 코드 활용 |
| **PostgreSQL → MySQL 호환** | 중 | 중 | JSON, TEXT 타입 검증, 테스트 |
| **Spring Boot 4.0.1 호환** | 낮 | 중 | 3.4.0 → 4.0.1 마이그레이션 가이드 확인 |
| **SeaweedFS 연동** | 낮 | 낮 | mini-pacs-poc 검증 완료, 문서 완벽 |
| **멀티프레임 트랜스코딩 성능** | 중 | 중 | 비동기 처리, GPU 가속화 고려 |

### 9.2 일정 위험

| 위험 | 완화 전략 |
|------|---------|
| Phase 2 DICOM 처리 지연 | mini-pacs-poc 코드 직접 이관 (학습 대신 이관) |
| Phase 4 DICOMWeb API 지연 | mini-pacs-poc 컨트롤러 직접 이관 |
| Phase 6 고급 기능 | 선택 기능으로 우선순위 하향 |

---

## 10. 성공 기준

### 10.1 Phase별 성공 기준

**Phase 1**:
- ✅ Entity 저장/조회 성공
- ✅ MySQL 스키마 정상 동작

**Phase 2**:
- ✅ DICOM 파일 파싱 → DicomMetadata 변환
- ✅ 썸네일 생성 (JPEG)

**Phase 3**:
- ✅ DICOM 업로드 → SeaweedFS 저장 → MySQL 메타데이터 저장

**Phase 4**:
- ✅ OHIF Viewer 연동 성공
- ✅ DICOM 검색/조회 정상 동작

**Phase 5**:
- ✅ Extended API 동작
- ✅ Swagger API 문서 생성

### 10.2 최종 성공 기준

- ✅ DICOM 업로드 → 저장 → 조회 E2E 플로우 동작
- ✅ OHIF Viewer에서 심장초음파 영상 재생
- ✅ DICOMWeb 표준 API 준수
- ✅ 멀티프레임 Cine 재생
- ✅ 썸네일 생성
- ✅ API 문서 완성

---

## 11. 학습 로드맵 통합

### 11.1 기존 16주 계획 수정

**Week 2 변경**:
- 기존: Spring Boot + JPA/MyBatis + MySQL + Multi-Tenancy
- 추가: **+ DICOM 도메인 모델 (Phase 1)**

**Week 3-5 변경**:
- 기존: Spring Cloud Gateway
- 추가: **+ DICOM 처리 서비스 (Phase 2-3)**

**Week 6-8 변경**:
- 기존: Kafka 아키텍처
- 추가: **+ DICOMWeb API (Phase 4-5)**

**Week 10-12 변경** (선택):
- 기존: Temporal 고급
- 추가: **+ DICOM 고급 기능 (Phase 6)**

### 11.2 학습 문서 활용

**mini-pacs-poc 문서 30+개를 sado-be docs/에 통합**:

```
docs/
├── mini-pacs/                  # mini-pacs-poc 학습 자료
│   ├── 01-아키텍처.md
│   ├── 02-도메인-모델.md
│   ├── 03-DICOM-처리.md
│   ├── 04-저장소.md
│   ├── 05-API.md
│   ├── 06-이벤트-처리.md
│   └── api/
│       ├── QIDO-RS.md
│       ├── WADO-RS.md
│       └── STOW-RS.md
│
└── 13_mini-pacs-poc_분석_및_통합_계획.md  # 이 문서
```

---

## 12. 결론 및 권장 사항

### 12.1 통합 전략 요약

**✅ 강력 권장: 선택적 통합 + 학습 중심 접근**

1. **도메인 모델**: 즉시 이관 (검증 완료, 시간 절약)
2. **DICOM 처리**: 참고 후 학습 구현 (dcm4che 마스터 필요)
3. **DICOMWeb API**: 컨트롤러 이관 (표준 준수 검증 완료)
4. **고급 기능**: 선택 적용 (트랜스코딩, 티어링)

### 12.2 예상 효과

- **개발 기간 단축**: 4-6주 → 8-10주 (mini-pacs-poc 없이 구현 시 20주 예상)
- **코드 품질**: 엔터프라이즈 레벨
- **학습 효과**: DICOM 표준 완전 이해
- **위험 감소**: 검증된 코드 재사용

### 12.3 다음 단계

1. **문서 검토 및 승인** (이 문서)
2. **Week 2-3 계획 수립**: Phase 1 도메인 모델 통합
3. **dcm4che 학습 시작**: mini-pacs-poc 문서 읽기
4. **Docker 환경 준비**: MySQL, Redis, SeaweedFS

---

**작성일**: 2025-12-20
**다음 업데이트**: Phase 1 완료 후 (Week 3)
