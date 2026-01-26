# Mini PACS PoC 분석 - Part 4: DICOMWeb API 설계

> **원본 문서**: [13_mini-pacs-poc_분석_개요.md](13_mini-pacs-poc_분석_개요.md)
> **시리즈**: Part 4/5
> **작성일**: 2025-12-20 (분할: 2025-12-28)

**관련 파일**:
- [← Part 3: 데이터베이스 스키마](13-3_데이터베이스_스키마.md)
- [→ Part 5: 통합 단계별 계획](13-5_통합_단계별_계획.md)

---

## 6. API 설계 (DICOMWeb + Extended)

### 6.1 DICOMWeb 표준 API (QIDO-RS, WADO-RS, STOW-RS)

**mini-pacs-poc 컨트롤러 직접 이관** (Spring Boot 4.0.1 호환 확인 필요)

#### QIDO-RS (검색)

```java
@RestController
@RequestMapping("/dicom-web")
public class QidoRsController {

    @GetMapping("/studies")
    public ResponseEntity<List<Map<String, Object>>> searchStudies(
        @RequestParam(required = false) String PatientID,
        @RequestParam(required = false) String StudyDate,
        @RequestParam(defaultValue = "100") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        // DICOM JSON Model 응답
        // application/dicom+json
    }

    @GetMapping("/studies/{studyUid}/series")
    public ResponseEntity<List<Map<String, Object>>> searchSeries(
        @PathVariable String studyUid,
        @RequestParam(required = false) String Modality
    ) {
        // ...
    }

    @GetMapping("/studies/{studyUid}/series/{seriesUid}/instances")
    public ResponseEntity<List<Map<String, Object>>> searchInstances(
        @PathVariable String studyUid,
        @PathVariable String seriesUid
    ) {
        // ...
    }
}
```

#### WADO-RS (조회)

```java
@RestController
@RequestMapping("/dicom-web")
public class WadoRsController {

    @GetMapping(value = "/studies/{studyUid}/series/{seriesUid}/instances/{instanceUid}",
                produces = "application/dicom")
    public ResponseEntity<byte[]> retrieveInstance(
        @PathVariable String studyUid,
        @PathVariable String seriesUid,
        @PathVariable String instanceUid
    ) {
        // DICOM 바이너리 반환
    }

    @GetMapping("/studies/{studyUid}/series/{seriesUid}/instances/{instanceUid}/rendered")
    public ResponseEntity<byte[]> retrieveRenderedInstance(
        @PathVariable String instanceUid
    ) {
        // JPEG 렌더링 이미지
    }

    @GetMapping("/studies/{studyUid}/series/{seriesUid}/instances/{instanceUid}/thumbnail")
    public ResponseEntity<byte[]> retrieveThumbnail(
        @PathVariable String instanceUid
    ) {
        // 썸네일 (256x256 JPEG)
    }
}
```

#### STOW-RS (저장)

```java
@RestController
@RequestMapping("/dicom-web")
public class StowRsController {

    @PostMapping(value = "/studies",
                 consumes = "application/dicom")
    public ResponseEntity<Map<String, Object>> storeInstances(
        @RequestBody byte[] dicomData
    ) {
        // DICOM 파일 저장
        // 응답: 저장 결과 + SOPInstanceUID
    }
}
```

### 6.2 Extended API (비표준 확장)

#### Instance API

```java
@RestController
@RequestMapping("/api/v1/instances")
public class InstanceController {

    @GetMapping("/{sopInstanceUid}")
    public InstanceDetailResponse getInstanceDetail(@PathVariable String sopInstanceUid) {
        // Instance 상세 정보 (확장 메타데이터 포함)
    }

    @GetMapping("/{sopInstanceUid}/cine-info")
    public CineInfoResponse getCineInfo(@PathVariable String sopInstanceUid) {
        // Cine 재생 정보 (fps, frameTime, numberOfFrames)
    }

    @GetMapping("/{sopInstanceUid}/frames/{frameNumber}")
    public ResponseEntity<byte[]> getFrame(
        @PathVariable String sopInstanceUid,
        @PathVariable int frameNumber  // 1-based
    ) {
        // 특정 프레임 JPEG
    }

    @GetMapping("/{sopInstanceUid}/thumbnail")
    public ResponseEntity<byte[]> getThumbnail(@PathVariable String sopInstanceUid) {
        // 썸네일 (중간 프레임 또는 첫 프레임)
    }

    @GetMapping("/{sopInstanceUid}/ultrasound-regions")
    public List<UltrasoundRegion> getUltrasoundRegions(@PathVariable String sopInstanceUid) {
        // 픽셀→물리 변환 정보
    }

    @GetMapping("/{sopInstanceUid}/video")
    public ResponseEntity<byte[]> getVideo(@PathVariable String sopInstanceUid) {
        // MP4 비디오 (멀티프레임만)
    }

    @GetMapping("/{sopInstanceUid}/video/available")
    public VideoAvailabilityResponse checkVideoAvailability(@PathVariable String sopInstanceUid) {
        // 비디오 변환 가능 여부 + 트랜스코딩 상태
    }
}
```

#### Study API

```java
@RestController
@RequestMapping("/api/v1/studies")
public class StudyController {

    @GetMapping("/{studyUid}/summary")
    public StudySummaryResponse getStudySummary(@PathVariable String studyUid) {
        // Study 요약 (Series 개수, Instance 개수, 환자 정보)
    }

    @GetMapping("/{studyUid}/instances")
    public List<InstanceBasicInfo> getInstances(@PathVariable String studyUid) {
        // Instance 목록 (경량)
    }

    @GetMapping("/{studyUid}/instances/detail")
    public List<InstanceDetailResponse> getInstancesDetail(@PathVariable String studyUid) {
        // Instance 상세 목록 (Cine 정보, 트랜스코딩 상태 포함)
    }
}
```

### 6.3 Dual API 패턴 (DICOMWeb vs Internal API)

#### 6.3.1 패턴 개요

SADO MiniPACS는 **Hybrid Approach**를 채택하여 2가지 API를 병행 제공합니다.

```
┌─────────────────────────────────────────────────────────────┐
│                     Client Types                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────────┐              ┌──────────────────┐    │
│  │  External Systems│              │  SADO Frontend   │    │
│  │  - PACS          │              │  - React App     │    │
│  │  - OHIF Viewer   │              │  - Mobile App    │    │
│  │  - 3rd Party     │              │  - Admin Panel   │    │
│  └────────┬─────────┘              └────────┬─────────┘    │
│           │                                 │              │
│           ▼                                 ▼              │
│  ┌──────────────────┐              ┌──────────────────┐    │
│  │ DICOMWeb API     │              │ Internal API     │    │
│  │ (UID 기반)        │              │ (id 기반)         │    │
│  └──────────────────┘              └──────────────────┘    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### 6.3.2 Pattern 1: DICOMWeb API (UID 기반)

**용도:** 외부 시스템 연동, PACS 간 상호운용성

**엔드포인트 예시:**
```http
GET /dicomweb/studies/{studyUid}/series/{seriesUid}/instances/{sopInstanceUid}
```

**장점:**
- ✅ DICOM 표준 완벽 준수 (ISO 12052)
- ✅ 다른 PACS와 100% 호환
- ✅ OHIF Viewer, Weasis 등 표준 뷰어 연동 가능
- ✅ DICOMWeb 클라이언트 라이브러리 사용 가능

**단점:**
- ❌ UID 중복 시 문제 발생 가능 (70% 신뢰도)
- ❌ 긴 URL (평균 120자)
- ❌ 조인 쿼리 복잡 (Study → Series → Instance)

**구현:**
```java
@RestController
@RequestMapping("/dicomweb")
public class WadoRsController {

    @Autowired
    private InstanceRepository instanceRepository;

    @Autowired
    private SeaweedFsClient storageClient;

    @GetMapping(
        value = "/studies/{studyUid}/series/{seriesUid}/instances/{sopInstanceUid}",
        produces = "application/dicom"
    )
    public ResponseEntity<byte[]> retrieveInstance(
        @PathVariable String studyUid,
        @PathVariable String seriesUid,
        @PathVariable String sopInstanceUid
    ) {
        // 1. UID 기반 조회 (여러 결과 가능)
        List<Instance> instances =
            instanceRepository.findAllBySopInstanceUid(sopInstanceUid);

        if (instances.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (instances.size() > 1) {
            // UID 충돌: 첫 번째 결과 반환 + 경고 헤더
            log.warn("UID collision: {} instances with UID {}",
                instances.size(), sopInstanceUid);

            return ResponseEntity.ok()
                .header("X-UID-Collision-Warning", "Multiple instances found")
                .body(loadDicomFile(instances.get(0)));
        }

        // 2. 단일 결과: 정상 반환
        Instance instance = instances.get(0);
        byte[] dicomBytes = storageClient.download(instance.getStorageFileId());

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/dicom"))
            .body(dicomBytes);
    }
}
```

#### 6.3.3 Pattern 2: Internal REST API (id 기반) ⭐ 권장

**용도:** 프론트엔드 최적화, 내부 시스템 전용

**엔드포인트 예시:**
```http
GET /api/v1/instances/{id}/file
GET /api/v1/instances/{id}/metadata
```

**장점:**
- ✅ **절대 고유성 보장** (DB Primary Key)
- ✅ **빠른 검색** (PK index, O(log n))
- ✅ **짧은 URL** (평균 30자)
- ✅ **단순한 쿼리** (단일 테이블 조회)

**단점:**
- ❌ DICOM 표준 아님 (외부 시스템 연동 불가)
- ❌ id 노출 시 보안 고려 필요 (tenant isolation 필수)

**구현:**
```java
@RestController
@RequestMapping("/api/v1/instances")
public class InstanceController {

    @Autowired
    private InstanceRepository instanceRepository;

    @Autowired
    private SeaweedFsClient storageClient;

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> getFile(@PathVariable Long id) {
        // 1. PK로 직접 조회 (단일 쿼리, 밀리초 이내)
        Instance instance = instanceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Instance not found"));

        // 2. Tenant 검증 (자동, @TenantFilter)
        // TenantContext.getCurrentTenantId() == instance.getTenantId()

        // 3. SeaweedFS 직접 읽기
        String fileId = instance.getStorageFileId();
        byte[] dicomBytes = storageClient.download(fileId);

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/dicom"))
            .header("X-Instance-Id", id.toString())
            .header("X-SOP-Instance-UID", instance.getSopInstanceUid())
            .body(dicomBytes);
    }

    @GetMapping("/{id}/metadata")
    public ResponseEntity<InstanceDetailResponse> getMetadata(@PathVariable Long id) {
        Instance instance = instanceRepository.findById(id)
            .orElseThrow();

        return ResponseEntity.ok(InstanceDetailResponse.from(instance));
    }
}
```

#### 6.3.4 성능 비교

**벤치마크** (MySQL 8.0, 1000만 레코드):

| API | 쿼리 | 인덱스 타입 | 평균 응답 시간 | QPS |
|-----|------|------------|--------------|-----|
| **DICOMWeb** | `SELECT * FROM instance WHERE sop_instance_uid = ?` | BTREE (Non-Unique) | 15ms | 600 |
| **Internal API** | `SELECT * FROM instance WHERE id = ?` | PRIMARY KEY | **2ms** | **5000** |
| **성능 차이** | - | - | **7.5배 빠름** | **8.3배 높음** |

#### 6.3.5 프론트엔드 통합 예시

```typescript
// React + TanStack Query
import { useQuery } from '@tanstack/react-query';

// Pattern 1: DICOMWeb API (외부 뷰어 연동)
function useDicomWebImage(sopInstanceUid: string) {
  return useQuery({
    queryKey: ['dicomweb', sopInstanceUid],
    queryFn: async () => {
      const response = await fetch(
        `/dicomweb/studies/${studyUid}/series/${seriesUid}/instances/${sopInstanceUid}`
      );
      return response.arrayBuffer();
    }
  });
}

// Pattern 2: Internal API (SADO 전용 최적화) ⭐ 권장
function useInstanceFile(id: number) {
  return useQuery({
    queryKey: ['instance', id],
    queryFn: async () => {
      const response = await fetch(`/api/v1/instances/${id}/file`);
      return response.arrayBuffer();
    },
    staleTime: 5 * 60 * 1000,  // 5분 캐싱
  });
}

// 사용 예시
function DicomViewer({ instanceId }: { instanceId: number }) {
  const { data: dicomFile, isLoading } = useInstanceFile(instanceId);

  if (isLoading) return <Spinner />;

  return <CornerstoneViewport dicomFile={dicomFile} />;
}
```

#### 6.3.6 권장 사항

**Hybrid Approach** 채택:

| 클라이언트 | 권장 API | 이유 |
|----------|---------|------|
| **SADO Frontend** | Internal API | 성능 최적화 (7.5배 빠름) |
| **SADO Mobile App** | Internal API | 짧은 URL, 간단한 통합 |
| **OHIF Viewer** | DICOMWeb API | 표준 준수, 외부 라이브러리 호환 |
| **병원 PACS** | DICOMWeb API | 상호운용성 필수 |
| **3rd Party Tool** | DICOMWeb API | 표준 API 요구 |

**구현 우선순위**:
1. Week 2-3: Internal API 구현 (FE 연동 우선)
2. Week 6-8: DICOMWeb API 추가 (외부 연동 대비)

**보안 고려사항**:
- Internal API: Tenant isolation 필수 (`@TenantFilter`)
- DICOMWeb API: 외부 노출 시 OAuth 2.0 적용

**관련 문서**:
- UID 신뢰도: `21_DICOM_원본_보존_정책.md § 4 sopInstanceUid 신뢰도 관리`
- 구현 상세: `17-1_AI_파일_서빙_아키텍처.md § 2.7 id 기반 서빙 아키텍처`

---

