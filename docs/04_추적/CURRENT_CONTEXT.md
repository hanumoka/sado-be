# Backend 컨텍스트

> **최종 업데이트**: 2026-01-22

---

## 현재 상태

| 항목 | 값 |
|------|-----|
| **프로젝트** | MiniPACS Standalone |
| **상태** | ✅ POC 완성 + 확장 API 추가 |
| **진행률** | 100% |
| **블로킹 이슈** | 없음 |

---

## 완성된 기능

### Core PACS
- ✅ **DICOM 업로드/저장/조회** - MultipartFile → SeaweedFS S3 → MySQL
- ✅ **DICOMweb API** - QIDO-RS, WADO-RS, WADO-URI, STOW-RS
- ✅ **DICOMweb 확장 API** - tenantId URL Path, DB ID 기반 API, Batch 프레임 조회 (2026-01-22)
- ✅ **SeaweedFS S3 연동** - 분산 객체 스토리지
- ✅ **멀티테넌시** - Tenant 격리 (tenant_id)
- ✅ **DICOM Pre-rendering** - Multi-Resolution (256/128px) + Thumbnail
- ✅ **Nginx 캐싱 레이어** - DICOMweb API 응답 캐싱 (2026-01-16)

### Admin Dashboard
- ✅ **Dashboard Summary** - 환자/Study/Series/Instance 통계
- ✅ **Storage Monitoring** - Tier별 용량, 카테고리별 사용량, 트렌드
- ✅ **Tiering 관리** - HOT/WARM/COLD 정책, 파일 목록, 전환 이력
- ✅ **실시간 모니터링** - 업로드/렌더링 작업 현황
- ✅ **SeaweedFS Admin** - Cluster 상태, Volume 관리

### 기술적 완성
- ✅ **통합 테스트** - 84개 100% 통과
- ✅ **Bruno API 테스트** - 72개 테스트 컬렉션
- ✅ **코드 품질 개선** - N+1 쿼리 최적화, 입력 검증, 보안 강화

### 성능 최적화 (2026-01-19)
- ✅ **Caffeine 캐시** - @Cacheable 적용 (Study, Series, Instance UID 조회)
- ✅ **캐시 즉시 무효화** - @CacheEvict + CacheManager (삭제 시 즉시 무효화)
- ✅ **N+1 쿼리 방지** - @BatchSize(size=10) 적용 (Study→Series, Series→Instances)
- ✅ **SRP 분리** - DicomUploadService 추출 (InstanceService 762줄 → 489줄)

### 버그 수정 (2026-01-19)
- ✅ **Nginx STOW-RS 업로드** - 대용량 DICOM 파일 업로드 지원 (`nginx-cache.conf`)
  - `client_max_body_size 500M`, `proxy_cache off`, 타임아웃 300초

---

## POC 제한 사항 (Important Limitations)

> 프로덕션 전환 시 아래 항목 구현 필요

### Tiering
| 항목 | POC 상태 | 프로덕션 필요 |
|------|----------|--------------|
| 메타데이터 업데이트 | ✅ 구현됨 | - |
| 물리적 파일 이동 | ❌ 미구현 | 필요 |
| SeaweedFS 볼륨 분리 | 단일 볼륨 | Multi-Volume 구성 |
| 스케줄러 | 비활성화 | 활성화 필요 |

### Pre-rendering
| 항목 | POC 상태 | 프로덕션 권장 |
|------|----------|--------------|
| 처리 방식 | 동기 (업로드 시 즉시) | 비동기 (큐 기반) |
| 해상도 | 256/128px (cine) + 128px (thumbnail) | 필요에 따라 조정 |
| 비활성화 | rendered(PNG), bulkdata, cine-64/32 | 용량 절감 |
| 디스크 절감 | ~54% (PNG → JPEG 전환) | - |

### 인증/보안
| 항목 | POC 상태 | 프로덕션 필요 |
|------|----------|--------------|
| 인증 | 미구현 | Keycloak OAuth2 |
| 멀티테넌시 | 헤더 기반 | JWT 토큰 기반 |

---

## 주요 설정

### Tiering 스케줄러
```yaml
# application.yml
admin:
  tiering:
    scheduler-enabled: false  # POC - DB 메타데이터만 업데이트, 물리적 파일 이동 미구현
    hot-to-warm-days: 30
    warm-to-cold-days: 365
```

### 포트 매핑
| 서비스 | 포트 | 설명 |
|--------|------|------|
| MySQL | 10100 | 데이터베이스 |
| MiniPACS Backend | 10201 | Spring Boot API |
| Nginx Cache | 10202 | 캐싱 프록시 (FE 기본 연결) |
| Frontend (Vite) | 10300 | React 개발 서버 |
| SeaweedFS S3 API | 10405 | 객체 스토리지 |

---

## 프로젝트 구조

```
sado_be/
├── sado-common/           # 공통 모듈 (ApiResponse, Exception)
├── sado-minipacs/         # MiniPACS 모듈
│   ├── controller/        # REST API (Admin, DICOM, DICOMWeb)
│   ├── domain/
│   │   ├── entity/        # JPA Entity (Patient, Study, Series, Instance, FileAsset)
│   │   ├── repository/    # Spring Data JPA Repository
│   │   └── service/       # 비즈니스 로직
│   │       ├── InstanceService.java      # Instance CRUD
│   │       ├── DicomUploadService.java   # DICOM 업로드 오케스트레이션 (SRP 분리)
│   │       ├── PreRenderingService.java  # 사전 렌더링
│   │       └── ...
│   ├── infrastructure/    # SeaweedFS, Storage, Cache 설정
│   └── scheduler/         # Tiering 스케줄러
├── docker-compose-dev.yml # MySQL, SeaweedFS, Nginx Cache
└── nginx-cache.conf       # Nginx 캐싱 프록시 설정
```

---

## API 엔드포인트 요약

### DICOMweb API (표준)
| API | 엔드포인트 |
|-----|-----------|
| QIDO-RS | GET /dicomweb/studies, /series, /instances |
| WADO-RS | GET /dicomweb/studies/{uid}/... |
| WADO-URI | GET /dicomweb/wado |
| STOW-RS | POST /dicomweb/studies |

### DICOMweb 확장 API (신규 2026-01-22)
| 유형 | 엔드포인트 | 설명 |
|------|-----------|------|
| 확장 WADO-RS | GET /dicomweb-ext/{tenantId}/studies/{uid}/... | tenantId URL Path 포함 |
| 내부 API | GET /dicomweb-ext/{tenantId}/internal/instances/{id} | DB ID 기반 조회 |
| 내부 API | GET /dicomweb-ext/{tenantId}/internal/instances/{id}/frames/{frameList} | BulkData |
| 내부 API | GET /dicomweb-ext/{tenantId}/internal/instances/{id}/frames/{frameList}/rendered | 렌더링 |
| Batch API | POST /dicomweb-ext/{tenantId}/internal/batch/frames | 다중 Instance 일괄 조회 |

### Admin API
| 기능 | 엔드포인트 |
|------|-----------|
| Dashboard | GET /api/admin/dashboard/summary |
| Storage | GET /api/admin/metrics/storage |
| Tiering | GET /api/admin/tiering/files |
| Monitoring | GET /api/admin/monitoring/tasks |
| SeaweedFS | GET /api/admin/seaweedfs/cluster/status |

---

## 참고 문서

| 문서 | 용도 |
|------|------|
| [START_HERE.md](../START_HERE.md) | 프로젝트 시작점 |
| [13_mini-pacs-poc_분석.md](../02_가이드/13_mini-pacs-poc_분석_개요.md) | DICOM 아키텍처 |
| [26_DICOM_사전렌더링_성능최적화_설계.md](../02_가이드/26_DICOM_사전렌더링_성능최적화_설계.md) | Pre-rendering 설계 |
| [33_DICOMWeb_확장_API_가이드.md](../02_가이드/33_DICOMWeb_확장_API_가이드.md) | 확장 API 가이드 |

---

## Claude Code 재시작 시

1. 이 문서 (CURRENT_CONTEXT.md) 확인
2. [START_HERE.md](../START_HERE.md) 확인
3. 사용자 요청에 따라 작업 진행

**진행상황 키워드**: "진행상황", "현재 상태", "status"

---

*최종 수정: 2026-01-22 (DICOMweb 확장 API 추가)*
