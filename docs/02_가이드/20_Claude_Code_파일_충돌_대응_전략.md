# Claude Code 파일 충돌 대응 전략

> **작성일**: 2025-12-26
> **목적**: Claude Code 사용 시 발생하는 파일 편집 충돌 문제의 근본적 해결
> **적용 범위**: sado 프로젝트 전체, 특히 문서 관리

---

## 📋 목차

1. [문제 정의](#1-문제-정의)
2. [원인 분석](#2-원인-분석)
3. [레벨별 대응 전략](#3-레벨별-대응-전략)
4. [문서 구조 개편안](#4-문서-구조-개편안)
5. [워크플로우 개선](#5-워크플로우-개선)
6. [실전 가이드](#6-실전-가이드)
7. [체크리스트](#7-체크리스트)

---

## 1. 문제 정의

### 증상
```
Error: File has been unexpectedly modified. Read it again before attempting to write it.
```

Claude Code의 Edit 도구 사용 시 반복적으로 발생하는 에러

### 영향
- 문서 업데이트 작업 중단
- 반복적인 재시도 필요
- 작업 효율성 저하
- 사용자 경험 악화

### 발생 빈도
- **높음**: CURRENT_CONTEXT.md, PROGRESS.md 같은 자주 업데이트되는 문서
- **중간**: 가이드 문서 (08~19번)
- **낮음**: 정적 문서 (README, CLAUDE.md)

---

## 2. 원인 분석

### 2.1 기술적 원인

#### A. Claude Code의 Edit 메커니즘
```
1. Read 도구로 파일 읽기
2. 파일의 modification timestamp 기록
3. 사용자/Claude의 작업 진행
4. Edit 도구 호출
5. ⚠️ 파일의 현재 timestamp와 기록된 timestamp 비교
6. 불일치 시 → "File has been unexpectedly modified" 에러
```

**문제점**:
- 파일 내용이 실제로 변경되지 않았어도 timestamp만 변경되면 에러 발생
- Windows 파일 시스템의 timestamp 정밀도 문제

#### B. 파일 시스템 이벤트 발생 원인

**1. 클라우드 동기화** (현재 환경: 미확인)
- OneDrive, Dropbox, Google Drive
- 메타데이터 동기화 중 timestamp 변경

**2. Git 관련**
- Git hooks (pre-commit, post-commit)
- Git auto-fetch
- Git LFS

**3. IDE/에디터**
- VS Code auto-save
- IntelliJ IDEA auto-sync
- 백그라운드 인덱싱

**4. 바이러스 백신/모니터링**
- 실시간 파일 스캔
- 파일 접근 로깅

**5. Windows 파일 시스템**
- NTFS 저널링
- 파일 핸들 유지
- CRLF 자동 변환 (core.autocrlf=true)

### 2.2 현재 환경 분석 결과

```bash
# 환경 점검 결과 (2025-12-26)
- OS: MINGW64_NT-10.0-26200 (Windows)
- Git 저장소: sado 폴더는 git 저장소 아님
- 클라우드 동기화: 프로세스 미발견
- Git autocrlf: true (CRLF 자동 변환 활성화)
```

**주요 의심 원인**:
1. ✅ `core.autocrlf=true` - 파일 읽기/쓰기 시 줄바꿈 변환
2. ✅ Windows 파일 시스템 timestamp 갱신
3. ⚠️ 백그라운드 프로세스 (에디터, 바이러스 백신 등)

---

## 3. 레벨별 대응 전략

### Level 1: 즉각 대응 (Immediate Workaround)
**목표**: 충돌 발생 시 즉시 해결

#### 방법 1: 파일 재읽기 후 재시도
```
1. 충돌 에러 발생
2. Claude가 Read 도구로 파일 다시 읽기
3. Edit 도구 재시도
```

**장점**: 간단, 빠름
**단점**: 근본 해결 아님, 반복 발생 가능

#### 방법 2: Write 도구 사용
```
1. Read로 전체 내용 읽기
2. 내용 수정
3. Write 도구로 전체 덮어쓰기
```

**장점**: Edit 메커니즘 우회
**단점**: 파일 전체를 덮어쓰므로 위험, 동시 편집 시 데이터 손실 가능

#### 방법 3: 변경안 텍스트 제공
```
1. Claude가 변경할 내용을 텍스트로 제공
2. 사용자가 직접 편집기에서 수정
```

**장점**: 충돌 없음, 사용자 제어
**단점**: 수동 작업 필요

---

### Level 2: 환경 최적화 (Environment Optimization)
**목표**: 충돌 발생 빈도 감소

#### A. Git 설정 최적화

```bash
# sado_docs 저장소에서
cd /c/Users/amagr/project/sado/sado_docs

# autocrlf 비활성화 (또는 input으로 변경)
git config core.autocrlf input

# 또는 프로젝트별 .gitattributes 사용
echo "*.md text eol=lf" >> .gitattributes
```

**효과**: CRLF 자동 변환으로 인한 timestamp 변경 방지

#### B. 클라우드 동기화 제외

```
OneDrive/Dropbox 설정에서:
- C:\Users\amagr\project\sado\ 폴더 동기화 제외
- 또는 .claudeignore 파일 사용 (가능한 경우)
```

#### C. IDE 자동 저장 설정

**VS Code**:
```json
{
  "files.autoSave": "off",
  "files.watcherExclude": {
    "**/sado_docs/**": true
  }
}
```

**IntelliJ IDEA**:
```
Settings → Appearance & Behavior → System Settings
→ Synchronization → Save files automatically 해제
```

---

### Level 3: 문서 구조 개편 (Structural Redesign)
**목표**: 충돌 발생 가능성을 구조적으로 감소

#### 현재 문제점
```
CURRENT_CONTEXT.md (6400 bytes)
├── 10개 섹션
├── 매 작업마다 업데이트
└── 여러 곳에서 동시 수정 가능성
```

#### 개편안: 분할 및 추가 전용 구조

**Before** (단일 파일):
```
tracking/
└── CURRENT_CONTEXT.md  (모든 정보)
```

**After** (분산 파일):
```
tracking/
├── CURRENT_CONTEXT.md        (읽기 전용 요약)
├── context/
│   ├── current_week.txt      (현재 Week만)
│   ├── recent_tasks.log      (추가 전용 로그)
│   ├── blocking_issues.md    (블로킹 이슈만)
│   └── next_actions.md       (다음 작업만)
└── snapshots/
    └── 2025-12-26.md         (일일 스냅샷)
```

**장점**:
- 작은 파일 → 충돌 가능성 감소
- 추가 전용 로그 → timestamp 이슈 최소화
- 단일 책임 원칙 → 동시 편집 충돌 방지

---

### Level 4: 워크플로우 개선 (Workflow Improvement)
**목표**: Claude와 사용자 간 협업 방식 재정의

#### 현재 워크플로우
```
사용자 요청 → Claude 직접 파일 수정 → 충돌 발생 → 재시도
```

#### 개선된 워크플로우 A: 변경안 제안 방식
```
사용자 요청 → Claude 변경안 제시 → 사용자 검토 → 사용자 직접 적용
```

**구현**:
```markdown
# Claude의 응답 예시

CURRENT_CONTEXT.md 업데이트 제안:

**섹션 2 (최근 완료된 작업)**:
다음 항목을 맨 위에 추가:
```
| 2025-12-26 | sado-common 구현 계획 | plan.md |
```

**적용 방법**:
1. CURRENT_CONTEXT.md 열기
2. 섹션 2 찾기
3. 위 내용 복사 붙여넣기
```

#### 개선된 워크플로우 B: Git Patch 방식
```
사용자 요청 → Claude patch 생성 → 사용자 git apply
```

**구현**:
```bash
# Claude가 생성한 patch 파일
cat > /tmp/update.patch << 'EOF'
--- a/sado_docs/be/tracking/CURRENT_CONTEXT.md
+++ b/sado_docs/be/tracking/CURRENT_CONTEXT.md
@@ -43,0 +44,1 @@
+| 2025-12-26 | sado-common 구현 계획 | plan.md |
EOF

# 사용자 적용
git apply /tmp/update.patch
```

#### 개선된 워크플로우 C: 추가 전용 로그 방식
```
사용자 요청 → Claude가 로그 파일에 추가만 → 충돌 없음
```

**구현**:
```bash
# Claude가 추가만 수행
echo "2025-12-26 | sado-common 구현 계획 | plan.md" >> tracking/context/recent_tasks.log

# 주기적으로 사용자가 병합
# (매일 또는 매주)
```

---

## 4. 문서 구조 개편안

### 4.1 현재 구조 분석

**충돌 위험도 분석**:

| 파일 | 크기 | 업데이트 빈도 | 충돌 위험 | 우선순위 |
|------|------|---------------|-----------|----------|
| CURRENT_CONTEXT.md | 6.4KB | 매일 여러 번 | 🔴 높음 | ⭐⭐⭐ |
| PROGRESS.md | 12KB | 주차 완료 시 | 🟡 중간 | ⭐⭐ |
| CHANGELOG.md | 가변 | 작업 완료 시 | 🟡 중간 | ⭐ |
| 가이드 문서 (08-19) | 10-30KB | 거의 없음 | 🟢 낮음 | - |

### 4.2 개편안: CURRENT_CONTEXT.md 분할

#### Phase 1: 최소 분할 (즉시 적용 가능)

```
tracking/
├── CURRENT_CONTEXT.md          # 메인 (읽기 중심)
└── context/
    ├── current_status.txt      # 섹션 1만 (현재 진행 상황)
    ├── recent_tasks.log        # 섹션 2만 (추가 전용)
    ├── blocking_issues.md      # 섹션 3만
    └── next_actions.md         # 섹션 4만
```

**CURRENT_CONTEXT.md는 자동 생성**:
```bash
# 매일 또는 필요 시 재생성
cat tracking/context/current_status.txt \
    tracking/context/recent_tasks.log \
    tracking/context/blocking_issues.md \
    tracking/context/next_actions.md \
    > tracking/CURRENT_CONTEXT.md
```

#### Phase 2: 완전 분리 (장기 계획)

```
tracking/
├── README.md                    # 전체 구조 설명
├── current/
│   ├── week.txt                # 현재 Week
│   ├── phase.txt               # 현재 Phase
│   └── status.txt              # 상태
├── tasks/
│   ├── recent.log              # 최근 작업 (추가 전용)
│   ├── blocking.md             # 블로킹 이슈
│   └── next.md                 # 다음 작업
├── snapshots/
│   ├── 2025-12-26.md           # 일일 스냅샷
│   └── weekly/
│       └── week-01.md          # 주간 스냅샷
└── generated/
    └── CURRENT_CONTEXT.md       # 자동 생성됨 (읽기 전용)
```

### 4.3 PROGRESS.md 개편

**현재 문제**:
- 16주차 전체를 하나의 파일에 관리
- 체크박스 업데이트 시 충돌 가능

**개편안**:
```
tracking/progress/
├── summary.md                   # 전체 요약 (읽기 전용)
├── phase-1/
│   ├── week-01.md              # Week 1 상세
│   ├── week-02.md
│   ├── week-03.md
│   └── week-04.md
├── phase-2/
│   └── ...
└── generated/
    └── PROGRESS.md              # 자동 생성됨
```

---

## 5. 워크플로우 개선

### 5.1 Claude의 역할 재정의

**기존 역할**:
- ❌ 문서 직접 수정 → 충돌 발생

**새로운 역할**:
1. ✅ **제안자**: 변경안 제시, 사용자가 적용
2. ✅ **추가자**: 로그 파일에 추가만 (충돌 없음)
3. ✅ **생성자**: 새 파일 생성 (충돌 없음)
4. ✅ **분석자**: 코드 리뷰, 설명, 가이드

### 5.2 문서 업데이트 프로토콜

#### 프로토콜 A: 변경안 제안 (추천)

**적용 대상**: CURRENT_CONTEXT.md, PROGRESS.md

**절차**:
```
1. 사용자: "문서 업데이트해줘"
2. Claude: 변경할 내용을 명확한 형식으로 제시
   ```
   CURRENT_CONTEXT.md 업데이트:

   [섹션 2: 최근 완료된 작업]
   다음 줄을 50번 라인에 삽입:
   | 2025-12-26 | sado-common 구현 | plan.md |
   ```
3. 사용자: 복사 → 붙여넣기 → 저장
4. Claude: "업데이트 완료했나요?" → 다음 작업
```

#### 프로토콜 B: 로그 추가 (자동화 가능)

**적용 대상**: 작업 로그, CHANGELOG

**절차**:
```bash
# Claude가 실행
echo "2025-12-26 | sado-common 구현 계획 수립 | plan.md" \
  >> tracking/context/recent_tasks.log

# 충돌 없음 (파일 끝에 추가만)
```

#### 프로토콜 C: 새 파일 생성 (충돌 없음)

**적용 대상**: 주간 스냅샷, 학습 노트

**절차**:
```bash
# Claude가 Write 도구로 새 파일 생성
tracking/snapshots/2025-12-26.md  # 기존 파일 없음 → 충돌 없음
```

---

## 6. 실전 가이드

### 6.1 충돌 발생 시 즉각 대응

#### 상황 1: Edit 도구 충돌
```
Error: File has been unexpectedly modified.
```

**대응**:
```
사용자: "편집기를 모두 종료했습니다."
Claude:
  1. Read 도구로 파일 재읽기
  2. 3초 대기
  3. Edit 도구 재시도
  4. 여전히 실패 시 → 변경안 텍스트 제공
```

#### 상황 2: 반복 충돌
```
3회 연속 충돌 발생
```

**대응**:
```
Claude: "변경안을 제공하겠습니다. 직접 적용해주세요."

[변경안 명확히 제시]
- 파일명
- 섹션명
- 라인 번호
- 추가/수정/삭제할 내용
```

### 6.2 환경 점검 체크리스트

#### 초기 설정 (프로젝트 시작 시 1회)

```bash
# 1. Git autocrlf 설정 확인
cd /c/Users/amagr/project/sado/sado_docs
git config core.autocrlf
# ✅ 권장: input 또는 false

# 2. .gitattributes 설정
cat > .gitattributes << 'EOF'
*.md text eol=lf
*.txt text eol=lf
*.log text eol=lf
EOF

# 3. 클라우드 동기화 확인
ps aux | grep -E "(dropbox|onedrive|google)" | grep -v grep
# ✅ 프로세스 없어야 함 (또는 폴더 제외 설정)

# 4. 백그라운드 프로세스 점검
# VS Code, IntelliJ 등 IDE 종료 후 작업
```

#### 정기 점검 (주 1회 또는 충돌 빈발 시)

```bash
# 1. 파일 timestamp 확인
stat sado_docs/be/tracking/CURRENT_CONTEXT.md

# 2. 수정 중인 파일 확인
lsof sado_docs/be/tracking/CURRENT_CONTEXT.md 2>/dev/null || echo "No locks"

# 3. Git 상태 확인
cd sado_docs && git status
```

### 6.3 문서 구조 개편 실행 계획

#### Step 1: 백업
```bash
cd /c/Users/amagr/project/sado/sado_docs/be/tracking
cp CURRENT_CONTEXT.md CURRENT_CONTEXT.md.backup
cp PROGRESS.md PROGRESS.md.backup
```

#### Step 2: 디렉토리 생성
```bash
mkdir -p context
mkdir -p snapshots
mkdir -p progress/phase-{1..4}
```

#### Step 3: CURRENT_CONTEXT.md 분할
```bash
# 섹션별로 추출 (사용자가 직접 또는 Claude 가이드)
# 예: 섹션 1 → context/current_status.txt
# 섹션 2 → context/recent_tasks.log
# 섹션 3 → context/blocking_issues.md
# 섹션 4 → context/next_actions.md
```

#### Step 4: 자동 생성 스크립트
```bash
cat > tracking/generate_current_context.sh << 'EOF'
#!/bin/bash
cat << 'HEADER'
# Claude Code 컨텍스트 복원 문서
> **자동 생성 문서** - 수정하지 마세요. context/ 폴더의 개별 파일을 수정하세요.
HEADER

cat context/current_status.txt
cat context/recent_tasks.log
cat context/blocking_issues.md
cat context/next_actions.md
EOF

chmod +x tracking/generate_current_context.sh
```

#### Step 5: 테스트
```bash
./tracking/generate_current_context.sh > tracking/CURRENT_CONTEXT_NEW.md
diff tracking/CURRENT_CONTEXT.md tracking/CURRENT_CONTEXT_NEW.md
```

#### Step 6: 적용
```bash
mv tracking/CURRENT_CONTEXT_NEW.md tracking/CURRENT_CONTEXT.md
```

---

## 7. 체크리스트

### 환경 최적화 체크리스트

- [ ] Git autocrlf 설정 변경 (true → input)
- [ ] .gitattributes 생성 (*.md text eol=lf)
- [ ] 클라우드 동기화 폴더 제외 설정
- [ ] IDE auto-save 비활성화
- [ ] 백그라운드 프로세스 점검

### 문서 구조 개편 체크리스트

- [ ] tracking/context/ 디렉토리 생성
- [ ] tracking/snapshots/ 디렉토리 생성
- [ ] CURRENT_CONTEXT.md 백업
- [ ] CURRENT_CONTEXT.md 섹션별 분할
- [ ] 자동 생성 스크립트 작성
- [ ] 테스트 및 검증
- [ ] 기존 파일 교체

### Claude 워크플로우 체크리스트

- [ ] Claude에게 "변경안 제안" 방식 요청
- [ ] 충돌 발생 시 텍스트 제공 요청
- [ ] 로그 파일은 추가만 수행
- [ ] 새 파일 생성 우선 활용

### 일일 운영 체크리스트

- [ ] 작업 전 IDE/에디터 모두 종료
- [ ] Claude 작업 중 파일 편집하지 않기
- [ ] 충돌 발생 시 즉시 대응 프로토콜 따르기
- [ ] 일일 스냅샷 생성 (자동화 권장)

---

## 8. 권장 사항

### 즉시 적용 (Priority 1)

1. **환경 설정**:
   ```bash
   cd /c/Users/amagr/project/sado/sado_docs
   git config core.autocrlf input
   echo "*.md text eol=lf" > .gitattributes
   ```

2. **워크플로우 변경**:
   - Claude에게 "변경안 제안 방식으로 진행해줘" 요청
   - 충돌 발생 시 직접 수정으로 전환

### 단기 적용 (Priority 2, 1주 이내)

3. **로그 파일 전환**:
   ```bash
   mkdir -p tracking/context
   touch tracking/context/recent_tasks.log
   ```
   - 최근 작업은 로그 파일에 추가만

### 중기 적용 (Priority 3, 2-4주 이내)

4. **CURRENT_CONTEXT.md 분할**:
   - Section 6.3 실행 계획 따라 진행
   - 주말에 집중 작업 권장

5. **자동화 스크립트**:
   - 일일 스냅샷 자동 생성
   - CURRENT_CONTEXT.md 자동 재생성

---

## 9. 결론

### 핵심 원칙

1. **예방**: 환경 최적화로 충돌 발생 최소화
2. **분산**: 큰 파일을 작은 파일로 분할
3. **추가**: 수정보다 추가 우선
4. **제안**: Claude는 변경안 제시, 사용자가 적용
5. **자동화**: 반복 작업은 스크립트로

### 기대 효과

- ✅ 파일 충돌 90% 이상 감소
- ✅ 작업 효율성 향상
- ✅ 사용자 경험 개선
- ✅ 문서 관리 체계화

### 다음 단계

1. 이 문서를 바탕으로 환경 최적화 실행
2. 워크플로우 개선 시범 적용
3. 2주 후 효과 검증 및 피드백
4. 필요 시 추가 개선

---

**마지막 업데이트**: 2025-12-26
**책임자**: 사용자 (amagr)
**검토 주기**: 월 1회 또는 문제 발생 시
