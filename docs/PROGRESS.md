# 📊 프로젝트 진행 상황 대시보드

> **최종 업데이트**: 2025-12-21
> **프로젝트 기간**: 16주 (약 4개월)
> **현재 주차**: Week 1
> **전체 진행률**: 0% (0/16 주차 완료)

---

## 🎯 Quick Status

| Phase | 기간 | 상태 | 완료율 |
|-------|------|------|--------|
| **Phase 1: 기반 구축** | Week 1-4 | 🔄 진행중 | 0% (0/4) |
| **Phase 2: 핵심 기능** | Week 5-8 | ⏸️ 대기중 | 0% (0/4) |
| **Phase 3: 이벤트 & 워크플로우** | Week 9-12 | ⏸️ 대기중 | 0% (0/4) |
| **Phase 4: 보안 & 운영** | Week 13-16 | ⏸️ 대기중 | 0% (0/4) |

**범례**: ✅ 완료 | 🔄 진행중 | ⏸️ 대기중 | ❌ 블로킹

---

## 📅 주차별 진행 상황

### Phase 1: 기반 구축 (Week 1-4)

#### Week 1: Gradle 멀티모듈 + 개발 환경
- **기간**: 미정
- **상태**: 🔄 진행중
- **목표**: Gradle 멀티모듈 구조 구축, Docker Compose 환경 설정
- **주요 산출물**:
  - [ ] 6개 모듈 Gradle 프로젝트 구조
  - [ ] Docker Compose (MySQL, Kafka, Zookeeper)
  - [ ] Common 모듈 기본 구조
  - [ ] Infrastructure 모듈 기본 구조
  - [ ] 학습 문서: `learning/week01-gradle-setup.md`
- **참고**: `07_최종_구현_계획.md` Week 1 섹션
- **상세**: [progress/week-01.md](./progress/week-01.md)

---

#### Week 2: Spring Boot + JPA/MyBatis + MySQL + Multi-Tenancy
- **기간**: 미정
- **상태**: ⏸️ 대기중
- **목표**: JPA/MyBatis 혼용, 멀티테넌시 기본 구현
- **주요 산출물**:
  - [ ] Study, Series, Instance Entity (tenant_id 포함)
  - [ ] TenantAwareEntity, TenantContext 구현
  - [ ] MyBatis Mapper (tenant_id 필터링)
  - [ ] MySQL Docker Compose 추가
  - [ ] 학습 문서: `learning/week02-jpa-mybatis-mysql-multitenancy.md`
- **참고**:
  - `07_최종_구현_계획.md` Week 2
  - `10_JPA_MyBatis_혼용_전략.md`
  - `12_멀티테넌시_설계_가이드.md`
- **상세**: [progress/week-02.md](./progress/week-02.md)

---

#### Week 3: DICOM 파일 저장소 (SeaweedFS)
- **기간**: 미정
- **상태**: ⏸️ 대기중
- **목표**: SeaweedFS 연동, DICOM 파일 업로드/다운로드
- **주요 산출물**:
  - [ ] SeaweedFS Docker Compose 추가
  - [ ] DICOM 파일 업로드 API
  - [ ] DICOM 파일 조회 API
  - [ ] 파일 경로 전략 (tenant 기반)
  - [ ] 학습 문서: `learning/week03-seaweedfs-dicom.md`
- **참고**: `07_최종_구현_계획.md` Week 3
- **상세**: [progress/week-03.md](./progress/week-03.md)

---

#### Week 4: DICOM 메타데이터 추출 (dcm4che)
- **기간**: 미정
- **상태**: ⏸️ 대기중
- **목표**: dcm4che 라이브러리로 DICOM 메타데이터 추출 및 저장
- **주요 산출물**:
  - [ ] DicomMetadataExtractor 구현
  - [ ] DICOM 태그 파싱 및 DB 저장
  - [ ] 메타데이터 조회 API
  - [ ] 학습 문서: `learning/week04-dcm4che-metadata.md`
- **참고**: `07_최종_구현_계획.md` Week 4
- **상세**: [progress/week-04.md](./progress/week-04.md)

---

### Phase 2: 핵심 기능 (Week 5-8)

#### Week 5: Kafka 기초 + Producer 구현
- **기간**: 미정
- **상태**: ⏸️ 대기중
- **목표**: Kafka 기본 개념 학습 및 Producer 구현
- **주요 산출물**:
  - [ ] Kafka 브로커 Docker Compose 추가
  - [ ] 이벤트 스키마 정의 (Avro)
  - [ ] StudyUploadedEvent Producer
  - [ ] 학습 문서: `learning/week05-kafka-producer.md`
- **참고**: `07_최종_구현_계획.md` Week 5
- **상세**: [progress/week-05.md](./progress/week-05.md)

---

#### Week 6: Kafka Consumer 구현
- **기간**: 미정
- **상태**: ⏸️ 대기중
- **목표**: Kafka Consumer 구현 및 이벤트 처리
- **주요 산출물**:
  - [ ] StudyUploadedEvent Consumer
  - [ ] Consumer Group 설정
  - [ ] Dead Letter Queue (DLQ) 구현
  - [ ] 학습 문서: `learning/week06-kafka-consumer.md`
- **참고**: `07_최종_구현_계획.md` Week 6
- **상세**: [progress/week-06.md](./progress/week-06.md)

---

#### Week 7: Spring Cloud Gateway
- **기간**: 미정
- **상태**: ⏸️ 대기중
- **목표**: API Gateway 구현 및 라우팅 설정
- **주요 산출물**:
  - [ ] Gateway 모듈 구현
  - [ ] 라우팅 규칙 설정
  - [ ] Custom Filter 구현
  - [ ] 학습 문서: `learning/week07-gateway.md`
- **참고**: `07_최종_구현_계획.md` Week 7
- **상세**: [progress/week-07.md](./progress/week-07.md)

---

#### Week 8: Temporal 기초 (워크플로우 엔진)
- **기간**: 미정
- **상태**: ⏸️ 대기중
- **목표**: Temporal 기본 개념 학습 및 첫 워크플로우 구현
- **주요 산출물**:
  - [ ] Temporal Server Docker Compose 추가
  - [ ] 첫 번째 Workflow 구현
  - [ ] Activity 구현
  - [ ] Worker 설정
  - [ ] 학습 문서: `learning/week08-temporal-basics.md`
- **참고**:
  - `07_최종_구현_계획.md` Week 8
  - `09_Temporal_점진적_도입_전략.md`
- **상세**: [progress/week-08.md](./progress/week-08.md)

---

### Phase 3: 이벤트 & 워크플로우 (Week 9-12)

#### Week 9: Temporal Saga 패턴 (보상 트랜잭션)
- **기간**: 미정
- **상태**: ⏸️ 대기중
- **목표**: Saga 패턴으로 분산 트랜잭션 처리
- **주요 산출물**:
  - [ ] Saga Workflow 구현
  - [ ] Compensation Logic 구현
  - [ ] 에러 핸들링 및 재시도
  - [ ] 학습 문서: `learning/week09-temporal-saga.md`
- **참고**: `09_Temporal_점진적_도입_전략.md`
- **상세**: [progress/week-09.md](./progress/week-09.md)

---

#### Week 10: AI 연동 (FastAPI Mock)
- **기간**: 미정
- **상태**: ⏸️ 대기중
- **목표**: AI 서비스 연동을 위한 FastAPI Mock 구현
- **주요 산출물**:
  - [ ] FastAPI Mock 서버 구현
  - [ ] AI 분석 결과 Mock 데이터
  - [ ] Orchestrator에서 AI 호출
  - [ ] 학습 문서: `learning/week10-ai-integration.md`
- **참고**: `07_최종_구현_계획.md` Week 10
- **상세**: [progress/week-10.md](./progress/week-10.md)

---

#### Week 11: BFF 모듈 구현
- **기간**: 미정
- **상태**: ⏸️ 대기중
- **목표**: Backend For Frontend 구현 및 API 조합
- **주요 산출물**:
  - [ ] BFF 모듈 구현
  - [ ] Feign Client 설정
  - [ ] API 조합 로직
  - [ ] 학습 문서: `learning/week11-bff.md`
- **참고**: `07_최종_구현_계획.md` Week 11
- **상세**: [progress/week-11.md](./progress/week-11.md)

---

#### Week 12: Keycloak 마스터 + Multi-Tenancy 통합
- **기간**: 미정
- **상태**: ⏸️ 대기중
- **목표**: Keycloak 인증/인가 및 멀티테넌시 통합
- **주요 산출물**:
  - [ ] Keycloak Docker Compose 추가
  - [ ] Realm, Client, Role 설정
  - [ ] JWT에 tenant_id claim 추가
  - [ ] Gateway OAuth2 Resource Server 설정
  - [ ] 학습 문서: `learning/week12-keycloak-multitenancy.md`
- **참고**:
  - `07_최종_구현_계획.md` Week 12
  - `12_멀티테넌시_설계_가이드.md`
- **상세**: [progress/week-12.md](./progress/week-12.md)

---

### Phase 4: 보안 & 운영 (Week 13-16)

#### Week 13: 보안 강화 + BFF 구현 + Multi-Tenancy 테스트
- **기간**: 미정
- **상태**: ⏸️ 대기중
- **목표**: 보안 강화 및 멀티테넌시 격리 테스트
- **주요 산출물**:
  - [ ] DICOM 파일 익명화
  - [ ] API 감사 로그 (tenant_id 포함)
  - [ ] Gateway Rate Limiting (tenant별)
  - [ ] 테넌트 격리 테스트
  - [ ] 학습 문서: `learning/week13-security-multitenancy-test.md`
- **참고**: `07_최종_구현_계획.md` Week 13
- **상세**: [progress/week-13.md](./progress/week-13.md)

---

#### Week 14: Redis 심화 + MySQL 복제
- **기간**: 미정
- **상태**: ⏸️ 대기중
- **목표**: Redis 캐싱 전략 및 MySQL Master-Slave 구성
- **주요 산출물**:
  - [ ] Redis 캐싱 전략 구현
  - [ ] MySQL Master-Slave 복제 설정
  - [ ] Read Replica 활용
  - [ ] 학습 문서: `learning/week14-redis-mysql-replication.md`
- **참고**:
  - `07_최종_구현_계획.md` Week 14
  - `08_Redis_활용_요구사항.md`
- **상세**: [progress/week-14.md](./progress/week-14.md)

---

#### Week 15: 모니터링 (Prometheus + Grafana)
- **기간**: 미정
- **상태**: ⏸️ 대기중
- **목표**: 애플리케이션 모니터링 및 알림 설정
- **주요 산출물**:
  - [ ] Prometheus + Grafana Docker Compose
  - [ ] 메트릭 수집 설정
  - [ ] 대시보드 구성
  - [ ] 알림 설정
  - [ ] 학습 문서: `learning/week15-monitoring.md`
- **참고**: `07_최종_구현_계획.md` Week 15
- **상세**: [progress/week-15.md](./progress/week-15.md)

---

#### Week 16: 최종 테스트 + 배포 + 문서화
- **기간**: 미정
- **상태**: ⏸️ 대기중
- **목표**: 통합 테스트, 배포 준비, 문서 완성
- **주요 산출물**:
  - [ ] 통합 테스트 작성
  - [ ] 배포 스크립트 작성
  - [ ] API 문서 완성 (Swagger/OpenAPI)
  - [ ] README 및 운영 가이드
  - [ ] 학습 문서: `learning/week16-final-deployment.md`
- **참고**: `07_최종_구현_계획.md` Week 16
- **상세**: [progress/week-16.md](./progress/week-16.md)

---

## 🎓 학습 문서 현황

### 완료된 학습 문서 (0/16)
- 없음

### 진행중인 학습 문서
- 없음

### 대기중인 학습 문서 (16/16)
- [ ] `learning/week01-gradle-setup.md`
- [ ] `learning/week02-jpa-mybatis-mysql-multitenancy.md`
- [ ] `learning/week03-seaweedfs-dicom.md`
- [ ] `learning/week04-dcm4che-metadata.md`
- [ ] `learning/week05-kafka-producer.md`
- [ ] `learning/week06-kafka-consumer.md`
- [ ] `learning/week07-gateway.md`
- [ ] `learning/week08-temporal-basics.md`
- [ ] `learning/week09-temporal-saga.md`
- [ ] `learning/week10-ai-integration.md`
- [ ] `learning/week11-bff.md`
- [ ] `learning/week12-keycloak-multitenancy.md`
- [ ] `learning/week13-security-multitenancy-test.md`
- [ ] `learning/week14-redis-mysql-replication.md`
- [ ] `learning/week15-monitoring.md`
- [ ] `learning/week16-final-deployment.md`

---

## 📝 블로그 작성 현황

### 작성 예정 (참고: `11_블로그_작성_가이드.md`)
- [ ] Week 2: "JPA와 MyBatis 혼용 전략"
- [ ] Week 2: "멀티테넌시 구현 - Shared Database 전략"
- [ ] Week 2: "JPA @Filter로 자동 테넌트 격리하기"
- [ ] Week 5: "Kafka 이벤트 기반 아키텍처 입문"
- [ ] Week 8: "Temporal로 워크플로우 오케스트레이션"
- [ ] Week 9: "Saga 패턴으로 분산 트랜잭션 처리"
- [ ] Week 12: "Keycloak JWT에 커스텀 Claim 추가하기"
- [ ] Week 13: "멀티테넌시 보안 테스트 - 테넌트 격리 검증"

---

## 🚧 현재 블로킹 이슈

없음

---

## 📌 다음 작업 (Next Steps)

1. **Week 1 시작**: Gradle 멀티모듈 프로젝트 구조 생성
2. 참고 문서: `07_최종_구현_계획.md` Week 1 섹션
3. 계획 문서: `progress/week-01.md` 작성

---

## 🔗 관련 문서

- **전체 계획**: [07_최종_구현_계획.md](./07_최종_구현_계획.md)
- **작업 이력**: [CHANGELOG.md](./CHANGELOG.md)
- **주차별 상세**: [progress/](./progress/)
- **학습 노트**: [learning/](./learning/)

---

**업데이트 규칙**:
- 주차 완료 시: 상태를 ✅로 변경, 완료율 업데이트
- 블로킹 발생 시: "현재 블로킹 이슈" 섹션에 기록
- 학습 문서 작성 시: 해당 항목 체크
- 매주 금요일: 진행 상황 리뷰 및 업데이트
