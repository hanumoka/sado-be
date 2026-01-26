# REST API 명세서

> **최종 업데이트**: 2026-01-16
> **버전**: 2.0
> **상태**: 구현 완료

---

## 1. 개요

sado_be 프로젝트의 전체 REST API 명세입니다. DICOMWeb 표준 API와 커스텀 API를 포함합니다.

### 1.1 Base URL

```
개발 환경: http://localhost:10201
운영 환경: https://api.sado.example.com
```

### 1.2 인증

- **방식**: JWT Bearer Token
- **헤더**: `Authorization: Bearer {token}`
- **멀티테넌시**: `X-Tenant-Id: {tenantId}` (선택)

---

## 2. DICOMWeb API

### 2.1 QIDO-RS (Query)

#### Study 검색

```http
GET /dicomweb/studies
```

**Query Parameters**:
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `PatientID` | string | 환자 ID |
| `PatientName` | string | 환자 이름 (와일드카드 지원) |
| `StudyDate` | string | 검사 날짜 (YYYYMMDD) |
| `StudyInstanceUID` | string | Study UID |
| `limit` | number | 결과 수 제한 (기본: 100) |
| `offset` | number | 페이지 오프셋 |

**응답**: `200 OK`
```json
[
  {
    "00080020": { "vr": "DA", "Value": ["20260115"] },
    "0020000D": { "vr": "UI", "Value": ["1.2.840.113619..."] },
    "00100010": { "vr": "PN", "Value": [{"Alphabetic": "Doe^John"}] },
    "00100020": { "vr": "LO", "Value": ["PAT001"] }
  }
]
```

---

#### Series 검색

```http
GET /dicomweb/studies/{studyUID}/series
```

**Path Parameters**:
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `studyUID` | string | Study Instance UID |

**Query Parameters**:
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `Modality` | string | 장비 타입 (US, CT, MR 등) |
| `SeriesInstanceUID` | string | Series UID |

**응답**: `200 OK`
```json
[
  {
    "0020000E": { "vr": "UI", "Value": ["1.2.840.113619..."] },
    "00080060": { "vr": "CS", "Value": ["US"] },
    "0008103E": { "vr": "LO", "Value": ["Cardiac 4CH"] }
  }
]
```

---

#### Instance 검색

```http
GET /dicomweb/studies/{studyUID}/series/{seriesUID}/instances
```

**Path Parameters**:
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `studyUID` | string | Study Instance UID |
| `seriesUID` | string | Series Instance UID |

**응답**: `200 OK`
```json
[
  {
    "00080018": { "vr": "UI", "Value": ["1.2.840.113619..."] },
    "00080016": { "vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.3.1"] },
    "00280008": { "vr": "IS", "Value": ["120"] },
    "00181063": { "vr": "DS", "Value": ["33.33"] }
  }
]
```

---

### 2.2 WADO-RS (Retrieve)

#### Instance 조회 (원본 DICOM)

```http
GET /dicomweb/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}
```

**응답**: `200 OK`
- **Content-Type**: `application/dicom`
- **Body**: 원본 DICOM 파일 바이너리

---

#### Instance 메타데이터 조회

```http
GET /dicomweb/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/metadata
```

**응답**: `200 OK`
```json
{
  "00080018": { "vr": "UI", "Value": ["1.2.840.113619..."] },
  "00280010": { "vr": "US", "Value": [480] },
  "00280011": { "vr": "US", "Value": [640] },
  "00280008": { "vr": "IS", "Value": ["120"] }
}
```

---

#### 렌더링된 이미지 조회

```http
GET /dicomweb/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/rendered
```

**Query Parameters**:
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `viewport` | string | 뷰포트 크기 (예: 256,256) |
| `quality` | number | JPEG 품질 (1-100) |

**응답**: `200 OK`
- **Content-Type**: `image/jpeg` 또는 `image/png`
- **Body**: 렌더링된 이미지

---

#### 특정 프레임 조회

```http
GET /dicomweb/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/frames/{frameNumber}
```

**Path Parameters**:
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `frameNumber` | number | 프레임 번호 (1부터 시작) |

**Query Parameters**:
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `viewport` | string | 뷰포트 크기 |

**응답**: `200 OK`
- **Content-Type**: `image/jpeg`
- **Body**: 렌더링된 프레임

---

### 2.3 STOW-RS (Store)

#### DICOM 업로드

```http
POST /dicomweb/studies
```

**Request**:
- **Content-Type**: `multipart/form-data`
- **Body**: DICOM 파일

**응답**: `200 OK`
```json
{
  "00081190": { "vr": "UR", "Value": ["/dicomweb/studies/1.2.840..."] },
  "00081199": {
    "vr": "SQ",
    "Value": [
      {
        "00081150": { "vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.3.1"] },
        "00081155": { "vr": "UI", "Value": ["1.2.840.113619..."] }
      }
    ]
  }
}
```

---

## 3. Cine Frames API

### 3.1 모든 프레임 일괄 조회

```http
GET /dicomweb/cine-frames/{sopInstanceUID}
```

**Query Parameters**:
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| `resolution` | number | 256 | 해상도 (256, 128, 64, 32) |

**응답**: `200 OK`
```json
{
  "sopInstanceUid": "1.2.840.113619...",
  "numberOfFrames": 120,
  "resolution": 256,
  "frames": [
    "/9j/4AAQSkZJRg...",
    "/9j/4AAQSkZJRg...",
    "..."
  ]
}
```

**특징**:
- Pre-rendered JPEG 프레임을 Base64 인코딩하여 반환
- 클라이언트 사이드 캐싱에 최적화
- MJPEG 뷰어에서 사용

---

### 3.2 프레임 정보 조회

```http
GET /dicomweb/cine-frames/{sopInstanceUID}/info
```

**응답**: `200 OK`
```json
{
  "sopInstanceUid": "1.2.840.113619...",
  "numberOfFrames": 120,
  "transcodingStatus": "COMPLETED",
  "prerenderedFrameCount": 120,
  "prerenderedTotalSize": 15360000,
  "estimatedDownloadSize": 7680000,
  "availableResolutions": [256, 128, 64, 32],
  "frameRate": 30
}
```

---

## 4. MJPEG Streaming API

### 4.1 MJPEG 스트림

```http
GET /mjpeg/{sopInstanceUID}
```

**Query Parameters**:
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| `fps` | number | 30 | 프레임 레이트 |
| `resolution` | number | 256 | 해상도 |

**응답**: `200 OK`
- **Content-Type**: `multipart/x-mixed-replace; boundary=--boundary`
- **Body**: 무한 MJPEG 스트림

---

## 5. Patient API

### 5.1 환자 목록 조회

```http
GET /api/v1/patients
```

**Query Parameters**:
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| `page` | number | 0 | 페이지 번호 |
| `size` | number | 20 | 페이지 크기 |
| `dicomPatientId` | string | - | DICOM 환자 ID |
| `patientName` | string | - | 환자 이름 |

**응답**: `200 OK`
```json
{
  "code": "SUCCESS",
  "message": "Success",
  "data": {
    "content": [
      {
        "id": 1,
        "dicomPatientId": "PAT001",
        "patientName": "Doe^John",
        "patientBirthDate": "1980-01-15",
        "patientSex": "M",
        "studiesCount": 3
      }
    ],
    "totalElements": 100,
    "totalPages": 5
  }
}
```

---

### 5.2 환자 상세 조회

```http
GET /api/v1/patients/{patientId}
```

**응답**: `200 OK`
```json
{
  "code": "SUCCESS",
  "data": {
    "id": 1,
    "dicomPatientId": "PAT001",
    "patientName": "Doe^John",
    "patientBirthDate": "1980-01-15",
    "patientSex": "M",
    "issuerOfPatientId": "HOSPITAL_A",
    "emrPatientId": "EMR001",
    "matchingStatus": "AUTO_MATCHED",
    "matchingConfidence": 0.98
  }
}
```

---

### 5.3 환자의 Study 목록

```http
GET /api/v1/patients/{patientId}/studies
```

**응답**: `200 OK`
```json
{
  "code": "SUCCESS",
  "data": [
    {
      "id": 1,
      "studyInstanceUid": "1.2.840.113619...",
      "studyDate": "2026-01-15",
      "studyDescription": "Cardiac Ultrasound",
      "numberOfSeries": 2,
      "numberOfInstances": 240
    }
  ]
}
```

---

## 6. Study API

### 6.1 Study 목록 조회

```http
GET /api/v1/studies
```

**Query Parameters**:
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `page` | number | 페이지 번호 |
| `size` | number | 페이지 크기 |
| `patientId` | number | 환자 ID |
| `studyDate` | string | 검사 날짜 |

**응답**: `200 OK`
```json
{
  "code": "SUCCESS",
  "data": {
    "content": [
      {
        "id": 1,
        "studyInstanceUid": "1.2.840.113619...",
        "studyDate": "2026-01-15",
        "studyDescription": "Cardiac Ultrasound",
        "patientName": "Doe^John",
        "numberOfSeries": 2,
        "numberOfInstances": 240
      }
    ]
  }
}
```

---

### 6.2 Study 상세 조회

```http
GET /api/v1/studies/{studyId}
```

---

### 6.3 Study의 Series 목록

```http
GET /api/v1/studies/{studyId}/series
```

---

## 7. Series API

### 7.1 Series 목록 조회

```http
GET /api/v1/series
```

---

### 7.2 Series 상세 조회

```http
GET /api/v1/series/{seriesId}
```

---

### 7.3 Series의 Instance 목록

```http
GET /api/v1/series/{seriesId}/instances
```

---

## 8. Instance API

### 8.1 Instance 목록 조회

```http
GET /api/v1/instances
```

---

### 8.2 Instance 상세 조회

```http
GET /api/v1/instances/{instanceId}
```

---

### 8.3 DICOM 파일 업로드

```http
POST /api/v1/instances/upload
```

**Request**:
- **Content-Type**: `multipart/form-data`
- **Body**: `file` (DICOM 파일)

**응답**: `201 Created`
```json
{
  "code": "SUCCESS",
  "message": "DICOM uploaded successfully",
  "data": {
    "instanceId": 1,
    "sopInstanceUid": "1.2.840.113619...",
    "numberOfFrames": 120,
    "storagePath": "studies/1.2.840.../instances/1.2.840..."
  }
}
```

---

## 9. Admin API

### 9.1 SeaweedFS 볼륨 상태

```http
GET /api/admin/seaweedfs/volumes
```

**응답**: `200 OK`
```json
{
  "code": "SUCCESS",
  "data": {
    "volumes": [
      {
        "id": 1,
        "size": 1073741824,
        "used": 536870912,
        "replication": "000",
        "collection": "minipacs"
      }
    ],
    "totalCapacity": 10737418240,
    "totalUsed": 5368709120
  }
}
```

---

### 9.2 스토리지 메트릭

```http
GET /api/admin/metrics/storage
```

**응답**: `200 OK`
```json
{
  "code": "SUCCESS",
  "data": {
    "totalFiles": 12345,
    "totalSize": 132070244352,
    "tierDistribution": {
      "HOT": 5000,
      "WARM": 5000,
      "COLD": 2345
    }
  }
}
```

---

### 9.3 스토리지 추세

```http
GET /api/admin/metrics/storage/trends
```

**Query Parameters**:
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| `period` | string | 7d | 기간 (7d, 30d, 90d) |

---

### 9.4 파일 Tier 변경

```http
POST /api/admin/files/{fileId}/tier
```

**Request Body**:
```json
{
  "targetTier": "WARM"
}
```

---

## 10. 에러 응답

### 10.1 표준 에러 형식

```json
{
  "code": "ERROR_CODE",
  "message": "Human readable message",
  "timestamp": "2026-01-16T09:00:00Z",
  "path": "/api/v1/patients/999"
}
```

### 10.2 에러 코드

| HTTP 상태 | 코드 | 설명 |
|----------|------|------|
| 400 | `BAD_REQUEST` | 잘못된 요청 |
| 401 | `UNAUTHORIZED` | 인증 필요 |
| 403 | `FORBIDDEN` | 권한 없음 |
| 404 | `NOT_FOUND` | 리소스 없음 |
| 409 | `CONFLICT` | 충돌 (중복 등) |
| 500 | `INTERNAL_ERROR` | 서버 오류 |

---

## 11. 관련 문서

- [DICOMWeb API 설계](13-4_DICOMWeb_API_설계.md)
- [FE DICOM 뷰어 가이드](../../02_프론트엔드/02_가이드/06_DICOM_뷰어_아키텍처_가이드.md)

---

*최종 수정: 2026-01-16*
