# sado-be 프로젝트 문서 통합 관리

> **최종 업데이트**: 2025-12-20
> **프로젝트**: sado-be (심장 초음파 DICOM 분석 시스템)
> **기간**: 16주 (학습 중심 프로젝트)

---

## 📖 용어 통일 규칙

### 기술 용어
- **BFF**: "Backend For Frontend" (첫 등장 시 전체 표기, 이후 "BFF")
- **Workflow**: 기술 문맥에서는 영문 "Workflow", 일반 설명에서는 "워크플로우"
- **Orchestrator**: 기술 문맥에서는 영문, 모듈명은 "sado-orchestrator"

### 모듈 명명 규칙
- **모듈명**: `sado-xxx` (소문자, 하이픈)
- **일반 명칭**: Orchestrator, MiniPACS, BFF (대문자 시작)

### 포맷 규칙
- **코드 블록**: 항상 언어 태그 명시 (```java, ```yaml, ```bash)
- **체크박스**: `- [ ]` 미완료, `- [x]` 완료
- **이모지**: 문서 역할 표시에만 사용

---

## 📖 문서 읽기 가이드

### 처음 읽는 분 (추천 순서)

```
1단계: 프로젝트 이해
  ├─ 01_개발_정책.md          ⭐ Claude Code 역할 정의 (필수)
  └─ 02_프로젝트_요구사항.md   초기 요구사항 (배경 이해용)

2단계: 설계 검토 과정
  ├─ 03_아키텍처_검토.md      기술 스택 비교 분석
  ├─ 04_요구사항_재검토.md    10가지 문제점 발견
  └─ 05_권장사항.md           개선 방향 제안

3단계: 최종 결정사항 (⭐ 핵심)
  ├─ 06_변경사항_요약.md      초기 대비 변경점 정리
  ├─ 07_최종_구현_계획.md     ⭐⭐⭐ 16주 상세 계획 (가장 중요)
  └─ 08_Redis_활용_요구사항.md Redis 분산락/캐싱 상세

4단계: 학습 노트 (구현 중 작성)
  └─ learning/               주차별 학습 노트 (Week 1-16)
```

### 바쁜 분 (빠른 이해)

1. **`07_최종_구현_계획.md`** 만 읽기 (모든 결정사항 포함)
2. **`06_변경사항_요약.md`** 훑어보기 (주요 변경점 10가지)

---

## 🗺️ 문서 읽기 흐름도

### 학습 목적 (처음 시작)

```
START
  ↓
📚 00_INDEX (이 문서) - 전체 구조 파악
  ↓
📋 01_개발_정책 - Claude 역할 이해 ⭐ 필수
  ↓
⭐ 07_최종_구현_계획 - 16주 계획 숙지 ⭐ 가장 중요
  ↓
🔄 09_Temporal_점진적_도입_전략 - Temporal 전략 이해
  ↓
🔧 08_Redis_활용_요구사항 - Redis 상세 (Week 14 시)
  ↓
📝 06_변경사항_요약 - 주요 변경점 확인 (선택)
  ↓
학습 노트 작성 (learning/)
```

### 참고 자료 (의사결정 과정 이해)

```
📜 02_프로젝트_요구사항 (초기 계획)
  ↓
🔍 03_아키텍처_검토 (기술 검토)
  ↓
⚠️ 04_요구사항_재검토 (문제점 10가지)
  ↓
💡 05_권장사항 (개선 제안)
  ↓
⭐ 07_최종_구현_계획 (최종 확정)
```

---

## 📂 문서 목록 및 설명

### 필수 문서 ⭐

#### [01_개발_정책.md](./01_개발_정책.md)
**읽어야 하는 이유**: Claude Code의 역할과 학습 프로젝트 정책 이해
**주요 내용**:
- Claude Code가 할 수 있는 것 / 하지 말아야 할 것
- 학습 프로젝트 작업 흐름
- 요청 가이드라인

**대상 독자**: 프로젝트 시작 전 필수 숙지

---

#### [07_최종_구현_계획.md](./07_최종_구현_계획.md) ⭐⭐⭐
**읽어야 하는 이유**: 가장 중요한 문서, 모든 최종 결정사항 포함
**주요 내용**:
- 6개 모듈 최종 구조
- 16주 주차별 상세 계획
- 기술 스택 확정 (Kafka, Temporal, PostgreSQL 등)
- Phase별 학습 목표 및 산출물
- 학습 성과 목표 (⭐⭐⭐⭐⭐ 마스터 레벨)

**대상 독자**: 프로젝트 전체 이해 및 구현 가이드

**핵심 섹션**:
- 🏗️ 최종 아키텍처 (6개 모듈)
- 🗓️ 16주 상세 일정
- 🛠️ 기술 스택 (최종)
- 📊 예상 학습 성과

---

### 배경 및 의사결정 과정 문서

#### [02_프로젝트_요구사항.md](./02_프로젝트_요구사항.md)
**읽어야 하는 이유**: 초기 요구사항 이해 (최종 계획과 비교용)
**주요 내용**:
- 초기 5개 모듈 구성 (Auth, MiniPACS, Gateway, BFF, Common)
- POC + 학습 프로젝트 혼재
- 기술 스택 "선택 필요" 상태

**현재 상태**: 참고용 (최종 계획과 차이 있음)

---

#### [03_아키텍처_검토.md](./03_아키텍처_검토.md)
**읽어야 하는 이유**: 기술 스택 선정 근거 이해
**주요 내용**:
- Kafka vs RabbitMQ 비교
- PostgreSQL vs MySQL 비교
- Temporal vs Spring Batch 비교
- Debezium 필요성 검토
- Saga 패턴 코드 예시
- Gateway, SeaweedFS 설계

**활용**: 기술 선택 이유 및 학습 자료

---

#### [04_요구사항_재검토.md](./04_요구사항_재검토.md)
**읽어야 하는 이유**: 초기 요구사항의 문제점 파악
**주요 내용**:
- 발견된 10가지 문제점
  1. BFF 모듈 과도한 책임
  2. MiniPACS 범위 불명확
  3. Auth 모듈 독립성
  4. 초기 POC 기술 스택 과다
  5. Keycloak vs Spring Security JWT
  6. SeaweedFS vs LocalStack S3
  7. Triton 서버 Kafka 의존성
  8. 이벤트 실패 처리 전략 부재
  9. 이벤트 버전 관리 누락
  10. POC vs 학습 목적 충돌

**활용**: 왜 아키텍처를 변경했는지 이해

---

#### [05_권장사항.md](./05_권장사항.md)
**읽어야 하는 이유**: 문제점 해결 방안 제시
**주요 내용**:
- 최종 모듈 구조 제안 (6개 모듈, Orchestrator 분리)
- 기술 스택 단계별 도입 전략
- 이벤트 플로우 개선 (Triton REST API 연동)
- 학습 로드맵 재구성

**활용**: 의사결정 근거 이해

---

#### [06_변경사항_요약.md](./06_변경사항_요약.md)
**읽어야 하는 이유**: 초기 요구사항 대비 변경점 빠르게 파악
**주요 내용**:
- 10가지 주요 변경점 정리
  1. 프로젝트 방향 (POC → 16주 학습)
  2. 모듈 구조 (5개 → 6개)
  3. BFF vs Orchestrator 분리
  4. 이벤트 플로우 (Kafka → REST)
  5. 기술 스택 확정
  6. 단계별 계획 (5단계 → 4단계)
  7. Spring Boot 버전
  8. 학습 성과 목표
  9. 문서 구조
  10. 다음 액션

**활용**: 빠른 변경점 이해

---

### 상세 기술 명세 문서

#### [08_Redis_활용_요구사항.md](./08_Redis_활용_요구사항.md)
**읽어야 하는 이유**: Redis 분산락 및 캐싱 구현 가이드
**주요 내용**:
- Redis 활용 시나리오 6가지
  1. DICOM 동시 업로드 방지 (분산락)
  2. AI 분석 중복 실행 방지 (분산락)
  3. Study 상태 변경 동시성 제어 (분산락)
  4. Study 메타데이터 캐싱
  5. Triton 분석 결과 캐싱
  6. API Gateway Rate Limiting
- Redisson 사용법 및 코드 예시
- 모듈별 Redis 의존성
- Week 14 학습 목표

**활용**: Week 14 Redis 구현 시 참조

---

#### [09_Temporal_점진적_도입_전략.md](./09_Temporal_점진적_도입_전략.md) ⭐⭐
**읽어야 하는 이유**: Temporal 없이 먼저 구현하고 유연하게 전환하는 전략
**주요 내용**:
- Temporal 점진적 도입 3단계
  1. Phase 1 (Week 4-7): Simple 구현 (Temporal 없이)
  2. Phase 2 (Week 8): Temporal 학습
  3. Phase 3 (Week 9-10): Temporal 전환
- 추상화 레이어 설계 (Workflow/Activity 인터페이스)
- Simple 구현체 vs Temporal 구현체
- 수동 Saga 패턴 → 자동 Saga 패턴
- Simple 구현의 한계점 체감 (학습 목표)
- A/B 테스트 전략

**대상 독자**: Week 4 시작 전 필수 숙지

**핵심 섹션**:
- 설계 패턴: 추상화 레이어
- 구현 1: Simple Workflow (Temporal 없이)
- 구현 2: Temporal Workflow
- 비교표: Simple vs Temporal
- 수정된 16주 계획 (Week 8-10)

**활용**: Week 4부터 Simple 구현 시작, Week 9에 Temporal 전환

---

#### [10_JPA_MyBatis_혼용_전략.md](./10_JPA_MyBatis_혼용_전략.md) ⭐
**읽어야 하는 이유**: JPA와 MyBatis를 언제 어떻게 사용할지 가이드
**주요 내용**:
- JPA vs MyBatis 장단점 비교
- 사용 기준 (도메인 CRUD vs 조회 최적화)
- 모듈별 적용 전략 (MiniPACS, BFF, Orchestrator)
- FK 제약조건 없는 설계 방법
- 성능 고려사항 (N+1 문제, 페이징)
- 테스트 전략
- 블로그 작성 가이드

**대상 독자**: Week 2 시작 전 필수 숙지

**핵심 섹션**:
- 사용 기준: JPA를 사용하는 경우 vs MyBatis를 사용하는 경우
- FK 제약조건 없는 설계
- 성능 고려사항

**활용**: Week 2 JPA+MyBatis 구현 시 참조

---

#### [11_블로그_작성_가이드.md](./11_블로그_작성_가이드.md)
**읽어야 하는 이유**: 주차별 학습을 블로그 글로 정리하는 가이드
**주요 내용**:
- 블로그 시리즈 구조 (30개 글 예상)
- 글 작성 템플릿 (들어가며, 문제 정의, 해결 방법, 트러블슈팅)
- Week 2 블로그 글 예시 (JPA+MyBatis 혼용 전략)
- 작성 스타일 가이드 (DO/DON'T)
- SEO 최적화 팁
- 주차별 블로그 주제 가이드

**대상 독자**: 블로그 글 작성 시 참조

**핵심 섹션**:
- 글 작성 템플릿
- Week 2 블로그 글 예시
- 주차별 블로그 주제 가이드

**활용**: 각 주차 완료 후 블로그 글 작성 시 참조

---

#### [12_멀티테넌시_설계_가이드.md](./12_멀티테넌시_설계_가이드.md) ⭐ NEW
**읽어야 하는 이유**: 멀티테넌시 전체 설계 및 구현 가이드
**주요 내용**:
- 데이터 격리 전략 비교 (Shared DB vs Schema per Tenant vs DB per Tenant)
- Shared Database with tenant_id 구현 상세
- TenantContext, TenantInterceptor, TenantFilterAspect 구현
- Keycloak JWT에 tenant_id claim 추가 방법
- 모듈별 멀티테넌시 적용 가이드
- 보안 고려사항 및 테스트 전략

**대상 독자**: Week 2 시작 전 필수 숙지

**핵심 섹션**:
- 아키텍처 설계: 전체 흐름 및 컴포넌트별 책임
- 구현 가이드: DB 스키마, JPA Entity, Interceptor
- 보안 고려사항: Tenant 격리 검증

**활용**: Week 2, 12, 13 구현 시 참조

---

#### [13_mini-pacs-poc_분석_및_통합_계획.md](./13_mini-pacs-poc_분석_및_통합_계획.md) ⭐ NEW
**읽어야 하는 이유**: 기존 mini-pacs-poc 프로젝트 분석 및 sado-be 통합 계획
**주요 내용**:
- mini-pacs-poc 프로젝트 종합 분석 (90% 완성도, 65개 Java 파일)
- 기술 스택 비교 (sado-be vs mini-pacs-poc)
- 도메인 모델 통합 계획 (PostgreSQL → MySQL 변환)
- DICOMWeb API (QIDO-RS, WADO-RS, STOW-RS) 이관 계획
- DICOM 처리 서비스 학습 및 구현 가이드
- Phase별 통합 단계 (6개 Phase, 8-10주 예상)
- 예상 효과: 개발 기간 50% 단축 (20주 → 8-10주)

**대상 독자**: Week 2 시작 전 필수 숙지, 프로젝트 리더

**핵심 섹션**:
- 기술 스택 비교 분석
- 도메인 모델 설계 (Entity 구조)
- Phase별 통합 계획 (상세 일정)

**활용**: Week 2부터 mini-pacs-poc 코드 이관 및 학습 시 참조

---

### 학습 노트 (구현 중 작성 예정)

#### [learning/](./learning/)
**목적**: 주차별 학습 내용 기록
**구조**:
```
learning/
├── week1-gradle-setup.md          # Week 1 학습 노트
├── week2-spring-jpa.md            # Week 2 학습 노트
├── ...
├── week16-portfolio.md            # Week 16 학습 노트
├── phase1-retrospective.md        # Phase 1 회고
├── phase2-retrospective.md        # Phase 2 회고
├── phase3-retrospective.md        # Phase 3 회고
├── phase4-retrospective.md        # Phase 4 회고
└── final-retrospective.md         # 최종 회고
```

**작성 시기**: 각 주차 완료 후
**내용**: 학습한 내용, 어려웠던 점, 해결 방법, 다음 주 목표

---

## 🎯 프로젝트 핵심 정보

### 최종 방향성
- **프로젝트 유형**: 16주 학습 중심 프로젝트 (빠른 POC 아님)
- **목표**: 각 기술 스택을 "사용" 수준이 아닌 "마스터" 수준까지 학습
- **성과물**: 프로덕션 레벨 포트폴리오

### 최종 모듈 구조 (6개)
```
sado-be/
├── sado-common/         # 공통 모듈 (core, event, security)
├── sado-infrastructure/ # 인프라 설정 (kafka, redis, temporal, storage)
├── sado-gateway/        # API Gateway (Keycloak 직접 연동)
├── sado-minipacs/       # DICOM 파일 관리
├── sado-orchestrator/   # 워크플로우 오케스트레이션 ⭐ NEW
└── sado-bff/            # 프론트엔드 API 조합 ⭐ FOCUSED
```

### 확정 기술 스택
| 기술 | 용도 | 학습 주차 |
|------|------|----------|
| **Kafka** | 메시지 브로커 | Week 5-7 |
| **MySQL 8.0** | RDB (JPA + MyBatis, **Multi-tenancy**) ⭐ | Week 2 |
| **Temporal** | 워크플로우 | Week 8-10 |
| **SeaweedFS** | Object Storage | Week 11 |
| **Keycloak** | 인증/인가, **tenant_id claim** ⭐ | Week 12-13 |
| **Redis** | 분산락/캐싱 | Week 14 |

### 16주 Phase 구성
- **Phase 1** (Week 1-4): POC (멀티모듈 + 기본 E2E)
- **Phase 2** (Week 5-7): Kafka 마스터
- **Phase 3** (Week 8-10): Temporal 마스터
- **Phase 4** (Week 11-16): SeaweedFS, Keycloak, Redis, 완성

---

## 🚀 다음 단계 (Week 1 시작 시)

### 준비물
- [ ] Docker & Docker Compose 설치
- [ ] IntelliJ IDEA (또는 VSCode)
- [ ] Java 21 JDK
- [ ] Git

### Week 1 첫 작업
1. Gradle 멀티모듈 프로젝트 구조 생성
2. 6개 모듈 디렉토리 및 build.gradle 설정
3. Docker Compose (PostgreSQL, Kafka 환경)
4. Common, Infrastructure 모듈 기본 구조

**참조 문서**: `07_최종_구현_계획.md` > Week 1 섹션

---

## ❓ 자주 묻는 질문 (FAQ)

### Q1. 어떤 문서를 먼저 읽어야 하나요?
**A**:
- **학습 시작**: 01_개발_정책 → 07_최종_구현_계획 → 09_Temporal 전략
- **빠른 이해**: 07_최종_구현_계획만 읽기
- **의사결정 이해**: 04_요구사항_재검토 → 05_권장사항 → 07_최종_구현_계획

### Q2. 초기 문서(02-05)와 최종 문서(07)가 다른데요?
**A**: 07_최종_구현_계획이 정답입니다. 02-05는 의사결정 과정 기록입니다.
변경사항은 06_변경사항_요약에서 확인하세요.

### Q3. Auth 모듈이 있는 문서도 있고 없는 문서도 있는데요?
**A**: 초기 계획(02)에는 있었지만, 최종 계획(07)에서 제거되었습니다.
Gateway가 Keycloak을 직접 연동합니다.

### Q4. Phase 1-5와 Week 1-16이 혼재되어 있는데요?
**A**: 초기 계획은 Phase 1-5였지만, 최종 계획은 Week 1-16입니다.
07_최종_구현_계획을 따르세요.

### Q5. "선택 필요"로 표시된 기술이 있는데요?
**A**: 초기 문서(02)에만 있습니다. 모든 기술은 이미 확정되었습니다:
- Kafka (메시지 브로커)
- MySQL 8.0 (데이터베이스, JPA + MyBatis)
- Temporal (워크플로우)
- SeaweedFS (스토리지)
- Keycloak (인증)
- Redis (분산락/캐싱)

### Q6. PostgreSQL에서 MySQL로 변경된 이유는?
**A**: 학습 목적과 실용성을 고려한 결정입니다:
- **JPA + MyBatis 혼용 전략**: 도메인 로직은 JPA, 복잡한 조회는 MyBatis로 분리하여 각 기술의 장점을 모두 학습
- **FK 제약조건 없는 설계**: 물리적 FK 없이 논리적 관계만 유지하여 DB 마이그레이션 유연성 확보
- **Master-Slave 구조**: 읽기/쓰기 분리를 통한 성능 최적화 학습 (미래 배포 대비)
- **실무 활용도**: MySQL은 실무에서 가장 많이 사용되는 RDB 중 하나
- **AWS 배포 고려**: RDS MySQL Multi-AZ 배포를 염두에 둔 설계

자세한 내용은 `10_JPA_MyBatis_혼용_전략.md` 참조

### Q7. Redis를 어떻게 활용하나요?
**A**: `08_Redis_활용_요구사항.md`에서 6가지 시나리오와 코드 예시를 확인하세요.

### Q8. Claude Code는 코드를 작성해주나요?
**A**: `01_개발_정책.md`를 읽어보세요. 학습 프로젝트 정책상 핵심 로직은 사용자가 직접 작성합니다.

### Q9. Week 1부터 시작하면 되나요?
**A**: 네, `07_최종_구현_계획.md`의 Week 1 섹션을 참조하여 시작하세요.

### Q10. 멀티테넌시는 어떻게 구현하나요? ⭐ NEW
**A**: `12_멀티테넌시_설계_가이드.md`를 참조하세요.
- **전략**: Shared Database with tenant_id 컬럼
- **식별**: Keycloak JWT에 tenant_id claim 포함
- **격리**: JPA @Filter로 자동 필터링
- **시기**: Week 2부터 즉시 적용

**구현 핵심**:
- TenantAwareEntity 상속으로 모든 Entity에 tenant_id 자동 관리
- TenantFilterAspect로 모든 JPA 쿼리에 WHERE tenant_id = ? 자동 추가
- ThreadLocal TenantContext로 요청별 tenant 관리
- Gateway에서 JWT 파싱 후 X-Tenant-ID 헤더로 전파

자세한 구현 방법은 `12_멀티테넌시_설계_가이드.md` 참조

---

## 📝 문서 업데이트 이력

| 날짜 | 변경 내용 | 작성자 |
|------|-----------|--------|
| 2025-12-20 | **멀티테넌시 기능 추가** (12번 문서 신규, 07/10/learning/week02 업데이트, FAQ Q10 추가) ⭐ | Claude |
| 2025-12-20 | PostgreSQL → MySQL 변경, JPA+MyBatis 혼용 전략 반영 (07, 10, 11 문서) | Claude |
| 2025-12-20 | AWS 배포 아키텍처 및 Master-Slave 전략 추가 (07) | Claude |
| 2025-12-20 | 10_JPA_MyBatis_혼용_전략.md 신규 작성 | Claude |
| 2025-12-20 | 11_블로그_작성_가이드.md 신규 작성 | Claude |
| 2025-12-20 | INDEX 업데이트 (신규 문서 링크, FAQ 추가, 기술 스택 변경) | Claude |
| 2025-12-20 | 문서 전면 리팩토링 (용어 통일, 오래된 정보 업데이트, 문서 역할 명확화) | Claude |
| 2025-12-20 | 용어 통일 규칙 및 문서 읽기 흐름도 추가 (00_INDEX) | Claude |
| 2025-12-20 | 참고용 문서에 경고 배너 추가 (02-05) | Claude |
| 2025-12-20 | 최종 문서 역할 강화 (06-09) | Claude |
| 2025-12-20 | FAQ 섹션 대폭 강화 (00_INDEX) | Claude |
| 2025-12-20 | 문서 통합 관리 INDEX 생성, 파일명 넘버링 | Claude |
| 2025-12-20 | Redis 활용 요구사항 추가 | Claude |
| 2025-12-20 | 최종 구현 계획 확정 (16주) | Claude |
| 2025-12-20 | 요구사항 재검토 및 권장사항 작성 | Claude |
| 2025-12-20 | 초기 요구사항 및 아키텍처 검토 | Claude |

---

## 💡 문서 작성 원칙

1. **단일 진실 원천 (Single Source of Truth)**: `07_최종_구현_계획.md`
2. **문서 간 일관성**: 모든 문서는 최종 계획과 일치
3. **학습 중심**: 왜 이렇게 설계했는지 근거 명시
4. **실용성**: 구현 시 바로 참조 가능한 코드 예시 포함

---

## 📧 문서 관련 문의

문서 내용이 불명확하거나 추가 설명이 필요한 경우, Claude Code에게 질문하세요.

**좋은 질문 예시**:
- "07_최종_구현_계획.md의 Week 5 Kafka 학습 내용을 더 자세히 설명해줘"
- "Orchestrator와 BFF의 차이를 코드 예시로 보여줘"
- "Redis 분산락 예시 코드를 MiniPACS에 어떻게 적용하는지 알려줘"

---

**최종 업데이트**: 2025-12-20
**다음 업데이트 예정**: Week 1 완료 후 (학습 노트 추가)
