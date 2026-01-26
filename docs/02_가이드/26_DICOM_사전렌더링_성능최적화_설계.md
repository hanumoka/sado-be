# SADO MiniPACS - DICOM 사전 렌더링/추출 성능 최적화 설계 문서

> **문서 버전**: 1.0
> **작성일**: 2026-01-09
> **프로젝트**: SADO (Specialized AI-Driven DICOM Operations)

---

## 1. Executive Summary

### 1.1 배경
현재 SADO MiniPACS는 WADO-RS API 요청 시 **실시간 렌더링** 방식을 사용하고 있어, 프레임당 300ms~1100ms의 지연이 발생합니다. 특히 멀티프레임 DICOM(Cine 영상) 재생 시 100프레임 프리로드에 약 30초가 소요되어 사용자 경험을 저해합니다.

### 1.2 목표
파일 업로드 완료 시 **모든 프레임을 사전 처리**하여 응답 시간을 획기적으로 단축합니다.

| 지표 | 현재 | 목표 | 개선율 |
|------|------|------|--------|
| **WADO-RS Rendered** | 300ms~1100ms | < 50ms | 85-95% |
| **WADO-RS BulkData** | 200ms~800ms | < 50ms | 75-94% |
| **Cine 프리로드 (100프레임)** | ~30초 | < 5초 | 83% |

### 1.3 핵심 전략
```
┌─────────────────────────────────────────────────────────────────┐
│                    사전 처리 전략 (Pre-Processing)                │
├─────────────────────────────────────────────────────────────────┤
│  DICOM 업로드 → Scheduler 감지 → 모든 프레임 사전 처리 → S3 저장   │
│                                                                 │
│  산출물:                                                         │
│  ├── WADO-RS Rendered용: Small/Medium/Large (JPEG/PNG)          │
│  └── WADO-RS BulkData용: Raw PixelData (비압축 바이너리)          │
│                                                                 │
│  API 요청 → 캐시 확인 → 캐시 히트 시 즉시 반환 (< 50ms)            │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. 현재 상태 분석

### 2.1 시스템 아키텍처 (현재)

```
┌──────────┐     ┌─────────────────┐     ┌──────────────┐     ┌─────────┐
│    FE    │────▶│ DicomWebController│────▶│ RenderingService│────▶│ SeaweedFS│
│Cornerstone│◀────│  (실시간 렌더링)   │◀────│  (매번 파싱)    │◀────│   (S3)   │
└──────────┘     └─────────────────┘     └──────────────┘     └─────────┘
     │                                                              │
     │                    매 요청마다 반복                            │
     └──────────────────────────────────────────────────────────────┘
```

### 2.2 WADO-RS Rendered 병목 분석

```
┌─────────────────────────────────────────────────────────────────┐
│                  WADO-RS Rendered 요청 플로우                      │
├─────────────────────────────────────────────────────────────────┤
│  1. S3에서 DICOM 파일 다운로드     │  ~100-500ms  │  ████████░░  │
│  2. DCM4CHE로 DICOM 파싱          │  ~50-200ms   │  ████░░░░░░  │
│  3. 프레임 추출 + W/L 적용         │  ~100-300ms  │  ██████░░░░  │
│  4. PNG/JPEG 인코딩               │  ~50-100ms   │  ██░░░░░░░░  │
├─────────────────────────────────────────────────────────────────┤
│  총 지연                          │  300ms~1100ms │             │
└─────────────────────────────────────────────────────────────────┘
```

### 2.3 WADO-RS BulkData 병목 분석

```
┌─────────────────────────────────────────────────────────────────┐
│                  WADO-RS BulkData 요청 플로우                      │
├─────────────────────────────────────────────────────────────────┤
│  1. S3에서 DICOM 파일 다운로드     │  ~100-500ms  │  ████████░░  │
│  2. DCM4CHE로 DICOM 파싱          │  ~50-200ms   │  ████░░░░░░  │
│  3. JPEG2000 디코딩 (압축 시)      │  ~50-100ms   │  ██░░░░░░░░  │
│  4. Raw PixelData 추출            │  ~10ms       │  █░░░░░░░░░  │
├─────────────────────────────────────────────────────────────────┤
│  총 지연                          │  200ms~800ms  │             │
└─────────────────────────────────────────────────────────────────┘
```

### 2.4 FE Cine 재생 현황

| 항목 | 현재 상태 |
|------|----------|
| **프로토콜** | WADO-RS BulkData (`wadors:`) 또는 Rendered (`wadors-rendered:`) |
| **프리로드 전략** | 배치 프리로드 (레이아웃별 6/4/3/2프레임씩) |
| **프레임레이트** | requestAnimationFrame 기반 30fps |
| **병목** | 네트워크 I/O (100프레임 × 300ms = 30초 대기) |

---

## 3. 솔루션 설계

### 3.1 시스템 아키텍처 (개선 후)

```
                              ┌──────────────────┐
                              │   Pre-Rendering  │
                              │    Scheduler     │
                              │   (30초 주기)     │
                              └────────┬─────────┘
                                       │
                                       ▼
┌──────────┐                  ┌──────────────────┐
│  DICOM   │─────업로드──────▶│  InstanceService │
│  Upload  │                  │ (status=PENDING) │
└──────────┘                  └────────┬─────────┘
                                       │
                              ┌────────▼─────────┐
                              │ PreRenderingService│
                              │  (모든 프레임 처리) │
                              └────────┬─────────┘
                                       │
        ┌──────────────────────────────┼──────────────────────────────┐
        ▼                              ▼                              ▼
┌───────────────┐            ┌───────────────┐            ┌───────────────┐
│  small.jpg    │            │  medium.jpg   │            │   raw.bin     │
│  (256x256)    │            │  (512x512)    │            │ (Raw Pixels)  │
└───────────────┘            └───────────────┘            └───────────────┘
        │                              │                              │
        └──────────────────────────────┼──────────────────────────────┘
                                       │
                                       ▼
                              ┌──────────────────┐
                              │    SeaweedFS     │
                              │   (S3 Storage)   │
                              └──────────────────┘

                    ─────────── API 요청 시 ───────────

┌──────────┐     ┌─────────────────┐     ┌──────────────┐
│    FE    │────▶│DicomWebController│────▶│  SeaweedFS   │
│Cornerstone│◀────│ (캐시 조회)      │◀────│ (즉시 반환)  │
└──────────┘     └─────────────────┘     └──────────────┘
                         │
                         │ 캐시 미스 시
                         ▼
                 ┌───────────────┐
                 │ Fallback:     │
                 │ 실시간 렌더링  │
                 └───────────────┘
```

### 3.2 사전 처리 산출물 정의

#### 3.2.1 저장 경로 구조
```
rendered/{sopInstanceUid}/frames/{frameNumber}/
├── small.jpg     # 256x256 JPEG (품질 85%)  - 썸네일/목록
├── medium.jpg    # 512x512 JPEG (품질 90%)  - 미리보기/Cine
├── large.png     # 원본 크기 PNG            - 상세 뷰어
└── raw.bin       # Raw PixelData (LE)       - BulkData API
```

#### 3.2.2 WADO-RS Rendered 산출물

| 크기 | 해상도 | 포맷 | 품질 | 용도 | 평균 크기 |
|------|--------|------|------|------|----------|
| **Small** | 256×256 | JPEG | 85% | 목록 썸네일 | ~20KB |
| **Medium** | 512×512 | JPEG | 90% | 미리보기/Cine | ~50KB |
| **Large** | 원본 | PNG | 무손실 | 상세 뷰어 | ~200KB |

#### 3.2.3 WADO-RS BulkData 산출물

| 타입 | 형식 | Byte Order | 용도 | 평균 크기 |
|------|------|------------|------|----------|
| **Raw** | 비압축 PixelData | Little Endian | Cornerstone wadors 로더 | ~512KB (16-bit) |

### 3.3 상태 관리

```
┌─────────┐     ┌─────────┐     ┌────────────┐     ┌───────────┐
│  NONE   │────▶│ PENDING │────▶│ PROCESSING │────▶│ COMPLETED │
└─────────┘     └─────────┘     └────────────┘     └───────────┘
                     │
                     │          ┌──────────┐
                     └─────────▶│  FAILED  │
                                └──────────┘
```

| 상태 | 설명 |
|------|------|
| `NONE` | 기존 Instance (사전 처리 대상 아님) |
| `PENDING` | 업로드 완료, 스케줄러 대기 중 |
| `PROCESSING` | 사전 처리 진행 중 |
| `COMPLETED` | 사전 처리 완료, 캐시 사용 가능 |
| `FAILED` | 사전 처리 실패 |

---

## 4. 구현 상세

### 4.1 Phase 1: 엔티티 수정

**파일**: `sado-minipacs/src/main/java/com/hanumoka/sado/minipacs/domain/entity/Instance.java`

```java
// ═══════════════════════════════════════════════════════════════
// 기존 필드 활용
// ═══════════════════════════════════════════════════════════════
@Column(name = "thumbnail_path")
private String thumbnailPath;  // 첫 프레임 Small 경로 (목록용)

@Enumerated(EnumType.STRING)
@Column(name = "transcoding_status")
private TranscodingStatus transcodingStatus;  // 이미 존재, 활용

// ═══════════════════════════════════════════════════════════════
// 신규 필드 추가
// ═══════════════════════════════════════════════════════════════
@Column(name = "rendered_base_path", length = 512)
private String renderedBasePath;  // rendered/{sopUid}/frames/

@Column(name = "rendered_frame_count")
private Integer renderedFrameCount;  // 렌더링 완료된 프레임 수

@Column(name = "raw_pixel_size")
private Long rawPixelSize;  // 단일 프레임 Raw PixelData 크기 (bytes)
```

**필드 설명**:

| 필드 | 타입 | 설명 |
|------|------|------|
| `renderedBasePath` | String | 사전 처리 파일 기본 경로 |
| `renderedFrameCount` | Integer | 처리 완료된 프레임 수 |
| `rawPixelSize` | Long | 단일 프레임 Raw 데이터 크기 (바이트) |
| `transcodingStatus` | Enum | 사전 처리 상태 |

---

### 4.2 Phase 2: 사전 처리 서비스

**신규 파일**: `sado-minipacs/src/main/java/com/hanumoka/sado/minipacs/domain/service/PreRenderingService.java`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PreRenderingService {

    private final DicomRenderingService dicomRenderingService;
    private final DicomStorageService dicomStorageService;
    private final InstanceRepository instanceRepository;

    // ═══════════════════════════════════════════════════════════════
    // 메인 처리 메서드
    // ═══════════════════════════════════════════════════════════════

    /**
     * Instance의 모든 프레임 사전 처리
     *
     * @param instance 처리 대상 Instance
     * @throws RuntimeException 처리 실패 시
     */
    @Transactional
    public void preProcessAllFrames(Instance instance) {
        String storagePath = instance.getStoragePath();
        int numberOfFrames = Optional.ofNullable(instance.getNumberOfFrames()).orElse(1);
        String basePath = "rendered/" + instance.getSopInstanceUid() + "/frames";
        long rawPixelSize = 0;

        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath)) {
            byte[] dicomBytes = dicomStream.readAllBytes();
            ImageReader reader = initializeImageReader(dicomBytes);

            for (int frame = 1; frame <= numberOfFrames; frame++) {
                String framePath = basePath + "/" + frame;

                // ─────────────────────────────────────────────────────
                // WADO-RS Rendered 산출물 생성
                // ─────────────────────────────────────────────────────
                BufferedImage renderedImage = extractRenderedFrame(reader, frame);

                uploadResizedImage(renderedImage, framePath + "/small.jpg", 256, 256, 0.85f);
                uploadResizedImage(renderedImage, framePath + "/medium.jpg", 512, 512, 0.90f);
                uploadOriginalAsPng(renderedImage, framePath + "/large.png");

                // ─────────────────────────────────────────────────────
                // WADO-RS BulkData 산출물 생성
                // ─────────────────────────────────────────────────────
                byte[] rawPixels = extractRawPixelData(reader, frame);
                uploadRawPixelData(rawPixels, framePath + "/raw.bin");

                if (frame == 1) {
                    rawPixelSize = rawPixels.length;
                }

                log.debug("Pre-processed frame {}/{} for {}",
                    frame, numberOfFrames, instance.getSopInstanceUid());
            }

            // Instance 메타데이터 업데이트
            updateInstanceMetadata(instance, basePath, numberOfFrames, rawPixelSize);

            log.info("Pre-processing completed: {} ({} frames, raw={}KB/frame)",
                instance.getSopInstanceUid(), numberOfFrames, rawPixelSize / 1024);

        } catch (Exception e) {
            handlePreProcessingFailure(instance, e);
            throw new RuntimeException("Pre-processing failed for " + instance.getSopInstanceUid(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Private Helper 메서드
    // ═══════════════════════════════════════════════════════════════

    private ImageReader initializeImageReader(byte[] dicomBytes) throws IOException {
        ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(dicomBytes));
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("DICOM");
        if (!readers.hasNext()) {
            throw new IOException("No DICOM ImageReader found");
        }
        ImageReader reader = readers.next();
        reader.setInput(iis);
        return reader;
    }

    private BufferedImage extractRenderedFrame(ImageReader reader, int frame) throws IOException {
        // W/L 적용된 렌더링 이미지 추출
        return reader.read(frame - 1);  // 0-indexed
    }

    private byte[] extractRawPixelData(ImageReader reader, int frame) throws IOException {
        // W/L 미적용 raw pixels 추출
        Raster raster = reader.readRaster(frame - 1, null);
        return extractRawBytesFromRaster(raster);
    }

    private byte[] extractRawBytesFromRaster(Raster raster) {
        DataBuffer dataBuffer = raster.getDataBuffer();
        int size = dataBuffer.getSize();

        if (dataBuffer instanceof DataBufferUShort) {
            short[] data = ((DataBufferUShort) dataBuffer).getData();
            ByteBuffer buffer = ByteBuffer.allocate(size * 2)
                .order(ByteOrder.LITTLE_ENDIAN);
            for (short s : data) {
                buffer.putShort(s);
            }
            return buffer.array();
        } else if (dataBuffer instanceof DataBufferByte) {
            return ((DataBufferByte) dataBuffer).getData();
        }
        throw new IllegalArgumentException("Unsupported DataBuffer type: " + dataBuffer.getClass());
    }

    private void uploadResizedImage(BufferedImage image, String path,
                                    int width, int height, float quality) throws IOException {
        BufferedImage resized = resizeImage(image, width, height);
        byte[] jpegBytes = encodeAsJpeg(resized, quality);
        dicomStorageService.uploadFile(path, jpegBytes, "image/jpeg");
    }

    private void uploadOriginalAsPng(BufferedImage image, String path) throws IOException {
        byte[] pngBytes = encodeAsPng(image);
        dicomStorageService.uploadFile(path, pngBytes, "image/png");
    }

    private void uploadRawPixelData(byte[] rawPixels, String path) throws IOException {
        dicomStorageService.uploadFile(path, rawPixels, "application/octet-stream");
    }

    private BufferedImage resizeImage(BufferedImage original, int width, int height) {
        Image scaled = original.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_USHORT_GRAY);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(scaled, 0, 0, null);
        g2d.dispose();
        return resized;
    }

    private byte[] encodeAsJpeg(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        writer.setOutput(ImageIO.createImageOutputStream(baos));
        writer.write(null, new IIOImage(image, null, null), param);
        writer.dispose();
        return baos.toByteArray();
    }

    private byte[] encodeAsPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private void updateInstanceMetadata(Instance instance, String basePath,
                                        int frameCount, long rawPixelSize) {
        instance.setThumbnailPath(basePath + "/1/small.jpg");
        instance.setRenderedBasePath(basePath);
        instance.setRenderedFrameCount(frameCount);
        instance.setRawPixelSize(rawPixelSize);
        instance.setTranscodingStatus(TranscodingStatus.COMPLETED);
        instanceRepository.save(instance);
    }

    private void handlePreProcessingFailure(Instance instance, Exception e) {
        log.error("Pre-processing failed for {}: {}", instance.getSopInstanceUid(), e.getMessage());
        instance.setTranscodingStatus(TranscodingStatus.FAILED);
        instanceRepository.save(instance);
    }
}
```

---

### 4.3 Phase 3: 스케줄러 구현

**신규 파일**: `sado-minipacs/src/main/java/com/hanumoka/sado/minipacs/scheduler/PreRenderingScheduler.java`

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class PreRenderingScheduler {

    private final InstanceRepository instanceRepository;
    private final PreRenderingService preRenderingService;

    private static final int BATCH_SIZE = 5;
    private static final long SCHEDULE_DELAY_MS = 30_000;  // 30초

    /**
     * PENDING 상태 Instance 사전 처리
     * 매 30초마다 실행, 배치 크기 5개
     */
    @Scheduled(fixedDelay = SCHEDULE_DELAY_MS)
    public void processPreRenderingQueue() {
        List<Instance> pendingInstances = instanceRepository
            .findByTranscodingStatus(TranscodingStatus.PENDING, PageRequest.of(0, BATCH_SIZE));

        if (pendingInstances.isEmpty()) {
            return;
        }

        log.info("Pre-rendering scheduler: found {} pending instances", pendingInstances.size());

        for (Instance instance : pendingInstances) {
            processInstance(instance);
        }
    }

    private void processInstance(Instance instance) {
        String sopUid = instance.getSopInstanceUid();
        int frameCount = Optional.ofNullable(instance.getNumberOfFrames()).orElse(1);

        try {
            log.info("Starting pre-processing: {} ({} frames)", sopUid, frameCount);

            // 상태를 PROCESSING으로 변경
            instance.setTranscodingStatus(TranscodingStatus.PROCESSING);
            instanceRepository.save(instance);

            // 사전 처리 실행
            preRenderingService.preProcessAllFrames(instance);

            log.info("Completed pre-processing: {}", sopUid);

        } catch (Exception e) {
            log.error("Failed pre-processing: {} - {}", sopUid, e.getMessage());
            instance.setTranscodingStatus(TranscodingStatus.FAILED);
            instanceRepository.save(instance);
        }
    }
}
```

---

### 4.4 Phase 4: 업로드 프로세스 수정

**파일**: `sado-minipacs/src/main/java/com/hanumoka/sado/minipacs/domain/service/InstanceService.java`

```java
// Instance 생성 시 transcodingStatus를 PENDING으로 설정
Instance instance = Instance.builder()
    // ... 기존 필드들
    .transcodingStatus(TranscodingStatus.PENDING)  // 스케줄러가 처리
    .build();
```

---

### 4.5 Phase 5: WADO-RS API 수정

**파일**: `sado-minipacs/src/main/java/com/hanumoka/sado/minipacs/controller/DicomWebController.java`

#### 4.5.1 WADO-RS Rendered API

```java
@GetMapping("/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/frames/{frameList}/rendered")
public ResponseEntity<?> renderFrames(
        @PathVariable String studyUID,
        @PathVariable String seriesUID,
        @PathVariable String sopInstanceUID,
        @PathVariable String frameList,
        @RequestParam(required = false, defaultValue = "large") String size) {

    Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
        .orElseThrow(() -> new NotFoundException("Instance not found"));

    List<Integer> frameNumbers = parseFrameList(frameList);

    // ─────────────────────────────────────────────────────────────
    // 캐시 히트: 사전 렌더링된 이미지 반환 (< 50ms)
    // ─────────────────────────────────────────────────────────────
    if (instance.getTranscodingStatus() == TranscodingStatus.COMPLETED) {
        if (frameNumbers.size() == 1) {
            String cachedPath = getRenderedFramePath(instance, frameNumbers.get(0), size);
            return serveRenderedImage(cachedPath, size);
        }
        // 다중 프레임: multipart 응답
        return serveMultipleRenderedFrames(instance, frameNumbers, size);
    }

    // ─────────────────────────────────────────────────────────────
    // 캐시 미스: Fallback 실시간 렌더링
    // ─────────────────────────────────────────────────────────────
    return renderFramesRealtime(instance, frameNumbers, size);
}

private String getRenderedFramePath(Instance instance, int frame, String size) {
    String extension = "large".equals(size) ? "png" : "jpg";
    return instance.getRenderedBasePath() + "/" + frame + "/" + size + "." + extension;
}

private ResponseEntity<?> serveRenderedImage(String path, String size) {
    Resource resource = dicomStorageService.downloadAsResource(path);
    MediaType mediaType = "large".equals(size)
        ? MediaType.IMAGE_PNG
        : MediaType.IMAGE_JPEG;

    return ResponseEntity.ok()
        .contentType(mediaType)
        .cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
        .body(resource);
}
```

#### 4.5.2 WADO-RS BulkData API

```java
@GetMapping("/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/frames/{frameList}")
public ResponseEntity<?> retrieveFrames(
        @PathVariable String studyUID,
        @PathVariable String seriesUID,
        @PathVariable String sopInstanceUID,
        @PathVariable String frameList) {

    Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
        .orElseThrow(() -> new NotFoundException("Instance not found"));

    List<Integer> frameNumbers = parseFrameList(frameList);

    // ─────────────────────────────────────────────────────────────
    // 캐시 히트: 사전 추출된 Raw PixelData 반환 (< 50ms)
    // ─────────────────────────────────────────────────────────────
    if (instance.getTranscodingStatus() == TranscodingStatus.COMPLETED) {
        if (frameNumbers.size() == 1) {
            String cachedPath = getRawPixelPath(instance, frameNumbers.get(0));
            return serveRawPixelData(cachedPath, instance);
        }
        // 다중 프레임: multipart 응답
        return serveMultipleRawFrames(instance, frameNumbers);
    }

    // ─────────────────────────────────────────────────────────────
    // 캐시 미스: Fallback 실시간 추출
    // ─────────────────────────────────────────────────────────────
    return extractFramesRealtime(instance, frameNumbers);
}

private String getRawPixelPath(Instance instance, int frame) {
    return instance.getRenderedBasePath() + "/" + frame + "/raw.bin";
}

private ResponseEntity<?> serveRawPixelData(String path, Instance instance) {
    Resource resource = dicomStorageService.downloadAsResource(path);

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header("X-DICOM-TransferSyntax", UID.ExplicitVRLittleEndian)
        .header("X-DICOM-BitsAllocated", String.valueOf(instance.getBitsAllocated()))
        .header("X-DICOM-BitsStored", String.valueOf(instance.getBitsStored()))
        .header("X-DICOM-HighBit", String.valueOf(instance.getHighBit()))
        .header("X-DICOM-PixelRepresentation", String.valueOf(instance.getPixelRepresentation()))
        .header("X-DICOM-PhotometricInterpretation", instance.getPhotometricInterpretation())
        .header("X-DICOM-Rows", String.valueOf(instance.getImageRows()))
        .header("X-DICOM-Columns", String.valueOf(instance.getImageColumns()))
        .cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
        .body(resource);
}
```

---

### 4.6 Phase 6: Repository 쿼리 추가

**파일**: `sado-minipacs/src/main/java/com/hanumoka/sado/minipacs/repository/InstanceRepository.java`

```java
public interface InstanceRepository extends JpaRepository<Instance, Long> {

    // ═══════════════════════════════════════════════════════════════
    // 사전 처리 관련 쿼리
    // ═══════════════════════════════════════════════════════════════

    /**
     * 특정 상태의 Instance 조회 (페이징)
     */
    List<Instance> findByTranscodingStatus(TranscodingStatus status, Pageable pageable);

    /**
     * 상태별 Instance 수 집계
     */
    @Query("SELECT i.transcodingStatus, COUNT(i) FROM Instance i GROUP BY i.transcodingStatus")
    List<Object[]> countByTranscodingStatus();

    /**
     * 특정 상태의 총 프레임 수 합계
     */
    @Query("SELECT COALESCE(SUM(i.numberOfFrames), 0) FROM Instance i WHERE i.transcodingStatus = :status")
    Long sumFramesByTranscodingStatus(@Param("status") TranscodingStatus status);

    /**
     * 사전 처리 대기 중인 총 Instance 수
     */
    @Query("SELECT COUNT(i) FROM Instance i WHERE i.transcodingStatus = 'PENDING'")
    Long countPendingInstances();
}
```

---

## 5. 수정 대상 파일 요약

| 구분 | 파일 | 작업 내용 |
|------|------|----------|
| **수정** | `Instance.java` | `renderedBasePath`, `renderedFrameCount`, `rawPixelSize` 필드 추가 |
| **수정** | `InstanceService.java` | 업로드 시 `transcodingStatus = PENDING` 설정 |
| **수정** | `InstanceRepository.java` | `findByTranscodingStatus`, 통계 쿼리 추가 |
| **수정** | `DicomWebController.java` | Rendered + BulkData API 캐시 조회 로직 |
| **수정** | `DicomStorageService.java` | 렌더링/Raw 파일 업로드 메서드 추가 |
| **신규** | `PreRenderingService.java` | 사전 렌더링 + Raw 추출 핵심 로직 |
| **신규** | `PreRenderingScheduler.java` | 백그라운드 스케줄러 |

---

## 6. 저장 공간 분석

### 6.1 단일 프레임 Instance (512×512, 16-bit)

| 산출물 | 파일 크기 | 1,000개 Instance |
|--------|----------|-----------------|
| Small JPEG (256×256) | ~20KB | 20MB |
| Medium JPEG (512×512) | ~50KB | 50MB |
| Large PNG (원본) | ~200KB | 200MB |
| **Raw PixelData** | **~512KB** | **512MB** |
| **Instance당 합계** | **~782KB** | **782MB** |

### 6.2 멀티프레임 Instance (100프레임, 512×512, 16-bit)

| 산출물 | 프레임당 | 100프레임 | 100개 Instance |
|--------|---------|----------|---------------|
| Small JPEG | ~20KB | 2MB | 200MB |
| Medium JPEG | ~50KB | 5MB | 500MB |
| Large PNG | ~200KB | 20MB | 2GB |
| **Raw PixelData** | **~512KB** | **51MB** | **5.1GB** |
| **Instance당 합계** | **~782KB** | **78MB** | **7.8GB** |

### 6.3 저장 공간 증가율

```
┌─────────────────────────────────────────────────────────────────┐
│                      저장 공간 증가 예측                          │
├─────────────────────────────────────────────────────────────────┤
│  원본 DICOM 대비 추가 저장 공간: +100% ~ +200%                    │
│                                                                 │
│  예시:                                                          │
│  - 원본 DICOM 10GB → 사전 처리 후 총 20~30GB                     │
│  - 주요 증가 요인: Raw PixelData (비압축)                         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 7. 성능 개선 효과

### 7.1 응답 시간 비교

```
                    현재                       개선 후
                    ────                       ────────
WADO-RS Rendered   ████████████ 300-1100ms    █ <50ms     (85-95% ↓)
WADO-RS BulkData   ████████░░░░ 200-800ms     █ <50ms     (75-94% ↓)
Cine 100프레임      ████████████████ ~30초    ███ <5초    (83% ↓)
```

### 7.2 정량적 개선 지표

| 지표 | 현재 | 목표 | 개선율 |
|------|------|------|--------|
| WADO-RS Rendered 평균 응답 | 700ms | 30ms | **95.7%** |
| WADO-RS BulkData 평균 응답 | 500ms | 30ms | **94.0%** |
| 100프레임 Cine 프리로드 | 30초 | 3초 | **90.0%** |
| 서버 CPU 부하 (요청당) | 높음 | 낮음 | **80% ↓** |

---

## 8. 검증 계획

### 8.1 단위 테스트

```java
@Test
void preProcessAllFrames_shouldGenerateAllOutputs() {
    // Given: 멀티프레임 DICOM Instance
    Instance instance = createTestInstance(10);  // 10 frames

    // When: 사전 처리 실행
    preRenderingService.preProcessAllFrames(instance);

    // Then: 모든 산출물 생성 확인
    for (int frame = 1; frame <= 10; frame++) {
        String basePath = instance.getRenderedBasePath() + "/" + frame;
        assertTrue(storageService.exists(basePath + "/small.jpg"));
        assertTrue(storageService.exists(basePath + "/medium.jpg"));
        assertTrue(storageService.exists(basePath + "/large.png"));
        assertTrue(storageService.exists(basePath + "/raw.bin"));
    }

    // And: 상태 업데이트 확인
    assertEquals(TranscodingStatus.COMPLETED, instance.getTranscodingStatus());
    assertEquals(10, instance.getRenderedFrameCount());
}

@Test
void extractRawPixelData_shouldMatchOriginal() {
    // Raw PixelData 무결성 검증
    byte[] original = extractFromOriginalDicom(instance, frame);
    byte[] preExtracted = loadPreExtractedRaw(instance, frame);

    assertArrayEquals(original, preExtracted);
}
```

### 8.2 통합 테스트

```bash
# 1. 멀티프레임 DICOM 업로드
curl -X POST http://localhost:8080/api/instances/upload \
  -F "file=@multiframe_100.dcm"

# 2. transcodingStatus 확인 (PENDING → PROCESSING → COMPLETED)
curl http://localhost:8080/api/instances/{id} | jq '.transcodingStatus'

# 3. WADO-RS Rendered 응답 시간 측정
time curl -o /dev/null -w "%{time_total}\n" \
  "http://localhost:8080/dicomweb/studies/.../instances/.../frames/1/rendered?size=medium"

# 4. WADO-RS BulkData 응답 시간 측정
time curl -o /dev/null -w "%{time_total}\n" \
  "http://localhost:8080/dicomweb/studies/.../instances/.../frames/1"
```

### 8.3 FE Cine 재생 테스트

| 단계 | 테스트 항목 | 목표 |
|------|------------|------|
| 1 | 100프레임 DICOM 업로드 | 성공 |
| 2 | 사전 처리 완료 대기 | COMPLETED |
| 3 | `wadors:` 로더로 Cine 재생 | 프리로드 < 5초 |
| 4 | `wadors-rendered:` 로더로 Cine 재생 | 프리로드 < 5초 |
| 5 | 30fps 연속 재생 | 끊김 없음 |

---

## 9. 향후 확장 고려사항

### 9.1 단기 개선 (Phase 2)

| 항목 | 설명 | 기대 효과 |
|------|------|----------|
| **LZ4 압축** | Raw PixelData LZ4 압축 | 저장 공간 50% 절감 |
| **WebP 포맷** | JPEG 대신 WebP 사용 | 파일 크기 30% 감소 |
| **병렬 처리** | 프레임별 병렬 처리 | 처리 시간 50% 단축 |

### 9.2 중기 개선 (Phase 3)

| 항목 | 설명 | 기대 효과 |
|------|------|----------|
| **Kafka 이벤트** | 스케줄러 → 이벤트 기반 | 대량 업로드 처리 개선 |
| **선택적 처리** | Rendered만 또는 BulkData만 | 저장 공간 최적화 |
| **우선순위 큐** | 긴급 처리 지원 | 사용자 경험 향상 |

### 9.3 장기 개선 (Phase 4)

| 항목 | 설명 | 기대 효과 |
|------|------|----------|
| **CDN 캐싱** | CloudFront 엣지 캐싱 | 글로벌 지연 감소 |
| **GPU 가속** | CUDA/OpenCL 렌더링 | 처리 속도 10배 향상 |
| **AVIF 포맷** | 차세대 이미지 포맷 | 파일 크기 50% 감소 |

---

## 10. 부록

### 10.1 TranscodingStatus Enum

```java
public enum TranscodingStatus {
    NONE,        // 기존 Instance (사전 처리 대상 아님)
    PENDING,     // 업로드 완료, 스케줄러 대기 중
    PROCESSING,  // 사전 처리 진행 중
    COMPLETED,   // 사전 처리 완료
    FAILED       // 사전 처리 실패
}
```

### 10.2 HTTP 응답 헤더 (BulkData)

| 헤더 | 설명 | 예시 |
|------|------|------|
| `X-DICOM-TransferSyntax` | 전송 구문 | `1.2.840.10008.1.2.1` |
| `X-DICOM-BitsAllocated` | 할당 비트 | `16` |
| `X-DICOM-BitsStored` | 저장 비트 | `12` |
| `X-DICOM-HighBit` | 최상위 비트 | `11` |
| `X-DICOM-PixelRepresentation` | 픽셀 표현 | `0` |
| `X-DICOM-PhotometricInterpretation` | 광도 해석 | `MONOCHROME2` |
| `X-DICOM-Rows` | 행 수 | `512` |
| `X-DICOM-Columns` | 열 수 | `512` |

### 10.3 참고 자료

- [DICOMweb Standard](https://www.dicomstandard.org/using/dicomweb)
- [DCM4CHE Documentation](https://dcm4che.atlassian.net/wiki/spaces/d4che)
- [Cornerstone3D Documentation](https://www.cornerstonejs.org/)

---

**문서 끝**
