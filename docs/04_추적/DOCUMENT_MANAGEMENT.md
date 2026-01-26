# 문서 관리 가이드

> **목적**: PROGRESS.md 파일 크기 관리 및 Archive 전략
> **작성일**: 2025-12-31
> **적용 범위**: sado_docs/be/tracking/ 디렉토리

---

## 문제 정의

### 발생한 문제
- PROGRESS.md 파일이 890줄, 47KB로 비대해짐
- Claude Code Edit 도구 사용 시 반복적인 "File has been unexpectedly modified" 오류 발생
- 파일이 너무 커서 읽기/쓰기 성능 저하
- Week 8까지 진행 시 더욱 비대해질 예정 (예상: 1,500줄 이상)

### 근본 원인
- 모든 Week 상세 내용을 하나의 파일에 누적
- 완료된 내용도 계속 유지
- 학습 성과, 트러블슈팅 경험 등 상세 내용 누적

---

## 해결 방안

### 1. 파일 크기 제한

**PROGRESS.md 최대 크기 제한**:
- **최대 줄 수**: 500줄
- **최대 파일 크기**: 25KB
- **기준**: Claude Code Edit 도구 안정성, 읽기 성능

**초과 시 조치**:
- Week 완료 시점에 Archive로 분리
- 현재 진행 중인 Week만 유지
- 과거 Week는 링크로 대체

---

### 2. 디렉토리 구조

```
tracking/
├── PROGRESS.md                           # 현재 진행 중 작업만 (최대 500줄)
├── CURRENT_CONTEXT.md                    # Claude 컨텍스트 복원
├── DOCUMENT_MANAGEMENT.md                # 이 문서
├── CHANGELOG.md                          # 변경 이력
├── WEEK_4_FileAsset_완료_리포트.md      # 특수 리포트
└── archive/                              # 완료된 Week 보관소
    ├── PROGRESS_Week1-2_Archive.md       # Week 1-2 상세 기록
    ├── PROGRESS_Week3-4_Archive.md       # Week 3-4 상세 기록
    └── PROGRESS_Week1-4_Archive_2025-12-31.md  # 전체 백업본
```

---

### 3. Archive 전략

#### Archive 생성 시점
- **Week 완료 시**: 해당 Week 완료 후 즉시 Archive 생성
- **파일 크기 초과 시**: 500줄 또는 25KB 초과 시
- **Phase 완료 시**: Phase 완료 후 전체 Phase Archive 생성

#### Archive 파일 네이밍 규칙
```
PROGRESS_Week{시작}-{종료}_Archive.md
PROGRESS_Week{시작}-{종료}_Archive_{날짜}.md (백업본)
```

**예시**:
- `PROGRESS_Week1-2_Archive.md` - Week 1-2 상세 기록
- `PROGRESS_Week3-4_Archive.md` - Week 3-4 상세 기록
- `PROGRESS_Week1-4_Archive_2025-12-31.md` - 전체 백업본 (날짜 포함)

#### Archive 파일 내용
- **개요**: Week 목표, 진행률, 완료 현황
- **상세 체크리스트**: 100% 체크리스트 유지
- **학습 성과**: 기술적 이해, 트러블슈팅 경험
- **구현 파일 목록**: 생성/수정된 파일 목록
- **다음 단계**: 다음 Week 준비 사항
- **참조 문서**: 관련 가이드 링크

---

### 4. PROGRESS.md 구조 (간결화)

#### 필수 포함 내용
1. **현재 상태** (대시보드)
   - 현재 Week
   - 현재 Phase
   - 전체 진행률
   - 최종 목표
   - 최종 업데이트 날짜

2. **우선순위 재조정** (최신 계획)
   - 변경 사항
   - 제외된 기능
   - 상세 계획 링크

3. **Phase 별 진행 상황**
   - **완료된 Phase**: 요약 + Archive 링크만
   - **진행 중 Phase**: 상세 체크리스트 (현재 Week만)
   - **대기 중 Phase**: 간단한 테이블

4. **현재 Week 상세**
   - 완료 작업 체크리스트
   - 진행 예정 작업 체크리스트
   - 우선순위 (P0, P1, P2)

5. **최근 학습 성과** (최근 1-2 Week만)
   - 기술적 이해 (5-10개)
   - 트러블슈팅 경험 (2-3개)

6. **Archive 링크**
   - 완료된 Week 링크
   - 전체 백업본 링크

7. **마일스톤**
   - 전체 프로젝트 마일스톤

8. **참조 문서**
   - 주요 가이드 링크

9. **변경 이력** (최근 5-10개만)
   - 최신 변경 사항

#### 제외할 내용 (Archive로 이동)
- 완료된 Week 상세 체크리스트
- 완료된 Week 학습 성과 (전체 목록)
- 완료된 Week 트러블슈팅 (전체 목록)
- 완료된 Week 구현 파일 목록
- 과거 Week 블로그 작성 계획

---

## Week 완료 시 Archive 절차

### 1단계: Archive 파일 생성
```bash
# archive/ 디렉토리 확인
ls -la C:/Users/amagr/project/sado/sado_docs/be/tracking/archive

# 새로운 Archive 파일 생성 (예: Week 5-6 완료 시)
# C:/Users/amagr/project/sado/sado_docs/be/tracking/archive/PROGRESS_Week5-6_Archive.md
```

**Archive 파일 작성**:
- Week 5-6 개요
- 상세 체크리스트 (100%)
- 학습 성과 (전체)
- 트러블슈팅 경험 (전체)
- 구현 파일 목록
- 다음 단계

### 2단계: PROGRESS.md 업데이트
```markdown
### Phase 3: Storage Layer (Week 4-6) - 완료
**진행률**: 100%

| Week | 목표 | 상태 | 완료일 |
|------|------|------|--------|
| Week 4-6 | Storage Layer, FileAsset API | [x] 완료 | 2025-01-05 |

**주요 성과**:
- SeaweedFS S3 API 연동 완료
- DicomStorageService 구현
- FileAsset API 구현

**상세 내용**: [Week 4-6 Archive](archive/PROGRESS_Week4-6_Archive.md)
```

### 3단계: 현재 Week 섹션 업데이트
```markdown
### Phase 4: DICOMWeb APIs (Week 6-8) - 진행 중
**진행률**: 20%

#### Week 6-7: QIDO-RS, WADO-RS 구현
진행률: 20% (2/10 P0 작업 완료)

**완료 작업** (2025-01-05):
- [x] QIDO-RS 인터페이스 설계
- [x] DICOMweb 표준 조사

**진행 예정** (P0 우선순위):
- [ ] QIDO-RS Controller 구현
- [ ] WADO-RS Controller 구현
...
```

### 4단계: 학습 성과 섹션 갱신
- 이전 Week 학습 성과 제거
- 현재 Week 학습 성과만 유지 (5-10개)

### 5단계: 변경 이력 추가
```markdown
| 날짜 | 변경 내용 |
|------|----------|
| 2025-01-05 | **Week 5-6 완료** - Storage Layer 100% 완료, Week 5-6 Archive 생성, PROGRESS.md 크기 500줄 유지 |
```

---

## 예외 상황 처리

### Case 1: 파일 크기 급격히 증가
**증상**: 한 Week 진행 중 PROGRESS.md가 500줄 초과

**조치**:
1. 학습 성과 섹션 축소 (10개 → 5개)
2. 트러블슈팅 경험 축소 (5개 → 3개)
3. 변경 이력 최근 5개만 유지
4. 긴 코드 블록 → Gist 링크로 대체

### Case 2: Archive 파일이 너무 많음
**증상**: archive/ 디렉토리에 10개 이상 파일

**조치**:
1. Phase 단위로 통합 Archive 생성
   - `PROGRESS_Phase1_Week1-2_Archive.md`
   - `PROGRESS_Phase2_Week3-4_Archive.md`
2. 개별 Week Archive는 Phase Archive로 통합 후 삭제
3. 전체 백업본만 날짜별로 유지

### Case 3: Claude Code 여전히 파일 수정 실패
**증상**: 500줄 이하인데도 "File has been unexpectedly modified" 오류

**조치**:
1. 파일을 250줄로 더 축소
2. 학습 성과를 별도 파일로 분리 (`LEARNING_OUTCOMES.md`)
3. 트러블슈팅을 별도 파일로 분리 (`TROUBLESHOOTING.md`)
4. PROGRESS.md는 순수 진행 상황만 유지

---

## 체크리스트: Archive 생성 시

- [ ] archive/ 디렉토리 확인
- [ ] 새로운 Archive 파일 작성 (PROGRESS_Week{X}-{Y}_Archive.md)
- [ ] Archive 파일 내용 검증
  - [ ] 개요
  - [ ] 상세 체크리스트
  - [ ] 학습 성과
  - [ ] 트러블슈팅 경험
  - [ ] 구현 파일 목록
  - [ ] 다음 단계
  - [ ] 참조 문서
- [ ] PROGRESS.md 업데이트
  - [ ] 완료된 Phase 섹션 축소 + Archive 링크 추가
  - [ ] 현재 Week 섹션 업데이트
  - [ ] 학습 성과 섹션 갱신 (최근 Week만)
  - [ ] 변경 이력 추가
- [ ] 파일 크기 확인 (500줄 이하, 25KB 이하)
- [ ] 링크 정상 작동 확인
- [ ] Git commit (선택)

---

## 자동화 고려 사항 (미래)

### Bash 스크립트 예시
```bash
#!/bin/bash
# archive_week.sh - Week 완료 시 Archive 생성 자동화

WEEK_START=$1
WEEK_END=$2
COMPLETION_DATE=$3

ARCHIVE_DIR="C:/Users/amagr/project/sado/sado_docs/be/tracking/archive"
ARCHIVE_FILE="${ARCHIVE_DIR}/PROGRESS_Week${WEEK_START}-${WEEK_END}_Archive.md"

# Archive 파일 생성
echo "# SADO-BE Week ${WEEK_START}-${WEEK_END} 완료 기록 (Archive)" > "$ARCHIVE_FILE"
echo "" >> "$ARCHIVE_FILE"
echo "> **완료 기간**: ${COMPLETION_DATE}" >> "$ARCHIVE_FILE"
echo "> **진행률**: 100% 완료" >> "$ARCHIVE_FILE"
echo "" >> "$ARCHIVE_FILE"

# 기존 PROGRESS.md에서 해당 Week 내용 추출 (sed/awk 사용)
# ...

echo "Archive 생성 완료: $ARCHIVE_FILE"
```

**주의**: 현재는 수동 관리, 향후 자동화 검토

---

## 요약

### 핵심 원칙
1. **PROGRESS.md는 현재 진행 중인 작업만** - 최대 500줄
2. **완료된 Week는 Archive로 분리** - 상세 내용 보존
3. **Archive 파일 네이밍 규칙 준수** - `PROGRESS_Week{X}-{Y}_Archive.md`
4. **Week 완료 시 즉시 Archive 생성** - 파일 크기 관리
5. **학습 성과는 최근 1-2 Week만** - 과거는 Archive에서 참조

### 성공 기준
- [x] PROGRESS.md 크기 < 500줄
- [x] Claude Code Edit 도구 안정성
- [x] 모든 이력 보존 (Archive)
- [x] 링크 정상 작동
- [x] 읽기 성능 개선

---

*최종 업데이트: 2025-12-31*
