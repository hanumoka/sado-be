# Week 4 FileAsset 기반 설계 완료 리포트

> **완료일**: 2025-12-29
> **담당**: Claude Code + 사용자
> **우선순위**: POC 개발 최우선 작업

---

## 📋 Executive Summary

### 배경
MiniPACS에서는 DICOM 파일 외에도 다양한 파일 관리가 필요합니다:
- **AI 분석 결과**: Segmentation 이미지 (PNG), 분석 리포트 (PDF)
- **임상 문서**: 진단서, 동의서
- **시스템 파일**: 썸네일, 비디오 변환 결과
- **Export 파일**: ZIP 압축 파일 (임시, TTL 적용)

기존 문서 `27_정적_파일_관리_설계.md`에 설계가 완료되어 있었으나, **실제 코드 구현이 누락**된 상태였습니다.

사용자의 우선순위 요청("POC 개발 운선순위 → Minipacs 스탠드얼론버전")에 따라, Week 4 작업을 즉시 수행했습니다.

### 완료 사항
- ✅ **FileAsset Entity** 설계 및 구현
- ✅ **FileAssetRepository** 13개 쿼리 메서드 설계
- ✅ **3개 Enum 클래스** 구현 (FileCategory, FileStatus, ReferenceType)
- ✅ **file_assets DDL 스크립트** 작성 (8개 인덱스)
- ✅ **84개 테스트 100% 통과** (Hibernate 스키마 생성 검증)

---

## 🎯 핵심 성과

### 1. FileAsset Entity 완성

**파일**: `sado-minipacs/src/main/java/com/hanumoka/sado/minipacs/domain/entity/FileAsset.java`

**주요 필드**:
```java
- id: Long (PK, BaseEntity 상속)
- category: FileCategory (AI_RESULT, CLINICAL_DOC, SYSTEM, EXPORT)
- referenceType: ReferenceType (INSTANCE, STUDY, SERIES, PATIENT, AI_ANALYSIS)
- referenceId: Long (Polymorphic Association)
- status: FileStatus (ACTIVE, EXPIRED, DELETED, ARCHIVED)
- fileName: String (원본 파일명)
- storagePath: String (물리 저장 경로, unique)
- fileSize: Long (bytes)
- mimeType: String (Content-Type)
- checksum: String (MD5/SHA-256 무결성 검증)
- expiresAt: LocalDateTime (TTL 관리)
- lastAccessedAt: LocalDateTime (Storage Tiering 기준)
- storageTier: String (HOT/WARM/COLD)
- metadata: String (JSON, 확장 메타데이터)
```

**비즈니스 메서드**:
- `isExpired()`: TTL 만료 확인
- `markAccessed()`: 마지막 접근 시각 갱신
- `markExpired()`: 만료 상태로 전환
- `markDeleted()`: 삭제 상태로 전환
- `markArchived()`: 아카이브 상태로 전환
- `updateStorageTier(String)`: Storage Tier 업데이트

**Builder 패턴**:
```java
FileAsset aiResult = FileAsset.builder()
    .category(FileCategory.AI_RESULT)
    .referenceType(ReferenceType.AI_ANALYSIS)
    .referenceId(aiAnalysis.getId())
    .fileName("segmentation_frame_001.png")
    .storagePath("/ai_results/123/seg/frame_001.png")
    .fileSize(128000L)
    .mimeType("image/png")
    .checksum("abc123def456")
    .status(FileStatus.ACTIVE)
    .build();
```

### 2. FileAssetRepository 완성

**파일**: `sado-minipacs/src/main/java/com/hanumoka/sado/minipacs/domain/repository/FileAssetRepository.java`

**13개 쿼리 메서드**:

| 메서드 | 용도 | 예시 |
|--------|------|------|
| `findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc()` | 참조 엔티티별 파일 조회 | AI 분석의 모든 결과 파일 |
| `findByCategoryAndStatus()` | 카테고리+상태 필터링 | 활성 상태의 AI 결과 |
| `findByStatus()` | 상태별 조회 | 만료된 파일 전체 조회 |
| `findExpiredFiles()` | TTL 만료 파일 조회 | RetentionPolicyService용 |
| `findFilesForTierMigration()` | Tier 마이그레이션 대상 | 1년 미접근 → COLD |
| `deleteByStatusAndUpdatedAtBefore()` | 오래된 레코드 정리 | 1년 경과 DELETED 제거 |
| `findByStoragePath()` | 경로로 조회 | 파일 시스템 동기화 |
| `findByChecksum()` | 중복 파일 감지 | 체크섬 기반 중복 방지 |
| `findByReferenceTypeAndReferenceIdAndCategory()` | 특정 카테고리만 조회 | AI_RESULT만 필터링 |
| `sumFileSizeByCategory()` | 카테고리별 용량 통계 | AI 결과 총 크기 |
| `sumFileSizeByStorageTier()` | Tier별 용량 통계 | HOT Storage 사용량 |

### 3. 3개 Enum 클래스 완성

#### FileCategory.java
```java
public enum FileCategory {
    AI_RESULT,      // AI 분석 결과 (영구 보관)
    CLINICAL_DOC,   // 임상 문서 (영구 보관)
    SYSTEM,         // 시스템 파일 (90일 TTL)
    EXPORT          // Export 파일 (7일 TTL)
}
```

**카테고리별 보관 정책**:
- `AI_RESULT`: TTL 없음, Storage Tier HOT (자주 접근)
- `CLINICAL_DOC`: TTL 없음, Storage Tier WARM (가끔 접근)
- `SYSTEM`: 90일 TTL, Storage Tier COLD (거의 접근 안 함)
- `EXPORT`: 7일 TTL, Storage Tier HOT (다운로드 대기)

#### FileStatus.java
```java
public enum FileStatus {
    ACTIVE,    // 활성 상태 (사용 가능)
    EXPIRED,   // 만료 상태 (유예 기간 7일)
    DELETED,   // 삭제됨 (메타데이터만 존재)
    ARCHIVED   // 아카이브됨 (COLD Storage)
}
```

**상태 전이 흐름**:
```
ACTIVE → EXPIRED → DELETED
  ↓
ARCHIVED
```

#### ReferenceType.java
```java
public enum ReferenceType {
    INSTANCE,      // DICOM Instance (썸네일, 비디오)
    SERIES,        // DICOM Series (Series ZIP)
    STUDY,         // DICOM Study (Study 리포트)
    PATIENT,       // Patient (동의서, 환자 문서)
    AI_ANALYSIS    // AI 분석 (Segmentation, 리포트)
}
```

### 4. file_assets DDL 스크립트

**파일**: `sado-minipacs/src/main/resources/db/schema/file_assets.sql`

**8개 인덱스**:
1. `idx_file_assets_reference`: (reference_type, reference_id) - 참조 엔티티로 파일 조회
2. `idx_file_assets_category_status`: (category, status) - 카테고리+상태 필터링
3. `idx_file_assets_expires_at`: (expires_at) - TTL 만료 파일 조회
4. `idx_file_assets_tenant_id`: (tenant_id) - 멀티테넌시 필터링
5. `idx_file_assets_checksum`: (checksum) - 중복 파일 감지
6. `idx_file_assets_tier_accessed`: (storage_tier, last_accessed_at) - Tier 마이그레이션

**샘플 데이터** (4가지 카테고리):
- AI 분석 결과 (영구 보관)
- Export 파일 (7일 TTL)
- 시스템 파일 (90일 TTL)
- 임상 문서 (영구 보관)

**유용한 쿼리 7가지**:
- 만료 예정 파일 조회 (7일 이내)
- 카테고리별 스토리지 사용량
- Storage Tier별 스토리지 사용량
- COLD Storage 이동 대상 (1년 미접근)
- 만료된 파일 정리 대상 (만료 후 7일 경과)
- 특정 AI 분석의 모든 결과 파일
- 중복 파일 감지 (동일 체크섬)

---

## 🔧 추가 작업

### Enum 리팩토링 (DICOM 도메인 명확화)

**작업 배경**:
기존 Enum들이 도메인 특화되지 않은 일반적인 이름을 사용하고 있었습니다.

**리팩토링 내용**:

#### 1. DicomValidationLevel (NEW)
- **변경**: ValidationLog 내부 enum에서 독립 파일로 분리
- **이유**: 재사용성 향상, 다른 클래스에서도 참조 가능
- **파일**: `DicomValidationLevel.java`
- **값**: LEVEL_1 (기본), LEVEL_2 (심초음파), LEVEL_3 (AI 준비)

#### 2. DicomValidationCategory (Renamed from ValidationType)
- **변경**: ValidationType → DicomValidationCategory
- **이유**: DICOM 도메인 명확화
- **파일**: `DicomValidationCategory.java`
- **값**: DICOM_CONFORMANCE, MANDATORY_TAGS, DATA_INTEGRITY, DUPLICATE_CHECK

#### 3. DicomValidationStatus (Renamed from ValidationResult)
- **변경**: ValidationResult → DicomValidationStatus
- **이유**: Result보다 Status가 의미 명확
- **파일**: `DicomValidationStatus.java`
- **값**: SUCCESS, WARNING, ERROR

### DicomMetadataRecord fileSize 추가

**파일**: `DicomMetadataRecord.java`

**변경**:
```java
@Column(name = "file_size")
private Long fileSize;  // 파일 크기 (bytes)
```

**용도**:
- 스토리지 사용량 통계
- Storage Tiering 계산 (파일 크기 기반 우선순위)
- 다운로드 진행률 표시

---

## 📊 테스트 결과

### 전체 테스트 통과
```
BUILD SUCCESSFUL in 21s
84 tests passed ✅
```

### Hibernate 스키마 검증
```sql
Hibernate: drop table if exists file_assets cascade
Hibernate: create table file_assets (
    id bigint not null auto_increment,
    category varchar(32) not null,
    reference_type varchar(32) not null,
    reference_id bigint not null,
    status varchar(16) default 'ACTIVE' not null,
    file_name varchar(255) not null,
    storage_path varchar(512) not null unique,
    file_size bigint not null,
    mime_type varchar(128) default 'application/octet-stream' not null,
    checksum varchar(128),
    expires_at datetime(6),
    last_accessed_at datetime(6),
    storage_tier varchar(16) default 'HOT',
    metadata text,
    tenant_id bigint not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(100),
    updated_by varchar(100),
    primary key (id)
) engine=InnoDB
```

---

## 🐛 트러블슈팅

### 1. Composite ID 오류
**문제**:
```
IdentifierGenerationException: Identity generation isn't supported for composite ids
```

**원인**:
- FileAsset에서 `assetId`를 `@Id`로 선언
- BaseEntity에서 이미 `id` 필드가 `@Id`로 선언됨
- Hibernate가 두 개의 ID를 composite ID로 인식

**해결**:
- `assetId` 필드 제거
- BaseEntity의 `id` 필드 사용 (다른 엔티티와 일관성 유지)
- DDL 스크립트도 `id` 컬럼으로 수정

**학습**:
- `@MappedSuperclass`의 필드는 상속됨
- 자식 클래스에서 ID 재선언 금지
- 일관성 있는 필드명 사용 (모든 Entity가 `id` 사용)

---

## 📁 생성/수정 파일 목록

### 신규 생성 (9개)
1. `FileCategory.java` - 파일 카테고리 Enum
2. `FileStatus.java` - 파일 상태 Enum
3. `ReferenceType.java` - 참조 타입 Enum
4. `FileAsset.java` - 파일 자산 Entity
5. `FileAssetRepository.java` - Repository 인터페이스
6. `file_assets.sql` - DDL 스크립트
7. `DicomValidationLevel.java` - 검증 레벨 Enum (분리)
8. `DicomValidationCategory.java` - 검증 카테고리 Enum (리팩토링)
9. `DicomValidationStatus.java` - 검증 상태 Enum (리팩토링)

### 수정 (3개)
10. `DicomMetadataRecord.java` - fileSize 필드 추가
11. `ValidationLog.java` - Enum import 업데이트
12. `ValidationLogRepository.java` - Enum import 업데이트

---

## 🎓 학습 성과

### 기술적 학습
1. **Enum 설계 일관성** - 도메인별 명확한 네이밍 규칙 (Dicom prefix)
2. **Enum 재사용성** - 내부 enum vs 외부 enum 분리 기준
3. **FileAsset 다형성 연관** - Polymorphic Association (referenceType + referenceId) 패턴
4. **TTL 관리 설계** - expiresAt 필드로 자동 만료 지원
5. **Storage Tiering 전략** - HOT/WARM/COLD 계층 기반 비용 최적화
6. **BaseEntity 상속 주의사항** - id 필드 중복 선언 시 Composite ID 오류

### 설계 패턴
1. **Builder 패턴** - 복잡한 Entity 생성 간소화
2. **Repository 패턴** - 쿼리 메서드 명명 규칙 (findBy, sumBy, deleteBy)
3. **Enum 기반 타입 안전성** - 매직 스트링 제거
4. **멀티테넌시 자동 필터링** - TenantAwareEntity 상속으로 투명한 격리
5. **메타데이터 확장성** - JSON 필드로 유연한 확장 지원

---

## 🚀 다음 단계 (Week 10 AI 통합 준비 완료)

이제 다음 작업이 가능합니다:

### 1. FileAssetService 구현 (Week 10)
```java
@Service
public class FileAssetService {
    // AI 분석 결과 저장
    public FileAsset saveAiResult(AiAnalysis analysis, MultipartFile file);

    // TTL 기반 만료 처리
    @Scheduled(cron = "0 0 * * * *")
    public void expireFiles();

    // 파일 다운로드
    public byte[] downloadFile(Long fileId);
}
```

### 2. RetentionPolicyService 구현 (Week 13)
```java
@Service
public class RetentionPolicyService {
    // 만료된 파일 정리 (유예 기간 7일)
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredFiles();

    // 오래된 DELETED 레코드 제거 (1년 경과)
    @Scheduled(cron = "0 0 2 * * SUN")
    public void purgeOldRecords();
}
```

### 3. StorageTieringService 구현 (Week 13)
```java
@Service
public class StorageTieringService {
    // HOT → WARM (30일 미접근)
    // WARM → COLD (1년 미접근)
    @Scheduled(cron = "0 0 3 * * *")
    public void migrateTiers();

    // S3 Glacier 복원
    public void restoreFromCold(Long fileId);
}
```

### 4. AI Workflow 통합 (Week 10)
```java
// Temporal Workflow Activity
public class SaveAiResultActivity {
    public void saveSegmentationImages(AiAnalysis analysis, List<byte[]> frames) {
        for (int i = 0; i < frames.size(); i++) {
            FileAsset segImage = FileAsset.builder()
                .category(FileCategory.AI_RESULT)
                .referenceType(ReferenceType.AI_ANALYSIS)
                .referenceId(analysis.getId())
                .fileName("segmentation_frame_" + i + ".png")
                .fileSize((long) frames.get(i).length)
                .build();
            fileAssetRepository.save(segImage);
        }
    }
}
```

---

## 📝 관련 문서 업데이트

이 작업 완료 후 다음 문서들이 업데이트되었습니다:

1. **PROGRESS.md** ✅
   - Week 3-4 코드 품질 개선 및 FileAsset 설계 섹션 추가
   - 변경 이력 업데이트 (2025-12-29 17:00)
   - 학습 성과 6개 추가

2. **CURRENT_CONTEXT.md** ✅
   - 현재 진행 상황 업데이트 (Domain Layer + FileAsset Foundation 완료)
   - 최근 완료된 작업 추가 (FileAsset 기반 설계 완료)
   - 테스트 개수 업데이트 (31개 → 84개)

3. **27_정적_파일_관리_설계.md** ✅
   - 최종 업데이트 날짜 변경 (2025-12-29)
   - 구현 상태 표시 (Week 4 FileAsset 설계 완료)
   - Section 3.1 FileAssetEntity 구현 완료 상태 추가

4. **본 문서 (WEEK_4_FileAsset_완료_리포트.md)** ✅ (신규)
   - Week 4 작업 종합 정리
   - 설계 → 구현 → 테스트 전 과정 문서화
   - 트러블슈팅 및 학습 성과 정리

---

## ✅ 체크리스트

- [x] FileCategory Enum 구현
- [x] FileStatus Enum 구현
- [x] ReferenceType Enum 구현
- [x] FileAsset Entity 구현 (모든 필드)
- [x] FileAssetRepository 구현 (13개 쿼리 메서드)
- [x] file_assets DDL 스크립트 작성
- [x] Enum 리팩토링 (DicomValidation*)
- [x] DicomMetadataRecord fileSize 필드 추가
- [x] 84개 테스트 100% 통과
- [x] Hibernate 스키마 생성 검증
- [x] Composite ID 이슈 해결
- [x] PROGRESS.md 업데이트
- [x] CURRENT_CONTEXT.md 업데이트
- [x] 27_정적_파일_관리_설계.md 업데이트
- [x] 완료 리포트 작성 (본 문서)

---

## 📌 결론

Week 4 FileAsset 기반 설계가 성공적으로 완료되었습니다.

**핵심 성과**:
- ✅ 12개 파일 생성/수정
- ✅ 84개 테스트 100% 통과
- ✅ Week 10 AI 통합 준비 완료
- ✅ POC 우선순위 작업 완료

이제 MiniPACS는 DICOM 파일 외에도 다양한 정적 파일을 통합 관리할 수 있는 기반을 갖추었습니다. Week 10에서 AI 분석 결과를 저장하고, Week 13에서 Storage Tiering 및 TTL 관리를 구현하면 완전한 파일 관리 시스템이 완성됩니다.

---

*작성일: 2025-12-29*
*작성자: Claude Code + 사용자*
*문서 버전: 1.0*
