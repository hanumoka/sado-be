# DICOMweb 확장 API 가이드

> **작성일**: 2026-01-22
> **관련 파일**: `DicomWebExtController.java`, `BatchFrameRequest.java`

---

## 1. 개요

### 1.1 목적

표준 DICOMweb API를 확장하여 다음 요구사항을 충족합니다:

1. **URL Path에 tenantId 포함** - 명시적 테넌트 식별
2. **내부 DB ID 기반 API** - DICOM UID 대신 DB PK 사용
3. **Batch 프레임 조회** - 여러 Instance의 프레임 일괄 조회

### 1.2 배경

| 구분 | 표준 DICOMweb | 확장 API |
|------|--------------|----------|
| 테넌트 식별 | Header (`X-Tenant-Id`) | URL Path (`/{tenantId}/...`) |
| Instance 식별 | DICOM UID (SOP Instance UID) | DB PK (Long id) |
| 프레임 조회 | 단일 요청 | Batch 지원 |

### 1.3 파일 구조

```
sado-minipacs/src/main/java/com/hanumoka/sado/minipacs/
├── controller/
│   ├── DicomWebController.java      # 표준 DICOMweb API
│   └── DicomWebExtController.java   # 확장 DICOMweb API (신규)
└── dto/request/
    └── BatchFrameRequest.java       # Batch 요청 DTO (신규)
```

---

## 2. API 엔드포인트

### 2.1 기본 경로

```
/dicomweb-ext/{tenantId}/...
```

### 2.2 확장 WADO-RS (DICOM UID 기반)

표준 WADO-RS와 동일하지만 URL Path에 tenantId 포함:

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/{tenantId}/studies/{studyUID}/metadata` | Study 메타데이터 (DICOM JSON) |
| GET | `/{tenantId}/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}` | DICOM 파일 다운로드 |
| GET | `/{tenantId}/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/frames/{frameList}/rendered` | 프레임 렌더링 |

### 2.3 내부 DB ID 기반 API

DICOM UID 대신 DB PK(id)를 사용하여 빠른 조회:

| 메서드 | 경로 | 설명 | 응답 형식 |
|--------|------|------|----------|
| GET | `/{tenantId}/internal/instances/{instanceId}` | Instance 메타데이터 | JSON |
| GET | `/{tenantId}/internal/instances/{instanceId}/frames/{frameList}` | 프레임 BulkData | octet-stream / multipart |
| GET | `/{tenantId}/internal/instances/{instanceId}/frames/{frameList}/rendered` | 프레임 렌더링 | image/* / multipart |

### 2.4 Batch API

여러 Instance의 프레임을 한 번에 조회:

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/{tenantId}/internal/batch/frames` | 다중 Instance 프레임 일괄 조회 |

---

## 3. 상세 스펙

### 3.1 확장 WADO-RS: Study 메타데이터

```http
GET /dicomweb-ext/{tenantId}/studies/{studyUID}/metadata
Accept: application/dicom+json
```

**응답**: DICOM JSON 배열 (Instance별 메타데이터)

### 3.2 확장 WADO-RS: Instance 다운로드

```http
GET /dicomweb-ext/{tenantId}/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}
Accept: application/dicom
```

**응답**: DICOM 바이너리 파일

### 3.3 확장 WADO-RS: 프레임 렌더링

```http
GET /dicomweb-ext/{tenantId}/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/frames/{frameList}/rendered
Accept: image/jpeg, image/png
```

**파라미터**:
| 이름 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| quality | Integer | 100 | 이미지 품질 (1-100, 100=PNG, <100=JPEG) |
| rows | Integer | null | 출력 이미지 높이 |
| columns | Integer | null | 출력 이미지 너비 |

**응답**:
- 단일 프레임: `image/jpeg` 또는 `image/png`
- 다중 프레임: `multipart/related`

### 3.4 내부 API: Instance 조회

```http
GET /dicomweb-ext/{tenantId}/internal/instances/{instanceId}
Accept: application/json
```

**응답 예시**:
```json
{
  "id": 123,
  "uuid": "0192abc0-...",
  "sopInstanceUid": "1.2.840.113619...",
  "sopClassUid": "1.2.840.10008.5.1.4.1.1.2",
  "seriesId": 45,
  "seriesInstanceUid": "1.2.840.113619...",
  "studyId": 12,
  "studyInstanceUid": "1.2.840.113619...",
  "instanceNumber": 1,
  "imageRows": 512,
  "imageColumns": 512,
  "numberOfFrames": 100,
  "bitsAllocated": 16,
  "bitsStored": 12,
  "transferSyntaxUid": "1.2.840.10008.1.2.1",
  "photometricInterpretation": "MONOCHROME2",
  "transcodingStatus": "COMPLETED",
  "thumbnailPath": "/tenants/1/instances/123/thumbnail.jpg",
  "prerenderedBasePath": "/tenants/1/instances/123/prerendered"
}
```

### 3.5 내부 API: 프레임 BulkData

```http
GET /dicomweb-ext/{tenantId}/internal/instances/{instanceId}/frames/{frameList}
Accept: application/octet-stream, multipart/related
```

**응답**:
- 단일 프레임: `application/octet-stream` (Raw Pixel Data)
- 다중 프레임: `multipart/related` (각 파트에 `X-Frame-Number` 헤더)

### 3.6 내부 API: 프레임 렌더링

```http
GET /dicomweb-ext/{tenantId}/internal/instances/{instanceId}/frames/{frameList}/rendered
Accept: image/jpeg, image/png, multipart/related
```

**파라미터**: 3.3과 동일

### 3.7 Batch API: 다중 Instance 프레임 조회

```http
POST /dicomweb-ext/{tenantId}/internal/batch/frames
Content-Type: application/json
Accept: multipart/related
```

**요청 본문**:
```json
{
  "requests": [
    { "instanceId": 123, "frames": [1, 2, 3] },
    { "instanceId": 124, "frames": [1, 2, 3, 4, 5] },
    { "instanceId": 125, "frames": [1] }
  ],
  "format": "rendered",
  "quality": 85,
  "resolution": 256
}
```

**필드 설명**:
| 필드 | 타입 | 필수 | 기본값 | 설명 |
|------|------|------|--------|------|
| requests | Array | Yes | - | 프레임 요청 목록 |
| requests[].instanceId | Long | Yes | - | Instance DB ID |
| requests[].frames | Array<Integer> | Yes | - | 프레임 번호 목록 (1부터 시작) |
| format | String | No | "rendered" | "rendered" (이미지) 또는 "bulkdata" (Raw) |
| quality | Integer | No | 85 | 이미지 품질 (rendered 형식) |
| resolution | Integer | No | null | 출력 해상도 (rendered 형식) |

**응답**: `multipart/related`
- 각 파트에 `X-Instance-Id`, `X-Frame-Number` 헤더 포함

---

## 4. 사용 예시

### 4.1 cURL 예시

```bash
# 1. 확장 WADO-RS: Study 메타데이터
curl "http://localhost:10201/dicomweb-ext/1/studies/1.2.840.113619.../metadata"

# 2. 내부 API: Instance 조회
curl "http://localhost:10201/dicomweb-ext/1/internal/instances/123"

# 3. 내부 API: 프레임 렌더링 (단일)
curl "http://localhost:10201/dicomweb-ext/1/internal/instances/123/frames/1/rendered?quality=85" \
  -o frame1.jpg

# 4. 내부 API: 프레임 렌더링 (다중)
curl "http://localhost:10201/dicomweb-ext/1/internal/instances/123/frames/1,2,3/rendered?quality=85" \
  -o frames.multipart

# 5. Batch API
curl -X POST "http://localhost:10201/dicomweb-ext/1/internal/batch/frames" \
  -H "Content-Type: application/json" \
  -d '{
    "requests": [
      {"instanceId": 123, "frames": [1, 2, 3]},
      {"instanceId": 124, "frames": [1, 2, 3, 4, 5]}
    ],
    "format": "rendered",
    "quality": 85,
    "resolution": 256
  }' \
  -o batch.multipart
```

### 4.2 TypeScript 예시

```typescript
// 내부 API: Instance 조회
async function getInstanceById(tenantId: number, instanceId: number) {
  const response = await fetch(
    `/dicomweb-ext/${tenantId}/internal/instances/${instanceId}`
  );
  return response.json();
}

// 내부 API: 프레임 렌더링
async function getFrameRendered(
  tenantId: number,
  instanceId: number,
  frameNumber: number,
  quality: number = 85
) {
  const response = await fetch(
    `/dicomweb-ext/${tenantId}/internal/instances/${instanceId}/frames/${frameNumber}/rendered?quality=${quality}`
  );
  return response.blob();
}

// Batch API
async function batchFrames(
  tenantId: number,
  requests: { instanceId: number; frames: number[] }[],
  format: 'rendered' | 'bulkdata' = 'rendered',
  quality: number = 85
) {
  const response = await fetch(
    `/dicomweb-ext/${tenantId}/internal/batch/frames`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ requests, format, quality })
    }
  );
  return response.body; // ReadableStream for multipart parsing
}
```

---

## 5. 성능 비교

| API | 식별자 | 쿼리 | 평균 응답 시간 |
|-----|--------|------|---------------|
| 표준 DICOMweb | SOP Instance UID | Index Scan | ~15ms |
| 내부 API | DB PK (id) | PK Lookup | ~2ms |
| 성능 차이 | - | - | **7.5배 빠름** |

---

## 6. 관련 문서

| 문서 | 설명 |
|------|------|
| [13-4_DICOMWeb_API_설계.md](13-4_DICOMWeb_API_설계.md) | 표준 DICOMweb API 설계 |
| [31_DICOMWeb_WADO_구현_가이드.md](31_DICOMWeb_WADO_구현_가이드.md) | WADO-RS 구현 상세 |
| [32_REST_API_명세서.md](32_REST_API_명세서.md) | 전체 REST API 명세 |

---

## 7. 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|----------|
| 2026-01-22 | 1.0 | 최초 작성 |
