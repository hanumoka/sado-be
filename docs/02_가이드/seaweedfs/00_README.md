# SeaweedFS 완벽 학습 가이드

> **작성일**: 2025-12-30
> **목적**: SADO MiniPACS 프로젝트에서 SeaweedFS를 연동하기 위한 포괄적 학습 자료
> **대상**: SeaweedFS를 처음 접하는 개발자

---

## 📚 학습 자료 개요

이 폴더는 **SeaweedFS 분산 객체 스토리지 시스템**에 대한 체계적인 학습 자료를 제공합니다. SADO MiniPACS 프로젝트에서 DICOM 의료 영상 파일을 저장하기 위해 SeaweedFS를 연동하는 데 필요한 모든 지식을 다룹니다.

### SeaweedFS란?

**SeaweedFS**는 수십억 개의 파일을 저장할 수 있는 빠른 분산 스토리지 시스템입니다. Facebook의 Haystack 논문에서 영감을 받아 Go 언어로 작성되었으며, **O(1) 디스크 읽기 성능**을 제공합니다.

**핵심 특징**:
- ✅ 수십억 개의 작은 파일 효율적 저장
- ✅ O(1) 디스크 읽기 (단일 디스크 seek)
- ✅ S3 API 호환 (Filer 필수 - SADO 프로젝트 사용)
- ✅ POSIX 파일 시스템 인터페이스 (Filer)
- ✅ 자동 복제 및 고가용성
- ✅ Kubernetes, Docker 지원
- ✅ 경량 (단일 Go 바이너리)

---

## 📖 학습 자료 목차

### 기초 과정

#### [01_아키텍처_개요.md](./01_아키텍처_개요.md)
- SeaweedFS 전체 아키텍처
- 설계 철학 및 장점
- Facebook Haystack 논문 배경
- O(1) 디스크 읽기 원리
- 다른 객체 스토리지와의 차이점

#### [02_핵심_컴포넌트.md](./02_핵심_컴포넌트.md)
- **Master Server**: 클러스터 관리 및 볼륨 조정
- **Volume Server**: 실제 파일 데이터 저장
- **Filer Server**: 파일 시스템 인터페이스 제공
- 각 컴포넌트의 역할 및 상호작용
- Raft 합의 알고리즘

#### [03_설치_및_설정.md](./03_설치_및_설정.md)
- Docker Compose를 이용한 빠른 설치
- 단일 노드 개발 환경 구성
- 다중 노드 클러스터 구성
- 설정 파일 상세 설명
- SADO 프로젝트 통합 (포트 10400번대)

---

### 개발 과정

#### [04_HTTP_API.md](./04_HTTP_API.md)
- HTTP REST API 완벽 가이드
- 파일 업로드 프로세스 (2단계)
- 파일 다운로드 및 삭제
- 파일 복사 및 메타데이터 조회
- 대용량 파일 처리 (Chunking)
- 실습 예제 (curl 명령어)

#### [05_Java_Spring_Boot_연동.md](./05_Java_Spring_Boot_연동.md)
- Java Client 라이브러리 비교
- Spring Boot 프로젝트 설정
- FileTemplate 패턴 구현
- DICOM 파일 업로드/다운로드 서비스
- MultipartFile 처리
- 에러 핸들링 및 재시도 로직
- **실전 코드 예제**

---

### 운영 과정

#### [06_복제_및_고가용성.md](./06_복제_및_고가용성.md)
- 복제 정책 (Replication Strategy)
- 3자리 복제 코드 설명 (예: 001, 010, 100)
- 데이터센터 및 랙 인식 복제
- Master 클러스터 HA (Raft)
- Filer 클러스터 HA
- Erasure Coding (Warm Storage)
- 일관성 모델 (W=N, R=1)

#### [07_모니터링_및_운영.md](./07_모니터링_및_운영.md)
- Prometheus 메트릭 수집
- Grafana 대시보드 구성
- Health Check 엔드포인트
- 핵심 모니터링 지표
- 알람 설정 권장사항
- 로그 분석
- 디스크 용량 관리

#### [11_SADO_스토리지_모니터링_현황_및_개선계획.md](./11_SADO_스토리지_모니터링_현황_및_개선계획.md)
- **SADO 프로젝트 모니터링 현황 분석**
- 현재 구현 수준 평가 (POC ~60%)
- SeaweedFS 네이티브 Prometheus 메트릭 연동 계획
- Volume 페이징 및 Collection 통계 개선안
- 4단계 개선 로드맵 (Phase 1-4)
- UI 목업 및 API 설계
- Prometheus/Grafana 통합 가이드

#### [09_프로덕션_배포.md](./09_프로덕션_배포.md)
- 프로덕션 환경 구성 권장사항
- 볼륨 크기 설정
- 네트워크 구성 (Multi-homed)
- 보안 설정
- 백업 및 복구 전략
- 성능 튜닝
- Kubernetes 배포

---

### 심화 과정

#### [08_성능_비교.md](./08_성능_비교.md)
- SeaweedFS vs MinIO
- SeaweedFS vs Ceph RGW
- SeaweedFS vs AWS S3
- 소규모 파일 성능
- 대용량 파일 성능
- 메모리 사용량 비교
- S3 API 호환성 비교
- 실제 벤치마크 결과 (2025)

#### [10_MiniPACS_연동_가이드.md](./10_MiniPACS_연동_가이드.md)
- **SADO 프로젝트 특화 가이드**
- DICOM 파일 저장 전략
- Instance 엔티티와 SeaweedFS 통합
- 파일 경로 설계 (studyUID/seriesUID/sopInstanceUID)
- 썸네일 및 트랜스코딩 파일 관리
- Storage Tiering (HOT/WARM/COLD)
- 메타데이터와 파일 동기화
- 백업 및 마이그레이션 전략
- **구현 체크리스트**

---

## 🎯 학습 순서 권장

### 1주차: 기초 이해
1. `01_아키텍처_개요.md` - SeaweedFS가 무엇인지 이해
2. `02_핵심_컴포넌트.md` - Master, Volume, Filer 역할 파악
3. `03_설치_및_설정.md` - Docker Compose로 실습 환경 구축

### 2주차: 개발 실습
4. `04_HTTP_API.md` - curl로 파일 업로드/다운로드 실습 (학습용)
5. `05_Java_Spring_Boot_연동.md` - Spring Boot S3 API 통합 (프로덕션 권장)

### 3주차: 운영 및 최적화
6. `06_복제_및_고가용성.md` - 프로덕션 준비
7. `07_모니터링_및_운영.md` - Prometheus/Grafana 설정
8. `09_프로덕션_배포.md` - 배포 전략 수립

### 4주차: MiniPACS 통합
9. `08_성능_비교.md` - 다른 솔루션과 비교 분석
10. `10_MiniPACS_연동_가이드.md` - SADO 프로젝트 실전 적용

---

## 📚 참고 자료

### 공식 문서
- [GitHub Repository](https://github.com/seaweedfs/seaweedfs)
- [DeepWiki Documentation](https://deepwiki.com/seaweedfs/seaweedfs)
- [Official Website](https://seaweedfs.com/)
- [GitHub Wiki](https://github.com/seaweedfs/seaweedfs/wiki)

### 커뮤니티
- [Google Groups](https://groups.google.com/g/seaweedfs)
- [GitHub Discussions](https://github.com/seaweedfs/seaweedfs/discussions)
- [Hacker News Discussions](https://news.ycombinator.com/item?id=39235593)

### 관련 기술
- **Facebook Haystack Paper**: SeaweedFS의 영감
- **Raft Consensus Algorithm**: Master HA 메커니즘
- **S3 API**: AWS S3 호환성
- **POSIX**: Filer 파일 시스템 인터페이스

---

## 🔗 SADO 프로젝트 통합 계획

### 현재 상태 (Week 4)
- ✅ MiniPACS Domain Layer 완성
- ✅ Instance 엔티티에 `storagePath` 필드 준비
- 🔄 Storage Layer 구현 예정

### SeaweedFS 연동 목표 (Week 4-6)
1. **Docker Compose 통합** (포트 10400번대)
   - Master HTTP: 10400
   - Master gRPC: 10401
   - Volume: 10402
   - Filer HTTP: 10403 (필수)
   - Filer gRPC: 10404 (필수)
   - Filer S3 API: 10405 (필수)

2. **Spring Boot 서비스 구현** (S3 API)
   - AWS SDK for S3 의존성 추가
   - `S3Client` 및 `S3Presigner` Bean 구성
   - `DicomStorageService`: DICOM 파일 업로드/다운로드 (S3 API)
   - Pre-signed URL 생성 (OHIF Viewer 연동)
   - 에러 핸들링 및 재시도

3. **Instance 엔티티 통합** (S3 경로)
   - `storagePath`: S3 경로 (studies/{studyUID}/series/{seriesUID}/{sopInstanceUID}.dcm)
   - `thumbnailPath`: 썸네일 S3 경로
   - `videoPath`: 트랜스코딩 비디오 S3 경로

4. **테스트 및 검증**
   - S3 Bucket 생성 (`minipacs`)
   - DICOM 파일 S3 업로드 통합 테스트
   - 파일 다운로드 및 Pre-signed URL 성능 테스트
   - 복제 및 고가용성 검증

---

## 💡 학습 팁

1. **실습 위주 학습**: 각 문서를 읽으면서 실제로 명령어를 실행해보세요
2. **Docker Compose 활용**: 로컬 환경에서 쉽게 실습할 수 있습니다
3. **GitHub 이슈 참고**: 실제 사용자들의 질문과 답변이 많은 도움이 됩니다
4. **단계적 접근**: 처음부터 완벽한 HA 구성을 하지 말고, 단일 노드부터 시작하세요
5. **MiniPACS 연동 우선**: 10번 문서를 최종 목표로 학습하세요

---

## 📝 문서 업데이트 이력

- **2025-12-30**: 초기 학습 자료 작성 (웹 검색 기반 포괄적 조사)
- **2026-01-19**: 11_SADO_스토리지_모니터링_현황_및_개선계획.md 추가 (POC 수준 분석 및 개선 로드맵)
- **향후 계획**: 실제 SADO 프로젝트 연동 후 실전 경험 추가

---

## ⚠️ 중요 사항

이 학습 자료는 **2025년 12월 30일** 기준 최신 정보를 바탕으로 작성되었습니다. SeaweedFS는 활발히 개발되는 프로젝트이므로, 공식 문서와 함께 참고하시기 바랍니다.

**학습 과정에서 궁금한 점이 있다면**:
1. 각 문서의 "참고 자료" 섹션 확인
2. GitHub Issues/Discussions 검색
3. Google Groups 커뮤니티 질문

**좋은 학습 되시길 바랍니다!** 🚀
