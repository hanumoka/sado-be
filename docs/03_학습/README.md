# 학습 노트 (Learning Notes)

> **목적**: 16주 동안의 학습 내용을 주차별로 기록
> **작성 시기**: 각 주차 완료 후
> **활용**: 복습, 포트폴리오, 면접 준비, 블로그 작성

---

## 폴더 구조

```
learning/
├── README.md                  # 이 파일
│
├── week-01/                   # Week 1: Gradle 멀티모듈
│   ├── notes.md               # 학습 노트
│   ├── blog-draft.md          # 블로그 초안
│   └── troubleshooting.md     # 트러블슈팅
│
├── week-02/                   # Week 2: JPA + MyBatis + 멀티테넌시
│   ├── notes.md
│   ├── blog-draft.md
│   └── troubleshooting.md
│
├── ... (week-03 ~ week-16)
│
├── phase-01-retrospective.md  # Phase 1 회고 (Week 1-4)
├── phase-02-retrospective.md  # Phase 2 회고 (Week 5-7)
├── phase-03-retrospective.md  # Phase 3 회고 (Week 8-10)
├── phase-04-retrospective.md  # Phase 4 회고 (Week 11-16)
├── final-retrospective.md     # 16주 최종 회고
│
└── special/                   # 특별 학습 자료
    └── Redisson_RMapCache_학습가이드.md
```

---

## 주차별 작업 흐름

### 매주 해야 할 것

```
1. 학습 시작 전
   └── notes.md에 학습 목표 작성

2. 학습 중
   └── troubleshooting.md에 이슈 기록

3. 학습 완료 후
   ├── notes.md 완성
   └── blog-draft.md 초안 작성

4. 블로그 게시 후
   └── blog-draft.md에 게시 URL 기록
```

---

## 파일 템플릿 설명

### notes.md (학습 노트)

| 섹션 | 내용 |
|------|------|
| 학습 목표 | 이번 주 달성할 목표 (체크리스트) |
| 핵심 개념 정리 | 배운 개념을 자신의 언어로 정리 |
| 실습 기록 | 실습 과정, 시간, 결과 |
| 어려웠던 점 | 겪은 문제와 해결 과정 |
| 다음 주 계획 | 예습 및 준비 사항 |

### blog-draft.md (블로그 초안)

| 섹션 | 내용 |
|------|------|
| 들어가며 | 글의 배경, 독자가 얻을 것 |
| 문제 정의 | 해결하려는 문제 |
| 해결 방법 | 단계별 구현 과정 |
| 트러블슈팅 | 겪은 문제와 해결 |
| 핵심 정리 | 핵심 포인트 요약 |

### troubleshooting.md (트러블슈팅)

| 섹션 | 내용 |
|------|------|
| 이슈 목록 | 이슈 요약 테이블 |
| 이슈 상세 | 현상, 환경, 원인, 해결, 배운 점 |
| 예방 체크리스트 | 다음에 주의할 점 |

---

## 작성 원칙

### DO

- **구체적으로 작성**: "Kafka를 배웠다" → "Kafka Partition 전략 3가지 학습 및 구현"
- **자신의 언어로**: 공식 문서 복사 금지, 이해한 대로 정리
- **코드 스니펫 포함**: 핵심 코드만 간단히
- **에러 기록**: 겪었던 에러와 해결 과정 상세히
- **시간 기록**: 실제 학습 시간 기록

### DON'T

- 공식 문서 그대로 복사
- "배웠다", "이해했다"로 끝내기
- 전체 코드 붙여넣기 (Git 참조)
- 형식적 작성

---

## 작성 주기

| 문서 | 작성 시기 | 소요 시간 |
|------|----------|----------|
| notes.md | 매주 일요일 저녁 | 30분~1시간 |
| blog-draft.md | 주차 완료 후 | 1~2시간 |
| troubleshooting.md | 이슈 발생 시 즉시 | 10분/이슈 |
| phase-XX-retrospective.md | Phase 완료 후 | 1~2시간 |

---

## Phase 회고 일정

| Phase | 기간 | 회고 시기 | 파일 |
|-------|------|----------|------|
| Phase 1 | Week 1-4 | Week 4 완료 후 | phase-01-retrospective.md |
| Phase 2 | Week 5-7 | Week 7 완료 후 | phase-02-retrospective.md |
| Phase 3 | Week 8-10 | Week 10 완료 후 | phase-03-retrospective.md |
| Phase 4 | Week 11-16 | Week 16 완료 후 | phase-04-retrospective.md |
| 최종 | 전체 | 프로젝트 완료 후 | final-retrospective.md |

---

## 활용 방법

### 1. 복습
- 다음 Phase 시작 전 이전 Phase 회고 읽기
- 막힐 때 이전 학습 노트 참조

### 2. 블로그
- blog-draft.md를 Notion에 붙여넣기
- 다듬고 게시

### 3. 포트폴리오
- GitHub README에 학습 과정 요약
- 면접 시 구체적 사례 제시

### 4. 면접 준비
- "프로젝트에서 어려웠던 점" → troubleshooting.md 참조
- "어떻게 해결했는지" → 구체적 사례 제시

---

## 최종 목표

16주 후:
- **16개 주차 학습 폴더** (각각 notes, blog-draft, troubleshooting)
- **4개 Phase 회고**
- **1개 최종 회고**
- **블로그 게시글 16개 이상**

→ 이것이 **진짜 실력**이자 **포트폴리오 핵심 자산**입니다.

---

## 참고 문서

### 학습 관련
- [11_블로그_작성_가이드.md](../02_가이드/11_블로그_작성_가이드.md) - 블로그 작성 상세 가이드

> **Note**: 최종 구현 계획 문서는 [../99_아카이브/01_미래계획/](../99_아카이브/01_미래계획/)로 이동되었습니다.

### 다른 프로젝트 학습 노트
- [FE 03_학습/](../../02_프론트엔드/03_학습/) - Frontend 학습 노트 (POC 기간 BE와 통합됨)

> **Note**: AI 학습 노트는 [99_아카이브/01_AI모듈/](../../99_아카이브/01_AI모듈/)로 이동되었습니다.

### 게시된 블로그
- [04_블로그/01_발행됨/](../../04_블로그/01_발행됨/) - 게시된 블로그 글

---

*최종 수정: 2026-01-15*
