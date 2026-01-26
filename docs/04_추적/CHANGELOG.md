# 📜 변경 이력 (Changelog)

> **작업 이력을 시간순으로 기록합니다**
> - 날짜 형식: YYYY-MM-DD
> - 최신 항목이 위에 오도록 작성

---

## 2026-01-16

### Added
- **Nginx 캐싱 레이어 구현** (로컬 개발환경)
  - `nginx-cache.conf` - DICOMweb API 캐시 프록시 설정
  - `docker-compose-dev.yml` - nginx-cache 서비스 추가 (포트 10202)
  - 캐시 정책: frames/rendered/thumbnail 7일, QIDO-RS 5분, MJPEG 1시간

### Changed
- **사전 렌더링 최적화 (~54% 디스크 절감)**
  - `application.yml` - rendered(PNG) 비활성화, cine-64/32 비활성화
  - `DicomWebController.java` - WADO-RS Rendered API가 cine-256.jpg 사용하도록 변경
  - 변경 전: thumbnail + compressed + jpeg + rendered(PNG) + cine(256/128/64/32)
  - 변경 후: thumbnail + compressed + jpeg + cine(256/128)
- **vite.config.ts** - 기본 프록시 타겟 10201 → 10202 (Nginx 캐시 경유)
- **docker-compose-dev.yml** - nginx-cache healthcheck wget → curl 수정

### 캐시 계층 구조
```
Browser Cache (Cache-Control: max-age=3600)
    ↓ (MISS 시)
Nginx Cache (proxy_cache 7d)
    ↓ (MISS 시)
Backend (Spring Boot)
    ↓
SeaweedFS S3
```

### 성능 효과
| 항목 | Before | After |
|------|--------|-------|
| 디스크 사용량 (10MB DICOM) | ~15MB 추가 | ~7MB 추가 |
| 반복 요청 응답 속도 | 100ms | 5ms (캐시 HIT) |
| Backend 부하 (반복 요청) | 100% | 0% |

---

## 2026-01-05

### Added
- **Study REST API 엔드포인트**
  - `StudyController.getAllStudies()` - GET /api/studies (patientId, patientName, studyDate 필터 지원)
  - `StudyResponse`에 `patientName` 필드 추가
- **logback-spring.xml 신규 생성**
  - ANSI 색상 로깅 지원
  - ERROR 전용 로그 파일 분리 (logs/error.log)
  - 파일 롤링 설정 (일별/크기별)

### Changed
- **UUID v7 Upsert 버그 수정** (6개 파일)
  - `PatientRepository.upsertPatient()` - uuid 파라미터 추가
  - `StudyRepository.upsertStudy()` - uuid 파라미터 추가
  - `SeriesRepository.upsertSeries()` - uuid 파라미터 추가
  - `PatientService.findOrCreate()` - UuidV7Generator.generateString() 사용
  - `StudyService.findOrCreate()` - UuidV7Generator.generateString() 사용
  - `SeriesService.findOrCreate()` - UuidV7Generator.generateString() 사용
- **DicomWebController 예외 로깅 개선**
  - `log.warn("message")` → `log.error("message", e)` 변경으로 stacktrace 포함
- **application.yml 로깅 설정 간소화**
  - 커스텀 로그 패턴 제거 (logback-spring.xml로 이관)

### Fixed
- **"Field 'uuid' doesn't have a default value" 에러 해결**
  - 원인: Native SQL Upsert 쿼리가 JPA @PrePersist 훅 우회
  - 해결: 서비스 레이어에서 UUID 생성 후 Repository에 전달

### Frontend (sado_fe)
- **studyService.ts** - DICOMweb → REST API 전환 (`/api/studies`)
- **PatientList.tsx** - id (PK), uuid 컬럼 추가
- **StudyList.tsx** - id (PK), uuid 컬럼 추가

---

## 2025-12-24

### Added
- **문서 리팩토링: 폴더 구조 개편**
  - `docs/core/` - 핵심 실행 문서 (00, 01, 07)
  - `docs/guides/` - 기술 명세 가이드 (08-15)
  - `docs/archive/` - 의사결정 과정 (02-06)
  - `docs/tracking/` - 진행 추적 (PROGRESS, CHANGELOG, CURRENT_CONTEXT)
  - `docs/START_HERE.md` - 5분 안에 시작하기 (신규 진입점)
  - `docs/tracking/CURRENT_CONTEXT.md` - Claude 컨텍스트 복원 문서

- **학습 폴더 구조 개선**
  - `docs/learning/week-01/` ~ `week-16/` - 주차별 폴더
  - 각 폴더에 `notes.md`, `blog-draft.md`, `troubleshooting.md` 템플릿
  - `docs/learning/phase-XX-retrospective.md` - Phase 회고 템플릿
  - `docs/learning/final-retrospective.md` - 최종 회고 템플릿
  - `docs/learning/special/` - 특별 학습 자료

### Changed
- `00_INDEX.md` - 계층적 문서 구조로 리팩토링, FAQ 업데이트
- `07_최종_구현_계획.md` - Week 2 멀티테넌시 섹션 보안 방식 업데이트
- `09_Temporal_점진적_도입_전략.md` - 실행 순서 명확화 섹션 추가
- `PROGRESS.md` - Claude 재시작 섹션 추가

### Fixed
- 없음

---

## 2025-12-21

### Added
- 📊 진행 상황 추적 시스템 구축
  - `PROGRESS.md` 생성 (전체 진행 상황 대시보드)
  - `CHANGELOG.md` 생성 (작업 이력)
  - `progress/` 폴더 생성 (주차별 상세 기록용)
  - `progress/README.md` 생성 (사용 가이드)

### Changed
- 없음

### Fixed
- 없음

---

## 2025-12-20

### Added
- 📚 프로젝트 문서화 완료
  - `12_멀티테넌시_설계_가이드.md` - 멀티테넌시 전체 설계 가이드
  - `13_mini-pacs-poc_분석_개요.md` - Mini-PACS 통합 계획
  - `07_최종_구현_계획.md` 업데이트 (Week 2, 12, 13 멀티테넌시 추가)
  - `10_JPA_MyBatis_혼용_전략.md` 업데이트 (멀티테넌시 섹션 추가)
  - `00_INDEX.md` 업데이트 (문서 12, 13 추가)
  - `learning/week02-jpa-mybatis-mysql-multitenancy.md` - Week 2 학습 가이드

### Changed
- 🗑️ 멀티모듈 설정 초기화
  - `sado-common/` 디렉토리 제거
  - `sado-minipacs/` 디렉토리 제거
  - `settings.gradle` 원래 상태로 복원
  - 사용자가 직접 구현할 수 있도록 가이드 문서만 유지

### Fixed
- 없음

---

## 작업 이력 템플릿

아래 템플릿을 복사하여 새로운 작업 이력을 추가하세요:

```markdown
## YYYY-MM-DD

### Added
- 추가된 기능이나 파일

### Changed
- 변경된 내용

### Fixed
- 수정된 버그나 이슈

### Removed
- 제거된 기능이나 파일
```

---

**Changelog 작성 가이드**:
- 매일 작업 종료 시 해당 날짜 섹션 업데이트
- 중요한 변경사항은 간결하게 기록
- 관련 파일이나 문서 경로 명시
- 이슈나 PR 번호가 있다면 함께 기록
