# SADO-BE Week 3-4 완료 기록 (Archive)

> **완료 기간**: 2025-12-27 ~ 2025-12-30
> **진행률**: 100% 완료
> **목표**: Domain Layer (Entity + Repository + Service + Controller + 통합 테스트)

---

## 개요

Week 3-4는 MiniPACS의 Domain Layer를 100% 완성하는 단계입니다. DICOM 계층 구조(Patient → Study → Series → Instance)를 JPA Entity로 설계하고, Repository, Service, Controller, 통합 테스트를 구현했습니다.

**핵심 성과**:
- Entity 6개 + Repository 6개 + Service 4개 + Controller 1개 구현
- 통합 테스트 31개 100% 통과
- 2-Layer 아키텍처 적용 (Storage Layer vs Application Layer)
- findOrCreate 패턴 구현 (DICOM Ingest Pipeline)

---

## Phase 2: Domain Layer (Week 3-4) - 완료

### 목표
- JPA Entity 설계 (Patient, Study, Series, Instance, DicomMetadataRecord, ValidationLog)
- Repository 계층 구현 (Spring Data JPA Query Methods)
- Service 계층 구현 (findOrCreate 패턴, @Transactional)
- Controller 계층 구현 (PatientController)
- 통합 테스트 31개 통과

### 완료 현황
- [x] Entity 6개 설계 완료
- [x] Repository 6개 구현 완료
- [x] Service 4개 구현 완료
- [x] Controller 1개 구현 완료
- [x] 통합 테스트 31개 통과
- [x] FileAsset 설계 완료 (Week 4 추가)
- [x] Swagger/OpenAPI 통합 완료

**완료일**: 2025-12-30

---

## 상세 체크리스트 (100% 완료)

### 학습 목표
- [x] 2-Layer 아키텍처 이해 (Storage Layer vs Application Layer)
- [x] DICOM 원본 보존 정책 이해 (WORM)
- [x] Option A vs Option B 관계 설계 비교
- [x] Issuer of PatientID 필요성 이해
- [x] EMR 환자 매핑 전략 이해
- [x] JPA 연관 관계 매핑 (@OneToMany, @ManyToOne)
- [x] Spring Data JPA Query Methods
- [x] ValidationLog 역할 이해 (검증 이력 추적)
- [x] @Transactional 활용 (readOnly, write 분리)
- [x] findOrCreate 패턴 (DICOM Ingest Pipeline용)
- [x] Service Layer 예외 처리 전략

### 실습 목표

#### 1. DICOM Entity 설계 (100% 완료)
- [x] **DicomMetadataRecord Entity**
  - [x] DICOM Storage Layer 역할 이해
  - [x] WORM 정책 적용 (immutable)
  - [x] nullable 제약 최소화 (무조건 업로드 허용)
  - [x] JSON metadata 저장 (원본 보존)
  - [x] fileHash, studyInstanceUid, filePath, filename 필드
- [x] **Study-DicomMetadataRecord 관계 설계**
  - [x] Option A vs Option B 비교 분석
  - [x] Option B (간접 참조) 선택
  - [x] studyInstanceUid 비즈니스 키로 조회
- [x] **Study Entity 수정**
  - [x] Application Domain Layer 역할 명확화
  - [x] metadata JSON 필드 제거 (Storage Layer 분리)
  - [x] patientId String 제거 (Patient 관계 준비)
  - [x] studyInstanceUid 간접 참조 유지

#### 2. JPA Repository 구현 (100% 완료)
- [x] **PatientRepository**
  - [x] findByDicomPatientIdAndIssuerOfPatientId
  - [x] findByEmrPatientId
- [x] **StudyRepository**
  - [x] findByStudyInstanceUid
  - [x] findByPatientIdOrderByStudyDateDesc
  - [x] countByPatientId
- [x] **SeriesRepository**
  - [x] findBySeriesInstanceUid
  - [x] findByStudyIdOrderBySeriesNumber
  - [x] findByStudyIdAndModality
  - [x] countByStudyId
- [x] **InstanceRepository**
  - [x] findBySopInstanceUid
  - [x] findBySeriesIdOrderByInstanceNumber
  - [x] findBySeriesIdAndInstanceNumber
  - [x] countBySeriesId
  - [x] findByFilePath
- [x] **DicomMetadataRecordRepository**
  - [x] findByInstanceId (1:1 관계)
  - [x] findBySopInstanceUid
  - [x] findByStudyInstanceUid
  - [x] findBySeriesInstanceUid
- [x] **ValidationLogRepository**
  - [x] findByInstanceIdOrderByCreatedAtDesc
  - [x] findByValidationResult
  - [x] findByValidationTypeAndValidationResult
  - [x] findByCreatedAtBetween

#### 3. Service 계층 구현 (100% 완료)
- [x] **PatientService**
  - [x] CRUD 기능 (create, findById, update, delete)
  - [x] findByDicomPatientId (DICOM PatientID + Issuer 조회)
  - [x] findByEmrPatientId (EMR 환자 ID 조회)
  - [x] findOrCreatePatient (Identity Resolution)
- [x] **StudyService**
  - [x] CRUD 기능 (create, findById, update, delete)
  - [x] findByStudyInstanceUid (Study UID 조회)
  - [x] findByPatientId (환자별 검사 목록, 최신순)
  - [x] countByPatientId (환자의 검사 개수)
  - [x] findOrCreateStudy (DICOM C-STORE Ingest용)
- [x] **SeriesService**
  - [x] CRUD 기능 (create, findById, update, delete)
  - [x] findBySeriesInstanceUid (Series UID 조회)
  - [x] findByStudyId (검사별 시리즈 목록, Series Number순)
  - [x] findByStudyIdAndModality (Modality 필터링)
  - [x] countByStudyId (검사의 시리즈 개수)
  - [x] findOrCreateSeries (DICOM C-STORE Ingest용)
- [x] **InstanceService**
  - [x] CRUD 기능 (create, findById, update, delete)
  - [x] findBySopInstanceUid (SOP Instance UID 조회)
  - [x] findByFilePath (파일 경로 조회, 중복 방지)
  - [x] findBySeriesId (시리즈별 Instance 목록, Instance Number순)
  - [x] countBySeriesId (시리즈의 Instance 개수)
  - [x] findOrCreateInstance (DICOM C-STORE Ingest용)
  - [x] storagePath 중복 검증
- [x] **@Transactional 적용**
  - [x] readOnly=true (조회 메서드)
  - [x] write (CUD 메서드)
- [x] **예외 처리**
  - [x] ResourceNotFoundException (엔티티 미존재)
  - [x] IllegalArgumentException (비즈니스 규칙 위반)
- [x] **findOrCreate 패턴**
  - [x] DICOM C-STORE 수신 시 중복 방지
  - [x] UID 기반 조회 → 없으면 생성

#### 4. Enum 타입 생성 (100% 완료)
- [x] **ValidationType** (Week 3)
  - [x] DICOM_CONFORMANCE
  - [x] MANDATORY_TAGS
  - [x] DATA_INTEGRITY
  - [x] DUPLICATE_CHECK
- [x] **ValidationResult** (Week 3)
  - [x] SUCCESS
  - [x] WARNING
  - [x] ERROR
- [x] **DicomValidationLevel** (Week 4)
  - [x] LEVEL_1 (Critical)
  - [x] LEVEL_2 (Warning)
  - [x] LEVEL_3 (AI Readiness)
- [x] **DicomValidationCategory** (Week 4)
  - [x] DICOM_CONFORMANCE
  - [x] MANDATORY_TAGS
  - [x] DATA_INTEGRITY
  - [x] DUPLICATE_CHECK
- [x] **DicomValidationStatus** (Week 4)
  - [x] SUCCESS
  - [x] WARNING
  - [x] ERROR
- [x] **FileCategory** (Week 4)
  - [x] AI_RESULT
  - [x] CLINICAL_DOC
  - [x] SYSTEM
  - [x] EXPORT
- [x] **FileStatus** (Week 4)
  - [x] ACTIVE
  - [x] EXPIRED
  - [x] DELETED
  - [x] ARCHIVED
- [x] **ReferenceType** (Week 4)
  - [x] INSTANCE
  - [x] STUDY
  - [x] SERIES
  - [x] PATIENT
  - [x] AI_ANALYSIS

#### 5. TenantProvider 인프라 (100% 완료)
- [x] **TenantProvider 인터페이스**
- [x] **DefaultTenantProvider** (tenant_id=1 반환)
- [x] **TenantEntityListener** (@PrePersist 자동 주입)

#### 6. 빌드 검증 (100% 완료)
- [x] sado-common 빌드 성공
- [x] sado-minipacs 빌드 성공
- [x] 전체 프로젝트 빌드 성공

#### 7. Controller 계층 구현 (100% 완료)
- [x] **PatientController 구현**
  - [x] GET /api/patients/{id}
  - [x] GET /api/patients/dicom/{dicomPatientId}
  - [x] GET /api/patients/emr/{emrPatientId}
  - [x] GET /api/patients/{patientId}/studies
  - [x] POST /api/patients
  - [x] PUT /api/patients/{id}
  - [x] DELETE /api/patients/{id}
- [x] **DTO 클래스 작성**
  - [x] CreatePatientRequest
  - [x] UpdatePatientRequest
  - [x] PatientResponse
  - [x] ApiResponse 래핑

#### 8. 통합 테스트 (100% 완료)
- [x] **PatientControllerTest**
  - [x] POST 테스트 3개 (생성 성공, 필수 필드 누락, 중복 생성)
  - [x] GET 테스트 2개 (조회 성공, 존재하지 않는 ID)
  - [x] PUT 테스트 3개 (수정 성공, 존재하지 않는 ID, 필수 필드 누락)
  - [x] DELETE 테스트 3개 (삭제 성공, 존재하지 않는 ID, 관련 데이터 있을 때)
  - [x] 추가 조회 테스트 2개 (DICOM PatientID 조회, Studies 목록 조회)
- [x] **테스트 인프라 설정**
  - [x] @SpringBootTest + @Transactional
  - [x] RestTestClient 설정
  - [x] H2 in-memory database
  - [x] TestFixtures 유틸리티
- [x] **전체 테스트 통과**
  - [x] 31개 테스트 100% 통과

#### 9. 통합 테스트 트러블슈팅 (100% 완료)
- [x] **PropertyReferenceException 해결**
  - [x] DicomMetadataRecord 필드 추가 (seriesInstanceUid, sopInstanceUid, instanceId)
  - [x] ValidationLogRepository 메서드명 수정
- [x] **Java `-parameters` 플래그 추가**
  - [x] build.gradle 컴파일러 설정
  - [x] @PathVariable 파라미터명 인식 문제 해결
- [x] **Jackson 역직렬화 수정**
  - [x] PatientResponse: @NoArgsConstructor, @AllArgsConstructor 추가
  - [x] ApiResponse: @JsonIgnore 추가
- [x] **isSuccess() 로직 수정**
  - [x] API 코드 범위 수정 (200-299 → 200000-299999)
- [x] **PatientServiceTest Mock 수정**
  - [x] findById() mock 추가

#### 10. Week 3-4 코드 품질 개선 및 FileAsset 설계 (100% 완료)
- [x] **DicomMetadataRecord fileSize 필드 추가**
- [x] **Enum 리팩토링** (DicomValidationLevel, DicomValidationCategory, DicomValidationStatus)
- [x] **Swagger/OpenAPI 통합 완료**
  - [x] SpringDoc OpenAPI 2.7.0 의존성 추가
  - [x] OpenApiConfig.java 공통 설정
  - [x] application.yml Swagger UI 설정
  - [x] API 문서 자동 생성 (/swagger-ui.html, /api-docs)
- [x] **FileAsset 기반 설계 완료**
  - [x] FileAsset Entity 설계
  - [x] FileAssetRepository 설계 (13개 쿼리 메서드)
  - [x] FileCategory, FileStatus, ReferenceType Enum
  - [x] file_assets DDL 스크립트 (8개 인덱스)

---

## 학습 성과

### 기술적 이해

1. **2-Layer 아키텍처 (DICOM)**
   - DICOM Storage Layer (원본 보존) vs Application Domain Layer (비즈니스 로직)
   - WORM 정책 (Write Once Read Many) - DICOM 원본 절대 수정 금지
   - JSON metadata 저장 (dcm4che로 추출)
   - 간접 참조 패턴 (Option B) - studyInstanceUid 비즈니스 키
   - 무조건 업로드 허용 설계 - nullable 제약 최소화

2. **Issuer of PatientID 중요성**
   - DICOM 표준 (0010,0021) - 환자 ID 발급 기관
   - 다중 병원 환경 필수 (같은 PatientID, 다른 병원 = 다른 환자)
   - Unique Constraint: tenant + patientId + issuer
   - EMR 환자 매핑 전략 (matchingConfidence, matchingStatus)

3. **JavaBeans 명명 규칙**
   - Boolean 필드: `private Boolean immutable` (is 접두사 없이)
   - Lombok getter: `isImmutable()` (자동 생성)
   - Column 이름: `is_immutable` (DB 컬럼명)

4. **Spring Data JPA Query Methods**
   - Method Naming Convention 활용
   - OrderBy, Between, Count 등 다양한 쿼리 패턴
   - findBy, countBy, existsBy 등 접두사

5. **ValidationLog 설계**
   - DICOM 파일 검증 이력 추적
   - Enum을 활용한 타입 안전성 (ValidationType, ValidationResult)
   - instance_id NULL 허용 (검증 실패 시)

6. **@Transactional 활용**
   - readOnly=true: 조회 메서드 성능 최적화 (Flush 생략, DB 읽기 전용)
   - write: CUD 메서드 (트랜잭션 커밋 필요)
   - 메서드 레벨 어노테이션으로 세밀한 제어

7. **findOrCreate 패턴**
   - DICOM C-STORE Ingest Pipeline 핵심 패턴
   - UID 기반 조회 → 없으면 생성 → 중복 방지
   - Identity Resolution: 환자 자동 매칭 (DICOM PatientID + Issuer)
   - 멱등성(Idempotency) 보장

8. **Service Layer 예외 처리 전략**
   - ResourceNotFoundException: 엔티티 미존재 (404)
   - IllegalArgumentException: 비즈니스 규칙 위반 (400)
   - 조회 메서드: orElseThrow() 패턴
   - 중복 검증: findBy → ifPresent → throw

9. **Service 의존성 주입**
   - @RequiredArgsConstructor: Lombok 생성자 자동 생성
   - final 필드: 불변성 보장
   - 순환 참조 주의: PatientService ← StudyService ← SeriesService ← InstanceService (단방향)

10. **Java `-parameters` 컴파일러 플래그**
    - Spring @PathVariable 파라미터명 인식에 필수
    - Java 컴파일러는 기본적으로 메서드 파라미터 이름을 바이트코드에 포함하지 않음
    - build.gradle: `tasks.withType(JavaCompile).configureEach { options.compilerArgs += ['-parameters'] }`
    - 런타임 Reflection API로 파라미터명 조회 가능하게 함

11. **Jackson 역직렬화 요구사항**
    - DTO에 기본 생성자 필수: `@NoArgsConstructor`
    - 모든 필드 생성자도 권장: `@AllArgsConstructor`
    - `@Builder`만으로는 Jackson 역직렬화 불가능
    - 인터페이스 타입 필드는 역직렬화 불가: `@JsonIgnore` 필요
    - HTTP 응답 본문을 DTO 객체로 변환 시 필수

12. **API 응답 코드 체계 이해**
    - 7자리 코드 시스템: 200000 (성공), 400001 (클라이언트 에러), 500001 (서버 에러)
    - HTTP 상태 코드(200)와 API 코드(200000)는 다름
    - isSuccess() 메서드: `code >= 200000 && code < 300000` (200-299 아님!)
    - Service-Module-Seq 구조 유지

13. **통합 테스트 vs 단위 테스트 레이어 차이**
    - 단위 테스트(Service): Mock 사용, HTTP 레이어 거치지 않음 → Spring 설정 무관
    - 통합 테스트(Controller): 실제 HTTP 요청/응답 → Spring 설정, Jackson, 컴파일러 플래그 모두 영향
    - "간단한 CRUD"도 통합 테스트에서는 다양한 인프라 이슈 발생 가능
    - RestTestClient: JSON 직렬화/역직렬화 전 과정 검증

14. **Enum 설계 일관성**
    - 도메인별 명확한 네이밍 (Dicom prefix)
    - Enum 재사용성 - ValidationLevel을 ValidationLog 내부 enum에서 독립 파일로 분리

15. **FileAsset 다형성 연관**
    - Polymorphic Association (referenceType + referenceId)
    - TTL 관리 설계 - expiresAt 필드로 자동 만료 지원
    - Storage Tiering 전략 - HOT/WARM/COLD 계층 기반 비용 최적화
    - BaseEntity 상속 주의사항 - id 필드 중복 선언 시 composite ID 오류 발생

### 트러블슈팅 경험

1. **통합 테스트 13개 동시 실패 원인 분석**
   - 증상: "간단한 CRUD" 테스트가 실패 (Service 레이어는 통과)
   - 근본 원인 1: Java `-parameters` 플래그 누락 (10개 실패)
   - 근본 원인 2: Jackson 역직렬화 설정 누락 (3개 실패)
   - 해결 방법: 계층별 분석 → 유사 에러 그룹핑 → 근본 원인 2개 식별 → 각각 해결
   - 학습: 통합 테스트는 단위 테스트와 다른 레이어(HTTP, JSON, Spring 설정) 문제 발견

2. **PropertyReferenceException 연쇄 에러**
   - Application Context 로딩 실패 → 3개 Repository 에러
   - DicomMetadataRecord: sopInstanceUid, seriesInstanceUid, instanceId 필드 누락
   - ValidationLogRepository: 메서드명 vs 엔티티 필드명 불일치
   - 해결: 엔티티 필드 추가 + Repository 메서드명 수정

3. **isSuccess() 로직 버그 발견**
   - 증상: 성공 응답(200000)에 대해 isSuccess() = false 반환
   - 원인: HTTP 상태 코드(200-299)와 API 코드(200000-299999) 혼동
   - 해결: 범위 체크 수정 (`>= 200 && < 300` → `>= 200000 && < 300000`)
   - 학습: 7자리 API 코드 체계 정확히 이해 필요

4. **근본 원인 분석(Root Cause Analysis) 방법론**
   - 증상 vs 원인 구분: 테스트 실패는 증상, 컴파일러 설정 누락이 원인
   - 계층별 분석: HTTP → Jackson → Spring → JPA → Business Logic
   - 유사 에러 그룹핑: 10개 @PathVariable 실패 → 1개 근본 원인
   - Explore agent 활용: 다양한 파일 검색, 패턴 발견

---

## 구현 파일 목록

### Entity (6개)
- `Patient.java` - 환자 엔티티
- `Study.java` - 검사 엔티티
- `Series.java` - 시리즈 엔티티
- `Instance.java` - 인스턴스 엔티티
- `DicomMetadataRecord.java` - DICOM 메타데이터
- `ValidationLog.java` - 검증 로그
- `FileAsset.java` - 파일 자산 (Week 4)

### Repository (6개)
- `PatientRepository.java`
- `StudyRepository.java`
- `SeriesRepository.java`
- `InstanceRepository.java`
- `DicomMetadataRecordRepository.java`
- `ValidationLogRepository.java`
- `FileAssetRepository.java` (Week 4)

### Service (4개)
- `PatientService.java`
- `StudyService.java`
- `SeriesService.java`
- `InstanceService.java`

### Controller (1개)
- `PatientController.java`

### DTO (3개)
- `CreatePatientRequest.java`
- `UpdatePatientRequest.java`
- `PatientResponse.java`

### Enum (12개)
- `ValidationType.java` (Week 3)
- `ValidationResult.java` (Week 3)
- `DicomValidationLevel.java` (Week 4)
- `DicomValidationCategory.java` (Week 4)
- `DicomValidationStatus.java` (Week 4)
- `FileCategory.java` (Week 4)
- `FileStatus.java` (Week 4)
- `ReferenceType.java` (Week 4)

### Infrastructure (3개)
- `TenantProvider.java`
- `DefaultTenantProvider.java`
- `TenantEntityListener.java`

### Config (1개)
- `OpenApiConfig.java` - Swagger/OpenAPI 설정

### Test (1개)
- `PatientControllerTest.java` - 31개 통합 테스트

---

## 다음 단계 (Week 4-5)

### 목표
- SeaweedFS S3 API 연동
- DicomStorageService 구현
- DicomUploadController API 구현
- DicomMetadataExtractor 구현 (dcm4che)
- FileAsset API 구현

### 우선순위 작업 (P0)
- [ ] Docker Compose Filer 추가 (S3 API 포트 10405)
- [ ] AWS SDK 의존성 추가 (s3:2.20.26, s3-presigner:2.20.26)
- [ ] SeaweedFsS3Config 구현 (S3Client, S3Presigner Bean)
- [ ] DicomStorageService S3 API 구현
- [ ] DicomUploadController API 구현

---

## 참조 문서

| 문서 | 역할 |
|-----|------|
| [07_최종_구현_계획.md](../../core/07_최종_구현_계획.md) | 전체 8주 구현 계획 |
| [CURRENT_CONTEXT.md](../CURRENT_CONTEXT.md) | Claude 컨텍스트 복원 |
| [10_JPA_MyBatis_혼용_전략.md](../../guides/10_JPA_MyBatis_혼용_전략.md) | JPA 설계 가이드 |
| [12_멀티테넌시_설계_가이드.md](../../guides/12_멀티테넌시_설계_가이드.md) | 멀티테넌시 패턴 |

---

*보관일: 2025-12-31*
