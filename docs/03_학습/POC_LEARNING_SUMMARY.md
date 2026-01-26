# POC 기간 학습 요약 (Week 1-8)

> **기간**: 2025-12 ~ 2026-01-14
> **상태**: MiniPACS POC 100% 완료

---

## 개요

MiniPACS POC 기간 동안 진행된 학습 내용의 요약입니다.
상세 내용은 각 주차별 폴더를 참조하세요.

---

## 주차별 핵심 학습

| Week | 주요 학습 주제 | 핵심 성과 | 블로그 |
|------|---------------|----------|--------|
| Week 1 | Gradle 멀티모듈, 프로젝트 구조 | sado-common, sado-minipacs 분리 | [게시됨](../../blog/published/gradle-multimodule-beginner.md) |
| Week 2 | JPA + MyBatis 혼용, 멀티테넌시 | Tenant 격리 구현 | 초안 완료 |
| Week 3 | SeaweedFS 연동, S3 API | 분산 스토리지 통합 | [게시됨](../../blog/published/seaweedfs-distributed-object-storage.md) |
| Week 4 | DICOM 처리, DCM4CHE | 메타데이터 추출/저장 | 초안 완료 |
| Week 5 | DICOMWeb API (QIDO-RS) | 검색 API 구현 | 초안 완료 |
| Week 6 | DICOMWeb API (WADO-RS/URI) | 조회 API, 렌더링 | 초안 완료 |
| Week 7 | Admin Dashboard, 모니터링 | 통계, Storage, Tiering API | 초안 완료 |
| Week 8 | 통합 테스트, 코드 품질 | 84개 테스트 100% 통과 | 초안 완료 |

---

## Phase별 회고

| Phase | 범위 | 핵심 학습 | 회고 문서 |
|-------|------|----------|----------|
| Phase 1 | Week 1-2 | 프로젝트 기반, 멀티모듈 | [phase-01-retrospective.md](phase-01-retrospective.md) |
| Phase 2 | Week 3-4 | SeaweedFS, DICOM 처리 | [phase-02-retrospective.md](phase-02-retrospective.md) |
| Phase 3 | Week 5-6 | DICOMWeb API 구현 | [phase-03-retrospective.md](phase-03-retrospective.md) |
| Phase 4 | Week 7-8 | Admin Dashboard, 테스트 | [phase-04-retrospective.md](phase-04-retrospective.md) |

---

## 게시된 블로그 (3개)

| 제목 | 주제 | 링크 |
|------|------|------|
| Gradle 멀티모듈 시작하기 | 프로젝트 구조 | [gradle-multimodule-beginner.md](../../blog/published/gradle-multimodule-beginner.md) |
| 공통 모듈 ApiResponse 패턴 | BE 아키텍처 | [common-module-apiresponse-pattern.md](../../blog/published/common-module-apiresponse-pattern.md) |
| SeaweedFS 분산 객체 스토리지 | 인프라 | [seaweedfs-distributed-object-storage.md](../../blog/published/seaweedfs-distributed-object-storage.md) |

---

## 주요 기술 습득

### Backend
- Spring Boot 4.0.1 + Java 21
- Spring Data JPA + 멀티테넌시
- DCM4CHE DICOM 라이브러리
- AWS SDK v2 (SeaweedFS S3)
- 통합 테스트 작성

### Infrastructure
- Docker Compose
- SeaweedFS 분산 스토리지
- MySQL 8.0

### 아키텍처
- 멀티모듈 프로젝트 설계
- DICOMWeb 표준 API
- Admin Dashboard 설계
- Storage Tiering 전략

---

## 폴더 구조

```
learning/
├── POC_LEARNING_SUMMARY.md   ← 이 문서
├── week-01/ ~ week-08/       # 주차별 상세 노트
├── phase-01~04-retrospective.md  # Phase 회고
└── special/                  # 특별 학습 자료
```

---

## 다음 학습 (AI 페이즈)

POC 완료 후 예정된 학습:

| 주제 | 기술 | 예상 시점 |
|------|------|----------|
| AI 추론 서버 | NVIDIA Triton | AI Phase 1 |
| gRPC 통신 | KServe Protocol | AI Phase 2 |
| 모델 배포 | EchoNet-Dynamic, ONNX | AI Phase 2 |
| 성능 최적화 | Dynamic Batching | AI Phase 3 |

---

*생성일: 2026-01-15*
