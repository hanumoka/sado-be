# SADO Week 8 E2E 테스트 문서

**목적**: Week 8 MiniPACS POC를 99% → 100% 완성하기 위한 종합 E2E 테스트

---

## 📁 문서 구조

| 파일명 | 설명 | 소요 시간 |
|--------|------|----------|
| **E2E_TEST_SETUP_GUIDE.md** | 테스트 환경 준비 (DICOM 파일 다운로드, 인프라 시작) | 30분 |
| **E2E_TEST_EXECUTION_GUIDE.md** | 실제 테스트 케이스 실행 방법 (E2E-001~007) | 15-60분 |
| **E2E_TEST_REPORT_Week8.md** | 테스트 결과 기록 템플릿 (작성용) | 작성 중 |

---

## 🚀 빠른 시작 (Quick Start)

### 1단계: 인프라 시작 (5분)

```bash
# 빠른 시작 스크립트 실행
cd C:/Users/amagr/project/sado
bash scripts/start-e2e-test.sh
```

**또는 수동으로**:
```bash
# Docker 인프라 시작
cd C:/Users/amagr/project/sado/sado_be
docker-compose up -d

# S3 버킷 생성
aws s3 mb s3://minipacs --endpoint-url http://localhost:10405
```

### 2단계: Backend & Frontend 시작 (2분)

**터미널 1 - Backend**:
```bash
cd C:/Users/amagr/project/sado/sado_be
./gradlew :sado-minipacs:bootRun
```

**터미널 2 - Frontend**:
```bash
cd C:/Users/amagr/project/sado/sado_fe
npm run dev
```

### 3단계: 인프라 검증 (1분)

```bash
# 인프라 상태 확인 스크립트
bash C:/Users/amagr/project/sado/scripts/verify-infrastructure.sh
```

**기대 출력**: ✅ 모든 인프라 정상 작동

### 4단계: DICOM 테스트 파일 준비 (15분)

**Medical Connections 샘플 파일 다운로드**:
1. https://www.medicalconnections.co.uk/kb/DICOM_test_images/ 접속
2. 아래 파일 다운로드:
   - `CT-MONO2-16-ankle.dcm` → 저장: `test-data/e2e/single-file/ct-sample-01.dcm`
   - `MR-MONO2-16-head.dcm` → 저장: `test-data/e2e/single-file/mr-sample-01.dcm`
   - `US-MONO2-8-8x-execho.dcm` → 저장: `test-data/e2e/single-file/us-sample-01.dcm`

**OsiriX BRAINIX 데이터셋 다운로드** (Multi-instance 테스트용):
1. https://www.osirix-viewer.com/resources/dicom-image-library/ 접속
2. BRAINIX.zip 다운로드
3. 압축 해제: `test-data/e2e/multi-file/brainix/`

### 5단계: 최소 필수 테스트 실행 (15분)

**E2E-001: 단일 CT 업로드**:
1. 브라우저: http://localhost:10300/upload
2. `ct-sample-01.dcm` 드래그 앤 드롭
3. 성공 확인 → Patient List → Study → Series → Viewer
4. 이미지 렌더링 확인

**E2E-003: Multi-instance 업로드**:
1. http://localhost:10300/upload
2. `brainix/` 폴더 내 모든 .dcm 파일 선택 → 업로드
3. Viewer에서 Stack Navigation 테스트 (마우스 휠, 화살표)

**결과 기록**:
- `E2E_TEST_REPORT_Week8.md` 파일에 결과 작성

---

## 📋 테스트 케이스 개요

| 테스트 ID | 시나리오 | 우선순위 | 소요 시간 | 가이드 위치 |
|----------|---------|----------|----------|------------|
| **E2E-001** | 단일 CT 업로드 | P0 (필수) | 5분 | EXECUTION_GUIDE.md → Section 3.1 |
| **E2E-002** | 단일 MR 업로드 | P0 (필수) | 3분 | EXECUTION_GUIDE.md → Section 3.2 |
| **E2E-003** | Multi-instance 업로드 | P0 (필수) | 10분 | EXECUTION_GUIDE.md → Section 3.3 |
| **E2E-007** | 중복 업로드 (Idempotency) | P1 (선택) | 3분 | EXECUTION_GUIDE.md → Section 3.4 |

**최소 필수**: E2E-001 + E2E-003 (15분)
**완전 검증**: 모든 테스트 (21분)

---

## 🎯 성공 기준

### Week 8 100% 완성 정의:

**기능적 기준**:
- [x] 3개 이상 DICOM 파일 업로드 성공 (CT, MR, US)
- [x] SeaweedFS S3에 올바른 경로로 저장
- [x] Database에 완전한 DICOM 계층 생성 (Patient/Study/Series/Instance)
- [x] DICOMweb API (QIDO-RS, WADO-RS) 정상 응답
- [x] Cornerstone3D 이미지 렌더링 성공
- [x] Multi-instance Stack Navigation 작동

**성능 기준 (POC 수준)**:
- 10MB 파일 업로드: <5초
- QIDO-RS 응답: <500ms
- WADO-RS 다운로드: <3초
- Cornerstone3D 로딩: <2초

**데이터 무결성**:
- MD5 체크섬 일치 (업로드 vs 다운로드)
- DICOM 태그 무결성 (원본 vs 조회)
- Database referential integrity (외래 키 일관성)

---

## 📊 테스트 워크플로우

```
┌─────────────────────────────────────────┐
│  1. 인프라 준비                           │
│  - Docker Compose up                    │
│  - Backend/Frontend 시작                 │
│  - DICOM 파일 다운로드                   │
└──────────┬──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│  2. E2E-001: 단일 CT 업로드               │
│  ✓ Upload → Storage → DB → API → Viewer│
└──────────┬──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│  3. E2E-002: 단일 MR 업로드 (선택)        │
│  ✓ Modality 다양성 검증                  │
└──────────┬──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│  4. E2E-003: Multi-instance 업로드        │
│  ✓ Stack Navigation 검증                │
└──────────┬──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│  5. E2E-007: 중복 업로드 (선택)           │
│  ✓ Idempotency 검증                     │
└──────────┬──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│  6. 테스트 리포트 작성                    │
│  - E2E_TEST_REPORT_Week8.md 작성        │
│  - 스크린샷 첨부                         │
│  - 이슈 기록                             │
└──────────┬──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│  7. Week 8 완성 선언 (99% → 100%)        │
│  - PROGRESS.md 업데이트                  │
│  - Week 9 계획 논의                      │
└─────────────────────────────────────────┘
```

---

## 🔧 유틸리티 스크립트

| 스크립트 | 설명 | 사용 시점 |
|---------|------|----------|
| `scripts/start-e2e-test.sh` | Docker 인프라 자동 시작 + S3 버킷 생성 | 테스트 시작 전 |
| `scripts/verify-infrastructure.sh` | 모든 서비스 상태 확인 (체크리스트) | 인프라 검증 |

**실행 방법**:
```bash
bash C:/Users/amagr/project/sado/scripts/start-e2e-test.sh
bash C:/Users/amagr/project/sado/scripts/verify-infrastructure.sh
```

---

## 🐛 문제 해결

### Docker 컨테이너가 시작 안 됨
```bash
docker-compose down
docker-compose up -d --force-recreate
```

### Backend 빌드 실패
```bash
cd C:/Users/amagr/project/sado/sado_be
./gradlew clean build
./gradlew :sado-minipacs:bootRun
```

### Frontend npm 에러
```bash
cd C:/Users/amagr/project/sado/sado_fe
rm -rf node_modules package-lock.json
npm install
npm run dev
```

### Port 이미 사용 중 (Windows)
```bash
# 포트 사용 프로세스 확인
netstat -ano | findstr :10201

# 프로세스 종료
taskkill /PID <PID번호> /F
```

---

## 📚 참조 문서

1. **계획서**: `~/.claude/plans/nifty-cooking-pebble.md`
   - 전체 프로젝트 현황 및 E2E 테스트 계획

2. **프로젝트 진행 상황**: `sado_docs/be/PROGRESS.md`
   - Week 1-8 완료 상태

3. **CURRENT_CONTEXT.md**: `sado_docs/be/CURRENT_CONTEXT.md`
   - 현재 작업 컨텍스트

---

## 📞 도움말

### 테스트 실행 중 막히는 경우:
1. `E2E_TEST_EXECUTION_GUIDE.md`의 해당 Section 재확인
2. `verify-infrastructure.sh` 스크립트로 인프라 상태 재확인
3. Backend/Frontend 터미널 로그 확인
4. 브라우저 Console (F12) 에러 확인

### 버그 발견 시:
1. `E2E_TEST_REPORT_Week8.md` Section 6에 기록
2. Bug Report 템플릿 사용 (계획서 참조)
3. Critical/High 이슈는 즉시 수정

---

## ✅ 체크리스트

테스트 시작 전 확인:
- [ ] Docker 인프라 실행 중
- [ ] Backend 서버 실행 중 (http://localhost:10201/actuator/health)
- [ ] Frontend 서버 실행 중 (http://localhost:10300)
- [ ] DICOM 테스트 파일 다운로드 완료
- [ ] S3 버킷 `minipacs` 생성됨
- [ ] 브라우저 DevTools 사용 가능 (F12)

---

## 🎉 완료 후

모든 테스트 통과 시:
1. **Week 8 100% 완성 선언**
2. **PROGRESS.md 업데이트** (99% → 100%)
3. **다음 단계 논의**:
   - Week 9: Backend Proxy + Audit Logging
   - 또는 Frontend Week 2-3: Patient List 구현
   - 또는 다른 우선순위 작업

**축하합니다! 🎉 SADO Mini-PACS POC 완성!**

---

**문서 작성일**: 2026-01-01
**마지막 업데이트**: 2026-01-01
