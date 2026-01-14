# SADO MiniPACS 배포 가이드

## 개요

SADO MiniPACS 프로젝트를 Docker를 사용하여 리눅스 서버에 배포하는 방법을 설명합니다.

### 서비스 구성

| 서비스 | 기술 | 포트 |
|--------|------|------|
| Frontend | React 19 + Nginx | 10300 |
| Backend | Spring Boot 4.0.1 | 10201 |
| Database | MySQL 8.0 | 10100 |
| Storage | SeaweedFS | 10400-10405 |

---

## 사전 요구사항

### 서버 환경

- **OS**: Linux (Ubuntu 20.04+ 권장)
- **RAM**: 최소 8GB (권장 16GB)
- **Disk**: 최소 50GB
- **Docker**: 20.10+
- **Docker Compose**: v2.0+

### Docker 설치 확인

```bash
docker --version
docker compose version
```

---

## 배포 파일 구조

```
sado/
├── docker-compose-deploy.yml    # 통합 Docker Compose
├── scripts/
│   └── deploy.sh                # 배포 스크립트
├── sado_be/
│   ├── Dockerfile               # Backend 이미지
│   ├── .dockerignore
│   └── seaweedfs-s3-config.json
└── sado_fe/
    ├── Dockerfile               # Frontend 이미지
    ├── nginx.conf               # Nginx 설정
    └── .dockerignore
```

---

## 배포 방법

### 방법 1: 배포 스크립트 사용 (권장)

#### 1. 소스 코드 서버로 복사

```bash
# Git clone
git clone <repository-url> /home/user/sado

# 또는 SCP
scp -r ./sado user@server:/home/user/
```

#### 2. 배포 스크립트 실행

```bash
cd /home/user/sado

# 스크립트 실행 권한 부여
chmod +x scripts/deploy.sh

# 서비스 시작
./scripts/deploy.sh start
```

#### 3. 배포 상태 확인

```bash
./scripts/deploy.sh status
```

---

### 방법 2: Docker Compose 직접 실행

```bash
cd /home/user/sado

# 이미지 빌드 및 서비스 시작
docker compose -f docker-compose-deploy.yml up -d --build

# 로그 확인
docker compose -f docker-compose-deploy.yml logs -f
```

---

## 배포 스크립트 명령어

```bash
./scripts/deploy.sh [command]
```

| 명령어 | 설명 |
|--------|------|
| `start` | 모든 서비스 시작 (기본값) |
| `stop` | 모든 서비스 중지 |
| `restart` | 모든 서비스 재시작 |
| `status` | 서비스 상태 및 헬스체크 |
| `logs` | 전체 로그 확인 |
| `logs [service]` | 특정 서비스 로그 (backend, frontend 등) |
| `build` | 이미지만 빌드 |
| `clean` | 컨테이너 및 볼륨 완전 삭제 |
| `init-bucket` | S3 버킷 생성 |

---

## S3 버킷 초기화

최초 배포 시 SeaweedFS에 `minipacs` 버킷을 생성해야 합니다.

### 자동 생성 (배포 스크립트 사용 시)

`./scripts/deploy.sh start` 실행 시 자동으로 버킷 생성을 시도합니다.

### 수동 생성

```bash
# AWS CLI 설치 필요
aws --endpoint-url http://localhost:10405 s3 mb s3://minipacs

# 버킷 확인
aws --endpoint-url http://localhost:10405 s3 ls
```

---

## 접속 URL

배포 완료 후 다음 URL로 접속할 수 있습니다.

| 서비스 | URL |
|--------|-----|
| **Frontend (웹 UI)** | `http://서버IP:10300` |
| **Backend API** | `http://서버IP:10201/api` |
| **Swagger UI** | `http://서버IP:10201/swagger-ui.html` |
| **API 문서** | `http://서버IP:10201/api-docs` |
| **Health Check** | `http://서버IP:10201/actuator/health` |

---

## 환경 변수 설정

### 기본값

`docker-compose-deploy.yml`에 기본값이 설정되어 있습니다.

```yaml
DB_USERNAME: sado_user
DB_PASSWORD: sado1234
DB_ROOT_PASSWORD: root1234
SEAWEEDFS_ACCESS_KEY: any
SEAWEEDFS_SECRET_KEY: any
```

### 커스텀 설정

프로젝트 루트에 `.env` 파일을 생성하여 오버라이드할 수 있습니다.

```bash
# .env 파일 생성
cat > .env << EOF
DB_USERNAME=myuser
DB_PASSWORD=mypassword
DB_ROOT_PASSWORD=myrootpassword
SEAWEEDFS_ACCESS_KEY=myaccesskey
SEAWEEDFS_SECRET_KEY=mysecretkey
EOF
```

---

## 방화벽 설정

다음 포트를 외부에서 접속할 수 있도록 열어야 합니다.

```bash
# Ubuntu (ufw)
sudo ufw allow 10300/tcp  # Frontend
sudo ufw allow 10201/tcp  # Backend API

# CentOS (firewalld)
sudo firewall-cmd --permanent --add-port=10300/tcp
sudo firewall-cmd --permanent --add-port=10201/tcp
sudo firewall-cmd --reload
```

---

## 로그 확인

### 전체 로그

```bash
./scripts/deploy.sh logs

# 또는
docker compose -f docker-compose-deploy.yml logs -f
```

### 특정 서비스 로그

```bash
# Backend
docker compose -f docker-compose-deploy.yml logs -f backend

# Frontend
docker compose -f docker-compose-deploy.yml logs -f frontend

# MySQL
docker compose -f docker-compose-deploy.yml logs -f mysql

# SeaweedFS
docker compose -f docker-compose-deploy.yml logs -f seaweedfs-filer
```

---

## 문제 해결

### 1. Backend 시작 실패

**증상**: Backend 컨테이너가 계속 재시작됨

**원인**: MySQL이 아직 준비되지 않음

**해결**:
```bash
# MySQL 상태 확인
docker compose -f docker-compose-deploy.yml logs mysql

# MySQL이 healthy 상태가 될 때까지 대기 (약 30초)
docker compose -f docker-compose-deploy.yml ps
```

### 2. Frontend에서 API 호출 실패

**증상**: 브라우저에서 API 요청 시 502 Bad Gateway

**원인**: Backend가 아직 시작되지 않음

**해결**:
```bash
# Backend 헬스체크
curl http://localhost:10201/actuator/health

# Backend 로그 확인
docker compose -f docker-compose-deploy.yml logs backend
```

### 3. DICOM 업로드 실패

**증상**: 파일 업로드 시 오류 발생

**원인**: S3 버킷이 없음

**해결**:
```bash
# 버킷 생성
aws --endpoint-url http://localhost:10405 s3 mb s3://minipacs

# 버킷 확인
aws --endpoint-url http://localhost:10405 s3 ls
```

### 4. 메모리 부족

**증상**: 컨테이너가 갑자기 종료됨

**원인**: 서버 메모리 부족

**해결**:
```bash
# 메모리 사용량 확인
docker stats

# JVM 메모리 조정 (docker-compose-deploy.yml)
JAVA_OPTS: "-Xms512m -Xmx2g"  # 기본값: -Xms1g -Xmx4g
```

### 5. 포트 충돌

**증상**: 서비스 시작 시 "port already in use" 오류

**해결**:
```bash
# 사용 중인 포트 확인
sudo netstat -tlnp | grep -E "10100|10201|10300|10400"

# 해당 프로세스 종료 또는 포트 변경
```

---

## 서비스 관리

### 서비스 중지

```bash
./scripts/deploy.sh stop

# 또는
docker compose -f docker-compose-deploy.yml down
```

### 서비스 재시작

```bash
./scripts/deploy.sh restart
```

### 완전 삭제 (데이터 포함)

```bash
./scripts/deploy.sh clean

# 또는 수동으로
docker compose -f docker-compose-deploy.yml down -v --rmi local
```

---

## 데이터 백업

### MySQL 데이터 백업

```bash
# 백업
docker compose -f docker-compose-deploy.yml exec mysql \
  mysqldump -u root -proot1234 sado_db > backup.sql

# 복원
docker compose -f docker-compose-deploy.yml exec -T mysql \
  mysql -u root -proot1234 sado_db < backup.sql
```

### SeaweedFS 데이터 백업

```bash
# 볼륨 위치 확인
docker volume inspect sado-seaweedfs-data

# 해당 경로의 데이터를 tar로 백업
```

---

## 업데이트 배포

코드 변경 후 재배포하는 방법입니다.

```bash
# 1. 최신 코드 가져오기
git pull origin main

# 2. 이미지 재빌드 및 재시작
./scripts/deploy.sh restart

# 또는
docker compose -f docker-compose-deploy.yml up -d --build
```

---

## 포트 요약

| 포트 | 서비스 | 설명 |
|------|--------|------|
| 10100 | MySQL | 데이터베이스 |
| 10201 | Backend | Spring Boot API |
| 10300 | Frontend | Nginx (React) |
| 10400 | SeaweedFS Master | 메타데이터 관리 |
| 10401 | SeaweedFS Master gRPC | Volume 등록 |
| 10402 | SeaweedFS Volume | 파일 저장소 |
| 10403 | SeaweedFS Filer | 파일시스템 인터페이스 |
| 10404 | SeaweedFS Filer gRPC | gRPC 인터페이스 |
| 10405 | SeaweedFS S3 | S3 호환 API |

---

## 참고 사항

- Backend 빌드 시간: 약 3-5분 (첫 빌드 시 의존성 다운로드)
- 전체 서비스 시작 시간: 약 2-3분 (MySQL 초기화 포함)
- 권장 서버 사양: 4 Core CPU, 16GB RAM, SSD 100GB
