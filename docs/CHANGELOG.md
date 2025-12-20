# 📜 변경 이력 (Changelog)

> **작업 이력을 시간순으로 기록합니다**
> - 날짜 형식: YYYY-MM-DD
> - 최신 항목이 위에 오도록 작성

---

## 2025-12-21

### Added
- 📊 진행 상황 추적 시스템 구축
  - `PROGRESS.md` 생성 (전체 진행 상황 대시보드)
  - `CHANGELOG.md` 생성 (작업 이력)
  - `progress/` 폴더 생성 (주차별 상세 기록용)
  - `progress/README.md` 생성 (사용 가이드)

### Changed
- 없음

### Fixed
- 없음

---

## 2025-12-20

### Added
- 📚 프로젝트 문서화 완료
  - `12_멀티테넌시_설계_가이드.md` - 멀티테넌시 전체 설계 가이드
  - `13_mini-pacs-poc_분석_및_통합_계획.md` - Mini-PACS 통합 계획
  - `07_최종_구현_계획.md` 업데이트 (Week 2, 12, 13 멀티테넌시 추가)
  - `10_JPA_MyBatis_혼용_전략.md` 업데이트 (멀티테넌시 섹션 추가)
  - `00_INDEX.md` 업데이트 (문서 12, 13 추가)
  - `learning/week02-jpa-mybatis-mysql-multitenancy.md` - Week 2 학습 가이드

### Changed
- 🗑️ 멀티모듈 설정 초기화
  - `sado-common/` 디렉토리 제거
  - `sado-minipacs/` 디렉토리 제거
  - `settings.gradle` 원래 상태로 복원
  - 사용자가 직접 구현할 수 있도록 가이드 문서만 유지

### Fixed
- 없음

---

## 작업 이력 템플릿

아래 템플릿을 복사하여 새로운 작업 이력을 추가하세요:

```markdown
## YYYY-MM-DD

### Added
- 추가된 기능이나 파일

### Changed
- 변경된 내용

### Fixed
- 수정된 버그나 이슈

### Removed
- 제거된 기능이나 파일
```

---

**Changelog 작성 가이드**:
- 매일 작업 종료 시 해당 날짜 섹션 업데이트
- 중요한 변경사항은 간결하게 기록
- 관련 파일이나 문서 경로 명시
- 이슈나 PR 번호가 있다면 함께 기록
