# SADO-BE 프로젝트 시작하기

> **5분 안에 현재 상태 파악하고 작업 시작하기**

---

## 현재 상태

| 항목 | 값 |
|------|-----|
| **Phase** | **MiniPACS Standalone** ✅ |
| **아키텍처** | sado_fe → sado-minipacs (직접 통신) |
| **진행률** | 100% ⭐ |
| **최종 업데이트** | 2026-01-14 |

### MiniPACS 범위

**포함된 기능** ✅:
- ✅ DICOM 업로드/저장/조회 (MultipartFile → SeaweedFS S3 → MySQL)
- ✅ DICOMweb API (QIDO-RS, WADO-RS, WADO-URI)
- ✅ SeaweedFS S3 연동 (파일 저장소)
- ✅ Cornerstone3D DICOM Viewer (Frontend 통합)
- ✅ MySQL 메타데이터 관리
- ✅ 통합 테스트 (84개 100% 통과)
- ✅ 멀티테넌시 지원 (Tenant 격리)

**추가 구현된 기능** ⭐:
- ✅ **Admin Dashboard** - DICOM 통계, Storage 사용량, Tier 분포 (2개 API)
- ✅ **Storage Monitoring** - Tier별 사용량, Trends 차트 (4개 API)
- ✅ **Tiering 관리** - HOT/WARM/COLD 정책 설정 및 파일 목록 (2개 API)
- ✅ **SeaweedFS Admin** - Cluster 상태, Volume 관리 (5개 API)

---

## MiniPACS 완성!

### 완료된 주요 기능
- [x] **DICOM 업로드/저장/조회** - 완전 동작
- [x] **DICOMWeb API** - QIDO-RS, WADO-RS, WADO-URI 구현
- [x] **SeaweedFS S3 연동** - 분산 객체 스토리지
- [x] **Cornerstone3D DICOM Viewer** - Frontend 통합
- [x] **Admin Dashboard** - DICOM 통계, Storage 모니터링
- [x] **Storage Tiering** - HOT/WARM/COLD 자동 전환
- [x] **P2 코드 품질 개선** - 37개 이슈 발견, 16개 작업 완료
- [x] **통합 테스트** - 84개 100% 통과

### API 검증 완료
- ✅ **Core PACS (7개 API)**: DICOMweb, Upload - 모두 정상 동작
- ✅ **Admin Features (8개 API)**: Dashboard, Monitoring, Tiering - 모두 정상 동작
- ✅ **SeaweedFS Admin (5개 API)**: Cluster, Volumes - Backend 구현 완료
- ✅ **FE-BE API 매칭**: 15/15 (100%) - 누락 없음

> 상세 내용: [CURRENT_CONTEXT.md](04_추적/CURRENT_CONTEXT.md), [PROGRESS.md](04_추적/PROGRESS.md)

---

## 필수 참고 문서

### 종합 개발자 가이드 (신규 개발자 필독)
| 문서 | 설명 |
|------|------|
| **[BE 종합 개발 가이드](../07_개발자가이드/01_BE_개발자_가이드.md)** | 개발환경, 아키텍처, API, DICOM 처리, 테스트, 유지보수 등 |

### 매일 확인
| 문서 | 역할 | 링크 |
|------|------|------|
| 진행 상황 | 전체 진행률 | [PROGRESS.md](04_추적/PROGRESS.md) |
| 현재 컨텍스트 | Claude 복원용 | [CURRENT_CONTEXT.md](04_추적/CURRENT_CONTEXT.md) |
| 문서 색인 | 전체 문서 | [00_INDEX.md](01_핵심/00_INDEX.md) |

---

## Claude Code가 재시작되었나요?

### 간편 복원: "진행상황"이라고 물으세요!

사용자가 **"진행상황"** 또는 **"현재 상태"**라고 물으면, Claude가 자동으로 필요한 문서를 읽고 현재 상태를 보고합니다.

**트리거 키워드:**
- "진행상황" / "진행 상황"
- "현재 상태" / "현황"
- "status" / "어디까지 했지?"

### 수동 복원 절차 (선택사항)

직접 복원하고 싶다면:

1. **[CURRENT_CONTEXT.md](04_추적/CURRENT_CONTEXT.md)** ← Claude 컨텍스트 복원용
2. [PROGRESS.md](04_추적/PROGRESS.md) → 현재 상태 확인

```
1. CURRENT_CONTEXT.md 읽기 (현재 상태 파악)
2. PROGRESS.md에서 현재 상태 확인
3. 작업 재개
```

---

## 문서 구조

```
01_백엔드/
├── START_HERE.md          ← 지금 여기 (진입점)
│
├── 01_핵심/               # 핵심 실행 문서
│   ├── 00_INDEX.md        # 전체 문서 색인
│   └── 01_개발_정책.md    # Claude 역할 정의
│
├── 02_가이드/             # 기술 가이드
│   ├── 10_JPA_MyBatis_혼용_전략.md
│   ├── 13_mini-pacs-poc_분석_개요.md
│   ├── 24_DICOM_검증_파이프라인_가이드.md
│   └── 32_REST_API_명세서.md
│
├── 03_학습/               # 학습 노트 (01주차~08주차)
│   └── 01주차~08주차/
│
├── 04_추적/               # 진행 추적
│   ├── PROGRESS.md        # 전체 대시보드
│   ├── CHANGELOG.md       # 변경 이력
│   └── CURRENT_CONTEXT.md # Claude 컨텍스트
│
└── 99_아카이브/           # 의사결정 기록 (참고용)
    └── 01_미래계획/       # MSA/Kafka 미래 계획
```

---

## 빠른 링크

| 필요한 것 | 문서 |
|----------|------|
| "지금 뭐 해야 하지?" | [CURRENT_CONTEXT.md](04_추적/CURRENT_CONTEXT.md) |
| "전체 진행률은?" | [PROGRESS.md](04_추적/PROGRESS.md) |
| "Claude가 재시작됐어" | [CURRENT_CONTEXT.md](04_추적/CURRENT_CONTEXT.md) |
| "이 기술 어떻게?" | [02_가이드/](02_가이드/) 폴더 |
| "내가 뭘 배웠지?" | [03_학습/](03_학습/) 폴더 |
| "블로그 어떻게 쓰지?" | [11_블로그_작성_가이드.md](02_가이드/11_블로그_작성_가이드.md) |

---

## 기술적 결정 사항 (요약)

### MiniPACS 완성
| 항목 | 결정 | 상세 문서 |
|------|------|----------|
| DICOM 라이브러리 | DCM4CHE 5.29.1 | [13_mini-pacs-poc_분석_개요.md](02_가이드/13_mini-pacs-poc_분석_개요.md) |
| 객체 스토리지 | SeaweedFS S3 API | seaweedfs/ 폴더 |
| DICOM 표준 | DICOMWeb (QIDO-RS, WADO-RS) | [13-4_DICOMWeb_API_설계.md](02_가이드/13-4_DICOMWeb_API_설계.md) |
| ORM | JPA (Spring Data JPA) | [10_JPA_MyBatis_혼용_전략.md](02_가이드/10_JPA_MyBatis_혼용_전략.md) |
| 검증 파이프라인 | 3-Level (Critical/Warning/AI) | [24_DICOM_검증_파이프라인_가이드.md](02_가이드/24_DICOM_검증_파이프라인_가이드.md) |
| 멀티테넌시 | Shared DB + tenant_id | [15_횡단_관심사_설계_가이드.md](02_가이드/15_횡단_관심사_설계_가이드.md) |

---

*최종 수정: 2026-01-23 (문서 아카이브 정리)*
