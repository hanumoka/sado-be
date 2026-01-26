# Backend 진행 상황

> **최종 업데이트**: 2026-01-19

---

## 전체 진행률

```
MiniPACS POC: ████████████████████ 100% ✅ 완료
```

---

## 완료된 기능 체크리스트

### Core PACS (100%)
- [x] DICOM 업로드 (MultipartFile → SeaweedFS S3)
- [x] DICOM 메타데이터 추출 (DCM4CHE)
- [x] DICOM 저장 (Patient/Study/Series/Instance 계층)
- [x] DICOMweb QIDO-RS (검색 API)
- [x] DICOMweb WADO-RS (조회 API)
- [x] DICOMweb WADO-URI (레거시 호환)
- [x] DICOMweb STOW-RS (저장 API)
- [x] Multi-Resolution Pre-rendering (256/128px cine + 128px thumbnail)
- [x] 멀티테넌시 (tenant_id 격리)

### Admin Dashboard (100%)
- [x] Dashboard Summary API
- [x] Storage Metrics API (Tier별, 카테고리별)
- [x] Storage Trends API (시계열 데이터)
- [x] Tiering 정책 조회/수정 API
- [x] Tiering 파일 목록 API
- [x] Tiering 수동 전환 API
- [x] Tier 전환 이력 API
- [x] 실시간 모니터링 API (업로드/렌더링 작업)
- [x] SeaweedFS Cluster 상태 API
- [x] SeaweedFS Volume 관리 API

### 인프라 (100%)
- [x] Docker Compose (MySQL, SeaweedFS, Nginx Cache)
- [x] SeaweedFS S3 연동 (AWS SDK v2)
- [x] Gradle 멀티모듈 (sado-common, sado-minipacs)
- [x] Nginx 캐싱 프록시 (DICOMweb API 응답 캐싱)

### 성능 최적화 (2026-01-19)
- [x] Caffeine 캐시 적용 (@Cacheable - Study/Series/Instance UID 조회)
- [x] 캐시 즉시 무효화 (@CacheEvict + CacheManager)
- [x] N+1 쿼리 방지 (@BatchSize(size=10) - Study→Series, Series→Instances)
- [x] SRP 분리 (DicomUploadService 추출 - InstanceService 762줄 → 489줄)

### 버그 수정 (2026-01-19)
- [x] Nginx STOW-RS 업로드 지원 (`nginx-cache.conf`)
  - 문제: `ERR_CONNECTION_ABORTED` - 대용량 DICOM 업로드 실패
  - 원인: `client_max_body_size` 기본값 1MB, POST 요청에 캐싱 적용
  - 해결: STOW-RS 전용 location 추가 (`location = /dicomweb/studies`)
    - `client_max_body_size 500M`
    - `proxy_request_buffering off` (스트리밍 업로드)
    - `proxy_cache off` (캐싱 비활성화)
    - 업로드 타임아웃 300초

### 테스트 (100%)
- [x] 통합 테스트 84개 (100% 통과)
- [x] Bruno API 테스트 72개

---

## 아키텍처

```
┌─────────────┐     ┌─────────────┐     ┌──────────────────┐     ┌─────────────┐
│   sado_fe   │────▶│ Nginx Cache │────▶│  sado-minipacs   │────▶│   MySQL     │
│  (React)    │     │  (10202)    │     │  (Spring Boot)   │     │  (10100)    │
│  (10300)    │     └─────────────┘     │    (10201)       │     └─────────────┘
└─────────────┘           │             └────────┬─────────┘
                          │                      │
                    ┌─────┴─────┐                ▼
                    │  7일 캐시  │       ┌─────────────────┐
                    │ (frames,  │       │   SeaweedFS     │
                    │  rendered,│       │   S3 API        │
                    │  thumbnail)       │   (10405)       │
                    └───────────┘       └─────────────────┘
```

### 캐시 계층
```
Browser Cache (max-age=3600) → Nginx Cache (7d) → Backend (Caffeine L1) → MySQL/SeaweedFS
```

### 캐시 전략 (2026-01-19 추가)
| 레이어 | 용도 | TTL |
|--------|------|-----|
| Browser | 정적 리소스 | 1시간 |
| Nginx | DICOMweb 응답 (frames, rendered, thumbnail) | 7일 |
| **Caffeine (L1)** | Study/Series/Instance UID 조회 | 1시간 (expireAfterWrite) |

캐시 무효화: 삭제 시 CacheManager로 즉시 evict

---

## 주요 엔티티

| Entity | 설명 |
|--------|------|
| Patient | 환자 정보 |
| Study | 검사 정보 |
| Series | 시리즈 정보 |
| Instance | 인스턴스 (DICOM 파일) |
| FileAsset | 부수 파일 (AI 결과, 썸네일 등) |
| DicomMetadataRecord | DICOM 메타데이터 JSON |
| ValidationLog | 검증 로그 |
| TierTransitionLog | Tier 전환 이력 |

---

## API 엔드포인트 현황

### DICOMweb API (12개)

| 기능 | 엔드포인트 수 | 상태 |
|------|--------------|------|
| QIDO-RS | 3 | ✅ 완료 |
| WADO-RS Metadata | 3 | ✅ 완료 |
| WADO-RS Instance | 1 | ✅ 완료 |
| WADO-RS Rendered | 2 | ✅ 완료 |
| WADO-RS BulkData | 1 | ✅ 완료 |
| WADO-URI | 1 | ✅ 완료 |
| STOW-RS | 1 | ✅ 완료 |

### Admin API (15+개)

| 기능 | 상태 |
|------|------|
| Dashboard Summary | ✅ |
| Storage Metrics | ✅ |
| Tier Distribution | ✅ |
| Tiering Files | ✅ |
| Tiering Policies | ✅ |
| Monitoring Tasks | ✅ |
| SeaweedFS Admin | ✅ |

---

## 기술 결정 사항

| 항목 | 결정 | 이유 |
|------|------|------|
| ORM | JPA (Spring Data JPA) | 표준, 생산성 |
| Storage | SeaweedFS S3 API | 분산, S3 호환 |
| DICOM 라이브러리 | DCM4CHE 5.29.1 | 업계 표준 |
| 멀티테넌시 | Shared DB + tenant_id | 단순성 |
| Tiering | DB 메타데이터 기반 | POC 범위 |
| **캐싱 (L1)** | Caffeine (로컬) | 빠른 응답, 단일 인스턴스 최적화 |
| **N+1 방지** | @BatchSize(size=10) | IN 쿼리로 쿼리 수 90% 감소 |

---

## Tiering 현재 상태

```yaml
# POC 상태: DB 메타데이터만 업데이트
# 물리적 파일 이동: 미구현
# SeaweedFS 볼륨: 1개 (분리 없음)

scheduler-enabled: false
hot-to-warm-days: 30
warm-to-cold-days: 365
```

향후 구현 시:
- Multi-Volume Server 구성 (SSD/HDD/Archive)
- 물리적 파일 이동 로직 추가
- 야간 배치 마이그레이션

---

## 향후 계획 (AI 통합)

MiniPACS POC 완료 후 다음 페이즈에서 구현 예정:

| 기능 | 설명 | 문서 |
|------|------|------|
| **AI 모듈 연동** | Triton 서버 gRPC 통신 | 📦 아카이브됨 |
| **gRPC 클라이언트** | BE→AI 추론 요청 | 📦 아카이브됨 |
| **분석 결과 저장** | EF, 세그멘테이션 결과 | 추후 설계 |

> **Note**: AI 모듈 문서는 [99_아카이브/01_AI모듈/](../../99_아카이브/01_AI모듈/)로 이동되었습니다.

---

## 참고 문서

- [CURRENT_CONTEXT.md](CURRENT_CONTEXT.md) - 현재 컨텍스트
- [../START_HERE.md](../START_HERE.md) - 프로젝트 시작점
- [../01_핵심/00_INDEX.md](../01_핵심/00_INDEX.md) - 문서 색인

---

*최종 수정: 2026-01-19*
