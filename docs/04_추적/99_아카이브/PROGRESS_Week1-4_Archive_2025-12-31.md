# SADO-BE 진행 상황 대시보드

> **전체 진행률 추적**

---

## 🚨 우선순위 재조정 (2025-12-30) ⭐ 최신

**MiniPACS POC를 Week 8까지 완성합니다 (Pure PACS 기능만).**

### 변경 사항
- **Week 1-8**: MiniPACS 핵심 기능 100% 집중 ⭐ (수정: Week 10 → 8)
- **Triton AI, Temporal**: Week 9+ 이후로 연기 ⭐ (추가)
- **Week 8 목표**: DICOM 업로드/저장/조회/뷰어 (Pure PACS)

### 제외된 기능 (Week 9+ 이후)
- ❌ Triton Inference Server (AI 분석)
- ❌ Temporal Workflow (장기 실행 워크플로우)
- ❌ AI 결과 표시

### 상세 계획
- **최신 계획서**: `C:\Users\amagr\.claude\plans\nested-wibbling-dragon.md` ⭐ 신규
- **기존 계획**: `C:\Users\amagr\.claude\plans\resilient-watching-stroustrup.md` (Week 10 계획, 참고용)

---

## 현재 상태

| 항목 | 값 |
|------|-----|
| **현재 Week** | 3-4 완료 (Domain Layer 100%) ⭐ |
| **현재 Phase** | Phase 2 완료, Phase 3 (Storage Layer) 준비 중 |
| **전체 진행률** | 50% (Week 4/8) ⭐ |
| **최종 목표** | **Week 8 MiniPACS POC 완성** ⭐ |
| **최종 업데이트** | 2025-12-30 ⭐ |

---

## Phase 별 진행 상황 (8주 계획) ⭐ **재조정**

### Phase 1: 기반 구축 (Week 1-2) ✅ **완료**
**진행률**: 100%

| Week | 목표 | 상태 | 완료일 |
|------|------|------|--------|
| Week 1-2 | Gradle 멀티모듈, Docker 환경, 공통 모듈 | [x] 완료 | 2025-12-26 |

### Phase 2: Domain Layer (Week 3-4) ✅ **완료**
**진행률**: 100%

| Week | 목표 | 상태 | 완료일 |
|------|------|------|--------|
| Week 3-4 | Entity + Repository + Service + Controller + 통합 테스트 | [x] 완료 | 2025-12-30 |

### Phase 3: Storage Layer + FileAsset API (Week 4-6) ⏳ **진행 예정**
**진행률**: 0%

**Week 4-5: SeaweedFS S3 API 연동** ⭐ 설계 변경 (2025-12-30)

> **⚠️ 설계 변경**: LocalFileStorageClient (POC) → SeaweedFS S3 API (Week 4-5부터)
>
> **변경 이유**:
> - DICOMweb 표준 준수 (계층적 경로)
> - OHIF Viewer S3 API 호환성
> - AI 서비스 (sado_ai) 표준 S3 SDK 사용
> - Pre-signed URL 지원 (시간 제한 보안 URL)
>
> **참조**: `sado_docs/seaweedfs/10_MiniPACS_연동_가이드.md` Line 132-710

| 작업 | 상태 | 우선순위 | 설명 |
|------|------|----------|------|
| Docker Compose Filer 추가 | [x] 완료 | P0 ⭐⭐⭐ | Master + Volume + **Filer** (S3 API 포트 10405) ⭐ 필수! |
| AWS SDK 의존성 추가 | [x] 완료 | P0 ⭐⭐⭐ | s3:2.20.26, s3-presigner:2.20.26 |
| SeaweedFsS3Config 구현 | [x] 완료 | P0 ⭐⭐⭐ | S3Client, S3Presigner Bean 생성 (pathStyleAccessEnabled=true) |
| DicomStorageService (S3 API) | [ ] 대기 | P0 ⭐⭐ | uploadDicomFile, downloadDicomFile, getPresignedUrl, deleteDicomFile |
| DicomUploadController API | [ ] 대기 | P0 ⭐⭐ | POST /api/v1/dicom/upload (KingArthur 통합 진입점) |

**Week 5-6: DICOM Processing + FileAsset API**
| 작업 | 상태 | 우선순위 | 설명 |
|------|------|----------|------|
| dcm4che 의존성 추가 | [ ] 대기 | P1 | dcm4che 5.29.1, gradle/libs.versions.toml 업데이트 |
| DicomMetadataExtractor 구현 | [ ] 대기 | P1 ⭐ | dcm4che 파싱, JSON 변환, DicomMetadataRecord 저장 |
| FileAssetController API | [ ] 대기 | P1 ⭐⭐ | POST/GET/DELETE /api/v1/files/* (KingArthur AI 결과 저장용) |
| FileAssetService 구현 | [ ] 대기 | P1 | 카테고리별 처리, TTL 관리, Storage Tiering |
| 3-Level 검증 파이프라인 | [ ] 대기 | P2 | Level 1 (Critical), Level 2 (Warning), Level 3 (AI Readiness) |

### Phase 4: DICOMWeb APIs (Week 6-8) ⏳ **진행 예정**
**진행률**: 0%

| Week | 목표 | 상태 | 완료일 |
|------|------|------|--------|
| Week 6-7 | QIDO-RS, WADO-RS, STOW-RS 구현 | [ ] 대기 | - |
| Week 8 | E2E 테스트, 성능 최적화, 문서화 | [ ] 대기 | - |

### 🔮 추후 작업 (Week 9+ 이후)

**Week 9-10: AI 분석 연동 (선택)**
- [ ] Triton Inference Server 연동
- [ ] Temporal Workflow 구현
- [ ] Frontend AI 결과 표시

**Week 11-16: MSA 고도화 (선택)**
- [ ] Spring Cloud Gateway
- [ ] Kafka 도메인 이벤트
- [ ] Keycloak 인증
- [ ] Redis 분산락
- [ ] Prometheus 모니터링

---

## 🔗 KingArthur 통합 로드맵 ⭐ NEW (2025-12-30)

### 개요

**KingArthur 프로젝트**: `C:\Users\amagr\ontact\sonix\be\sonix_kingarthur`

**역할**: 프로덕션급 의료 영상 시스템 (참조용)

**MiniPACS의 새로운 역할**: Storage-as-a-Service (중앙 집중식 DICOM + 파일 관리)

### 서빙 대상 (Multi-Client)

- **KingArthur Backend** - DICOM 업로드, AI 결과 저장, 리포트 PDF 저장
- **Frontend Viewer** - DICOM 조회, 이미지 렌더링
- **AI 서버** - DICOM 다운로드, 분석 수행, 결과 업로드

### Phase 0: MiniPACS POC 완성 (Week 1-8) ⏳ **진행 중**
**목표**: 독립 실행 가능한 PACS 시스템 + FileAsset API

| 작업 | 상태 | 완료일 |
|------|------|--------|
| Domain Layer (Entity/Repository/Service/Controller) | [x] 완료 | 2025-12-30 |
| Storage Layer (SeaweedFS S3 API + DicomStorageService) ⭐ 설계 변경 | [ ] 대기 | - |
| DICOM Processing (dcm4che + Metadata Extractor) | [ ] 대기 | - |
| FileAsset API (부수 파일 관리) | [ ] 대기 | - |
| DICOMWeb APIs (QIDO-RS, WADO-RS, STOW-RS) | [ ] 대기 | - |
| Frontend Viewer (Cornerstone3D) | [ ] 대기 | - |

**Deliverable**: DICOMWeb API + FileAsset API + Frontend DICOM 뷰어

---

### Phase 1: API 호환성 레이어 (Week 9-10) 🔮 **선택**
**목표**: KingArthur에 MiniPacsClient 추가 (Adapter Pattern)

| 작업 | 상태 | 설명 |
|------|------|------|
| MiniPacsClient 구현 (KingArthur) | [ ] 대기 | WebClient 기반, MiniPACS API 호출 |
| MiniPacsDicomStorageServiceImpl | [ ] 대기 | DicomStorageService 인터페이스 구현 |
| FileAssetClient 구현 | [ ] 대기 | 부수 파일 업로드/다운로드 |
| 설정 기반 전환 | [ ] 대기 | storage.provider = minipacs (application.yml) |

**장점**: 0줄 비즈니스 로직 변경, 설정만으로 전환

---

### Phase 2: 데이터 마이그레이션 (Week 11-12) 🔮 **선택**
**전략**: Lazy Migration (접근 시 마이그레이션)

| 작업 | 상태 | 설명 |
|------|------|------|
| Migration 매핑 테이블 생성 | [ ] 대기 | Legacy Path → MiniPACS SOP Instance UID |
| Lazy Migration 로직 구현 | [ ] 대기 | 접근 시 자동 마이그레이션 |
| Checksum 검증 | [ ] 대기 | Legacy vs MiniPACS 데이터 무결성 확인 |

**장점**: 다운타임 없음, 점진적 마이그레이션

---

### Phase 3: Dual-Write Period (Week 12-14) 🔮 **선택**
**목표**: Legacy + MiniPACS 동시 쓰기 (안전성 확보)

| 작업 | 상태 | 설명 |
|------|------|------|
| Dual-Write 구현 | [ ] 대기 | Legacy와 MiniPACS 동시 저장 |
| Checksum 비교 | [ ] 대기 | 두 결과 검증 |
| 14일 검증 기간 | [ ] 대기 | 데이터 무결성 확인 |

**기간**: 14일 검증 기간

---

### Phase 4: MiniPACS Primary (Week 15-16) 🔮 **선택**
**목표**: MiniPACS가 유일한 Source of Truth

| 작업 | 상태 | 설명 |
|------|------|------|
| Dual-Write 종료 | [ ] 대기 | MiniPACS만 사용 |
| Legacy 아카이브 | [ ] 대기 | S3 Glacier 이동 |
| Legacy 완전 제거 | [ ] 대기 | 90일 후 삭제 |

---

### KingArthur 참조 패턴

| 패턴 | 설명 | MiniPACS 적용 시기 |
|------|------|-------------------|
| **Storage Abstraction** | SeaweedFS S3 API (Filer 필수) ⭐ 설계 변경 | Week 4-5 ⭐ 필수 |
| **Command Pattern** | DICOM 업로드 파이프라인 | Week 5-6 |
| **Distributed Locking** | Redisson 중복 방지 | Week 5-6 |
| **FileAsset 관리** | AI 결과, 리포트, 썸네일 | Week 5-6 ⭐ 필수 |
| **Event-Driven (Redis)** | Ingest Queue | Week 7 (선택) |

---

## Week 3-4 코드 품질 개선 및 FileAsset 설계 (2025-12-29 추가 완료)

### 개선 사항
- [x] **DicomMetadataRecord fileSize 필드 추가** - 파일 크기 추적 (스토리지 관리 및 tiering 계산용)
- [x] **Enum 리팩토링** - DICOM 도메인 명확화
  - DicomValidationLevel (LEVEL_1, LEVEL_2, LEVEL_3) - ValidationLog에서 분리하여 재사용 가능하게
  - DicomValidationCategory (DICOM_CONFORMANCE, MANDATORY_TAGS, DATA_INTEGRITY, DUPLICATE_CHECK) - ValidationType 개선
  - DicomValidationStatus (SUCCESS, WARNING, ERROR) - ValidationResult 개선
- [x] **Swagger/OpenAPI 통합 완료 (Week 3-4)** ⭐ NEW (2025-12-30)
  - SpringDoc OpenAPI 2.7.0 의존성 추가 (libs.versions.toml)
  - OpenApiConfig.java 공통 설정 (sado-common 모듈)
  - application.yml Swagger UI 설정 (Gateway + MiniPACS)
  - API 문서 자동 생성 (/swagger-ui.html, /api-docs)
  - ApiResponse 스키마 공통 정의
- [x] **FileAsset 기반 설계 완료 (Week 4)** ⭐ POC 우선순위
  - FileAsset Entity 설계 (모든 필드 포함)
  - FileAssetRepository 설계 (13개 쿼리 메서드)
  - FileCategory Enum (AI_RESULT, CLINICAL_DOC, SYSTEM, EXPORT)
  - FileStatus Enum (ACTIVE, EXPIRED, DELETED, ARCHIVED)
  - ReferenceType Enum (INSTANCE, STUDY, SERIES, PATIENT, AI_ANALYSIS)
  - file_assets DDL 스크립트 (8개 인덱스)

---

## Week 4-5 코드 점검 및 개선 (2025-12-31) ⭐⭐⭐ **최신**

### 작업 목적
**MiniPACS POC 코드 전체 점검 및 품질 개선** - 빌드 안정성 확보, 아키텍처 일관성 유지, 프로덕션 준비 기반 마련

### 개선 사항 (5대 문제점 해결)

#### 1. DCM4CHE 의존성 해결 실패 수정 ✅
- [x] **근본 원인 발견** - DCM4CHE는 Maven Central에 없음, 자체 저장소 사용 (https://maven.dcm4che.org/)
- [x] **root build.gradle 수정** - DCM4CHE 저장소 추가, `content { includeGroup 'org.dcm4che' }` 보안 설정
- [x] **버전 다운그레이드** - 5.34.1 → 5.29.1 (mini-pacs-poc 검증 버전, 안정성 보장)
- [x] **빌드 성공** - `./gradlew :sado-minipacs:build` → BUILD SUCCESSFUL in 19s
- **학습**: Supply Chain Attack 방어 (includeGroup), 자체 Maven 저장소 관리

#### 2. 패키지 구조 개선 ✅
- [x] **storage/service 패키지 생성** - DicomStorageService 이동 (인프라 서비스 분리)
- [x] **exception 패키지 생성** - MiniPacsErrorCode 이동 (예외 관련 통합)
- [x] **domain/parser 패키지 생성** - DicomMetadataExtractor 생성 (DICOM 메타데이터 파싱)
- [x] **InstanceController import 수정** - 새로운 패키지 경로 반영
- **결과**: 관심사 분리 완료, 계층형 아키텍처 명확화

#### 3. Patient.Builder 검증 모순 수정 ✅
- [x] **문제 발견** - Builder.build()에서 dicomPatientId 필수 체크, but 엔티티는 nullable 허용
- [x] **해결** - Builder 검증 제거, 주석 명확화 ("연구용 DICOM은 PatientID 없을 수 있음")
- [x] **파일** - Patient.java Line 274-276
- **학습**: DICOM 표준 이해, nullable 정책 일관성

#### 4. Study/Series 주석과 구현 불일치 수정 ✅
- [x] **문제 발견** - 주석: "nullable: Study 생성 후 DICOM 업로드 가능", Builder: required
- [x] **해결** - 주석 수정 ("DICOM 업로드 시 자동 추출 (필수)"), `@Column(nullable = false)` 추가
- [x] **파일** - Study.java Line 31-34, Series.java Line 29-32
- **결과**: 문서와 코드 일치, find-or-create 패턴에서 UID 항상 필수 명시

#### 5. Service 레이어 Builder 패턴 일관성 개선 ✅
- [x] **문제 발견** - 모든 find-or-create 메서드에서 직접 setter 사용, Builder 패턴 검증 로직 활용 불가
- [x] **PatientService.findOrCreatePatient()** - Builder 패턴 적용 (Line 107-113)
- [x] **StudyService.findOrCreateStudy()** - Builder 패턴 적용 (Line 127-132)
- [x] **SeriesService.findOrCreateSeries()** - Builder 패턴 적용 (Line 149-156)
- [x] **InstanceService.findOrCreateInstance()** - Builder 패턴 적용 (Line 174-181)
- [x] **InstanceController.uploadDicom()** - Builder 패턴 적용 (Line 287-297)
- **결과**: 코드 가독성 향상, 필수 필드 검증, 일관된 엔티티 생성 패턴

### 수정/생성 파일 (11개)

**Gradle 설정**:
1. `build.gradle` (root) - DCM4CHE 저장소 추가
2. `sado-minipacs/build.gradle` - 버전 5.29.1 변경

**Entity 클래스**:
3. `Patient.java` - Builder 검증 제거
4. `Study.java` - 주석 + nullable=false 추가
5. `Series.java` - 주석 + nullable=false 추가

**Service 클래스**:
6. `PatientService.java` - Builder 패턴 적용
7. `StudyService.java` - Builder 패턴 적용
8. `SeriesService.java` - Builder 패턴 적용
9. `InstanceService.java` - Builder 패턴 적용

**Controller 클래스**:
10. `InstanceController.java` - Builder 패턴 적용, import 수정

**신규 생성**:
11. `DicomMetadataExtractor.java` - DICOM 메타데이터 파싱 (131 lines, DCM4CHE 사용)

**이동된 파일**:
- `DicomStorageService.java` (domain.service → storage.service)
- `MiniPacsErrorCode.java` (code → exception)

### 최종 패키지 구조
```
com.hanumoka.sado.minipacs
│
├── controller/
├── domain/
│   ├── entity/
│   ├── enums/
│   ├── repository/
│   ├── service/         (도메인 서비스만)
│   ├── parser/          ✅ NEW (DICOM 메타데이터 파싱)
│   └── util/
├── dto/
├── storage/
│   ├── dto/
│   ├── strategy/
│   └── service/         ✅ NEW (인프라 서비스)
├── infrastructure/
├── exception/           ✅ NEW (에러 코드)
└── MiniPacsApplication.java
```

### 코드 품질 지표

| 항목 | Before | After | 개선 효과 |
|------|--------|-------|-----------|
| **Builder 패턴 사용** | 0/5 | 5/5 | 필수 필드 검증, 코드 가독성 |
| **패키지 구조** | 혼재 | 계층 분리 | 관심사 분리, 유지보수성 |
| **DCM4CHE 의존성** | 빌드 실패 | 정상 | 안정성 |
| **엔티티 주석** | 불일치 | 일치 | 문서 정확성 |
| **예외 처리** | 분산 | 통합 (exception/) | 일관성 |

### 빌드 검증
```bash
$ ./gradlew :sado-minipacs:build

BUILD SUCCESSFUL in 19s
8 actionable tasks: 4 executed, 4 up-to-date
```
- ✅ Spring Boot Application 시작 성공
- ✅ Hibernate DDL 모든 엔티티 테이블 생성 성공
- ✅ 테스트 통과율 100%

---

### Phase 7: MSA 고도화 (Week 10-16) ⚠️ **선택 사항**
**진행률**: 0%

| Week | 목표 | 상태 | 완료일 |
|------|------|------|--------|
| Week 10-12 | 통합 테스트, 버그 수정 (버퍼 기간) | [ ] 대기 | - |
| Week 12-14 | Gateway, Kafka 연동 (기존 계획 재개) | [ ] 대기 | - |
| Week 14-16 | AI 연동, Temporal, 고급 기능 | [ ] 대기 | - |

---

## 마일스톤 ⭐ **조정됨**

| 마일스톤 | 목표일 | 상태 | 달성일 |
|---------|-------|------|--------|
| **M1** MiniPACS 독립 빌드 | Week 2 | [ ] 대기 | - |
| **M2** DICOM 업로드 API 동작 | Week 6 | [ ] 대기 | - |
| **M3** DICOMWeb APIs 완성 | Week 8 | [ ] 대기 | - |
| **M4** Frontend 뷰어 동작 | Week 10 | [ ] 대기 | - |
| **M5** Standalone PACS 완성 | Week 10 | [ ] 대기 | - |
| **M6** MSA 고도화 (선택) | Week 16 | [ ] 대기 | - |

---

## Week 1-2 상세 체크리스트 (80% 완료)

### 학습 목표
- [x] Gradle 멀티모듈 프로젝트 구조 이해
- [x] kingarthur-app 공통 모듈 패턴 분석
- [x] Version Catalog 사용법 학습
- [x] REST API 기본 동작 이해
- [x] GlobalExceptionHandler 동작 원리 이해
- [x] Docker Compose 네트워크 설정 이해
- [x] Spring Boot 4.0 새 기능 파악 (Hibernate 6.x)

### 실습 목표

#### 1. Gradle 멀티모듈 구조
- [x] `sado-common` 모듈 생성
- [x] `sado-gateway` 모듈 생성
- [x] `settings.gradle` 구성
- [x] Version Catalog (`gradle/libs.versions.toml`) 설정

#### 2. sado-common 핵심 클래스 구현 ✅
- [x] **ApiCode 인터페이스**
  - [x] `name()`, `getHttpStatus()`, `getMessage()`, `getCode()` 메서드 정의
- [x] **CommonCode enum (7자리 코드 체계)**
  - [x] SUCCESS (100000)
  - [x] INVALID_PARAMETER (1000001)
  - [x] RESOURCE_NOT_FOUND (1000002)
  - [x] INTERNAL_SERVER_ERROR (1000003)
- [x] **BusinessException**
  - [x] ApiCode 필드
  - [x] 2개 생성자
- [x] **ResourceNotFoundException**
  - [x] BusinessException 상속
- [x] **ApiResponse (Generic, Builder 패턴)**
  - [x] Generic 타입 `<T>` 지원
  - [x] `@Builder` 패턴
  - [x] `isSuccess()` 메서드 (HttpStatus 기반)
  - [x] 팩토리 메서드 (success, of)
- [x] **GlobalExceptionHandler**
  - [x] `@RestControllerAdvice`
  - [x] BusinessException 처리
  - [x] MethodArgumentNotValidException 처리
  - [x] Exception 처리

#### 3. sado-gateway 테스트 ✅
- [x] **TestController 작성**
  - [x] GET `/api/test/success` → 200 OK
  - [x] GET `/api/test/error` → 404 NOT_FOUND
- [x] **REST API 동작 확인**
  - [x] ApiResponse 형식 검증
  - [x] GlobalExceptionHandler 동작 확인
  - [x] HTTP 상태 코드 검증

#### 4. Docker Compose 환경 구축 ✅
- [x] **docker-compose.yml 작성**
  - [x] MySQL 8.0 컨테이너
  - [x] Kafka 7.5.0 컨테이너
  - [x] Zookeeper 7.5.0 컨테이너
  - [x] 네트워크 설정 (sado-network)
  - [x] 볼륨 설정 (mysql-data)
- [x] **컨테이너 동작 확인**
  - [x] MySQL 연결 테스트
  - [x] Kafka 토픽 생성 테스트
  - [x] Kafka Producer/Consumer 테스트

#### 5. application.yml 설정 ✅
- [x] **Datasource 설정**
  - [x] JDBC URL (MySQL 8.0)
  - [x] username/password
  - [x] driver-class-name
- [x] **JPA 설정**
  - [x] hibernate.ddl-auto
  - [x] hibernate.format_sql
  - [x] Dialect 자동 감지 (Spring Boot 4.0)
- [x] **Kafka 설정**
  - [x] bootstrap-servers
  - [x] consumer 설정
  - [x] producer 설정

#### 6. 의존성 추가 ✅
- [x] **sado-gateway/build.gradle**
  - [x] spring-boot-starter-data-jpa
  - [x] mysql-connector-java
- [x] **gradle/libs.versions.toml**
  - [x] mysql-connector 버전 정의
  - [x] 라이브러리 별칭 추가

#### 7. 빌드 및 서버 실행 ✅
- [x] sado-common 빌드 성공
- [x] sado-gateway 빌드 성공
- [x] 서버 실행 성공 (포트 8080)
- [x] 에러 없이 정상 동작 확인

### 블로그 작성
- [x] "Gradle 멀티모듈 프로젝트 - 초보자 가이드"
- [x] "API 응답 표준화 - ApiResponse 패턴"
- [ ] "Docker Compose로 개발 환경 구축하기" (선택)

---

## Week 3-4 상세 체크리스트 (100% 완료) ⭐ NEW

### 학습 목표
- [x] 2-Layer 아키텍처 이해 (Storage Layer vs Application Layer)
- [x] DICOM 원본 보존 정책 이해 (WORM)
- [x] Option A vs Option B 관계 설계 비교
- [x] Issuer of PatientID 필요성 이해
- [x] EMR 환자 매핑 전략 이해
- [x] JPA 연관 관계 매핑 (@OneToMany, @ManyToOne)
- [x] Spring Data JPA Query Methods
- [x] ValidationLog 역할 이해 (검증 이력 추적)
- [x] @Transactional 활용 (readOnly, write 분리) ⭐ NEW
- [x] findOrCreate 패턴 (DICOM Ingest Pipeline용) ⭐ NEW
- [x] Service Layer 예외 처리 전략 ⭐ NEW

### 실습 목표

#### 1. DICOM Entity 설계 (100% 완료) ✅
- [x] **DicomMetadataRecord Entity** ✅
  - [x] DICOM Storage Layer 역할 이해
  - [x] WORM 정책 적용 (immutable)
  - [x] nullable 제약 최소화 (무조건 업로드 허용)
  - [x] JSON metadata 저장 (원본 보존)
  - [x] fileHash, studyInstanceUid, filePath, filename 필드
- [x] **Study-DicomMetadataRecord 관계 설계** ✅
  - [x] Option A vs Option B 비교 분석
  - [x] Option B (간접 참조) 선택
  - [x] studyInstanceUid 비즈니스 키로 조회
- [x] **Study Entity 수정** ✅
  - [x] Application Domain Layer 역할 명확화
  - [x] metadata JSON 필드 제거 (Storage Layer 분리)
  - [x] patientId String 제거 (Patient 관계 준비)
  - [x] studyInstanceUid 간접 참조 유지

#### 2. JPA Repository 구현 (100% 완료) ✅
- [x] **PatientRepository** ✅
  - [x] findByDicomPatientIdAndIssuerOfPatientId
  - [x] findByEmrPatientId
- [x] **StudyRepository** ✅
  - [x] findByStudyInstanceUid
  - [x] findByPatientIdOrderByStudyDateDesc
  - [x] countByPatientId
- [x] **SeriesRepository** ✅
  - [x] findBySeriesInstanceUid
  - [x] findByStudyIdOrderBySeriesNumber
  - [x] findByStudyIdAndModality
  - [x] countByStudyId
- [x] **InstanceRepository** ✅
  - [x] findBySopInstanceUid
  - [x] findBySeriesIdOrderByInstanceNumber
  - [x] findBySeriesIdAndInstanceNumber
  - [x] countBySeriesId
  - [x] findByFilePath
- [x] **DicomMetadataRecordRepository** ✅
  - [x] findByInstanceId (1:1 관계)
  - [x] findBySopInstanceUid
  - [x] findByStudyInstanceUid
  - [x] findBySeriesInstanceUid
- [x] **ValidationLogRepository** ✅
  - [x] findByInstanceIdOrderByCreatedAtDesc
  - [x] findByValidationResult
  - [x] findByValidationTypeAndValidationResult
  - [x] findByCreatedAtBetween

#### 3. Service 계층 구현 (100% 완료) ✅ ⭐ NEW
- [x] **PatientService** ✅
  - [x] CRUD 기능 (create, findById, update, delete)
  - [x] findByDicomPatientId (DICOM PatientID + Issuer 조회)
  - [x] findByEmrPatientId (EMR 환자 ID 조회)
  - [x] findOrCreatePatient (Identity Resolution)
- [x] **StudyService** ✅
  - [x] CRUD 기능 (create, findById, update, delete)
  - [x] findByStudyInstanceUid (Study UID 조회)
  - [x] findByPatientId (환자별 검사 목록, 최신순)
  - [x] countByPatientId (환자의 검사 개수)
  - [x] findOrCreateStudy (DICOM C-STORE Ingest용)
- [x] **SeriesService** ✅
  - [x] CRUD 기능 (create, findById, update, delete)
  - [x] findBySeriesInstanceUid (Series UID 조회)
  - [x] findByStudyId (검사별 시리즈 목록, Series Number순)
  - [x] findByStudyIdAndModality (Modality 필터링)
  - [x] countByStudyId (검사의 시리즈 개수)
  - [x] findOrCreateSeries (DICOM C-STORE Ingest용)
- [x] **InstanceService** ✅
  - [x] CRUD 기능 (create, findById, update, delete)
  - [x] findBySopInstanceUid (SOP Instance UID 조회)
  - [x] findByFilePath (파일 경로 조회, 중복 방지)
  - [x] findBySeriesId (시리즈별 Instance 목록, Instance Number순)
  - [x] countBySeriesId (시리즈의 Instance 개수)
  - [x] findOrCreateInstance (DICOM C-STORE Ingest용)
  - [x] storagePath 중복 검증
- [x] **@Transactional 적용** ✅
  - [x] readOnly=true (조회 메서드)
  - [x] write (CUD 메서드)
- [x] **예외 처리** ✅
  - [x] ResourceNotFoundException (엔티티 미존재)
  - [x] IllegalArgumentException (비즈니스 규칙 위반)
- [x] **findOrCreate 패턴** ✅
  - [x] DICOM C-STORE 수신 시 중복 방지
  - [x] UID 기반 조회 → 없으면 생성

#### 4. Enum 타입 생성 (100% 완료) ✅
- [x] **ValidationType** ✅
  - [x] DICOM_CONFORMANCE
  - [x] MANDATORY_TAGS
  - [x] DATA_INTEGRITY
  - [x] DUPLICATE_CHECK
- [x] **ValidationResult** ✅
  - [x] SUCCESS
  - [x] WARNING
  - [x] ERROR

#### 5. TenantProvider 인프라 (100% 완료) ✅
- [x] **TenantProvider 인터페이스** ✅
- [x] **DefaultTenantProvider** ✅ (tenant_id=1 반환)
- [x] **TenantEntityListener** ✅ (@PrePersist 자동 주입)

#### 6. 빌드 검증 (100% 완료) ✅
- [x] sado-common 빌드 성공
- [x] sado-minipacs 빌드 성공
- [x] 전체 프로젝트 빌드 성공

#### 7. Controller 계층 구현 (100% 완료) ✅ ⭐ NEW (2025-12-29)
- [x] **PatientController 구현** ✅
  - [x] GET /api/patients/{id}
  - [x] GET /api/patients/dicom/{dicomPatientId}
  - [x] GET /api/patients/emr/{emrPatientId}
  - [x] GET /api/patients/{patientId}/studies
  - [x] POST /api/patients
  - [x] PUT /api/patients/{id}
  - [x] DELETE /api/patients/{id}
- [x] **DTO 클래스 작성** ✅
  - [x] CreatePatientRequest
  - [x] UpdatePatientRequest
  - [x] PatientResponse
  - [x] ApiResponse 래핑

#### 8. 통합 테스트 (100% 완료) ✅ ⭐ NEW (2025-12-29)
- [x] **PatientControllerTest** ✅
  - [x] POST 테스트 3개 (생성 성공, 필수 필드 누락, 중복 생성)
  - [x] GET 테스트 2개 (조회 성공, 존재하지 않는 ID)
  - [x] PUT 테스트 3개 (수정 성공, 존재하지 않는 ID, 필수 필드 누락)
  - [x] DELETE 테스트 3개 (삭제 성공, 존재하지 않는 ID, 관련 데이터 있을 때)
  - [x] 추가 조회 테스트 2개 (DICOM PatientID 조회, Studies 목록 조회)
- [x] **테스트 인프라 설정** ✅
  - [x] @SpringBootTest + @Transactional
  - [x] RestTestClient 설정
  - [x] H2 in-memory database
  - [x] TestFixtures 유틸리티
- [x] **전체 테스트 통과** ✅
  - [x] 31개 테스트 100% 통과

#### 9. 통합 테스트 트러블슈팅 (100% 완료) ✅ ⭐ NEW (2025-12-29)
- [x] **PropertyReferenceException 해결** ✅
  - [x] DicomMetadataRecord 필드 추가 (seriesInstanceUid, sopInstanceUid, instanceId)
  - [x] ValidationLogRepository 메서드명 수정
- [x] **Java `-parameters` 플래그 추가** ✅
  - [x] build.gradle 컴파일러 설정
  - [x] @PathVariable 파라미터명 인식 문제 해결
- [x] **Jackson 역직렬화 수정** ✅
  - [x] PatientResponse: @NoArgsConstructor, @AllArgsConstructor 추가
  - [x] ApiResponse: @JsonIgnore 추가
- [x] **isSuccess() 로직 수정** ✅
  - [x] API 코드 범위 수정 (200-299 → 200000-299999)
- [x] **PatientServiceTest Mock 수정** ✅
  - [x] findById() mock 추가

---

## Week 3 준비 사항 (다음 작업)

### 학습 목표
- [ ] JPA Entity 설계 기본
- [ ] 연관 관계 매핑 (@OneToMany, @ManyToOne)
- [ ] Audit 필드 (@CreatedDate, @LastModifiedDate)
- [ ] 멀티테넌시 패턴 (tenant_id)
- [ ] **추상화 레이어 패턴 (Adapter Pattern)** ⭐ NEW (2025-12-27)

### 실습 목표
- [x] **공통 Entity 설계 (sado-common)** ✅
  - [x] BaseEntity (id, createdAt, updatedAt) ✅
  - [x] TenantAwareEntity (tenant_id, Hibernate Filter) ✅
- [ ] **sado-minipacs 모듈 생성** ⭐ 신규
  - [ ] settings.gradle에 모듈 추가
  - [ ] build.gradle 생성
  - [ ] 디렉토리 구조 생성 (domain/entity, domain/enums, domain/repository)
- [ ] **DICOM 도메인 Entity 설계 (sado-minipacs)** ⭐ 모듈 명시
  - [ ] Study Entity (studyInstanceUid, patientId, JSON metadata, @OneToMany)
  - [ ] Series Entity (seriesInstanceUid, @ManyToOne, @OneToMany)
  - [ ] Instance Entity (sopInstanceUid, filePath, @ManyToOne)
- [ ] **의료 장비 인증 Entity 설계**
  - [ ] Device Entity (api_key, deviceType, status)
  - [ ] DeviceType Enum (CT, MRI, ULTRASOUND, XRAY)
  - [ ] DeviceStatus Enum (ACTIVE, INACTIVE, REVOKED)
- [ ] **JPA Repository 구현**
  - [ ] StudyRepository (findByStudyInstanceUid, findByPatientId)
  - [ ] SeriesRepository (findBySeriesInstanceUid)
  - [ ] InstanceRepository (findBySopInstanceUid)
  - [ ] DeviceRepository (findByApiKey, findByStatus)
- [ ] **Entity 테스트**
  - [ ] 멀티테넌시 격리 검증 (Hibernate Filter)
  - [ ] Device API Key 검증
  - [ ] 연관 관계 매핑 테스트 (Study-Series-Instance)

### 새로운 학습 내용 ⭐ NEW (2025-12-27)

**Infrastructure 추상화 레이어 설계**:
- Infrastructure 모듈의 역할 이해
- 인터페이스 기반 설계의 중요성 (Adapter Pattern)
- 외부 솔루션 교체 시나리오 학습
- Week 4-11에 걸쳐 점진적 적용 (Storage → Messaging → Workflow → Auth → Cache)

**참고 문서**:
- `docs/guides/16_Infrastructure_추상화_레이어_가이드.md` - 추상화 레이어 완전 가이드
- `docs/core/07_최종_구현_계획.md` - 업데이트된 Infrastructure 모듈 구조

---

## 학습 성과

### 기술적 이해
1. **Gradle 멀티모듈** - 모듈 간 의존성 관리 이해
2. **Version Catalog** - 의존성 버전 중앙 관리
3. **ApiCode 패턴** - 응답 코드 표준화 및 타입 안전성
4. **7자리 코드 체계** - Service-Module-Seq 구조 (10-00-001)
5. **Generic + Builder 패턴** - 재사용 가능한 ApiResponse 설계
6. **GlobalExceptionHandler** - 전역 예외 처리 및 응답 변환
7. **Docker Compose** - 컨테이너 네트워크 및 볼륨 관리
8. **Spring Boot 4.0** - Hibernate 6.x 변경사항 (Dialect 자동 감지)
9. **의료 장비 인증 (Device Authentication)** - M2M 인증의 3단계 접근법 (API Key → OAuth2 Client Credentials → mTLS)
10. **HIPAA 2025 규제** - MFA 필수 요구사항, ePHI 보호, 산업 표준 (Google Cloud Healthcare API, AWS HealthLake)
11. **OAuth2 Flow 비교** - Client Credentials (M2M) vs Device Authorization Flow
12. **단계적 마이그레이션 전략** - POC 단순화 + 프로덕션 준비 경로
13. **Infrastructure 추상화 레이어** ⭐ (2025-12-27)
    - Adapter Pattern (Hexagonal Architecture)
    - 외부 솔루션 종속성 최소화 전략
    - 인터페이스 기반 설계 (StorageClient, WorkflowEngine, EventPublisher, AuthProvider, CacheService)
    - @ConditionalOnProperty를 활용한 구현체 전환
    - 비즈니스 로직 변경 없이 외부 솔루션 교체 (Local→SeaweedFS, Kafka→RabbitMQ, Temporal→Simple)
14. **DB를 Infrastructure에 포함하지 않는 이유** ⭐ (2025-12-27)
    - Database per Service 패턴 (MSA 원칙)
    - 각 서비스 독립성 보장
    - 향후 DB 기술 변경 가능성 (MySQL → PostgreSQL)
    - 공통 DB 유틸리티는 Infrastructure 포함 가능 (TenantContextHolder, BaseRepository)
15. **인증/인가 아키텍처** ⭐ (2025-12-27)
    - Keycloak (외부 인증 서버) vs sado-auth (자체 구축) 선택
    - common-security (공통 라이브러리) 역할
    - OAuth2 Resource Server (각 서비스에서 JWT 재검증 - Defense in Depth)
    - sado-auth 서비스를 만들지 않는 이유: Keycloak과 역할 중복
16. **2-Layer 아키텍처 (DICOM)** ⭐ NEW (2025-12-27)
    - DICOM Storage Layer (원본 보존) vs Application Domain Layer (비즈니스 로직)
    - WORM 정책 (Write Once Read Many) - DICOM 원본 절대 수정 금지
    - JSON metadata 저장 (dcm4che로 추출)
    - 간접 참조 패턴 (Option B) - studyInstanceUid 비즈니스 키
    - 무조건 업로드 허용 설계 - nullable 제약 최소화
17. **Issuer of PatientID 중요성** ⭐ NEW (2025-12-27)
    - DICOM 표준 (0010,0021) - 환자 ID 발급 기관
    - 다중 병원 환경 필수 (같은 PatientID, 다른 병원 = 다른 환자)
    - Unique Constraint: tenant + patientId + issuer
    - EMR 환자 매핑 전략 (matchingConfidence, matchingStatus)
18. **JavaBeans 명명 규칙** ⭐ NEW (2025-12-27)
    - Boolean 필드: `private Boolean immutable` (is 접두사 없이)
    - Lombok getter: `isImmutable()` (자동 생성)
    - Column 이름: `is_immutable` (DB 컬럼명)
19. **Spring Data JPA Query Methods** ⭐ NEW (2025-12-28)
    - Method Naming Convention 활용
    - OrderBy, Between, Count 등 다양한 쿼리 패턴
    - findBy, countBy, existsBy 등 접두사
20. **ValidationLog 설계** ⭐ NEW (2025-12-28)
    - DICOM 파일 검증 이력 추적
    - Enum을 활용한 타입 안전성 (ValidationType, ValidationResult)
    - instance_id NULL 허용 (검증 실패 시)
21. **@Transactional 활용** ⭐ NEW (2025-12-28)
    - readOnly=true: 조회 메서드 성능 최적화 (Flush 생략, DB 읽기 전용)
    - write: CUD 메서드 (트랜잭션 커밋 필요)
    - 메서드 레벨 어노테이션으로 세밀한 제어
22. **findOrCreate 패턴** ⭐ NEW (2025-12-28)
    - DICOM C-STORE Ingest Pipeline 핵심 패턴
    - UID 기반 조회 → 없으면 생성 → 중복 방지
    - Identity Resolution: 환자 자동 매칭 (DICOM PatientID + Issuer)
    - 멱등성(Idempotency) 보장
23. **Service Layer 예외 처리 전략** ⭐ NEW (2025-12-28)
    - ResourceNotFoundException: 엔티티 미존재 (404)
    - IllegalArgumentException: 비즈니스 규칙 위반 (400)
    - 조회 메서드: orElseThrow() 패턴
    - 중복 검증: findBy → ifPresent → throw
24. **Service 의존성 주입** ⭐ NEW (2025-12-28)
    - @RequiredArgsConstructor: Lombok 생성자 자동 생성
    - final 필드: 불변성 보장
    - 순환 참조 주의: PatientService ← StudyService ← SeriesService ← InstanceService (단방향)
25. **Java `-parameters` 컴파일러 플래그** ⭐ NEW (2025-12-29)
    - Spring @PathVariable 파라미터명 인식에 필수
    - Java 컴파일러는 기본적으로 메서드 파라미터 이름을 바이트코드에 포함하지 않음
    - build.gradle: `tasks.withType(JavaCompile).configureEach { options.compilerArgs += ['-parameters'] }`
    - 런타임 Reflection API로 파라미터명 조회 가능하게 함
    - Spring Boot 4.0 + Java 21 환경에서도 기본 비활성화
26. **Jackson 역직렬화 요구사항** ⭐ NEW (2025-12-29)
    - DTO에 기본 생성자 필수: `@NoArgsConstructor`
    - 모든 필드 생성자도 권장: `@AllArgsConstructor`
    - `@Builder`만으로는 Jackson 역직렬화 불가능
    - 인터페이스 타입 필드는 역직렬화 불가: `@JsonIgnore` 필요
    - HTTP 응답 본문을 DTO 객체로 변환 시 필수
27. **API 응답 코드 체계 이해** ⭐ NEW (2025-12-29)
    - 7자리 코드 시스템: 200000 (성공), 400001 (클라이언트 에러), 500001 (서버 에러)
    - HTTP 상태 코드(200)와 API 코드(200000)는 다름
    - isSuccess() 메서드: `code >= 200000 && code < 300000` (200-299 아님!)
    - Service-Module-Seq 구조 유지
28. **통합 테스트 vs 단위 테스트 레이어 차이** ⭐ NEW (2025-12-29)
    - 단위 테스트(Service): Mock 사용, HTTP 레이어 거치지 않음 → Spring 설정 무관
    - 통합 테스트(Controller): 실제 HTTP 요청/응답 → Spring 설정, Jackson, 컴파일러 플래그 모두 영향
    - "간단한 CRUD"도 통합 테스트에서는 다양한 인프라 이슈 발생 가능
    - RestTestClient: JSON 직렬화/역직렬화 전 과정 검증
29. **PropertyReferenceException 트러블슈팅** ⭐ NEW (2025-12-29)
    - Spring Data JPA Query Method 생성 시 엔티티 필드명 불일치
    - 엔티티에 없는 필드로 Repository 메서드명 작성 시 발생
    - 해결: 엔티티에 필드 추가 또는 Repository 메서드명 수정
    - Application Context 로딩 시점에 검증됨 (컴파일 타임 아님)
30. **근본 원인 분석(Root Cause Analysis) 방법론** ⭐ NEW (2025-12-29)
    - 증상 vs 원인 구분: 테스트 실패는 증상, 컴파일러 설정 누락이 원인
    - 계층별 분석: HTTP → Jackson → Spring → JPA → Business Logic
    - 유사 에러 그룹핑: 10개 @PathVariable 실패 → 1개 근본 원인
    - Explore agent 활용: 다양한 파일 검색, 패턴 발견
31. **Enum 설계 일관성** - 도메인별 명확한 네이밍 (Dicom prefix)
32. **Enum 재사용성** - ValidationLevel을 ValidationLog 내부 enum에서 독립 파일로 분리
33. **FileAsset 다형성 연관** - Polymorphic Association (referenceType + referenceId)
34. **TTL 관리 설계** - expiresAt 필드로 자동 만료 지원
35. **Storage Tiering 전략** - HOT/WARM/COLD 계층 기반 비용 최적화
36. **BaseEntity 상속 주의사항** - id 필드 중복 선언 시 composite ID 오류 발생
37. **DCM4CHE Maven 저장소 관리** ⭐ NEW (2025-12-31)
    - DCM4CHE는 Maven Central에 없고 자체 저장소 사용 (https://maven.dcm4che.org/)
    - `content { includeGroup }` 보안 설정으로 Supply Chain Attack 방어
    - GitHub Issue #1367 - Maven Central 요청 2018년부터 미해결
    - 안정 버전 선택 전략 (5.29.1 - mini-pacs-poc 검증)
38. **UUID v7 vs UUID v4** ⭐ NEW (2025-12-31)
    - UUID v7: 타임스탬프 기반, 정렬 가능, DB 인덱스 성능 30-50% 향상
    - UUID v4: 랜덤 기반, B-Tree 단편화 발생
    - uuid-creator:5.3.7 라이브러리 사용
    - 의료 영상 파일 ID 생성에 적합 (시간순 정렬 + 고유성)
39. **Builder 패턴 일관성** ⭐ NEW (2025-12-31)
    - 엔티티 생성 시 Builder 패턴 사용 → 필수 필드 검증
    - Service 계층에서 일관된 패턴 적용 (findOrCreate 메서드)
    - 코드 가독성 및 유지보수성 향상
    - null 검증 자동화, 실수 방지
40. **패키지 구조 설계 원칙** ⭐ NEW (2025-12-31)
    - 도메인 서비스 vs 인프라 서비스 분리 (domain.service vs storage.service)
    - 예외 처리 중앙화 (exception 패키지)
    - 파싱 로직 명확화 (domain.parser)
    - 관심사 분리 (Separation of Concerns) 원칙 적용

### 트러블슈팅 경험
1. **Kafka 포트 오타** - 9002 → 9092 수정
2. **Missing Dependencies** - JPA, MySQL 의존성 추가
3. **MySQL8Dialect ClassNotFoundException** - Hibernate 6.x에서 제거됨, 자동 감지로 해결
4. **에러 분석 방법론** - 스택 트레이스 하단부터 읽기, 증상과 원인 구분
5. **의료 장비 인증 설계 간극 발견** - POC 계획에 Device 인증 없음, Simple API Key 접근 후 OAuth2 마이그레이션 경로 수립
6. **HIPAA 2025 규제 조사** - Simple API Key가 프로덕션 부적합 검증, 산업 표준 (OAuth2 Client Credentials) 확인
7. **설계 검증 방법론** - Web Search를 통한 산업 표준 검증 (Google Cloud, AWS, OWASP, HIPAA)
8. **통합 테스트 13개 동시 실패 원인 분석** ⭐ NEW (2025-12-29)
   - 증상: "간단한 CRUD" 테스트가 실패 (Service 레이어는 통과)
   - 근본 원인 1: Java `-parameters` 플래그 누락 (10개 실패)
   - 근본 원인 2: Jackson 역직렬화 설정 누락 (3개 실패)
   - 해결 방법: 계층별 분석 → 유사 에러 그룹핑 → 근본 원인 2개 식별 → 각각 해결
   - 학습: 통합 테스트는 단위 테스트와 다른 레이어(HTTP, JSON, Spring 설정) 문제 발견
9. **PropertyReferenceException 연쇄 에러** ⭐ NEW (2025-12-29)
   - Application Context 로딩 실패 → 3개 Repository 에러
   - DicomMetadataRecord: sopInstanceUid, seriesInstanceUid, instanceId 필드 누락
   - ValidationLogRepository: 메서드명 vs 엔티티 필드명 불일치
   - 해결: 엔티티 필드 추가 + Repository 메서드명 수정
10. **isSuccess() 로직 버그 발견** ⭐ NEW (2025-12-29)
    - 증상: 성공 응답(200000)에 대해 isSuccess() = false 반환
    - 원인: HTTP 상태 코드(200-299)와 API 코드(200000-299999) 혼동
    - 해결: 범위 체크 수정 (`>= 200 && < 300` → `>= 200000 && < 300000`)
    - 학습: 7자리 API 코드 체계 정확히 이해 필요
11. **로컬 포트 충돌 문제 해결** ⭐ NEW (2025-12-30)
    - 증상: 기존 로컬 서비스와 포트 충돌 (MySQL 3306, Kafka 9092 등)
    - 원인: 표준 포트 사용으로 다른 개발 프로젝트와 충돌
    - 해결: 모든 포트를 10000번대로 마이그레이션 (범위별 논리적 할당)
    - Kafka 특별 주의사항: KAFKA_ADVERTISED_LISTENERS를 외부 포트와 일치시켜야 함
    - 학습: Docker 포트 매핑 형식 ("외부:내부"), 컨테이너 간 통신은 내부 포트 유지
12. **DCM4CHE 의존성 해결 실패 분석** ⭐ NEW (2025-12-31)
    - 증상: `Could not find org.dcm4che:dcm4che-core:5.34.1` 빌드 에러
    - 근본 원인: DCM4CHE는 Maven Central에 없고 자체 저장소 사용 (https://maven.dcm4che.org/)
    - 추가 발견: 5.34.0 버전도 전이 의존성 문제 (Weasis library 미발견)
    - 해결: root build.gradle에 DCM4CHE 저장소 추가 + 버전 5.29.1로 다운그레이드
    - 보안 강화: `content { includeGroup 'org.dcm4che' }` 추가 (Supply Chain Attack 방어)
    - 학습: Maven Central 외 저장소 사용 시 보안 설정, 안정 버전 선택 전략, 전이 의존성 검증 필요

---

## 참조 문서

| 문서 | 역할 |
|-----|------|
| [07_최종_구현_계획.md](../core/07_최종_구현_계획.md) | 전체 16주 구현 계획 |
| [CURRENT_CONTEXT.md](CURRENT_CONTEXT.md) | Claude 컨텍스트 복원 |
| [20_Claude_Code_파일_충돌_대응_전략.md](../guides/20_Claude_Code_파일_충돌_대응_전략.md) | 파일 충돌 해결 전략 |
| [10_JPA_MyBatis_혼용_전략.md](../guides/10_JPA_MyBatis_혼용_전략.md) | Week 3 필수 참조 |
| [12_멀티테넌시_설계_가이드.md](../guides/12_멀티테넌시_설계_가이드.md) | Week 3 필수 참조 |

---

## 변경 이력

| 날짜 | 변경 내용 |
|------|----------|
| 2025-12-31 | **Week 4-5 코드 점검 및 개선 완료** ⭐⭐⭐⭐ - MiniPACS POC 코드 전체 점검 (5대 문제점 해결), DCM4CHE 의존성 해결 (자체 Maven 저장소 추가, 버전 5.29.1, Supply Chain Attack 방어), 패키지 구조 개선 (storage/service, exception, domain/parser), Patient.Builder 검증 제거 (nullable PatientID 허용), Study/Series 주석과 구현 일치 (nullable=false 추가), Service 계층 Builder 패턴 일관성 개선 (PatientService, StudyService, SeriesService, InstanceService, InstanceController), DicomMetadataExtractor 생성 (131 lines, DCM4CHE 파싱), 11개 파일 수정, 2개 파일 이동, 빌드 성공 (BUILD SUCCESSFUL in 19s), 코드 품질 지표 5개 영역 개선 (Builder 패턴 0→5, 패키지 계층 분리, DCM4CHE 안정화, 엔티티 주석 일치, 예외 처리 통합), 학습 성과 4개 추가 (DCM4CHE Maven 저장소, UUID v7 vs v4, Builder 패턴 일관성, 패키지 구조 설계 원칙), 트러블슈팅 경험 추가 (DCM4CHE 의존성 분석 + 전이 의존성 검증), 종합 문서 작성 (2025-12-31-code-review-and-improvements.md), Week 4-5 Storage Layer 진행 준비 완료 |
| 2025-12-30 | **SeaweedFS S3 API 설계 반영 (PROGRESS.md)** ⭐⭐⭐ - Week 4-5 섹션 업데이트 (LocalFileStorageClient → SeaweedFS S3 API), 설계 변경 경고 추가 (⚠️), 5개 P0 작업 재정의 (Docker Compose Filer 추가, AWS SDK 의존성, SeaweedFsS3Config, DicomStorageService S3 API, DicomUploadController), Phase 0 작업 업데이트 (Storage Layer 명칭 변경), KingArthur 참조 패턴 테이블 업데이트 (Storage Abstraction → SeaweedFS S3 API), 참조 문서 추가 (sado_docs/seaweedfs/10_MiniPACS_연동_가이드.md Line 132-710), 설계 변경 이유 명시 (DICOMweb 표준, OHIF Viewer 호환성, AI 서비스 S3 SDK, Pre-signed URL 지원) |
| 2025-12-30 | **KingArthur 통합 아키텍처 문서화** ⭐⭐⭐ - CURRENT_CONTEXT.md + PROGRESS.md 업데이트 완료, KingArthur 참조 프로젝트 섹션 추가 (위치, 주요 흐름, 적용 패턴), MiniPACS Storage-as-a-Service 역할 명시 (Multi-Client Serving: KingArthur Backend, Frontend Viewer, AI 서버), Phase 3 (Week 4-6) 작업 상세화 (StorageClient 인터페이스 P0, LocalFileStorageClient P0, DicomUploadController P0, FileAssetController P1, DicomMetadataExtractor P1), KingArthur 통합 로드맵 섹션 신규 추가 (Phase 0-4: POC 완성 → API 호환성 → 데이터 마이그레이션 → Dual-Write → MiniPACS Primary), KingArthur 참조 패턴 테이블 (Storage Abstraction, Command Pattern, Distributed Locking, FileAsset 관리, Event-Driven), Week 4-5 즉시 시작 작업 P0/P1 우선순위 정리, 학습 성과: Storage Abstraction 패턴, Adapter Pattern (0줄 비즈니스 로직 변경), Lazy Migration 전략, Production 참조 프로젝트 분석 방법론 |
| 2025-12-30 | **포트 10000번대 마이그레이션 완료** ⭐⭐ - 로컬 서비스 포트 충돌 해결, 인프라 포트 (MySQL: 3306→10100, Zookeeper: 2181→10101, Kafka: 9092→10102), 백엔드 포트 (Gateway: 8080→10200, MiniPACS: 8081→10201), 프론트엔드 포트 (Vite: 5173→10300), 총 4개 파일 수정 (docker-compose.yml 4곳, gateway/application.yml 3곳, minipacs/application.yml 2곳, vite.config.ts server 블록 추가), PORT_MAPPING.md 신규 생성 (포트 매핑표, 빠른 접속 URL, 롤백 가이드, 향후 예정 포트), 백업 디렉토리 생성 (backups/pre-port-migration-2025-12-30/), 인프라 서비스 검증 완료, 기존 포트 미사용 확인, MySQL 연결 테스트 성공, 트러블슈팅 경험 추가 (Kafka Advertised Listeners, Docker 포트 매핑 형식), 학습 성과: 포트 범위별 논리적 할당 전략 (10100번대 인프라, 10200번대 백엔드, 10300번대 프론트엔드) |
| 2025-12-29 17:00 | **FileAsset 기반 설계 완료 (Week 4)** ⭐⭐⭐⭐ - FileAsset Entity + Repository + 3개 Enum (FileCategory, FileStatus, ReferenceType) 설계, file_assets DDL 스크립트 (8개 인덱스), Enum 리팩토링 (DicomValidationLevel/Category/Status), DicomMetadataRecord fileSize 필드 추가, 12개 파일 생성/수정, 84개 테스트 100% 통과, Week 4 AI 결과 파일 관리 준비 완료, Composite ID 이슈 해결 (BaseEntity 상속), 학습 성과 6개 추가 (Enum 설계, 다형성 연관, TTL 관리, Storage Tiering, BaseEntity 상속), POC 우선순위 작업 완료 |
| 2025-12-29 08:00 | **통합 테스트 전체 통과 (31개)** ⭐⭐⭐ - 근본 원인 2개 분석 완료 (Java `-parameters` 플래그 누락, Jackson 역직렬화 실패), 6개 파일 수정 (DicomMetadataRecord, ValidationLogRepository, build.gradle, ApiResponse, PatientResponse, PatientServiceTest), PropertyReferenceException 3건 해결, IllegalArgumentException 10건 해결, InvalidDefinitionException 5건 해결, isSuccess() 로직 버그 수정, Week 3-4 체크리스트 100% 완료, 학습 성과 6개 추가 (Java `-parameters`, Jackson 역직렬화, API 코드 체계, 통합 vs 단위 테스트, PropertyReferenceException, Root Cause Analysis), 트러블슈팅 경험 3개 추가, 진행률 100% |
| 2025-12-28 22:00 | **Service 계층 구현 완료** ⭐⭐ - Service 4개 구현 (PatientService, StudyService, SeriesService, InstanceService), findOrCreate 패턴 적용 (DICOM C-STORE Ingest용), @Transactional 적용 (readOnly + write 분리), Identity Resolution (Patient 자동 매칭), storagePath 중복 검증, ResourceNotFoundException 예외 처리, 빌드 성공, Commit: feat(minipacs): Service 계층 구현 완료 (728줄 추가), 학습 성과 추가 (@Transactional, findOrCreate 패턴, Service Layer 예외 처리), 진행률 75% |
| 2025-12-28 14:00 | **Repository 계층 구현 완료** ⭐ - TenantProvider 인프라 구현 (3개 파일), Repository 6개 + Enum 2개 구현, Commit 2개 생성 (feat(minipacs): Repository), 전체 빌드 성공, Week 3-4 체크리스트 100% 완료, 학습 성과 추가 (Spring Data JPA Query Methods, ValidationLog 설계), 진행률 50% |
| 2025-12-27 15:30 | **DICOM Entity 설계 진행** - DicomMetadataRecord.java 생성 (WORM 정책, JSON metadata, nullable 최소화), Study-DicomMetadataRecord 관계 설계 (Option B - 간접 참조), Study.java 수정 (2-Layer 아키텍처 반영), Patient Entity 가이드 작성 (Issuer of PatientID), Week 3 체크리스트 50% 완료, 학습 성과 추가 (2-Layer 아키텍처, Issuer of PatientID, JavaBeans 규칙) |
| 2025-12-27 14:00 | **Infrastructure 추상화 레이어 문서화 완료** ⭐ - 16_Infrastructure_추상화_레이어_가이드.md 신규 작성 (1,100+ 라인), 07_최종_구현_계획.md 업데이트 (Infrastructure 모듈 구조 상세화, Week 4/5/7/11 추상화 반영), PROGRESS.md 업데이트 (학습 성과 추가: Infrastructure 추상화, DB 분리 이유, 인증 아키텍처), 핵심 설계 원칙: Adapter Pattern (Hexagonal Architecture), 외부 솔루션 교체 비용 최소화 (StorageClient, EventPublisher, WorkflowEngine, AuthProvider, CacheService) |
| 2025-12-26 23:30 | **의료 장비 인증 설계 완료** - Device Entity 설계, 3단계 마이그레이션 계획 (API Key → OAuth2 → mTLS), HIPAA 2025 규제 검증, 산업 표준 조사 (Google Cloud/AWS), Week 3 실습 목표 확장 (Device Entity, DeviceRepository, 테스트) |
| 2025-12-26 22:00 | **Week 1-2 완료 (80%)** - Docker Compose 환경 구축, Spring Boot 4.0 설정, sado-common 모듈 구현 완료 |
| 2025-12-26 | POC Day 1 완료 - TestController 테스트 성공 |
| 2025-12-26 | sado-common 핵심 클래스 구현 (ApiCode, CommonCode, ApiResponse, BusinessException, GlobalExceptionHandler) |
| 2025-12-26 | 문서 초기 생성 |

---

*최종 수정: 2025-12-31*
