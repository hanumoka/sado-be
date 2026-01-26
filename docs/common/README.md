# SADO 프로젝트 통합 문서 저장소

> SADO 프로젝트 (DICOM 의료 영상 관리 시스템) 전체 문서 관리
>
> **최종 업데이트**: 2026-01-23

---

## 프로젝트 개요

| 프로젝트 | 설명 | 기술 스택 | 상태 |
|---------|------|----------|------|
| **sado_be** | 백엔드 API 서버 | Spring Boot 4.0.1, Java 21, MySQL 8.0, SeaweedFS | ✅ MiniPACS 완성 |
| **sado_fe** | 프론트엔드 웹 앱 | React 19.2.0, TypeScript 5.9, Cornerstone3D 4.12.6 | ✅ MiniPACS 완성 |
| **sado_ai** | AI 추론 서버 | NVIDIA Triton, gRPC, EchoNet-Dynamic | 📦 아카이브 |

---

## MiniPACS Standalone ✅ 완료

**목표**: DICOM 업로드/저장/조회 + 5개 DICOM 뷰어 + Admin Dashboard

### 완료된 기능

| 카테고리 | 기능 | 상태 |
|---------|------|------|
| **Core PACS** | DICOM 업로드/저장/조회 | ✅ |
| **Core PACS** | DICOMweb API (QIDO-RS, WADO-RS, WADO-URI, STOW-RS) | ✅ |
| **Core PACS** | SeaweedFS S3 연동 | ✅ |
| **Core PACS** | 멀티테넌시 지원 (Tenant 격리) | ✅ |
| **DICOM 뷰어** | WADO-RS Rendered (Cornerstone) | ✅ |
| **DICOM 뷰어** | WADO-RS BulkData (Cornerstone) | ✅ |
| **DICOM 뷰어** | MJPEG (Canvas 기반 빠른 재생) | ✅ |
| **DICOM 뷰어** | Hybrid MJPEG+WADO-RS (듀얼 레이어) | ✅ |
| **DICOM 뷰어** | WADO-URI (레거시 호환) | ✅ |
| **Admin** | Dashboard (통계, Tier 분포) | ✅ |
| **Admin** | Storage Monitoring (용량, 트렌드) | ✅ |
| **Admin** | Tiering 관리 (HOT/WARM/COLD) | ✅ |
| **Admin** | 실시간 모니터링 | ✅ |
| **테스트** | 통합 테스트 84개 100% 통과 | ✅ |

---

## 5개 DICOM 뷰어 아키텍처

| 뷰어 | 용도 | 기술 | 초기 로딩 |
|------|------|------|----------|
| **WADO-RS Rendered** | 진단용 고속 | Cornerstone | ~2초 |
| **WADO-RS BulkData** | 원본 보존, W/L 조정 | Cornerstone | ~3초 |
| **MJPEG** | 빠른 스크리닝 | Canvas | ~100ms |
| **Hybrid** | 빠른 재생 + 고품질 | MJPEG→Cornerstone | ~100ms |
| **WADO-URI** | 외부 PACS 연동 | Cornerstone | ~3초 |

상세 아키텍처: [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md)

---

## 문서 구조

```
sado_docs/
├── README.md                    # 이 문서 (프로젝트 개요)
├── SYSTEM_ARCHITECTURE.md       # 시스템 아키텍처
├── PORT_MAPPING.md              # 포트 매핑 정보
│
├── 01_백엔드/                   # Backend 문서
│   ├── START_HERE.md            # BE 시작점
│   ├── 01_핵심/                 # 핵심 문서
│   │   └── 00_INDEX.md          # BE 문서 색인
│   ├── 02_가이드/               # 기술 가이드 (30개+)
│   ├── 03_학습/                 # 학습 노트 (01주차~08주차)
│   ├── 04_추적/                 # 진행 추적
│   └── 99_아카이브/             # 과거 문서
│
├── 02_프론트엔드/               # Frontend 문서
│   ├── START_HERE.md            # FE 시작점
│   ├── 01_핵심/                 # 핵심 문서
│   ├── 02_가이드/               # 기술 가이드
│   ├── 04_추적/                 # 진행 추적
│   └── 99_아카이브/             # 완료된 POC 문서
│
├── 03_SeaweedFS/                # SeaweedFS 문서 (11개)
│   └── 00_README.md
│
├── 04_블로그/                   # 블로그 글 (3개 게시됨)
│   └── 01_발행됨/
│
├── 05_작업/                     # 작업 템플릿
│
├── 06_테스팅/                   # E2E 테스트 가이드
│
├── 07_개발자가이드/             # 개발 환경 설정 및 개발 가이드
│
└── 99_아카이브/                 # 아카이브 (과거 작업 기록)
    └── 01_AI모듈/               # AI 모듈 문서 (미구현)
```

---

## 빠른 시작

### Backend 개발자
1. [07_개발자가이드/01_BE_개발자_가이드.md](07_개발자가이드/01_BE_개발자_가이드.md) - **종합 개발 가이드 (권장)**
2. [01_백엔드/START_HERE.md](01_백엔드/START_HERE.md) - 현재 상태 파악
3. [01_백엔드/01_핵심/00_INDEX.md](01_백엔드/01_핵심/00_INDEX.md) - 문서 색인

### Frontend 개발자
1. [07_개발자가이드/02_FE_개발자_가이드.md](07_개발자가이드/02_FE_개발자_가이드.md) - **종합 개발 가이드 (권장)**
2. [02_프론트엔드/START_HERE.md](02_프론트엔드/START_HERE.md) - 현재 상태 파악
3. [02_프론트엔드/02_가이드/06_DICOM_뷰어_아키텍처_가이드.md](02_프론트엔드/02_가이드/06_DICOM_뷰어_아키텍처_가이드.md) - 5개 뷰어 가이드

---

## 기술 스택 요약

### Backend (sado_be)
| 기술 | 버전 | 용도 |
|-----|------|-----|
| Spring Boot | 4.0.1 | 프레임워크 |
| Java | 21 | 언어 (Virtual Thread) |
| MySQL | 8.0 | 데이터베이스 |
| SeaweedFS | - | 분산 객체 스토리지 (S3 API) |
| DCM4CHE | 5.29.1 | DICOM 라이브러리 |
| OpenCV | 4.9.0 | 이미지 처리 |

### Frontend (sado_fe)
| 기술 | 버전 | 용도 |
|-----|------|-----|
| React | 19.2.0 | UI 프레임워크 |
| TypeScript | 5.9.3 | 타입 안전성 |
| Vite | 7.2.4 | 빌드 도구 |
| Zustand | 5.0.9 | 클라이언트 상태 |
| TanStack Query | 5.90.16 | 서버 상태 |
| Cornerstone3D | 4.12.6 | DICOM 뷰어 |
| Tailwind CSS | 3.4.19 | 스타일링 |

---

## 포트 매핑

| 서비스 | 포트 | 용도 |
|--------|------|------|
| MySQL | 10100 | 데이터베이스 |
| MiniPACS Backend | 10201 | REST/DICOMweb API |
| Nginx Cache | 10202 | 캐싱 프록시 |
| Frontend (Vite) | 10300 | 개발 서버 |
| SeaweedFS Master | 10220 | 스토리지 마스터 |
| SeaweedFS Volume | 10221 | 스토리지 볼륨 |
| SeaweedFS Filer | 10222 | 파일러 |
| SeaweedFS S3 API | 10405 | S3 호환 API |

상세: [PORT_MAPPING.md](PORT_MAPPING.md)

---

## 다음 단계 (What's Next)

| 단계 | 작업 | 상태 |
|------|-----|------|
| **프로덕션 준비** | Tiering 물리적 파일 이동, 비동기 처리 | 📋 선택 |

---

## 미포함 범위 (Archived)

다음 기능들은 MiniPACS POC 범위에서 제외되어 아카이브되었습니다:

| 기능 | 아카이브 위치 | 비고 |
|------|-------------|------|
| **AI 모듈** | [99_아카이브/01_AI모듈/](99_아카이브/01_AI모듈/) | Triton, EchoNet 미구현 |
| **인증 (Keycloak)** | - | POC에서 제외 (X-Tenant-ID 헤더로 대체) |
| **MSA 아키텍처** | [01_백엔드/99_아카이브/01_미래계획/](01_백엔드/99_아카이브/01_미래계획/) | Kafka, Redis Stream 등 |
| **FE 초기 설정 문서** | [02_프론트엔드/99_아카이브/](02_프론트엔드/99_아카이브/) | 완료된 POC 문서 보관 |

---

## 문서 작성 원칙

1. **CLAUDE.md는 각 프로젝트에 유지**: 코드 저장소(sado_be, sado_fe)의 루트에 보관
2. **문서는 sado_docs에서 관리**: 모든 요구사항, 설계 문서, 학습 노트
3. **한국어 작성**: 학습 목적 프로젝트이므로 한국어로 작성
4. **간결하게 유지**: 불필요한 이력은 archive로 이동

---

## 주요 문서 링크

| 문서 | 설명 |
|------|------|
| [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md) | 시스템 아키텍처 |
| [07_개발자가이드/01_BE_개발자_가이드.md](07_개발자가이드/01_BE_개발자_가이드.md) | BE 종합 개발 가이드 |
| [07_개발자가이드/02_FE_개발자_가이드.md](07_개발자가이드/02_FE_개발자_가이드.md) | FE 종합 개발 가이드 |
| [01_백엔드/01_핵심/00_INDEX.md](01_백엔드/01_핵심/00_INDEX.md) | BE 문서 색인 |
| [02_프론트엔드/01_핵심/00_INDEX.md](02_프론트엔드/01_핵심/00_INDEX.md) | FE 문서 색인 |
| [01_백엔드/02_가이드/32_REST_API_명세서.md](01_백엔드/02_가이드/32_REST_API_명세서.md) | REST API 명세 |
| [02_프론트엔드/02_가이드/06_DICOM_뷰어_아키텍처_가이드.md](02_프론트엔드/02_가이드/06_DICOM_뷰어_아키텍처_가이드.md) | 5개 DICOM 뷰어 가이드 |

---

*최종 수정: 2026-01-23*
