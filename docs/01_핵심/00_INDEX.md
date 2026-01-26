# sado-be 프로젝트 문서 통합 관리

> **최종 업데이트**: 2026-01-19
> **프로젝트**: sado-be (DICOM PACS Backend)
> **상태**: MiniPACS POC 100% 완료

---

## 시작하기

> **처음이신가요?** → [START_HERE.md](../START_HERE.md) ← **여기서 시작**
>
> **Claude가 재시작되었나요?** → [CURRENT_CONTEXT.md](../04_추적/CURRENT_CONTEXT.md)

---

## 문서 구조

```
01_백엔드/
├── START_HERE.md           # 5분 안에 시작하기 (진입점)
│
├── 01_핵심/                # 핵심 실행 문서 (필수)
│   ├── 00_INDEX.md         # 이 문서
│   └── 01_개발_정책.md     # Claude Code 역할 정의
│
├── 02_가이드/              # 기술 명세 가이드
│   ├── 10_JPA_MyBatis_혼용_전략.md
│   ├── 13_mini-pacs-poc_분석_개요.md (+ Part 1-5)
│   ├── 24_DICOM_검증_파이프라인_가이드.md
│   └── 32_REST_API_명세서.md
│
├── 03_학습/                # 학습 노트 (01주차~08주차)
│   └── 01주차~08주차/
│
├── 04_추적/                # 진행 추적
│   ├── PROGRESS.md         # 전체 대시보드
│   ├── CHANGELOG.md        # 변경 이력
│   └── CURRENT_CONTEXT.md  # Claude 컨텍스트 복원
│
└── 99_아카이브/            # 의사결정 과정 (참고용)
    └── 01_미래계획/        # MSA/Kafka 미래 계획 문서
```

---

## 핵심 문서

| 문서 | 역할 | 상태 |
|------|------|------|
| [01_개발_정책.md](01_개발_정책.md) | Claude Code 역할 정의 | 기본 |

> **Note**: 16주 로드맵 문서(07_최종_구현_계획.md)는 [99_아카이브/01_미래계획/](../99_아카이브/01_미래계획/)로 이동되었습니다.

---

## 기술 스택 (현재 구현)

### Core
| 기술 | 버전 | 용도 |
|-----|------|-----|
| **Spring Boot** | 4.0.1 | 프레임워크 |
| **Java** | 21 | 언어 (Virtual Thread) |
| **MySQL** | 8.0 | 데이터베이스 |
| **DCM4CHE** | 5.29.1 | DICOM 파싱/렌더링 |
| **OpenCV** | 4.9.0 | JPEG/JPEG2000 코덱 |
| **SeaweedFS** | - | 분산 객체 스토리지 (S3 API) |

### 모듈 구조
```
sado-be/
├── sado-common/         # 공통 라이브러리
│   ├── ApiResponse      # 표준 응답 형식
│   ├── BusinessException # 예외 처리
│   ├── TenantContext    # 멀티테넌시
│   └── BaseEntity       # 기본 엔티티
│
└── sado-minipacs/       # MiniPACS 핵심 모듈
    ├── controller/      # REST/DICOMWeb API
    ├── domain/          # Entity, Repository, Service
    ├── storage/         # SeaweedFS 연동
    └── scheduler/       # Tiering 스케줄러
```

---

## 기술 가이드 (구현 완료)

### DICOMWeb API
| 문서 | 내용 |
|------|------|
| [31_DICOMWeb_WADO_구현_가이드.md](../02_가이드/31_DICOMWeb_WADO_구현_가이드.md) | WADO-RS/WADO-URI 구현 |
| [32_REST_API_명세서.md](../02_가이드/32_REST_API_명세서.md) | 전체 REST API 명세 |
| [13-4_DICOMWeb_API_설계.md](../02_가이드/13-4_DICOMWeb_API_설계.md) | QIDO-RS, WADO-RS, STOW-RS |

### DICOM 처리
| 문서 | 내용 |
|------|------|
| [13_mini-pacs-poc_분석_개요.md](../02_가이드/13_mini-pacs-poc_분석_개요.md) | POC 아키텍처 분석 (6파트) |
| [24_DICOM_검증_파이프라인_가이드.md](../02_가이드/24_DICOM_검증_파이프라인_가이드.md) | 3-Level 검증 |
| [25_Identity_Resolution_가이드.md](../02_가이드/25_Identity_Resolution_가이드.md) | 환자 동일성 확인 |
| [26_DICOM_사전렌더링_성능최적화_설계.md](../02_가이드/26_DICOM_사전렌더링_성능최적화_설계.md) | Pre-rendering |

### 인프라
| 문서 | 내용 |
|------|------|
| [10_JPA_MyBatis_혼용_전략.md](../02_가이드/10_JPA_MyBatis_혼용_전략.md) | DB 접근 전략 |
| [15_횡단_관심사_설계_가이드.md](../02_가이드/15_횡단_관심사_설계_가이드.md) | 멀티테넌시, 공통 패턴 |

---

## API 엔드포인트 요약

### DICOMWeb API
| API | 엔드포인트 | 설명 |
|-----|-----------|------|
| QIDO-RS | `GET /dicomweb/studies` | Study/Series/Instance 검색 |
| WADO-RS | `GET /dicomweb/.../instances/{uid}` | DICOM 조회 |
| WADO-RS Rendered | `GET /dicomweb/.../frames/{n}/rendered` | 이미지 렌더링 |
| WADO-URI | `GET /dicomweb/wado` | 레거시 호환 |
| STOW-RS | `POST /dicomweb/studies` | DICOM 업로드 |

### Cine Frames API (MJPEG용)
| 엔드포인트 | 설명 |
|-----------|------|
| `GET /dicomweb/cine-frames/{sopUID}` | 모든 프레임 일괄 조회 |
| `GET /dicomweb/cine-frames/{sopUID}/info` | 프레임 정보 |

### Admin API
| 기능 | 엔드포인트 |
|------|-----------|
| Dashboard | `GET /api/admin/dashboard/summary` |
| Storage | `GET /api/admin/metrics/storage` |
| Tiering | `GET /api/admin/tiering/files` |
| Monitoring | `GET /api/admin/monitoring/tasks` |
| SeaweedFS | `GET /api/admin/seaweedfs/cluster/status` |

---

## 완료된 기능

### Core PACS
- [x] **DICOM 업로드/저장/조회** - MultipartFile → SeaweedFS S3 → MySQL
- [x] **DICOMweb API** - QIDO-RS, WADO-RS, WADO-URI, STOW-RS
- [x] **SeaweedFS S3 연동** - 분산 객체 스토리지
- [x] **멀티테넌시** - Tenant 격리 (tenant_id)
- [x] **DICOM Pre-rendering** - Multi-Resolution (256/128px) + Thumbnail
- [x] **Nginx 캐싱 레이어** - DICOMweb API 응답 캐싱

### Admin Dashboard
- [x] **Dashboard Summary** - 환자/Study/Series/Instance 통계
- [x] **Storage Monitoring** - Tier별 용량, 트렌드
- [x] **Tiering 관리** - HOT/WARM/COLD 정책
- [x] **실시간 모니터링** - 업로드/렌더링 작업 현황
- [x] **SeaweedFS Admin** - Cluster 상태, Volume 관리

### 기술적 완성
- [x] **통합 테스트** - 84개 100% 통과
- [x] **Bruno API 테스트** - 72개 테스트 컬렉션
- [x] **N+1 쿼리 최적화** - JOIN FETCH 적용

---

## 진행 추적

| 문서 | 역할 |
|------|------|
| [PROGRESS.md](../04_추적/PROGRESS.md) | 전체 진행 대시보드 |
| [CHANGELOG.md](../04_추적/CHANGELOG.md) | 날짜별 작업 이력 |
| [CURRENT_CONTEXT.md](../04_추적/CURRENT_CONTEXT.md) | Claude 컨텍스트 복원 |

---

## 아카이브 (의사결정 과정)

> archive/ 폴더의 문서들은 초기 의사결정 과정 기록입니다.

| 문서 | 역할 |
|------|------|
| [02_프로젝트_요구사항.md](../99_아카이브/02_프로젝트_요구사항.md) | 초기 요구사항 |
| [03_아키텍처_검토.md](../99_아카이브/03_아키텍처_검토.md) | 기술 스택 비교 |
| [04_요구사항_재검토.md](../99_아카이브/04_요구사항_재검토.md) | 문제점 분석 |
| [05_권장사항.md](../99_아카이브/05_권장사항.md) | 개선 제안 |
| [06_변경사항_요약.md](../99_아카이브/06_변경사항_요약.md) | 변경점 정리 |

---

## 학습 노트

| 폴더/파일 | 역할 |
|----------|------|
| [03_학습/](../03_학습/) | 학습 노트 폴더 |
| [03_학습/01주차~08주차/](../03_학습/) | 주차별 학습 기록 |
| [03_학습/phase-01~04-retrospective.md](../03_학습/) | Phase 회고 |
| [03_학습/final-retrospective.md](../03_학습/) | 최종 회고 |

---

## 자주 묻는 질문 (FAQ)

### Q1. 어떤 문서를 먼저 읽어야 하나요?
**A**: [START_HERE.md](../START_HERE.md)를 먼저 읽으세요. 5분 안에 현재 상태를 파악할 수 있습니다.

### Q2. Claude Code가 재시작되었어요.
**A**: [CURRENT_CONTEXT.md](../04_추적/CURRENT_CONTEXT.md)를 읽으세요. 현재 진행 상황과 다음 작업이 정리되어 있습니다.

### Q3. MiniPACS는 어떻게 구현되어 있나요?
**A**: [13_mini-pacs-poc_분석_개요.md](../02_가이드/13_mini-pacs-poc_분석_개요.md)를 참조하세요. 6개 파트로 상세하게 분석되어 있습니다.

### Q4. DICOMWeb API 명세는?
**A**: [32_REST_API_명세서.md](../02_가이드/32_REST_API_명세서.md)를 참조하세요. 전체 API가 정리되어 있습니다.

### Q5. 멀티테넌시는 어떻게 구현되어 있나요?
**A**: [15_횡단_관심사_설계_가이드.md](../02_가이드/15_횡단_관심사_설계_가이드.md)를 참조하세요.
- **전략**: Shared Database with tenant_id 컬럼
- **식별**: X-Tenant-ID 헤더 (POC), JWT 토큰 (프로덕션)
- **격리**: JPA @Filter로 자동 필터링

---

## SADO 프로젝트 전체 구조

```
sado/
├── sado_be/    # Spring Boot 백엔드 (이 프로젝트)
├── sado_fe/    # React 프론트엔드
├── sado_ai/    # Triton AI 서버 (계획됨)
└── sado_docs/  # 통합 문서 저장소
```

| 프로젝트 | 기술 스택 | 상태 |
|---------|----------|------|
| **sado_be** | Spring Boot 4.0.1, Java 21, MySQL 8.0 | ✅ MiniPACS 완료 |
| **sado_fe** | React 19.2.0, TypeScript, Cornerstone3D | ✅ MiniPACS 완료 |
| **sado_ai** | NVIDIA Triton, gRPC | 📋 계획됨 |

---

## 포트 매핑

| 서비스 | 포트 | 설명 |
|--------|------|------|
| MySQL | 10100 | 데이터베이스 |
| MiniPACS Backend | 10201 | Spring Boot API |
| Nginx Cache | 10202 | 캐싱 프록시 |
| Frontend (Vite) | 10300 | 개발 서버 |
| SeaweedFS S3 API | 10405 | 객체 스토리지 |

상세: [PORT_MAPPING.md](../../PORT_MAPPING.md)

---

*최종 수정: 2026-01-23*
