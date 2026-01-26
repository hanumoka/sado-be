# SADO 프로젝트 포트 매핑

## 개요
모든 SADO 프로젝트 서비스는 **10000번대 포트**를 사용하여 기존 로컬 서비스와의 충돌을 회피합니다.

**변경일**: 2025-12-30

---

## 포트 범위 할당

| 범위 | 카테고리 | 용도 |
|------|---------|------|
| **10100-10199** | 인프라 (데이터 레이어) | 데이터베이스, 메시지 큐, 워크플로우 엔진 |
| **10200-10299** | 백엔드 API | Spring Boot 서비스, gRPC 엔드포인트 |
| **10300-10399** | 프론트엔드/프록시 | Vite 개발 서버, Gateway, 리버스 프록시 |
| **10400-10499** | 객체 스토리지 | SeaweedFS, MinIO, S3 호환 서비스 |
| **10500-10599** | AI/ML 서비스 | Triton Inference Server, 모델 엔드포인트 |
| **10600-10699** | 인증 | Keycloak, OAuth2 제공자 |
| **10700-10799** | 관찰성 | Prometheus, Grafana, OTLP 수집기 |
| **10800-10899** | DICOM 프로토콜 | C-STORE, C-FIND 리스너 |
| **10900-10999** | 예약 | 향후 확장 |

---

## 현재 활성 포트

### 인프라 서비스
| 서비스 | 기존 포트 | 새 포트 | 상태 | 접속 방법 |
|--------|----------|---------|------|----------|
| **MySQL** | 3306 | **10100** | ✅ 활성 | `mysql -h 127.0.0.1 -P 10100 -u sado_user -psado1234` |
| **Zookeeper** | 2181 | **10101** | ✅ 활성 | `localhost:10101` |
| **Kafka** | 9092 | **10102** | ✅ 활성 | `localhost:10102` |

### 백엔드 서비스
| 서비스 | 기존 포트 | 새 포트 | 상태 | 접속 URL |
|--------|----------|---------|------|----------|
| **Gateway API** | 8080 | **10200** | ✅ 활성 | http://localhost:10200 |
| **MiniPACS API** | 8081 | **10201** | ✅ 활성 | http://localhost:10201 |

### 프론트엔드
| 서비스 | 기존 포트 | 새 포트 | 상태 | 접속 URL |
|--------|----------|---------|------|----------|
| **Vite Dev Server** | 5173 | **10300** | ✅ 활성 | http://localhost:10300 |

### 스토리지 (10400번대)
| 서비스 | 기존 포트 | 새 포트 | 상태 | 접속 URL |
|--------|----------|---------|------|----------|
| **SeaweedFS Master HTTP** | 9333 | **10400** | ✅ 활성 | http://localhost:10400 |
| **SeaweedFS Master gRPC** | 19333 | **10401** | ✅ 활성 | - |
| **SeaweedFS Volume HTTP** | 8080 | **10402** | ✅ 활성 | http://localhost:10402 |
| **SeaweedFS Filer HTTP** | 8888 | **10403** | ✅ 활성 | http://localhost:10403 |
| **SeaweedFS Filer gRPC** | 18888 | **10404** | ✅ 활성 | - |
| **SeaweedFS Filer S3 API** | 8333 | **10405** | ✅ 활성 | http://localhost:10405 |

---

## 향후 추가 예정 포트

### 인프라 (10100번대)
| 서비스 | 기존 포트 | 새 포트 | 상태 |
|--------|----------|---------|------|
| **Redis** | 6379 | **10103** | 📋 계획됨 |

### AI/ML 서비스 (10500번대)
| 서비스 | 기존 포트 | 새 포트 | 상태 |
|--------|----------|---------|------|
| **Triton HTTP** | 8000 | **10500** | 📋 계획됨 |
| **Triton gRPC** | 8001 | **10501** | 📋 계획됨 |
| **Triton Metrics** | 8002 | **10502** | 📋 계획됨 |

### 인증 서비스 (10600번대)
| 서비스 | 기존 포트 | 새 포트 | 상태 |
|--------|----------|---------|------|
| **Keycloak HTTP** | 8180 | **10600** | 📋 계획됨 |
| **Keycloak Admin** | 9080 | **10601** | 📋 계획됨 |

### 관찰성 (10700번대)
| 서비스 | 기존 포트 | 새 포트 | 상태 |
|--------|----------|---------|------|
| **OTLP Collector** | 4317 | **10700** | 📋 계획됨 |

### DICOM 프로토콜 (10800번대)
| 서비스 | 기존 포트 | 새 포트 | 상태 | 비고 |
|--------|----------|---------|------|------|
| **DICOM C-STORE** | 11112 | **10800** | 📋 계획됨 | SCP 리스너 |
| **DICOM Standard** | 104 | **104** | 📋 유지 | 표준 포트 (root 권한 필요) |

---

## 빠른 접속 URL

### 개발 환경
- **프론트엔드**: http://localhost:10300
- **Gateway API**: http://localhost:10200
- **MiniPACS API**: http://localhost:10201
- **MySQL**: localhost:10100

### Health Check
```bash
# Gateway
curl http://localhost:10200/api/test/success

# MiniPACS
curl http://localhost:10201/actuator/health

# MySQL
mysql -h 127.0.0.1 -P 10100 -u sado_user -psado1234 -e "SELECT 1"
```

---

## Docker 포트 매핑 형식

**중요**: Docker Compose의 포트 매핑은 `"외부포트:내부포트"` 형식입니다.

```yaml
ports:
  - "10100:3306"  # 호스트는 10100으로 접속, 컨테이너 내부는 3306 유지
```

### 예시: MySQL
```yaml
services:
  mysql:
    ports:
      - "10100:3306"  # 외부(호스트): 10100, 내부(컨테이너): 3306
```

애플리케이션은 `localhost:10100`으로 접속하지만, 컨테이너 내부에서는 여전히 3306 포트를 사용합니다.

---

## Kafka 특별 주의사항

Kafka는 `KAFKA_ADVERTISED_LISTENERS`를 **반드시 외부 포트와 일치**시켜야 합니다:

```yaml
environment:
  KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:10102
ports:
  - "10102:9092"
```

불일치 시 클라이언트 연결 실패가 발생합니다.

---

## 롤백 방법

문제 발생 시 백업에서 복원:

```bash
# 서비스 중지
cd C:/Users/amagr/project/sado/sado_be
docker-compose down

# 백업 복원
cp C:/Users/amagr/project/sado/backups/pre-port-migration-2025-12-30/docker-compose.yml \
   C:/Users/amagr/project/sado/sado_be/

cp C:/Users/amagr/project/sado/backups/pre-port-migration-2025-12-30/gateway-application.yml \
   C:/Users/amagr/project/sado/sado_be/sado-gateway/src/main/resources/application.yml

cp C:/Users/amagr/project/sado/backups/pre-port-migration-2025-12-30/minipacs-application.yml \
   C:/Users/amagr/project/sado/sado_be/sado-minipacs/src/main/resources/application.yml

cp C:/Users/amagr/project/sado/backups/pre-port-migration-2025-12-30/vite.config.ts \
   C:/Users/amagr/project/sado/sado_fe/

# 서비스 재시작
docker-compose up -d
```

**백업 위치**: `C:/Users/amagr/project/sado/backups/pre-port-migration-2025-12-30/`

---

## 수정된 파일 목록

1. `C:\Users\amagr\project\sado\sado_be\docker-compose.yml` (4곳 변경)
2. `C:\Users\amagr\project\sado\sado_be\sado-gateway\src\main\resources\application.yml` (3곳 변경)
3. `C:\Users\amagr\project\sado\sado_be\sado-minipacs\src\main\resources\application.yml` (2곳 변경)
4. `C:\Users\amagr\project\sado\sado_fe\vite.config.ts` (server 블록 추가)
5. `C:\Users\amagr\project\sado\sado_docs\PORT_MAPPING.md` (신규 생성)

---

## 변경 이력

| 날짜 | 변경 내용 | 작성자 |
|------|----------|--------|
| 2025-12-30 | 초기 포트 매핑 (10000번대로 마이그레이션) | - |

---

## 참고 문서

- 구현 계획: `C:\Users\amagr\.claude\plans\snuggly-prancing-prism.md`
- 백엔드 설정: `sado_docs/be/guides/`
- 프론트엔드 설정: `sado_docs/fe/guides/`
