# DICOMWeb WADO 구현 가이드

> **문서 위치**: `sado_docs/be/guides/31_DICOMWeb_WADO_구현_가이드.md`
> **작성일**: 2026-01-12
> **상태**: Production Ready

---

## 1. 개요

### 1.1 WADO란?
- **Web Access to DICOM Objects**의 약자
- DICOM Part 18 표준 (DICOMweb)
- HTTP를 통한 DICOM 객체 조회 표준

### 1.2 구현 범위

| 기능 | 설명 | 상태 |
|------|------|------|
| **WADO-URI** | 레거시 지원 (Query String 방식) | ✅ 완료 |
| **WADO-RS** | RESTful 접근 (Path 방식) | ✅ 완료 |
| **WADO-RS Rendered** | 서버 사이드 이미지 렌더링 | ✅ 완료 |
| **WADO-RS BulkData** | Raw 픽셀 데이터 추출 | ✅ 완료 |

---

## 2. Backend API 엔드포인트

### 2.1 WADO-URI (레거시)

| Method | Endpoint | 설명 | 응답 |
|--------|----------|------|------|
| GET | `/dicomweb/wado?requestType=WADO&studyUID=...&seriesUID=...&objectUID=...` | DICOM 파일 다운로드 | `application/dicom` |

**사용 예시:**
```http
GET /dicomweb/wado?requestType=WADO&studyUID=1.2.840.113619.2.417...&seriesUID=1.2.840.113619.2.417...&objectUID=1.2.840.113619.2.417...
Accept: application/dicom
```

**파라미터:**
- `requestType`: 항상 `WADO` (필수)
- `studyUID`: Study Instance UID (필수)
- `seriesUID`: Series Instance UID (필수)
- `objectUID`: SOP Instance UID (필수)

---

### 2.2 WADO-RS Metadata

| Method | Endpoint | 설명 | 응답 |
|--------|----------|------|------|
| GET | `/dicomweb/studies/{studyUID}/metadata` | Study 전체 메타데이터 | `application/dicom+json` |
| GET | `/dicomweb/studies/{studyUID}/series/{seriesUID}/metadata` | Series 메타데이터 | `application/dicom+json` |
| GET | `/dicomweb/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/metadata` | Instance 메타데이터 | `application/dicom+json` |

**Cornerstone3D 필수 메타데이터 (Instance 레벨):**
- Rows (0028,0010)
- Columns (0028,0011)
- Samples Per Pixel (0028,0002)
- Photometric Interpretation (0028,0004)
- Bits Allocated (0028,0100)
- Bits Stored (0028,0101)
- High Bit (0028,0102)
- Pixel Representation (0028,0103)
- Number of Frames (0028,0008)
- Transfer Syntax UID (0002,0010)

---

### 2.3 WADO-RS Instance

| Method | Endpoint | 설명 | 응답 |
|--------|----------|------|------|
| GET | `/dicomweb/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}` | DICOM 파일 다운로드 | `application/dicom` |

**응답 헤더:**
- `Content-Disposition: attachment; filename="{sopInstanceUID}.dcm"`

---

### 2.4 WADO-RS Rendered (PNG 이미지)

| Method | Endpoint | 설명 | 응답 |
|--------|----------|------|------|
| GET | `.../instances/{sopInstanceUID}/rendered` | 첫 프레임 PNG | `image/png` |
| GET | `.../instances/{sopInstanceUID}/frames/{frameList}/rendered` | 지정 프레임 PNG | `image/png` 또는 `multipart/related` |

**다중 프레임 요청:**
```http
GET /dicomweb/studies/.../instances/.../frames/1,2,3,4,5/rendered
Accept: image/png

# 응답
Content-Type: multipart/related; type="image/png"; boundary=frame-boundary
```

**캐싱:**
- `Cache-Control: max-age=3600` (1시간)

---

### 2.5 WADO-RS BulkData (PixelData)

| Method | Endpoint | 설명 | 응답 |
|--------|----------|------|------|
| GET | `.../instances/{sopInstanceUID}/frames/{frameList}` | Raw PixelData | `multipart/related; type="application/octet-stream"` |

**응답 헤더 (Cornerstone3D 연동용):**
- `X-DICOM-TransferSyntax`: 출력 형식 (1.2.840.10008.1.2.1 - Explicit VR LE)
- `X-DICOM-OriginalTransferSyntax`: 원본 압축 형식
- `X-DICOM-PhotometricInterpretation`: RGB, MONOCHROME2 등
- `X-DICOM-BitsAllocated`: 8, 16 등
- `X-DICOM-BitsStored`: 8, 16 등
- `X-DICOM-DecompressedOnServer`: true (서버에서 디코딩됨)

**프레임 리스트 형식:**
- 단일: `/frames/1`
- 다중: `/frames/1,2,3,4,5`

---

## 3. 성능 최적화

### 3.1 적용된 최적화

| 최적화 | 설명 | 개선 효과 |
|--------|------|----------|
| **StreamingResponseBody** | 메모리에 버퍼링 없이 스트리밍 | 메모리 90% 절감 |
| **JOIN FETCH 쿼리** | N+1 쿼리 방지 | 응답 시간 90% 단축 |
| **다중 프레임 배치** | 1 HTTP 요청으로 다중 프레임 | I/O 90% 절감 |
| **단일 DICOM 로드** | 다중 프레임 요청 시 파일 1회만 로드 | I/O 최적화 |

### 3.2 캐싱 설정

- 모든 렌더링 응답: `Cache-Control: max-age=3600` (1시간)
- SeaweedFS Pre-signed URL: 1시간 유효

---

## 4. 구현 파일 목록

### Backend 핵심 파일

| 파일 | 라인 수 | 역할 |
|------|--------|------|
| `DicomWebController.java` | ~1,176 | DICOMweb API 전체 |
| `DicomRenderingService.java` | ~500 | PNG 렌더링, PixelData 추출 |
| `DicomStorageService.java` | - | S3 파일 다운로드 인터페이스 |
| `SeaweedFsS3StorageService.java` | - | SeaweedFS S3 구현체 |
| `InstanceService.java` | - | Instance 조회 |

### 컨트롤러 메서드

```
DicomWebController.java
├── searchStudies() - QIDO-RS Study 검색
├── searchSeries() - QIDO-RS Series 검색
├── searchInstances() - QIDO-RS Instance 검색
├── getStudyMetadata() - WADO-RS Study 메타데이터
├── getSeriesMetadata() - WADO-RS Series 메타데이터
├── getInstanceMetadata() - WADO-RS Instance 메타데이터
├── retrieveInstance() - WADO-RS Instance 다운로드
├── retrieveRenderedInstance() - WADO-RS Rendered (단일)
├── retrieveRenderedFrames() - WADO-RS Rendered (다중)
├── retrieveFrames() - WADO-RS BulkData (PixelData)
├── wadoUri() - WADO-URI (레거시)
└── storeInstances() - STOW-RS 업로드
```

---

## 5. 에러 코드

| 에러 코드 | HTTP 상태 | 설명 |
|----------|-----------|------|
| INSTANCE_NOT_FOUND | 404 | Instance 없음 |
| INVALID_FRAME_NUMBER | 400 | 프레임 번호 범위 초과 |
| DICOM_RENDER_FAILED | 500 | 렌더링 실패 |
| STORAGE_DOWNLOAD_FAILED | 500 | 파일 다운로드 실패 |
| DICOM_INVALID_FORMAT | 400 | 유효하지 않은 DICOM 형식 |

---

## 6. 참고 자료

- [DICOM Part 18 (DICOMweb)](https://dicom.nema.org/medical/dicom/current/output/html/part18.html)
- [Cornerstone3D Documentation](https://www.cornerstonejs.org/)
- [DCM4CHE Library](https://www.dcm4che.org/)
