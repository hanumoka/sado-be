# Week 1: Gradle 멀티모듈 + 개발 환경

> **기간**: 미정 ~ 미정
> **상태**: 🔄 진행중
> **진행률**: 0%

---

## 🎯 주간 목표

1. Gradle 멀티모듈 프로젝트 구조 완전 이해
2. 6개 모듈 구조 생성 및 의존성 설정
3. Docker Compose로 개발 환경 구축 (MySQL, Kafka, Zookeeper)
4. Common 및 Infrastructure 모듈 기본 구조 완성

---

## 📚 학습 내용

### 1. Gradle 멀티모듈 구조
**학습 시간**: 미정
**참고 자료**:
- [Gradle 공식 문서 - Multi-Project Builds](https://docs.gradle.org/current/userguide/multi_project_builds.html)
- [Spring Boot Multi-Module Guide](https://spring.io/guides/gs/multi-module/)

**핵심 정리**:
- settings.gradle: 모듈 include 설정
- 루트 build.gradle: allprojects, subprojects 설정
- 모듈별 build.gradle: 각 모듈의 의존성
- Version Catalog: 의존성 버전 관리 (선택)

### 2. Docker Compose 기초
**학습 시간**: 미정
**참고 자료**:
- Docker Compose 공식 문서

**핵심 정리**:
- 서비스 정의 및 네트워크 구성
- 볼륨 마운트 전략
- 환경 변수 설정

---

## ✅ 작업 체크리스트

### 구현 작업

#### Gradle 멀티모듈 구조
- [ ] `settings.gradle` 작성 (6개 모듈 include)
- [ ] 루트 `build.gradle` 작성 (allprojects, subprojects)
- [ ] `sado-common/common-core/build.gradle`
- [ ] `sado-common/common-event/build.gradle`
- [ ] `sado-common/common-security/build.gradle`
- [ ] `sado-infrastructure/kafka-config/build.gradle`
- [ ] `sado-infrastructure/redis-config/build.gradle`
- [ ] `sado-infrastructure/temporal-config/build.gradle`
- [ ] `sado-infrastructure/storage-config/build.gradle`
- [ ] `sado-gateway/build.gradle`
- [ ] `sado-minipacs/minipacs-api/build.gradle`
- [ ] `sado-minipacs/minipacs-domain/build.gradle`
- [ ] `sado-minipacs/minipacs-storage/build.gradle`
- [ ] `sado-orchestrator/orchestrator-workflow/build.gradle`
- [ ] `sado-orchestrator/orchestrator-api/build.gradle`
- [ ] `sado-bff/bff-api/build.gradle`

#### Docker Compose 환경 구축
- [ ] `docker-compose.yml` 작성
  - [ ] MySQL 8.0 서비스
  - [ ] Kafka 서비스
  - [ ] Zookeeper 서비스
- [ ] 네트워크 설정
- [ ] 볼륨 설정 (데이터 영속성)

#### Common 모듈 기본 구조
- [ ] `common-core` 패키지 구조
  - [ ] `exception/` - 공통 예외 클래스
  - [ ] `dto/` - 공통 DTO
  - [ ] `utils/` - 공통 유틸리티
- [ ] `common-event` 패키지 구조
  - [ ] 이벤트 스키마 정의 (Avro 준비)
- [ ] `common-security` 패키지 구조
  - [ ] 보안 설정 기본 클래스

#### Infrastructure 모듈 기본 구조
- [ ] `kafka-config` 패키지 구조
  - [ ] KafkaProducerConfig
  - [ ] KafkaConsumerConfig
- [ ] `redis-config` 패키지 구조
  - [ ] RedissonConfig
- [ ] `temporal-config` 패키지 구조 (Week 8 준비)
- [ ] `storage-config` 패키지 구조 (Week 3 준비)

### 문서 작업
- [ ] 학습 노트: `learning/week01-gradle-setup.md`
- [ ] 이 파일 업데이트 (진행 상황)

### 검증 작업
- [ ] `./gradlew projects` - 모든 모듈 인식 확인
- [ ] `./gradlew clean build` - 전체 빌드 성공 확인
- [ ] `docker-compose up -d` - 컨테이너 정상 구동 확인
- [ ] MySQL 접속 테스트
- [ ] Kafka 브로커 연결 테스트

---

## 🐛 트러블슈팅

### 문제 1: [문제 발생 시 기록]
**발생 일시**: YYYY-MM-DD
**증상**:
-

**원인**:
-

**해결 방법**:
-

**교훈**:
-

---

## 💡 인사이트

- (작업하면서 배운 중요한 인사이트 기록)

---

## 📌 다음 주 계획 (Week 2)

- [ ] JPA Entity 설계 (Patient, Study, Series, Instance)
- [ ] TenantAwareEntity 구현
- [ ] MyBatis Mapper 작성
- [ ] MySQL DDL 스키마 실행
- [ ] 학습 문서: `learning/week02-jpa-mybatis-mysql-multitenancy.md`

**참고 문서**:
- `07_최종_구현_계획.md` Week 2
- `10_JPA_MyBatis_혼용_전략.md`
- `12_멀티테넌시_설계_가이드.md`
- `13_mini-pacs-poc_분석_및_통합_계획.md`

---

**작성일**: 2025-12-21
**최종 수정일**: 2025-12-21
