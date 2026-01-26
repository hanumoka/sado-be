# SADO Week 8 E2E 테스트 준비 가이드

**작성일**: 2026-01-01
**목적**: Week 8을 100% 완성하기 위한 E2E 테스트 환경 구축

---

## 1. DICOM 테스트 파일 다운로드

### 옵션 A: Medical Connections DICOM Test Objects (추천)

**장점**: 빠른 다운로드, 다양한 modality, 소형 파일 크기

**다운로드 방법**:

1. **브라우저에서 아래 URL 접속**:
   ```
   https://www.medicalconnections.co.uk/kb/DICOM_test_images/
   ```

2. **추천 다운로드 파일** (우클릭 → 다른 이름으로 저장):

   **CT 파일**:
   - CT-MONO2-16-ankle.dcm
   - 또는 CT-MONO2-16-brain.dcm
   - 저장 위치: `C:/Users/amagr/project/sado/test-data/e2e/single-file/ct-sample-01.dcm`

   **MR 파일**:
   - MR-MONO2-16-head.dcm
   - 또는 MR-MONO2-8-16x-heart.dcm
   - 저장 위치: `C:/Users/amagr/project/sado/test-data/e2e/single-file/mr-sample-01.dcm`

   **US 파일**:
   - US-MONO2-8-8x-execho.dcm
   - 또는 US-RGB-8-epicard.dcm
   - 저장 위치: `C:/Users/amagr/project/sado/test-data/e2e/single-file/us-sample-01.dcm`

### 옵션 B: OsiriX DICOM Sample Dataset (Multi-instance 테스트)

**장점**: 실제 임상 데이터와 유사한 multi-slice 데이터셋

**다운로드 방법**:

1. **브라우저에서 아래 URL 접속**:
   ```
   https://www.osirix-viewer.com/resources/dicom-image-library/
   ```

2. **BRAINIX 데이터셋 다운로드**:
   - 파일명: `BRAINIX.zip` 또는 `BRAINIX.tar.gz`
   - 크기: 약 30MB
   - 내용: MR Brain 스캔, 약 30개 인스턴스

3. **압축 해제**:
   ```bash
   # ZIP 파일인 경우
   cd C:/Users/amagr/project/sado/test-data/e2e/multi-file
   unzip BRAINIX.zip -d brainix/

   # 또는 tar.gz 파일인 경우
   tar -xzf BRAINIX.tar.gz -C brainix/
   ```

4. **파일 확인**:
   ```bash
   ls -lh C:/Users/amagr/project/sado/test-data/e2e/multi-file/brainix/
   # 예상: IM-0001-0001.dcm, IM-0001-0002.dcm, ... (30개 파일)
   ```

### 옵션 C: 로컬 샘플 파일 사용 (빠른 테스트)

프로젝트에 이미 포함된 샘플 DICOM 파일 사용:

```bash
# Frontend node_modules에서 샘플 파일 복사
cp "C:/Users/amagr/project/sado/sado_fe/node_modules/jpeg-lossless-decoder-js/tests/data/jpeg_lossless_sel1-8bit.dcm" \
   "C:/Users/amagr/project/sado/test-data/e2e/single-file/local-sample-01.dcm"
```

**주의**: 이 파일들은 매우 작고 제한적이므로, 실제 테스트에는 옵션 A 또는 B 권장

---

## 2. 파일 구조 확인

다운로드 완료 후 아래 구조로 정리되어 있는지 확인:

```
C:/Users/amagr/project/sado/test-data/e2e/
├── single-file/
│   ├── ct-sample-01.dcm       # CT 파일
│   ├── mr-sample-01.dcm       # MR 파일
│   └── us-sample-01.dcm       # US 파일
└── multi-file/
    └── brainix/
        ├── IM-0001-0001.dcm   # MR Brain 인스턴스 #1
        ├── IM-0001-0002.dcm   # MR Brain 인스턴스 #2
        └── ...                # 30개 파일
```

**검증 명령**:
```bash
# 파일 개수 확인
ls -1 C:/Users/amagr/project/sado/test-data/e2e/single-file/ | wc -l
# 예상: 3 (또는 그 이상)

ls -1 C:/Users/amagr/project/sado/test-data/e2e/multi-file/brainix/ | wc -l
# 예상: 30 (또는 비슷한 수)
```

---

## 3. 인프라 실행 및 검증

### 3.1 Docker 인프라 시작

```bash
# 1. Backend 디렉토리로 이동
cd C:/Users/amagr/project/sado/sado_be

# 2. Docker Compose로 인프라 시작
docker-compose up -d

# 3. 모든 컨테이너가 시작될 때까지 대기 (약 10-20초)
sleep 15

# 4. 컨테이너 상태 확인
docker ps --filter "name=sado-*" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

**기대 출력**:
```
NAMES                       STATUS          PORTS
sado-mysql                  Up 15 seconds   0.0.0.0:10100->3306/tcp
sado-seaweedfs-master       Up 15 seconds   0.0.0.0:10400->9333/tcp, 0.0.0.0:10401->19333/tcp
sado-seaweedfs-volume       Up 15 seconds   0.0.0.0:10402->8080/tcp
sado-seaweedfs-filer        Up 15 seconds   0.0.0.0:10403->8888/tcp, 0.0.0.0:10404->18888/tcp, 0.0.0.0:10405->8333/tcp
sado-kafka                  Up 15 seconds   0.0.0.0:10102->9092/tcp
sado-zookeeper              Up 15 seconds   0.0.0.0:10101->2181/tcp
```

**문제 해결**:
- 컨테이너가 계속 재시작되는 경우:
  ```bash
  docker logs sado-mysql  # 로그 확인
  docker-compose down      # 중지
  docker-compose up -d     # 재시작
  ```

### 3.2 MySQL 연결 테스트

```bash
# MySQL 접속 테스트
docker exec -it sado-mysql mysql -usado_user -psado1234 -e "SHOW DATABASES;"
```

**기대 출력**:
```
+--------------------+
| Database           |
+--------------------+
| information_schema |
| performance_schema |
| sado_db            |
+--------------------+
```

### 3.3 SeaweedFS S3 API 테스트

```bash
# SeaweedFS 헬스 체크
curl http://localhost:10405/

# 기대 출력 (에러가 아닌 응답이면 OK):
# {"error":"no volume server found"} 또는 다른 JSON 응답
```

**S3 버킷 생성** (아직 없는 경우):

```bash
# AWS CLI 사용 (설치 필요)
aws s3 mb s3://minipacs --endpoint-url http://localhost:10405

# 성공 시 출력:
# make_bucket: minipacs
```

**AWS CLI 미설치 시**:
- SeaweedFS Web UI 사용: http://localhost:10403
- 브라우저에서 수동으로 버킷 생성 가능

### 3.4 Backend 서버 시작

**새 터미널 열기** (PowerShell 또는 Git Bash):

```bash
cd C:/Users/amagr/project/sado/sado_be

# Gradle로 Backend 실행
./gradlew :sado-minipacs:bootRun

# 또는 Windows에서:
gradlew.bat :sado-minipacs:bootRun
```

**성공 시 마지막 로그**:
```
Started SadoMiniPacsApplication in X.XXX seconds (process running for X.XXX)
```

**헬스 체크**:
```bash
# 다른 터미널에서
curl http://localhost:10201/actuator/health

# 기대 출력:
# {"status":"UP"}
```

### 3.5 Frontend 서버 시작

**또 다른 새 터미널 열기**:

```bash
cd C:/Users/amagr/project/sado/sado_fe

# NPM으로 Frontend 실행
npm run dev
```

**성공 시 출력**:
```
  VITE v7.2.4  ready in XXX ms

  ➜  Local:   http://localhost:10300/
  ➜  Network: use --host to expose
  ➜  press h + enter to show help
```

**브라우저 테스트**:
- URL: http://localhost:10300
- 기대: SADO 홈페이지 로드

---

## 4. 전체 인프라 체크리스트

테스트 시작 전 아래 항목을 모두 확인:

- [ ] MySQL 컨테이너 실행 중 (`docker ps`)
- [ ] SeaweedFS 컨테이너 실행 중 (master, volume, filer)
- [ ] Kafka & Zookeeper 실행 중
- [ ] MySQL 접속 가능 (`sado_db` 데이터베이스 존재)
- [ ] SeaweedFS S3 API 응답 (port 10405)
- [ ] S3 버킷 `minipacs` 생성됨
- [ ] Backend 서버 실행 중 (port 10201, /actuator/health UP)
- [ ] Frontend 서버 실행 중 (port 10300, 브라우저 로드 가능)
- [ ] DICOM 테스트 파일 3개 이상 준비됨

**모든 체크리스트 완료 시 → E2E 테스트 진행 가능**

---

## 5. 빠른 시작 스크립트 (선택 사항)

전체 인프라를 한 번에 시작하는 스크립트:

**파일 생성**: `C:/Users/amagr/project/sado/scripts/start-e2e-test.sh`

```bash
#!/bin/bash
set -e

echo "====================================="
echo "  SADO E2E 테스트 환경 시작"
echo "====================================="

# 1. Docker 인프라 시작
echo "[1/5] Docker 인프라 시작..."
cd C:/Users/amagr/project/sado/sado_be
docker-compose up -d
sleep 15

# 2. 컨테이너 상태 확인
echo "[2/5] 컨테이너 상태 확인..."
docker ps --filter "name=sado-*" --format "table {{.Names}}\t{{.Status}}"

# 3. MySQL 테스트
echo "[3/5] MySQL 연결 테스트..."
docker exec -it sado-mysql mysql -usado_user -psado1234 -e "SELECT 'MySQL OK' as Status;"

# 4. S3 버킷 생성 (이미 있으면 무시)
echo "[4/5] S3 버킷 확인/생성..."
aws s3 mb s3://minipacs --endpoint-url http://localhost:10405 2>/dev/null || echo "버킷 이미 존재"

# 5. 완료
echo "[5/5] 인프라 준비 완료!"
echo ""
echo "다음 단계:"
echo "  1. Backend 시작:  cd C:/Users/amagr/project/sado/sado_be && ./gradlew :sado-minipacs:bootRun"
echo "  2. Frontend 시작: cd C:/Users/amagr/project/sado/sado_fe && npm run dev"
echo "  3. 브라우저 접속:  http://localhost:10300"
echo ""
```

**실행 방법**:
```bash
chmod +x C:/Users/amagr/project/sado/scripts/start-e2e-test.sh
C:/Users/amagr/project/sado/scripts/start-e2e-test.sh
```

---

## 6. 문제 해결

### 문제: Docker 컨테이너가 시작 안 됨
**해결책**:
```bash
docker-compose down
docker-compose up -d --force-recreate
```

### 문제: Backend 빌드 실패
**해결책**:
```bash
./gradlew clean build
./gradlew :sado-minipacs:bootRun
```

### 문제: Frontend npm install 에러
**해결책**:
```bash
rm -rf node_modules package-lock.json
npm install
npm run dev
```

### 문제: Port 이미 사용 중
**해결책**:
```bash
# Windows에서 포트 사용 프로세스 확인
netstat -ano | findstr :10201

# 프로세스 종료
taskkill /PID <PID번호> /F
```

---

## 다음 단계

인프라 준비가 완료되면:
1. `E2E_TEST_EXECUTION_GUIDE.md` 참조하여 테스트 수행
2. 테스트 결과를 `E2E_TEST_REPORT_Week8.md`에 기록

**테스트 시작 전 최종 확인**: 모든 체크리스트 ✅
