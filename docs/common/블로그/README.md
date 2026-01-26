# SADO 프로젝트 블로그

> 학습 과정을 기록하는 블로그 폴더입니다.

## 폴더 구조

```
blog/
├── drafts/      # 작성 중인 초안
├── published/   # 발행된 글
└── templates/   # 블로그 템플릿
```

## 블로그 글 유형

| 유형 | 설명 | 예시 |
|------|------|------|
| 개념 정리 | 기술 개념 학습 정리 | "Temporal Workflow란?" |
| 구현 가이드 | 실제 구현 과정 기록 | "dcm4che로 C-STORE SCP 구현하기" |
| 트러블슈팅 | 문제 해결 과정 | "Kafka 연동 시 만난 에러들" |
| 비교 분석 | 기술 선택 근거 | "SeaweedFS vs MinIO 비교" |

## 주차별 블로그 주제 (계획)

| Week | 주제 후보 |
|------|----------|
| 1 | Gradle 멀티모듈, Spring Boot 4.0 신기능 |
| 2 | JPA vs MyBatis 혼용 전략, DICOM 데이터 모델 |
| 3 | Spring Cloud Gateway, API 라우팅 |
| 4 | dcm4che 입문, DICOM 파일 파싱 |
| 5 | DICOM 네트워크 프로토콜 (C-STORE) |
| 6 | DICOMWeb 표준 (STOW-RS) |
| 7 | Kafka 기초, 이벤트 드리븐 아키텍처 |
| 8 | Temporal Workflow 입문 |
| 9 | Saga 패턴, 분산 트랜잭션 |
| 10 | gRPC 기초, BE-AI 통신 |
| 11 | 스토리지 티어링 전략 |
| 12 | OAuth2/OIDC, Keycloak |
| 13 | BFF 패턴 |
| 14 | Redis 분산 락, 캐싱 전략 |
| 15 | Prometheus + Grafana 모니터링 |
| 16 | Docker Swarm 배포 |

## 작성 워크플로우

1. `drafts/` 폴더에 초안 작성
2. Claude에게 리뷰 요청
3. 수정 후 `published/` 폴더로 이동
4. 실제 블로그 플랫폼에 발행
