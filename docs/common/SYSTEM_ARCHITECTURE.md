# SADO 시스템 아키텍처 개요

> **최종 업데이트**: 2026-01-19
> **버전**: 3.0
> **상태**: MiniPACS POC 100% 완료

---

## 1. 시스템 개요

SADO (Sado Advanced DICOM Operations)는 DICOM 의료 영상을 관리하고 시각화하는 Mini PACS 시스템입니다.

### 1.1 핵심 특징

- **5개 독립적인 DICOM 뷰어** - 다양한 사용 사례에 최적화
- **DICOMWeb 표준 API** - OHIF, 3D Slicer 등 표준 뷰어 호환
- **하이브리드 렌더링** - MJPEG 즉시 재생 + Cornerstone 고품질 전환
- **분산 스토리지** - SeaweedFS 기반 확장 가능한 저장소
- **멀티테넌시** - 테넌트별 데이터 격리
- **성능 최적화** - N+1 쿼리 제거, Nginx 캐싱, Progressive Playback

---

## 2. 시스템 구성도

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Client Layer                                   │
├─────────────────────────────────────────────────────────────────────────┤
│  sado_fe (React 19.2.0 + TypeScript 5.9 + Vite 7.2.4)                   │
│  ├── 5개 DICOM 뷰어                                                      │
│  │   ├── WADO-RS Rendered (Cornerstone) - 진단용 고속                    │
│  │   ├── WADO-RS BulkData (Cornerstone) - 원본 보존, W/L 조정            │
│  │   ├── MJPEG (Canvas) - 빠른 스크리닝 (~100ms)                         │
│  │   ├── Hybrid MJPEG+WADO-RS (듀얼 레이어) - 빠른 재생 + 고품질 전환     │
│  │   └── WADO-URI (Cornerstone) - 레거시 PACS 호환                       │
│  ├── Patient/Study/Series/Instance 관리                                  │
│  └── Admin Dashboard (SeaweedFS 관리, 스토리지 모니터링, Tiering)         │
└─────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           API Gateway                                    │
├─────────────────────────────────────────────────────────────────────────┤
│  Nginx (Reverse Proxy + Cache Layer)                                     │
│  ├── /dicomweb/* → sado_be (캐싱: rendered, cine-frames)                 │
│  ├── /mjpeg/* → sado_be                                                  │
│  └── /api/* → sado_be                                                    │
└─────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Application Layer                                │
├─────────────────────────────────────────────────────────────────────────┤
│  sado_be (Spring Boot 4.0.1 + Java 21 Virtual Thread)                   │
│  ├── sado-common (공통 라이브러리)                                        │
│  │   ├── ApiResponse (표준 응답 형식)                                     │
│  │   ├── BusinessException (예외 처리)                                    │
│  │   ├── TenantContext (멀티테넌시)                                       │
│  │   └── BaseEntity, TenantAwareEntity                                   │
│  └── sado-minipacs (Mini PACS 모듈)                                      │
│      ├── DICOMWeb API (QIDO-RS, WADO-RS, WADO-URI, STOW-RS)             │
│      ├── Cine Frames API (MJPEG용)                                       │
│      ├── DICOM Rendering Service (DCM4CHE 5.29.1)                        │
│      ├── Pre-rendering Service (Thumbnail, Cine JPEG)                    │
│      └── Storage Service (SeaweedFS S3 API)                              │
└─────────────────────────────────────────────────────────────────────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    ▼              ▼              ▼
┌───────────────────────┐ ┌───────────────┐ ┌───────────────────────┐
│    Database Layer     │ │ Storage Layer │ │    (Future)           │
├───────────────────────┤ ├───────────────┤ ├───────────────────────┤
│  MySQL 8.0            │ │ SeaweedFS     │ │ Kafka 7.5.0           │
│  ├── Patient          │ │ ├── Master    │ │ ├── dicom-upload      │
│  ├── Study            │ │ ├── Volume    │ │ └── pre-rendering     │
│  ├── Series           │ │ └── Filer     │ │                       │
│  ├── Instance         │ │               │ │ (비동기 처리 예정)     │
│  └── FileAsset        │ │ S3 Compatible │ └───────────────────────┘
└───────────────────────┘ └───────────────┘
```

---

## 3. 핵심 모듈

### 3.1 Frontend (sado_fe)

| 모듈 | 경로 | 역할 |
|------|------|------|
| **dicom-viewer** | `/features/dicom-viewer/` | WADO-RS Rendered 뷰어 |
| **dicom-viewer-wado-rs-bulkdata** | `/features/dicom-viewer-wado-rs-bulkdata/` | WADO-RS BulkData 뷰어 |
| **dicom-viewer-mjpeg** | `/features/dicom-viewer-mjpeg/` | MJPEG 스트리밍 뷰어 |
| **dicom-viewer-mjpeg-wado-rs** | `/features/dicom-viewer-mjpeg-wado-rs/` | 하이브리드 뷰어 |
| **dicom-viewer-wado-uri** | `/features/dicom-viewer-wado-uri/` | WADO-URI 뷰어 |
| **dicom-viewer-shared** | `/features/dicom-viewer-shared/` | 공유 컴포넌트/전략 |
| **patient** | `/features/patient/` | 환자 관리 |
| **study** | `/features/study/` | 검사 관리 |
| **upload** | `/features/upload/` | DICOM 업로드 |
| **admin** | `/features/admin/` | 관리자 기능 |

### 3.2 Backend (sado_be)

| 모듈 | 경로 | 역할 |
|------|------|------|
| **sado-common** | `/sado-common/` | 공통 라이브러리 (ApiResponse, Exception, Tenant) |
| **sado-minipacs** | `/sado-minipacs/` | Mini PACS 핵심 기능 |

---

## 4. 5개 DICOM 뷰어 아키텍처

### 4.1 뷰어 비교

| 뷰어 | 용도 | 기술 | 초기 로딩 | W/L 조정 |
|------|------|------|----------|---------|
| **WADO-RS Rendered** | 진단용 고속 | Cornerstone | ~2초 | 제한적 |
| **WADO-RS BulkData** | 원본 보존 | Cornerstone | ~3초 | 가능 |
| **MJPEG** | 빠른 스크리닝 | Canvas | ~100ms | 불가 |
| **Hybrid** | 빠른 재생 + 고품질 | MJPEG→Cornerstone | ~100ms | 전환 후 가능 |
| **WADO-URI** | 외부 PACS | Cornerstone | ~3초 | 가능 |

### 4.2 하이브리드 뷰어 동작 흐름

```
1. Series 선택
   ↓
2. MJPEG Layer 즉시 시작 (~100ms)
   ├── GET /dicomweb/cine-frames/{sopUID}?resolution=256
   ├── Base64 JPEG 프레임 캐싱
   └── Canvas에 requestAnimationFrame으로 재생
   ↓
3. Cornerstone Layer 백그라운드 프리로드
   ├── GET /dicomweb/.../frames/1,2,3.../rendered
   ├── Cornerstone 이미지 캐시에 순차 로드
   └── Progressive: 초기 20프레임만 로드 후 나머지 백그라운드
   ↓
4. 프리로드 완료 시 자동 전환
   ├── 루프 경계 (프레임 0)에서 전환
   ├── 크로스페이드 애니메이션 (200ms)
   └── MJPEG Layer 숨김, Cornerstone Layer 활성화
   ↓
5. Cornerstone 모드
   ├── W/L 조정 가능
   ├── 측정 도구 사용 가능
   └── 고품질 렌더링
```

### 4.3 성능 최적화 기법

| 기법 | 구현 | 효과 |
|------|------|------|
| **Progressive Playback** | 초기 20프레임만 로드 후 재생 | 5-10초 → 1-2초 |
| **중앙 집중식 애니메이션** | advanceAllPlayingFrames() 배칭 | 리렌더링 N회 → 1회 |
| **LRU 캐시** | MinHeap 기반 O(log N) | 빠른 eviction |
| **해상도 자동 조정** | 레이아웃별 512/256/128px | 대역폭 50:1 절감 |
| **N+1 쿼리 최적화** | JOIN FETCH | 5-10초 → 500ms |
| **Nginx 캐싱** | rendered, cine-frames | 반복 요청 즉시 응답 |

---

## 5. 데이터 흐름

### 5.1 DICOM 업로드 흐름

```
1. 사용자가 DICOM 파일 선택 (Drag & Drop)
2. FE → POST /api/v1/instances/upload
3. BE: DICOM 메타데이터 추출 (DCM4CHE)
4. BE: Patient/Study/Series/Instance 엔티티 생성/조회
5. BE: SeaweedFS에 원본 DICOM 저장
6. BE: Pre-rendering 동기 실행
   ├── Thumbnail (128px)
   └── Cine JPEG (256px, 128px)
7. 응답: Instance 메타데이터 반환
```

### 5.2 DICOM 조회 흐름 (하이브리드 뷰어)

```
1. 사용자가 Series 선택
2. FE: /viewer/mjpeg-wado-rs/{studyUID}/{seriesUID} 이동
3. FE: QIDO-RS로 Instance 목록 조회
4. FE: MjpegLayer 즉시 시작
   ├── GET /dicomweb/cine-frames/{sopUID}?resolution=256
   ├── Base64 JPEG 프레임 캐싱
   └── Canvas에 즉시 재생 (~100ms)
5. FE: CornerstoneLayer 백그라운드 프리로드
   ├── GET /dicomweb/.../frames/1,2,3.../rendered
   └── Cornerstone 이미지 캐시에 로드
6. 프리로드 완료 후 루프 경계에서 전환
   ├── 크로스페이드 애니메이션 (200ms)
   └── Cornerstone 활성화 (W/L 조정 가능)
```

---

## 6. API 엔드포인트 요약

### 6.1 DICOMWeb API

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/dicomweb/studies` | Study 검색 (QIDO-RS) |
| GET | `/dicomweb/studies/{uid}/series` | Series 검색 |
| GET | `/dicomweb/studies/{uid}/series/{uid}/instances` | Instance 검색 |
| GET | `/dicomweb/.../instances/{uid}` | Instance 조회 (WADO-RS) |
| GET | `/dicomweb/.../instances/{uid}/rendered` | 렌더링된 이미지 |
| GET | `/dicomweb/.../instances/{uid}/frames/{n}/rendered` | 특정 프레임 렌더링 |
| GET | `/dicomweb/.../instances/{uid}/frames/{n}` | Raw PixelData (BulkData) |
| GET | `/dicomweb/wado` | WADO-URI 레거시 |
| POST | `/dicomweb/studies` | DICOM 업로드 (STOW-RS) |

### 6.2 Cine Frames API

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/dicomweb/cine-frames/{sopUID}` | 모든 프레임 일괄 조회 (Base64 JPEG) |
| GET | `/dicomweb/cine-frames/{sopUID}/info` | 프레임 정보 조회 |

### 6.3 REST API

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/v1/patients` | 환자 목록 |
| GET | `/api/v1/studies` | Study 목록 |
| GET | `/api/v1/series` | Series 목록 |
| GET | `/api/v1/instances` | Instance 목록 |
| POST | `/api/v1/instances/upload` | DICOM 업로드 |

### 6.4 Admin API

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/admin/dashboard/summary` | 통계 요약 |
| GET | `/api/admin/metrics/storage` | 스토리지 메트릭 |
| GET | `/api/admin/tiering/files` | Tiering 파일 목록 |
| GET | `/api/admin/monitoring/tasks` | 작업 모니터링 |
| GET | `/api/admin/seaweedfs/cluster/status` | SeaweedFS 상태 |

---

## 7. 기술 스택

### 7.1 Frontend

| 기술 | 버전 | 용도 |
|-----|------|-----|
| React | 19.2.0 | UI 프레임워크 |
| TypeScript | 5.9.3 | 타입 안전성 |
| Vite | 7.2.4 | 빌드 도구 |
| Zustand | 5.0.9 | 클라이언트 상태 |
| TanStack Query | 5.90.16 | 서버 상태 |
| Cornerstone.js | 4.12.6 | DICOM 렌더링 |
| Tailwind CSS | 3.4.19 | 스타일링 |
| Recharts | 3.6.0 | 차트 |

### 7.2 Backend

| 기술 | 버전 | 용도 |
|-----|------|-----|
| Spring Boot | 4.0.1 | 프레임워크 |
| Java | 21 | 언어 (Virtual Thread) |
| MySQL | 8.0 | 데이터베이스 |
| DCM4CHE | 5.29.1 | DICOM 처리 |
| OpenCV | 4.9.0 | 이미지 처리 |
| SeaweedFS | - | 분산 스토리지 |

---

## 8. 배포 구성

### 8.1 포트 매핑

| 서비스 | 포트 | 설명 |
|--------|------|------|
| sado_fe | 10300 | Frontend (Vite 개발 서버) |
| sado_be | 10201 | Backend API |
| Nginx Cache | 10202 | 캐싱 프록시 (FE 기본 연결) |
| MySQL | 10100 | 데이터베이스 |
| SeaweedFS Master | 10220 | 스토리지 마스터 |
| SeaweedFS Volume | 10221 | 스토리지 볼륨 |
| SeaweedFS Filer | 10222 | 스토리지 파일러 |
| SeaweedFS S3 API | 10405 | S3 호환 API |

### 8.2 Docker Compose 실행

```bash
# 전체 시스템 시작
docker-compose -f docker-compose-dev.yml up -d

# 로그 확인
docker-compose -f docker-compose-dev.yml logs -f sado_be
```

---

## 9. POC 제한사항 및 향후 계획

### 9.1 현재 POC 상태

| 항목 | POC 상태 | 프로덕션 필요 |
|------|----------|--------------|
| **인증** | X-Tenant-ID 헤더 | Keycloak OAuth2/JWT |
| **Tiering** | DB 메타데이터만 | 물리적 파일 이동 |
| **Pre-rendering** | 동기 처리 | Kafka 비동기 처리 |
| **스케줄러** | 비활성화 | 활성화 |

### 9.2 향후 계획

| 단계 | 작업 | 상태 |
|------|-----|------|
| **AI 통합** | Triton 서버, EchoNet 모델 배포 | 📋 계획됨 |
| **프로덕션 준비** | Tiering 물리적 파일 이동, 비동기 처리 | 📋 계획됨 |
| **보안 강화** | Keycloak OAuth2 인증 통합 | 📋 계획됨 |

---

## 10. 관련 문서

### 10.1 Frontend 문서
- [FE INDEX](02_프론트엔드/01_핵심/00_INDEX.md)
- [DICOM 뷰어 아키텍처 가이드](02_프론트엔드/02_가이드/06_DICOM_뷰어_아키텍처_가이드.md)
- [상태 관리 전략](02_프론트엔드/02_가이드/03_상태_관리_전략.md)

### 10.2 Backend 문서
- [BE INDEX](01_백엔드/01_핵심/00_INDEX.md)
- [REST API 명세서](01_백엔드/02_가이드/32_REST_API_명세서.md)
- [DICOMWeb API 설계](01_백엔드/02_가이드/13-4_DICOMWeb_API_설계.md)

### 10.3 인프라 문서
- [포트 매핑](PORT_MAPPING.md)
- [SeaweedFS 가이드](03_SeaweedFS/00_README.md)

---

*최종 수정: 2026-01-19*
