# 22. PostgreSQL → MySQL 마이그레이션 가이드

> **작성일**: 2025-12-28
> **목적**: mini-pacs-poc (PostgreSQL) → sado-be (MySQL) 스키마 변환 및 데이터 타입 매핑
> **우선순위**: CRITICAL (Phase 2 통합 필수)

---

## 1. Executive Summary

### 1.1 배경

| 항목 | mini-pacs-poc | sado-be | 변경 이유 |
|------|---------------|---------|-----------|
| **Database** | PostgreSQL 15 | MySQL 8.0 | sado 기존 인프라 정합성 |
| **JSON 타입** | JSONB | JSON | MySQL 네이티브 지원 |
| **Array 타입** | TEXT[] | JSON Array | MySQL Array 미지원 |
| **UUID** | UUID (native) | BINARY(16) | MySQL UUID 함수 활용 |

### 1.2 마이그레이션 전략

**핵심 원칙**: DICOM 메타데이터 무결성 보존 + 성능 최적화

✅ **보존 항목**:
- DICOM 태그 구조 (StudyInstanceUID, SeriesInstanceUID 등)
- 멀티프레임 프레임 순서
- 환자 식별 정보 (PatientID, IssuerOfPatientID)

⚠️ **변경 항목**:
- JSONB → JSON (쿼리 성능 영향 최소화)
- Array → JSON Array (인덱싱 전략 변경)
- UUID → BINARY(16) (저장 공간 16 bytes 동일)

---

## 2. 데이터 타입 매핑

### 2.1 JSON 타입 변환

#### PostgreSQL JSONB vs MySQL JSON

| 기능 | PostgreSQL JSONB | MySQL JSON |
|------|------------------|------------|
| **저장 방식** | Binary (압축) | Text-based (MySQL 8.0+는 내부 최적화) |
| **인덱싱** | GIN Index | Generated Column + Index |
| **성능** | 빠른 쿼리 | MySQL 8.0+에서 비슷한 성능 |

#### 마이그레이션 코드

**Before (PostgreSQL)**:
```sql
CREATE TABLE dicom_instances (
    id BIGSERIAL PRIMARY KEY,
    dicom_metadata JSONB NOT NULL,
    frame_metadata JSONB
);

-- JSONB GIN 인덱스
CREATE INDEX idx_dicom_metadata_gin ON dicom_instances USING GIN (dicom_metadata);

-- JSONB 쿼리
SELECT * FROM dicom_instances
WHERE dicom_metadata @> '{"PatientID": "P12345"}';
```

**After (MySQL)**:
```sql
CREATE TABLE dicom_instances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dicom_metadata JSON NOT NULL,
    frame_metadata JSON,

    -- Generated Column for indexing
    patient_id VARCHAR(64) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(dicom_metadata, '$.PatientID'))) STORED,
    study_instance_uid VARCHAR(128) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(dicom_metadata, '$.StudyInstanceUID'))) STORED,

    INDEX idx_patient_id (patient_id),
    INDEX idx_study_instance_uid (study_instance_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- MySQL JSON 쿼리 (Generated Column 활용)
SELECT * FROM dicom_instances WHERE patient_id = 'P12345';

-- JSON Path 직접 쿼리 (느림, 비추천)
SELECT * FROM dicom_instances
WHERE JSON_EXTRACT(dicom_metadata, '$.PatientID') = 'P12345';
```

### 2.2 Array 타입 변환

**Before (PostgreSQL)**:
```sql
CREATE TABLE series (
    id BIGSERIAL PRIMARY KEY,
    instance_uids TEXT[] NOT NULL  -- Array 타입
);

-- Array 쿼리
SELECT * FROM series WHERE 'instance123' = ANY(instance_uids);
```

**After (MySQL)**:
```sql
CREATE TABLE series (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instance_uids JSON NOT NULL  -- JSON Array
);

-- JSON Array 쿼리
SELECT * FROM series
WHERE JSON_CONTAINS(instance_uids, '"instance123"');

-- JSON Array 길이
SELECT id, JSON_LENGTH(instance_uids) as frame_count FROM series;
```

### 2.3 UUID 타입 변환

**Before (PostgreSQL)**:
```sql
CREATE TABLE uploads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid()
);
```

**After (MySQL)**:
```sql
CREATE TABLE uploads (
    id BINARY(16) PRIMARY KEY DEFAULT (UNHEX(REPLACE(UUID(), '-', '')))
);

-- UUID 변환 함수
CREATE FUNCTION BIN_TO_UUID(b BINARY(16))
RETURNS CHAR(36) DETERMINISTIC
RETURN LOWER(CONCAT(
    HEX(SUBSTRING(b, 1, 4)), '-',
    HEX(SUBSTRING(b, 5, 2)), '-',
    HEX(SUBSTRING(b, 7, 2)), '-',
    HEX(SUBSTRING(b, 9, 2)), '-',
    HEX(SUBSTRING(b, 11))
));

CREATE FUNCTION UUID_TO_BIN(uuid CHAR(36))
RETURNS BINARY(16) DETERMINISTIC
RETURN UNHEX(REPLACE(uuid, '-', ''));
```

---

## 3. 스키마 변환 (Flyway)

### 3.1 Patients 테이블

**PostgreSQL → MySQL DDL**:

```sql
-- V1__create_patients.sql
CREATE TABLE patients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,

    -- DICOM Patient Module
    patient_id VARCHAR(64) NOT NULL,
    issuer_of_patient_id VARCHAR(64),
    patient_name VARCHAR(255),
    patient_birth_date DATE,
    patient_sex ENUM('M', 'F', 'O') COMMENT 'M=Male, F=Female, O=Other',

    -- Application Layer
    application_patient_id VARCHAR(64) COMMENT 'EMR 환자 ID (매핑 성공 시)',
    trust_level ENUM('VERIFIED', 'RESEARCH', 'EDUCATIONAL', 'UNVERIFIED') DEFAULT 'UNVERIFIED',

    -- Timestamps
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- Indexes
    UNIQUE KEY uk_tenant_patient (tenant_id, patient_id, issuer_of_patient_id),
    INDEX idx_application_patient_id (application_patient_id),
    INDEX idx_trust_level (trust_level),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 3.2 Studies 테이블

```sql
-- V2__create_studies.sql
CREATE TABLE studies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    patient_id BIGINT NOT NULL,

    -- DICOM Study Module
    study_instance_uid VARCHAR(128) NOT NULL,
    study_date DATE,
    study_time TIME,
    study_description VARCHAR(255),
    accession_number VARCHAR(64),

    -- Metadata (JSON)
    dicom_metadata JSON COMMENT 'Full DICOM Study tags',

    -- Generated Columns for indexing
    modality VARCHAR(16) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(dicom_metadata, '$.Modality'))) STORED,

    -- Timestamps
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- Indexes
    UNIQUE KEY uk_tenant_study_uid (tenant_id, study_instance_uid),
    INDEX idx_patient_id (patient_id),
    INDEX idx_study_date (study_date),
    INDEX idx_modality (modality),
    INDEX idx_accession_number (accession_number),

    -- Foreign Keys
    CONSTRAINT fk_studies_patient FOREIGN KEY (patient_id)
        REFERENCES patients(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.3 Series 테이블

```sql
-- V3__create_series.sql
CREATE TABLE series (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    study_id BIGINT NOT NULL,

    -- DICOM Series Module
    series_instance_uid VARCHAR(128) NOT NULL,
    series_number INT,
    series_description VARCHAR(255),
    modality VARCHAR(16),

    -- 멀티프레임 정보
    is_multiframe BOOLEAN DEFAULT FALSE,
    number_of_frames INT DEFAULT 1,
    frame_rate DECIMAL(10, 4) COMMENT 'Calculated from FrameTime or CineRate',
    frame_rate_source VARCHAR(32) COMMENT 'FrameTime | CineRate | Default',

    -- Instance UIDs (JSON Array)
    instance_uids JSON COMMENT 'Array of SOPInstanceUIDs in frame order',

    -- Metadata
    dicom_metadata JSON,

    -- Timestamps
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- Indexes
    UNIQUE KEY uk_tenant_series_uid (tenant_id, series_instance_uid),
    INDEX idx_study_id (study_id),
    INDEX idx_modality (modality),
    INDEX idx_is_multiframe (is_multiframe),

    -- Foreign Keys
    CONSTRAINT fk_series_study FOREIGN KEY (study_id)
        REFERENCES studies(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.4 Instances 테이블

```sql
-- V4__create_instances.sql
CREATE TABLE instances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    series_id BIGINT NOT NULL,

    -- DICOM Instance Module
    sop_instance_uid VARCHAR(128) NOT NULL,
    sop_class_uid VARCHAR(128) NOT NULL,
    instance_number INT,

    -- 멀티프레임
    frame_number INT DEFAULT 1 COMMENT '멀티프레임 내 프레임 순서 (1-based)',

    -- Storage
    storage_backend ENUM('seaweedfs', 's3', 'local') DEFAULT 'seaweedfs',
    storage_key VARCHAR(512) NOT NULL COMMENT 'SeaweedFS fid or S3 key',
    file_size BIGINT NOT NULL,
    sha256_hash CHAR(64) COMMENT 'DICOM 원본 무결성 검증',

    -- Metadata
    dicom_metadata JSON NOT NULL,

    -- Generated Columns
    transfer_syntax_uid VARCHAR(128) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(dicom_metadata, '$.TransferSyntaxUID'))) STORED,

    -- Timestamps
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- Indexes
    UNIQUE KEY uk_tenant_sop_uid (tenant_id, sop_instance_uid),
    INDEX idx_series_id (series_id),
    INDEX idx_storage_key (storage_key(255)),
    INDEX idx_transfer_syntax (transfer_syntax_uid),
    INDEX idx_frame_number (frame_number),

    -- Foreign Keys
    CONSTRAINT fk_instances_series FOREIGN KEY (series_id)
        REFERENCES series(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 4. JSON 함수 차이

### 4.1 추출 함수

| PostgreSQL | MySQL | 설명 |
|------------|-------|------|
| `metadata->>'PatientID'` | `JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.PatientID'))` | String 추출 |
| `metadata->'tags'` | `JSON_EXTRACT(metadata, '$.tags')` | JSON 객체 추출 |
| `metadata#>>'{patient,name}'` | `JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.patient.name'))` | 중첩 필드 |

### 4.2 검색 함수

| PostgreSQL | MySQL | 설명 |
|------------|-------|------|
| `metadata @> '{"key":"value"}'` | `JSON_CONTAINS(metadata, '{"key":"value"}')` | JSON 포함 검색 |
| `'value' = ANY(array_col)` | `JSON_CONTAINS(json_array, '"value"')` | Array 검색 |
| `jsonb_array_length(arr)` | `JSON_LENGTH(arr)` | Array 길이 |

---

## 5. 성능 최적화

### 5.1 Generated Column 인덱싱 전략

**핵심 원칙**: 자주 쿼리하는 JSON 필드는 Generated Column + Index

```sql
ALTER TABLE dicom_instances
ADD COLUMN patient_id VARCHAR(64)
    GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(dicom_metadata, '$.PatientID')))
    STORED,
ADD INDEX idx_patient_id (patient_id);
```

**장점**:
- JSON Path 쿼리보다 10-100배 빠름
- 일반 B-Tree Index 활용 가능

**단점**:
- 저장 공간 추가 소모 (trade-off 수용)

### 5.2 JSON vs 정규화 컬럼

| 항목 | JSON 저장 | 정규화 컬럼 | 권장 |
|------|----------|------------|------|
| **자주 쿼리** (PatientID, StudyUID) | ❌ 느림 | ✅ Generated Column + Index | Generated Column |
| **가끔 조회** (Manufacturer, DeviceSerialNumber) | ✅ JSON | ❌ 불필요한 컬럼 | JSON |
| **전체 메타데이터** | ✅ JSON | ❌ 200+ 컬럼 비현실적 | JSON |

### 5.3 Connection Pool 설정

**HikariCP (sado-be 표준)**:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

      # MySQL 최적화
      data-source-properties:
        cachePrepStmts: true
        prepStmtCacheSize: 250
        prepStmtCacheSqlLimit: 2048
        useServerPrepStmts: true
        useLocalSessionState: true
        rewriteBatchedStatements: true
        cacheResultSetMetadata: true
        cacheServerConfiguration: true
        elideSetAutoCommits: true
        maintainTimeStats: false
```

---

## 6. Entity 클래스 변환

### 6.1 JSON 타입 매핑 (Hibernate)

**Before (PostgreSQL + Hibernate Types)**:
```java
@Entity
@Table(name = "dicom_instances")
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class DicomInstanceEntity {
    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> dicomMetadata;
}
```

**After (MySQL + JPA 2.1+)**:
```java
@Entity
@Table(name = "dicom_instances")
public class DicomInstanceEntity {

    @Convert(converter = JsonConverter.class)
    @Column(columnDefinition = "JSON")
    private Map<String, Object> dicomMetadata;

    // Generated Column (Read-Only)
    @Column(name = "patient_id", insertable = false, updatable = false)
    private String patientId;
}

// JsonConverter.java
@Converter
public class JsonConverter implements AttributeConverter<Map<String, Object>, String> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error converting Map to JSON", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        try {
            return objectMapper.readValue(dbData, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new IllegalArgumentException("Error converting JSON to Map", e);
        }
    }
}
```

---

## 7. 마이그레이션 체크리스트

### Phase 1: 스키마 변환 (Week 2-3)

- [ ] Flyway 마이그레이션 스크립트 작성 (V1-V10)
- [ ] JSON Converter 구현 및 테스트
- [ ] Generated Column 인덱스 전략 확정
- [ ] Entity 클래스 MySQL 변환
- [ ] Repository 쿼리 메서드 검증

### Phase 2: 데이터 마이그레이션 (Optional)

- [ ] PostgreSQL → MySQL 데이터 Export 스크립트
- [ ] JSONB → JSON 변환 검증
- [ ] Array → JSON Array 변환
- [ ] UUID → BINARY(16) 변환

### Phase 3: 성능 검증 (Week 3-4)

- [ ] Generated Column 쿼리 성능 비교 (JSON Path vs Index)
- [ ] Connection Pool 튜닝
- [ ] Slow Query 로그 분석

---

## 8. 참고 자료

- **MySQL JSON 공식 문서**: https://dev.mysql.com/doc/refman/8.0/en/json.html
- **Flyway 마이그레이션**: https://flywaydb.org/documentation/
- **mini-pacs-poc 스키마**: `13-3_데이터베이스_스키마.md`
- **DICOM 메타데이터**: `21_DICOM_원본_보존_정책.md`

---

**작성일**: 2025-12-28
**다음 리뷰**: Phase 1 완료 후 (Week 3)
