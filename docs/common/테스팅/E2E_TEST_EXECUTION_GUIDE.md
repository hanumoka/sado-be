# SADO Week 8 E2E 테스트 실행 가이드

**작성일**: 2026-01-01
**목적**: Week 8 MiniPACS POC의 전체 워크플로우 검증
**전제 조건**: `E2E_TEST_SETUP_GUIDE.md` 완료

---

## 테스트 시작 전 체크리스트

실행 전 아래 항목을 모두 확인:

- [ ] Docker 인프라 실행 중 (MySQL, SeaweedFS, Kafka)
- [ ] Backend 서버 실행 중 (http://localhost:10201/actuator/health)
- [ ] Frontend 서버 실행 중 (http://localhost:10300)
- [ ] DICOM 테스트 파일 준비됨 (최소 3개)
- [ ] 브라우저 DevTools 열기 가능 (F12)

---

## 테스트 케이스 개요

| ID | 시나리오 | 소요 시간 | 우선순위 | 목적 |
|----|---------|----------|----------|------|
| **E2E-001** | 단일 CT 업로드 | 5분 | P0 | 기본 워크플로우 검증 |
| **E2E-002** | 단일 MR 업로드 | 3분 | P0 | Modality 다양성 검증 |
| **E2E-003** | Multi-instance 업로드 | 10분 | P0 | 시리즈 네비게이션 검증 |
| **E2E-007** | 중복 업로드 | 3분 | P1 | Idempotency 검증 |

**최소 필수 테스트**: E2E-001 + E2E-003 (15분)
**완전 검증**: 모든 테스트 (21분)

---

## E2E-001: 단일 CT 파일 업로드 테스트

### 목표
Upload → Storage → Database → DICOMweb API → Viewer 전체 워크플로우 검증

### 테스트 파일
- **파일**: `C:/Users/amagr/project/sado/test-data/e2e/single-file/ct-sample-01.dcm`
- **Modality**: CT
- **예상 크기**: 1-10MB

---

### Step 1: Frontend 업로드 (2분)

**실행**:
1. 브라우저에서 http://localhost:10300/upload 접속
2. **F12** 눌러 DevTools 열기 → **Console** 탭 선택
3. **Network** 탭도 활성화 (업로드 시간 측정용)
4. `ct-sample-01.dcm` 파일을 드래그 앤 드롭

**검증**:
- [ ] 업로드 진행률 0% → 100% 표시
- [ ] 성공 메시지 표시: "1 file uploaded successfully" (또는 유사)
- [ ] Console에 에러 없음 (빨간색 메시지 없음)
- [ ] Network 탭에서 업로드 시간 확인 (목표: <5초)

**스크린샷 캡처**:
- 업로드 성공 화면
- Network 탭 (업로드 시간)
- Console (에러 없음 확인)

**실패 시**:
- Console 에러 로그 복사
- Network 탭에서 실패한 요청 확인 (Response 탭)
- Backend 터미널 로그 확인

---

### Step 2: Database 검증 (2분)

**실행**:
```bash
# MySQL 접속
docker exec -it sado-mysql mysql -usado_user -psado1234 sado_db
```

**쿼리 1: Patient 생성 확인**
```sql
SELECT
    id,
    dicom_patient_id,
    patient_name,
    birth_date,
    matching_status,
    created_at
FROM patients
ORDER BY created_at DESC LIMIT 1;
```

**기대 결과**:
- 1행 반환
- `dicom_patient_id`: DICOM 파일의 Patient ID 값
- `patient_name`: DICOM 파일의 Patient Name 값
- `matching_status`: PENDING 또는 CONFIRMED

**쿼리 2: Study/Series/Instance 계층 확인**
```sql
-- 최신 업로드의 전체 계층 조회
SELECT
    p.dicom_patient_id AS PatientID,
    p.patient_name AS PatientName,
    st.study_instance_uid AS StudyUID,
    st.study_description AS StudyDesc,
    se.series_instance_uid AS SeriesUID,
    se.modality AS Modality,
    se.number_of_instances AS InstanceCount,
    i.sop_instance_uid AS SOPInstanceUID,
    i.storage_path AS StoragePath,
    i.file_size AS FileSize
FROM instances i
JOIN series se ON i.series_id = se.id
JOIN studies st ON se.study_id = st.id
JOIN patients p ON st.patient_id = p.id
WHERE i.id = (SELECT id FROM instances ORDER BY created_at DESC LIMIT 1);
```

**검증**:
- [ ] 1행 반환
- [ ] `Modality` = 'CT'
- [ ] `InstanceCount` = 1
- [ ] `StoragePath` 형식: `studies/{study_uid}/series/{series_uid}/instances/{sop_uid}.dcm`
- [ ] `FileSize` > 0

**쿼리 3: DICOM 메타데이터 레코드 확인**
```sql
SELECT
    id,
    instance_id,
    JSON_EXTRACT(metadata_json, '$."00080060"') AS Modality,
    JSON_EXTRACT(metadata_json, '$."00280010"') AS Rows,
    JSON_EXTRACT(metadata_json, '$."00280011"') AS Columns
FROM dicom_metadata_records
WHERE instance_id = (SELECT id FROM instances ORDER BY created_at DESC LIMIT 1);
```

**검증**:
- [ ] 1행 반환
- [ ] `Modality` JSON 값 존재
- [ ] `Rows`, `Columns` 값 > 0

**MySQL 종료**:
```sql
EXIT;
```

---

### Step 3: S3 스토리지 검증 (1분)

**실행**:
```bash
# S3 파일 목록 조회
aws s3 ls s3://minipacs/studies/ --recursive --endpoint-url http://localhost:10405
```

**기대 출력 예시**:
```
2026-01-01 14:30:00   2451234 studies/1.2.840.113619.../series/1.2.840.113619.../instances/1.2.840.113619....dcm
```

**검증**:
- [ ] 파일이 `studies/` 경로 하위에 존재
- [ ] 파일 크기가 원본과 동일 (약 2.4MB 등)

**파일 다운로드 및 무결성 확인**:
```bash
# 위 출력에서 전체 경로 복사 (studies/... 부분)
STORAGE_PATH="studies/1.2.840.113619.../series/1.2.840.113619.../instances/1.2.840.113619....dcm"

# S3에서 파일 다운로드
aws s3 cp "s3://minipacs/$STORAGE_PATH" /tmp/s3-retrieved.dcm --endpoint-url http://localhost:10405

# 원본 파일과 크기 비교
ls -lh C:/Users/amagr/project/sado/test-data/e2e/single-file/ct-sample-01.dcm
ls -lh /tmp/s3-retrieved.dcm
# 크기가 동일해야 함
```

**dcmdump로 유효성 검증** (선택 사항):
```bash
# dcm4che 도구 사용 (설치된 경우)
dcmdump /tmp/s3-retrieved.dcm | head -30

# 기대: DICOM 태그 출력 (에러 없음)
```

---

### Step 4: DICOMweb API 검증 (3분)

#### 4-1. QIDO-RS: Study 검색

**실행**:
```bash
curl -H "Accept: application/dicom+json" \
     http://localhost:10201/dicomweb/studies | jq .
```

**기대 출력** (JSON 배열):
```json
[
  {
    "0020000D": {
      "vr": "UI",
      "Value": ["1.2.840.113619..."]
    },
    "00100020": {
      "vr": "LO",
      "Value": ["PATIENT123"]
    },
    "00100010": {
      "vr": "PN",
      "Value": [{"Alphabetic": "DOE^JOHN"}]
    },
    "00081030": {
      "vr": "LO",
      "Value": ["CT CHEST"]
    }
  }
]
```

**검증**:
- [ ] HTTP 200 OK
- [ ] 배열에 최소 1개 Study 포함
- [ ] StudyInstanceUID (`0020000D`) 존재
- [ ] PatientID (`00100020`) 존재

#### 4-2. QIDO-RS: Series 검색

**StudyInstanceUID를 위 출력에서 복사 후**:
```bash
STUDY_UID="1.2.840.113619..."

curl -H "Accept: application/dicom+json" \
     "http://localhost:10201/dicomweb/studies/$STUDY_UID/series" | jq .
```

**검증**:
- [ ] SeriesInstanceUID (`0020000E`) 존재
- [ ] Modality (`00080060`) = "CT"
- [ ] NumberOfSeriesRelatedInstances > 0

#### 4-3. QIDO-RS: Instance 검색

**SeriesInstanceUID도 복사 후**:
```bash
SERIES_UID="1.2.840.113619..."

curl -H "Accept: application/dicom+json" \
     "http://localhost:10201/dicomweb/studies/$STUDY_UID/series/$SERIES_UID/instances" | jq .
```

**검증**:
- [ ] SOPInstanceUID (`00080018`) 존재
- [ ] Rows (`00280010`), Columns (`00280011`) 존재

#### 4-4. WADO-RS: Instance 다운로드

**SOPInstanceUID도 복사 후**:
```bash
SOP_UID="1.2.840.113619..."

curl -H "Accept: application/dicom" \
     "http://localhost:10201/dicomweb/studies/$STUDY_UID/series/$SERIES_UID/instances/$SOP_UID" \
     -o /tmp/wado-retrieved.dcm
```

**검증**:
```bash
ls -lh /tmp/wado-retrieved.dcm
# 파일 크기 확인 (원본과 동일)

# MD5 체크섬 비교 (선택 사항)
md5sum C:/Users/amagr/project/sado/test-data/e2e/single-file/ct-sample-01.dcm
md5sum /tmp/wado-retrieved.dcm
# 체크섬이 동일하면 100% 무결성 보장
```

---

### Step 5: Cornerstone3D 뷰어 검증 (2분)

**실행**:
1. 브라우저: http://localhost:10300
2. **Patient List** 페이지로 이동
3. 업로드한 환자 찾기 (Patient Name 또는 Patient ID)
4. 환자 클릭 → **Study List** 페이지
5. Study 클릭 → **Series List** (썸네일 그리드)
6. Series 클릭 → **DICOM Viewer** 페이지

**Viewer 페이지 검증**:
- [ ] DICOM 이미지가 Viewport에 렌더링됨 (검은 배경에 회색조 이미지)
- [ ] 이미지 정보 표시: Patient Name, Study Date, Modality 등
- [ ] Console에 에러 없음 (F12 → Console 탭)

**도구 테스트**:

**Window/Level 조정**:
- 마우스 좌클릭 + 드래그
- **검증**: 이미지 밝기/대비 변화 확인

**Zoom**:
- 마우스 우클릭 + 상하 드래그
- **검증**: 이미지 확대/축소 확인

**Pan (이동)**:
- 마우스 중간 클릭 + 드래그
- **검증**: 이미지 이동 확인

**Stack Scroll** (단일 이미지이므로 작동 안 할 수 있음):
- 마우스 휠 스크롤
- **검증**: "1 / 1" 표시 (단일 인스턴스)

**스크린샷 캡처**:
- Viewer에서 이미지 렌더링 성공 화면
- 도구 사용 중 화면 (Window/Level, Zoom)

---

### Step 6: 테스트 결과 기록

**결과 요약**:
```markdown
## E2E-001: 단일 CT 업로드 - [✅ PASS / ❌ FAIL]

**실행 일시**: 2026-01-XX HH:MM
**파일**: ct-sample-01.dcm
**파일 크기**: X.X MB

### 검증 결과:
- [✅/❌] Step 1: Frontend 업로드 성공
- [✅/❌] Step 2: Database 레코드 생성 (Patient/Study/Series/Instance)
- [✅/❌] Step 3: S3 파일 저장 확인
- [✅/❌] Step 4: DICOMweb API 응답 정상 (QIDO-RS, WADO-RS)
- [✅/❌] Step 5: Cornerstone3D 이미지 렌더링
- [✅/❌] Console/Backend 로그 에러 없음

### 성능:
- 업로드 시간: X.X초 (목표: <5초)
- QIDO-RS 응답 시간: XXXms (목표: <500ms)
- Viewer 로딩 시간: X.X초 (목표: <2초)

### 발견된 이슈:
[없음 또는 Bug ID]

### 스크린샷:
- 업로드 성공: [파일명 또는 경로]
- Viewer 렌더링: [파일명 또는 경로]
```

---

## E2E-002: 단일 MR 파일 업로드 테스트

### 목표
CT와 동일한 워크플로우를 MR Modality로 검증

### 테스트 파일
- **파일**: `C:/Users/amagr/project/sado/test-data/e2e/single-file/mr-sample-01.dcm`
- **Modality**: MR

### 실행 방법
**E2E-001과 동일한 5단계 수행**, 단 아래 차이점 확인:
- Database 쿼리에서 `Modality` = 'MR' 확인
- Viewer에서 MR 이미지 특성 확인 (CT와 다른 조직 대비)

### 간소화된 체크리스트
- [ ] 업로드 성공
- [ ] Modality = 'MR' 확인
- [ ] Viewer 렌더링 성공
- [ ] 소요 시간: ~3분

---

## E2E-003: Multi-Instance 업로드 테스트

### 목표
동일 Series에 속하는 여러 Instance 업로드 및 Stack Navigation 검증

### 테스트 파일
- **디렉토리**: `C:/Users/amagr/project/sado/test-data/e2e/multi-file/brainix/`
- **파일 수**: 8-10개 (또는 30개 전체)
- **Modality**: MR

---

### Step 1: Multi-File 업로드

**실행**:
1. http://localhost:10300/upload 접속
2. **Ctrl + A** (또는 Shift + 클릭)로 brainix 폴더 내 모든 .dcm 파일 선택
3. 드래그 앤 드롭으로 한 번에 업로드

**검증**:
- [ ] 진행률 표시: "Uploading X / Y files"
- [ ] 모든 파일 업로드 완료: "Y files uploaded successfully"
- [ ] Console 에러 없음

**예상 소요 시간**: 30개 파일 기준 ~1-2분

---

### Step 2: Database 검증

```sql
-- 최신 Series의 Instance 개수 확인
SELECT
    se.series_instance_uid,
    se.modality,
    se.number_of_instances,
    COUNT(i.id) AS actual_count
FROM series se
LEFT JOIN instances i ON se.id = i.series_id
WHERE se.id = (SELECT series_id FROM instances ORDER BY created_at DESC LIMIT 1)
GROUP BY se.id;
```

**검증**:
- [ ] `number_of_instances` = 업로드한 파일 수 (예: 30)
- [ ] `actual_count` = `number_of_instances` (일치)

---

### Step 3: Viewer Stack Navigation

**실행**:
1. Patient List → Study → Series 클릭 → Viewer
2. **Viewport 하단** 확인: "1 / 30" (현재 인스턴스 / 전체)

**Navigation 테스트**:

**마우스 휠 스크롤**:
- 위로 스크롤: 다음 이미지 (2/30, 3/30, ...)
- 아래로 스크롤: 이전 이미지
- **검증**: 이미지가 순차적으로 변경됨

**키보드 화살표**:
- **→ (오른쪽 화살표)**: 다음 이미지
- **← (왼쪽 화살표)**: 이전 이미지
- **Home**: 첫 번째 이미지 (1/30)
- **End**: 마지막 이미지 (30/30)

**검증**:
- [ ] Stack navigation 정상 작동
- [ ] 모든 이미지 렌더링 가능 (1번부터 30번까지 순회)
- [ ] 이미지 간 전환 시 깜빡임 최소화
- [ ] Console 에러 없음

**스크린샷**:
- Stack position 표시 화면 (예: "15 / 30")
- 여러 slice 화면 (예: 1/30, 15/30, 30/30)

---

## E2E-007: 중복 업로드 (Idempotency) 테스트

### 목표
동일한 DICOM 파일을 2회 업로드 시 중복 Instance가 생성되지 않는지 확인

### 실행

**1차 업로드**:
1. `ct-sample-01.dcm` 업로드 (E2E-001과 동일)
2. Database에서 Instance ID 기록:
   ```sql
   SELECT id, sop_instance_uid FROM instances ORDER BY created_at DESC LIMIT 1;
   ```

**2차 업로드** (동일 파일):
1. 동일한 `ct-sample-01.dcm` 파일을 다시 업로드
2. 에러 메시지 또는 성공 메시지 확인

**검증**:

**옵션 A: Idempotency 구현된 경우**:
- [ ] 업로드 성공 메시지 (하지만 새 Instance 생성 안 함)
- [ ] Database Instance 개수 변화 없음:
  ```sql
  SELECT COUNT(*) FROM instances WHERE sop_instance_uid = '1.2.840...';
  -- 기대: 1 (증가하지 않음)
  ```
- [ ] Backend 로그: "Instance already exists, skipping" (또는 유사)

**옵션 B: Idempotency 미구현 (에러 발생)**:
- [ ] 업로드 실패 메시지: "Duplicate SOP Instance UID"
- [ ] Database 무결성 유지 (외래 키 위반 없음)

**이슈 기록**:
- Idempotency가 없다면 → Medium 우선순위 이슈로 기록
- 중복 업로드 시 에러 처리가 불친절하다면 → Low 우선순위 개선 사항

---

## 테스트 완료 후 작업

### 1. 테스트 리포트 작성
- `E2E_TEST_REPORT_Week8.md` 파일에 모든 결과 기록
- 템플릿은 해당 파일 참조

### 2. 스크린샷 정리
```bash
# 스크린샷 저장 위치
mkdir -p C:/Users/amagr/project/sado/sado_docs/testing/screenshots/week8

# 파일 명명 규칙:
# e2e-001-upload-success.png
# e2e-001-viewer-rendering.png
# e2e-003-stack-navigation.png
```

### 3. 버그 이슈 생성
발견된 Critical/High 이슈는:
- `sado_docs/testing/ISSUES.md`에 기록
- Bug Report 템플릿 사용 (계획서 참조)

### 4. 100% 완성 선언
**모든 P0 테스트 통과 시**:
- PROGRESS.md 업데이트: Week 8 → 100%
- 다음 단계 계획 (Week 9+ 또는 다른 작업)

---

## 빠른 참조: 필수 명령어

### MySQL 접속
```bash
docker exec -it sado-mysql mysql -usado_user -psado1234 sado_db
```

### S3 파일 목록
```bash
aws s3 ls s3://minipacs/studies/ --recursive --endpoint-url http://localhost:10405
```

### DICOMweb Study 검색
```bash
curl -H "Accept: application/dicom+json" http://localhost:10201/dicomweb/studies | jq .
```

### Backend 헬스 체크
```bash
curl http://localhost:10201/actuator/health
```

---

## 문제 해결

### 업로드 실패 (500 에러)
- Backend 로그 확인 (터미널)
- Database 연결 확인 (MySQL 컨테이너 실행 중?)
- S3 연결 확인 (SeaweedFS 컨테이너 실행 중?)

### Viewer에서 이미지 안 나옴
- Console 에러 확인 (CORS, 404, WADO-URI 실패 등)
- DICOMweb API 응답 확인 (Step 4)
- Cornerstone3D 초기화 로그 확인

### Database 레코드 없음
- Backend 로그에서 Exception 확인
- Transaction rollback 발생 가능성 (S3 업로드 실패 시)

---

## 다음 단계

모든 테스트 완료 후:
1. **테스트 리포트 검토** (`E2E_TEST_REPORT_Week8.md`)
2. **Week 8 완성 선언** (99% → 100%)
3. **Week 9+ 계획 논의** 또는 **다른 작업 진행**

**축하합니다! E2E 테스트를 완료하면 SADO Mini-PACS POC가 완성됩니다.** 🎉
