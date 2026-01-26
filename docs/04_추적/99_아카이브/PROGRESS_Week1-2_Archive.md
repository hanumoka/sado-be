# SADO-BE Week 1-2 완료 기록 (Archive)

> **완료 기간**: 2025-12-26 ~ 2025-12-26
> **진행률**: 80% 완료
> **목표**: Gradle 멀티모듈, Docker 환경, 공통 모듈 구축

---

## 개요

Week 1-2는 SADO 프로젝트의 기반을 구축하는 단계입니다. Gradle 멀티모듈 프로젝트 구조를 설정하고, Docker Compose로 개발 환경을 구성하며, 공통 모듈(sado-common)을 구현했습니다.

---

## Phase 1: 기반 구축 (Week 1-2) - 완료

### 목표
- Gradle 멀티모듈 프로젝트 구조 이해
- kingarthur-app 공통 모듈 패턴 분석
- Version Catalog 사용법 학습
- REST API 기본 동작 이해
- GlobalExceptionHandler 동작 원리 이해
- Docker Compose 네트워크 설정 이해
- Spring Boot 4.0 새 기능 파악 (Hibernate 6.x)

### 완료 현황
- [x] Gradle 멀티모듈 구조 설정
- [x] sado-common 모듈 생성
- [x] sado-gateway 모듈 생성
- [x] Docker Compose 환경 구축
- [x] 기본 REST API 테스트 완료

---

## 상세 체크리스트 (80% 완료)

### 학습 목표
- [x] Gradle 멀티모듈 프로젝트 구조 이해
- [x] kingarthur-app 공통 모듈 패턴 분석
- [x] Version Catalog 사용법 학습
- [x] REST API 기본 동작 이해
- [x] GlobalExceptionHandler 동작 원리 이해
- [x] Docker Compose 네트워크 설정 이해
- [x] Spring Boot 4.0 새 기능 파악 (Hibernate 6.x)

### 실습 목표

#### 1. Gradle 멀티모듈 구조
- [x] `sado-common` 모듈 생성
- [x] `sado-gateway` 모듈 생성
- [x] `settings.gradle` 구성
- [x] Version Catalog (`gradle/libs.versions.toml`) 설정

#### 2. sado-common 핵심 클래스 구현
- [x] **ApiCode 인터페이스**
  - [x] `name()`, `getHttpStatus()`, `getMessage()`, `getCode()` 메서드 정의
- [x] **CommonCode enum (7자리 코드 체계)**
  - [x] SUCCESS (100000)
  - [x] INVALID_PARAMETER (1000001)
  - [x] RESOURCE_NOT_FOUND (1000002)
  - [x] INTERNAL_SERVER_ERROR (1000003)
- [x] **BusinessException**
  - [x] ApiCode 필드
  - [x] 2개 생성자
- [x] **ResourceNotFoundException**
  - [x] BusinessException 상속
- [x] **ApiResponse (Generic, Builder 패턴)**
  - [x] Generic 타입 `<T>` 지원
  - [x] `@Builder` 패턴
  - [x] `isSuccess()` 메서드 (HttpStatus 기반)
  - [x] 팩토리 메서드 (success, of)
- [x] **GlobalExceptionHandler**
  - [x] `@RestControllerAdvice`
  - [x] BusinessException 처리
  - [x] MethodArgumentNotValidException 처리
  - [x] Exception 처리

#### 3. sado-gateway 테스트
- [x] **TestController 작성**
  - [x] GET `/api/test/success` → 200 OK
  - [x] GET `/api/test/error` → 404 NOT_FOUND
- [x] **REST API 동작 확인**
  - [x] ApiResponse 형식 검증
  - [x] GlobalExceptionHandler 동작 확인
  - [x] HTTP 상태 코드 검증

#### 4. Docker Compose 환경 구축
- [x] **docker-compose.yml 작성**
  - [x] MySQL 8.0 컨테이너
  - [x] Kafka 7.5.0 컨테이너
  - [x] Zookeeper 7.5.0 컨테이너
  - [x] 네트워크 설정 (sado-network)
  - [x] 볼륨 설정 (mysql-data)
- [x] **컨테이너 동작 확인**
  - [x] MySQL 연결 테스트
  - [x] Kafka 토픽 생성 테스트
  - [x] Kafka Producer/Consumer 테스트

#### 5. application.yml 설정
- [x] **Datasource 설정**
  - [x] JDBC URL (MySQL 8.0)
  - [x] username/password
  - [x] driver-class-name
- [x] **JPA 설정**
  - [x] hibernate.ddl-auto
  - [x] hibernate.format_sql
  - [x] Dialect 자동 감지 (Spring Boot 4.0)
- [x] **Kafka 설정**
  - [x] bootstrap-servers
  - [x] consumer 설정
  - [x] producer 설정

#### 6. 의존성 추가
- [x] **sado-gateway/build.gradle**
  - [x] spring-boot-starter-data-jpa
  - [x] mysql-connector-java
- [x] **gradle/libs.versions.toml**
  - [x] mysql-connector 버전 정의
  - [x] 라이브러리 별칭 추가

#### 7. 빌드 및 서버 실행
- [x] sado-common 빌드 성공
- [x] sado-gateway 빌드 성공
- [x] 서버 실행 성공 (포트 8080)
- [x] 에러 없이 정상 동작 확인

### 블로그 작성
- [x] "Gradle 멀티모듈 프로젝트 - 초보자 가이드"
- [x] "API 응답 표준화 - ApiResponse 패턴"
- [ ] "Docker Compose로 개발 환경 구축하기" (선택)

---

## 학습 성과

### 기술적 이해
1. **Gradle 멀티모듈** - 모듈 간 의존성 관리 이해
2. **Version Catalog** - 의존성 버전 중앙 관리
3. **ApiCode 패턴** - 응답 코드 표준화 및 타입 안전성
4. **7자리 코드 체계** - Service-Module-Seq 구조 (10-00-001)
5. **Generic + Builder 패턴** - 재사용 가능한 ApiResponse 설계
6. **GlobalExceptionHandler** - 전역 예외 처리 및 응답 변환
7. **Docker Compose** - 컨테이너 네트워크 및 볼륨 관리
8. **Spring Boot 4.0** - Hibernate 6.x 변경사항 (Dialect 자동 감지)

### 트러블슈팅 경험
1. **Kafka 포트 오타** - 9002 → 9092 수정
2. **Missing Dependencies** - JPA, MySQL 의존성 추가
3. **MySQL8Dialect ClassNotFoundException** - Hibernate 6.x에서 제거됨, 자동 감지로 해결
4. **에러 분석 방법론** - 스택 트레이스 하단부터 읽기, 증상과 원인 구분

---

## 구현 파일 목록

### Gradle 설정
- `settings.gradle` - 멀티모듈 프로젝트 설정
- `gradle/libs.versions.toml` - 의존성 버전 카탈로그
- `build.gradle` (root)
- `sado-common/build.gradle`
- `sado-gateway/build.gradle`

### sado-common 모듈
- `ApiCode.java` - 인터페이스
- `CommonCode.java` - Enum (7자리 코드 체계)
- `BusinessException.java` - 예외 클래스
- `ResourceNotFoundException.java` - 예외 클래스
- `ApiResponse.java` - Generic + Builder 패턴
- `GlobalExceptionHandler.java` - @RestControllerAdvice

### sado-gateway 모듈
- `TestController.java` - REST API 테스트
- `application.yml` - Spring Boot 설정

### Docker
- `docker-compose.yml` - MySQL, Kafka, Zookeeper

---

## 다음 단계 (Week 3-4)

### 학습 목표
- [ ] JPA Entity 설계 기본
- [ ] 연관 관계 매핑 (@OneToMany, @ManyToOne)
- [ ] Audit 필드 (@CreatedDate, @LastModifiedDate)
- [ ] 멀티테넌시 패턴 (tenant_id)

### 실습 목표
- [ ] 공통 Entity 설계 (BaseEntity, TenantAwareEntity)
- [ ] sado-minipacs 모듈 생성
- [ ] DICOM 도메인 Entity 설계 (Study, Series, Instance)
- [ ] JPA Repository 구현

---

## 참조 문서

| 문서 | 역할 |
|-----|------|
| [07_최종_구현_계획.md](../../core/07_최종_구현_계획.md) | 전체 8주 구현 계획 |
| [CURRENT_CONTEXT.md](../CURRENT_CONTEXT.md) | Claude 컨텍스트 복원 |

---

*보관일: 2025-12-31*
