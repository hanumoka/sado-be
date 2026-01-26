# 13. Mini PACS PoC 분석 및 통합 계획

> 📋 **문서 역할**: 기존 mini-pacs-poc 프로젝트 분석 및 sado-be 통합 계획
>
> 📅 **작성일**: 2025-12-20 (업데이트: 2025-12-25)
>
> 🎯 **목표**: mini-pacs-poc의 검증된 기능을 sado-be에 통합하여 개발 기간 단축
>
> 🔗 **연결**: `07_최종_구현_계획.md` 보완 문서, `17_MiniPACS_확장_설계_개요.md` 상세

---

## 📚 문서 구성

이 문서는 총 **6개 파트**로 구성되어 있습니다:

| 파트 | 파일명 | 내용 | 줄수 |
|------|--------|------|------|
| **개요** | [13_mini-pacs-poc_분석_개요.md](13_mini-pacs-poc_분석_개요.md) (현재 문서) | 전체 개요, 결론, 권장사항 | 42줄 |
| **Part 1** | [13-1_POC_프로젝트_개요.md](13-1_POC_프로젝트_개요.md) | Executive Summary, 기술 스택 비교 | 108줄 |
| **Part 2** | [13-2_아키텍처_통합_계획.md](13-2_아키텍처_통합_계획.md) | 아키텍처, 도메인 모델 통합 | 492줄 |
| **Part 3** | [13-3_데이터베이스_스키마.md](13-3_데이터베이스_스키마.md) | MySQL 스키마 (POC → 통합) | 149줄 |
| **Part 4** | [13-4_DICOMWeb_API_설계.md](13-4_DICOMWeb_API_설계.md) | QIDO-RS, WADO-RS, STOW-RS | 402줄 |
| **Part 5** | [13-5_통합_단계별_계획.md](13-5_통합_단계별_계획.md) | Phase 1-4, 일정, 위험 관리 | 998줄 |

**원본 문서**: 2,191줄 → 6개 파일로 분할 (2025-12-28)

---

## 📖 파트별 상세 요약

### Part 1: POC 프로젝트 개요
**파일**: [13-1_POC_프로젝트_개요.md](13-1_POC_프로젝트_개요.md)

**주요 내용**:
- mini-pacs-poc의 기술 스택 분석 (PostgreSQL 15, dcm4che 5.32.0)
- sado-be와의 기술 스택 비교 (MySQL 8.0, Kafka, Temporal)
- PostgreSQL → MySQL 마이그레이션 전략 (자세한 내용: `22_PostgreSQL_MySQL_마이그레이션_가이드.md`)

**핵심 포인트**: POC는 DICOM 처리의 기술적 검증이 완료되었으나, 멀티테넌시 및 MSA 요구사항은 미지원

---

### Part 2: 아키텍처 통합 계획
**파일**: [13-2_아키텍처_통합_계획.md](13-2_아키텍처_통합_계획.md)

**주요 내용**:
- sado-be 모듈 구조 확장 (7개 → 8개 모듈)
- DICOM 처리 파이프라인 설계 (C-STORE, STOW-RS, Redis Stream)
- 도메인 모델 통합 (Patient, Study, Series, Instance 엔티티)
- **UID 설계 원칙**: sopInstanceUid (70% 신뢰도), fileHash (100% 고유성) - 상세: `21_DICOM_원본_보존_정책.md`

**핵심 포인트**: POC의 도메인 모델을 sado-be 멀티테넌시 구조에 맞게 확장

---

### Part 3: 데이터베이스 스키마
**파일**: [13-3_데이터베이스_스키마.md](13-3_데이터베이스_스키마.md)

**주요 내용**:
- MySQL DDL 스크립트 (patient, study, series, instances 테이블)
- PostgreSQL → MySQL 변환 (JSONB → JSON, Generated Column 활용)
- 멀티테넌시 적용 (tenant_id 추가, 복합 인덱스)

**핵심 포인트**: POC의 PostgreSQL 스키마를 MySQL로 변환하면서 성능 최적화

---

### Part 4: DICOMWeb API 설계
**파일**: [13-4_DICOMWeb_API_설계.md](13-4_DICOMWeb_API_설계.md)

**주요 내용**:
- QIDO-RS (Query) API 설계 및 구현
- WADO-RS (Retrieve) API 설계 및 구현
- STOW-RS (Store) API 설계 및 구현
- Dual API 패턴: UID 기반 + id 기반 (UID 충돌 회피)

**핵심 포인트**: DICOMWeb 표준 준수 + sado-be 확장 (멀티테넌시, AI 연동)

---

### Part 5: 통합 단계별 계획
**파일**: [13-5_통합_단계별_계획.md](13-5_통합_단계별_계획.md)

**주요 내용**:
- Phase 1: 도메인 모델 통합 (Week 2-3)
- Phase 2: DICOM 처리 파이프라인 (Week 4-6)
- Phase 3: DICOMWeb API 구현 (Week 7-9)
- Phase 4: 고급 기능 (Week 10-12, 트랜스코딩, 티어링)
- Identity Resolution: 환자 매칭 알고리즘 (상세: `25_Identity_Resolution_가이드.md`)

**핵심 포인트**: 점진적 통합 전략 - 검증된 기능부터 단계적으로 적용

---

## 14. 결론 및 권장 사항

### 14.1 통합 전략 요약

**✅ 강력 권장: 선택적 통합 + 학습 중심 접근**

1. **도메인 모델**: 즉시 이관 (검증 완료, 시간 절약)
2. **DICOM 처리**: 참고 후 학습 구현 (dcm4che 마스터 필요)
3. **DICOMWeb API**: 컨트롤러 이관 (표준 준수 검증 완료)
4. **고급 기능**: 선택 적용 (트랜스코딩, 티어링)

### 14.2 예상 효과

- **개발 기간 단축**: 4-6주 → 8-10주 (mini-pacs-poc 없이 구현 시 20주 예상)
- **코드 품질**: 엔터프라이즈 레벨
- **학습 효과**: DICOM 표준 완전 이해
- **위험 감소**: 검증된 코드 재사용

### 14.3 다음 단계

1. **문서 검토 및 승인** (이 문서)
2. **Week 2-3 계획 수립**: Phase 1 도메인 모델 통합
3. **dcm4che 학습 시작**: mini-pacs-poc 문서 읽기
4. **Docker 환경 준비**: MySQL, Redis, SeaweedFS

---

**작성일**: 2025-12-20
**업데이트**: 2025-12-25 (다중 업로드 프로토콜, DICOM 검증 파이프라인 추가)
**다음 업데이트**: Phase 1 완료 후 (Week 3)
