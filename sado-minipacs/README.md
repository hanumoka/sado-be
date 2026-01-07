# SADO MiniPACS

DICOM 영상 관리 시스템 (Standalone PACS)

## 기술 스택

- Java 21
- Spring Boot 4.0.1
- DCM4CHE 5.29.1 (DICOM 라이브러리)
- Weasis OpenCV 4.9.0-dcm (이미지 처리)
- SeaweedFS (S3 호환 스토리지)
- MySQL 8.0

---

## 실행 방법

### 1. Gradle 명령어 실행 (권장)

```bash
# 프로젝트 루트에서 실행
./gradlew :sado-minipacs:bootRun
```

JVM 옵션이 `build.gradle`에 자동 설정되어 있어 추가 설정 불필요.

---

### 2. IntelliJ IDEA 실행

#### Step 1: Run Configuration 열기
- **Run** → **Edit Configurations...**

#### Step 2: Spring Boot 설정 추가/수정
- 좌측 **+** 버튼 → **Spring Boot** 선택
- 또는 기존 **MiniPacsApplication** 선택

#### Step 3: 기본 설정
| 항목 | 값 |
|------|-----|
| Name | MiniPacsApplication |
| Main class | `com.hanumoka.sado.minipacs.MiniPacsApplication` |
| Module | `sado-minipacs.main` |
| JDK | Java 21 |

#### Step 4: VM Options 추가 (필수)
1. **Modify options** 클릭
2. **Add VM options** 체크
3. 다음 내용 입력:

```
--add-opens java.desktop/javax.imageio.stream=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED
```

#### Step 5: 저장 및 실행
- **Apply** → **OK** → 실행 버튼 클릭

> **주의**: VM Options 미설정 시 JPMS 관련 `InaccessibleObjectException` 발생

---

## 배포 방법 (bootJar)

### 1. JAR 파일 빌드

```bash
# 프로젝트 루트에서 실행
./gradlew :sado-minipacs:bootJar
```

빌드 완료 후 JAR 파일 위치:
```
sado-minipacs/build/libs/sado-minipacs-0.0.1-SNAPSHOT.jar
```

### 2. JAR 파일 실행

```bash
java \
  --add-opens java.desktop/javax.imageio.stream=ALL-UNNAMED \
  --add-opens java.base/java.io=ALL-UNNAMED \
  -jar sado-minipacs-0.0.1-SNAPSHOT.jar
```

### 3. 환경 변수 설정 (선택)

```bash
# Linux/Mac
export DB_USERNAME=root
export DB_PASSWORD=root1234
export SEAWEEDFS_ACCESS_KEY=any
export SEAWEEDFS_SECRET_KEY=any

# Windows (PowerShell)
$env:DB_USERNAME="root"
$env:DB_PASSWORD="root1234"
$env:SEAWEEDFS_ACCESS_KEY="any"
$env:SEAWEEDFS_SECRET_KEY="any"
```

### 4. 프로덕션 실행 예시

```bash
java \
  --add-opens java.desktop/javax.imageio.stream=ALL-UNNAMED \
  --add-opens java.base/java.io=ALL-UNNAMED \
  -Dspring.profiles.active=prod \
  -DDB_USERNAME=prod_user \
  -DDB_PASSWORD=prod_password \
  -jar sado-minipacs-0.0.1-SNAPSHOT.jar
```

---

## Docker 배포 (예정)

### Dockerfile 예시

```dockerfile
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY build/libs/sado-minipacs-0.0.1-SNAPSHOT.jar app.jar

# OpenCV 네이티브 라이브러리 복사 (Linux)
COPY build/natives/libopencv_java.so /app/

ENV JAVA_OPTS="--add-opens java.desktop/javax.imageio.stream=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

## 주요 설정

### application.yml 주요 항목

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `server.port` | 10201 | 서버 포트 |
| `spring.datasource.url` | localhost:10100 | MySQL 연결 |
| `seaweedfs.s3.endpoint` | localhost:10405 | SeaweedFS S3 API |
| `seaweedfs.s3.bucket` | minipacs | DICOM 파일 버킷 |

### JVM 옵션 (필수)

DCM4CHE ImageIO가 Java 내부 클래스에 리플렉션 접근이 필요하여 다음 옵션 필수:

```
--add-opens java.desktop/javax.imageio.stream=ALL-UNNAMED
--add-opens java.base/java.io=ALL-UNNAMED
```

---

## 트러블슈팅

### 1. UnsatisfiedLinkError: no opencv_java

**원인**: OpenCV 네이티브 라이브러리 미발견

**해결**:
```bash
./gradlew :sado-minipacs:copyOpenCVNatives
```

### 2. InaccessibleObjectException

**원인**: JPMS 모듈 접근 차단

**해결**: JVM 옵션 추가
```
--add-opens java.desktop/javax.imageio.stream=ALL-UNNAMED
--add-opens java.base/java.io=ALL-UNNAMED
```

### 3. S3Exception: access key ID does not exist

**원인**: SeaweedFS 인증 실패

**해결**: 환경 변수 설정 또는 application.yml 기본값 확인
```yaml
seaweedfs:
  s3:
    access-key: ${SEAWEEDFS_ACCESS_KEY:any}
    secret-key: ${SEAWEEDFS_SECRET_KEY:any}
```

---

## API 문서

서버 실행 후 Swagger UI 접속:
```
http://localhost:10201/swagger-ui.html
```
