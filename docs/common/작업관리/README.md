# SADO 작업 관리 시스템

## 현재 상태

> **상태**: 대기 중 (MiniPACS POC 완료)
> **최종 업데이트**: 2026-01-15

MiniPACS POC가 100% 완료되어 작업 관리 시스템은 현재 대기 상태입니다.
다음 개발 페이즈 (AI 통합, 추가 기능) 시작 시 활성화됩니다.

---

## 📚 개요

3개의 Claude Code 터미널을 효율적으로 운영하기 위한 작업 분리 시스템입니다.

**핵심 아이디어**:
1. 사용자가 메인 터미널에서 작업 요청
2. Claude가 작업을 FE/BE/공통으로 분리하여 문서 생성
3. 사용자가 각 문서 경로를 해당 터미널에 전달
4. 각 터미널의 Claude가 문서 읽고 가이드 제공

---

## 📁 폴더 구조

```
tasks/
├── README.md                    # 이 파일
├── templates/                   # 작업 문서 템플릿
│   ├── TASK-TEMPLATE-FE.md
│   ├── TASK-TEMPLATE-BE.md
│   └── TASK-TEMPLATE-COMMON.md
├── archive/                     # 완료된 작업 보관
│   ├── TASK-001-FE.md
│   └── TASK-001-BE.md
├── TASK-002-FE.md              # 진행 중인 FE 작업
├── TASK-002-BE.md              # 진행 중인 BE 작업
└── TASK-003-COMMON.md          # 진행 중인 공통 작업
```

---

## 🔢 작업 ID 규칙

### 형식
```
TASK-{번호}-{타입}
```

### 타입
- **FE**: Frontend 작업 (React, TypeScript)
- **BE**: Backend 작업 (Spring Boot, Java)
- **COMMON**: 공통 작업 (문서, 인프라, 설정)

### 번호 부여
- 연속된 숫자 (001, 002, 003...)
- 연관된 FE/BE 작업은 같은 번호 사용
  - 예: TASK-001-FE, TASK-001-BE (Study 검색 기능)

### 예시
```
TASK-001-FE: Study 검색 UI 구현
TASK-001-BE: Study 검색 API 구현
TASK-002-COMMON: Week 8 회고 블로그 작성
TASK-003-FE: DICOM Viewer 썸네일 기능
```

---

## 🎯 작업 분리 기준

### Frontend (FE) 작업

**포함 사항**:
- UI 컴포넌트 개발
- 페이지 구현
- 상태 관리 (Zustand, TanStack Query)
- API 호출 로직
- 타입 정의 (TypeScript)
- 스타일링 (Tailwind CSS)
- Frontend 테스트

**예시**:
- "Study 목록 페이지 개선"
- "DICOM Viewer 기능 추가"
- "Admin Dashboard 통계 차트"

---

### Backend (BE) 작업

**포함 사항**:
- REST API 엔드포인트 개발
- 비즈니스 로직 (Service)
- 데이터 접근 (Repository)
- DTO 작성
- 엔티티 수정
- DB 스키마 변경
- Backend 테스트

**예시**:
- "Study 검색 API 구현"
- "DICOM 메타데이터 추출 로직"
- "Tier 변경 스케줄러"

---

### Common (공통) 작업

**포함 사항**:
- 문서 작성 (블로그, 가이드)
- 인프라 설정 (Docker, 포트 변경)
- E2E 테스트 작성
- 프로젝트 설정 파일
- 공통 스크립트

**예시**:
- "Week 8 회고 블로그 작성"
- "E2E 테스트 가이드 문서"
- "Docker Compose 메모리 제한 설정"

---

## 🔄 작업 워크플로우

### 1단계: 작업 요청 (메인 터미널)

사용자가 메인 터미널에서 작업 요청:
```
사용자: "Study 목록에 검색 기능을 추가해줘"
```

### 2단계: 작업 분석 및 문서 생성 (Claude - 메인)

Claude가 작업 분석 후 FE/BE 분리:

**생성 파일**:
- `tasks/TASK-001-FE.md` - Study 검색 UI 구현
- `tasks/TASK-001-BE.md` - Study 검색 API 구현

**문서 내용**:
- 요구사항
- 구현 가이드
- 체크리스트
- 테스트 시나리오

### 3단계: 문서 경로 전달 (사용자)

사용자가 각 터미널에 문서 경로 전달:

**FE 터미널**:
```
사용자: "sado_docs/tasks/TASK-001-FE.md 파일 읽고 작업 가이드 제공해줘"
```

**BE 터미널**:
```
사용자: "sado_docs/tasks/TASK-001-BE.md 파일 읽고 작업 가이드 제공해줘"
```

### 4단계: 작업 수행 (각 터미널)

각 터미널의 Claude가 문서 읽고 단계별 가이드 제공:

**FE Claude**:
1. 문서 내용 확인
2. SearchBar 컴포넌트 구현 가이드
3. API 연동 방법 안내
4. 테스트 방법 제공

**BE Claude**:
1. 문서 내용 확인
2. DTO 작성 가이드
3. Repository 쿼리 작성 안내
4. Controller 엔드포인트 추가 방법

### 5단계: 작업 완료 및 아카이브

작업 완료 후:
```bash
# 완료된 작업 아카이브
mv tasks/TASK-001-FE.md tasks/archive/
mv tasks/TASK-001-BE.md tasks/archive/
```

---

## 📝 작업 문서 작성 가이드

### 좋은 작업 문서 예시

✅ **구체적인 요구사항**:
```markdown
- Study 목록 화면에 검색 입력 필드 추가
- 검색어 입력 시 실시간 필터링 (debounce 300ms)
- 검색 결과가 없을 때 "검색 결과가 없습니다" 메시지 표시
```

✅ **명확한 파일 경로**:
```markdown
수정 파일:
- sado_fe/src/pages/StudyListPage.tsx
- sado_fe/src/components/study/SearchBar.tsx
```

✅ **단계별 가이드**:
```markdown
1단계: SearchBar 컴포넌트 작성
2단계: useStudySearch 훅 작성
3단계: API 연동
```

### 나쁜 작업 문서 예시

❌ **모호한 요구사항**:
```markdown
- 검색 기능 추가
```

❌ **파일 경로 없음**:
```markdown
- 검색 컴포넌트 만들기
```

❌ **가이드 없음**:
```markdown
- 검색 기능 구현하세요
```

---

## 🎯 작업 우선순위

### High (긴급)
- 블로킹 이슈 수정
- 핵심 기능 구현
- 보안 취약점 패치

### Medium (보통)
- 기능 개선
- 성능 최적화
- 문서 작성

### Low (낮음)
- UI 개선
- 리팩토링
- 선택적 기능

---

## 💡 팁

### 의존성 명시
FE 작업이 BE API를 필요로 하는 경우:
```markdown
## 의존성
- [ ] TASK-001-BE 완료 필요 (API 엔드포인트)
```

### 동시 작업 가능
의존성이 없는 작업은 동시 진행:
```
TASK-002-FE: UI 컴포넌트 개선 (독립)
TASK-003-BE: 스케줄러 추가 (독립)
→ 동시에 작업 가능!
```

### 순차 작업 필요
의존성이 있는 경우 순서 명시:
```
1. TASK-004-BE: API 먼저 구현
2. TASK-004-FE: API 완료 후 UI 연동
```

---

## 📊 진행 상황 추적

### 활성 작업 확인
```bash
# 진행 중인 작업 목록
ls tasks/TASK-*.md

# 완료된 작업 목록
ls tasks/archive/
```

### 작업 상태
문서 내 체크리스트로 추적:
```markdown
## 상태 추적
- [x] 작업 시작
- [x] 구현 완료
- [ ] 테스트 완료
- [ ] 완료 → archive 이동
```

---

## 🔧 유지보수

### 주기적 정리
- 완료된 작업은 archive로 이동
- 취소된 작업은 삭제 또는 archive
- 오래된 archive는 날짜별로 정리

### 템플릿 업데이트
- 반복적인 패턴 발견 시 템플릿 개선
- 새로운 섹션 추가 필요 시 템플릿 수정

---

## 📚 참고 문서

- `sado_docs/be/core/00_개발_방식_및_Claude_역할.md` - 학습 프로젝트 정책
- `sado_docs/be/core/07_최종_구현_계획.md` - 16주 로드맵
- `sado_docs/fe/core/02_최종_구현_계획.md` - FE 구현 계획

---

**작성일**: 2026-01-02
**최종 수정**: 2026-01-15
**버전**: 1.1.0
