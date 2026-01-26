# WADO-RS Rendered 사전 렌더링 (Pre-rendering) 구현 가이드

> **문서 위치**: `sado_docs/be/guides/26_WADO-RS_사전_렌더링_가이드.md`
> **작성일**: 2025-01-08
> **상태**: 구현 대기

---

## 1. 개요

### 1.1 배경

현재 WADO-RS Rendered API는 **요청 시점에 실시간으로 DICOM 파일을 디코딩**하여 PNG 이미지를 생성합니다. 이 방식은 다음과 같은 문제점이 있습니다:

| 문제점 | 영향 |
|--------|------|
| **메모리 사용량** | 100MB DICOM → 270~750MB 피크 메모리 |
| **응답 지연** | 500ms ~ 3초 (파일 크기에 따라) |
| **CPU 부하** | 동일 프레임 반복 요청 시 중복 디코딩 |
| **확장성 제한** | 동시 요청 증가 시 서버 리소스 고갈 |

### 1.2 목표

**사전 렌더링(Pre-rendering)** 방식을 도입하여:

- 조회 응답 시간: **500ms~3초 → 20~50ms** (90% 개선)
- 서버 CPU 부하: **실시간 디코딩 제거**
- 사용자 경험: **일관된 빠른 응답**

### 1.3 핵심 전략

```
┌─────────────────────────────────────────────────────────────────┐
│                     Fallback 전략 (권장)                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [사전 렌더링 완료]                                             │
│  요청 → S3에서 PNG 직접 반환 (20~50ms)                         │
│                                                                 │
│  [사전 렌더링 미완료 / 진행 중]                                 │
│  요청 → Fallback: 실시간 렌더링 (500ms~3s)                     │
│                                                                 │
│  ※ 사용자는 항상 이미지를 받음 (실패 없음)                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. 아키텍처

### 2.1 전체 흐름

```
                    ┌─────────────────┐
                    │   DICOM Upload  │
                    │   (STOW-RS)     │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │ InstanceService │
                    │ - 메타데이터 저장│
                    │ - 렌더링 Job 등록│
                    └────────┬────────┘
                             │
              ┌──────────────┴──────────────┐
              │                             │
              ▼                             ▼
        ┌───────────┐              ┌─────────────────┐
        │ Instance  │              │ RenderingJob    │
        │   (DB)    │              │ Queue (DB)      │
        │           │              │ status=QUEUED   │
        └───────────┘              └────────┬────────┘
                                            │
                                            ▼
                                   ┌─────────────────┐
                                   │ Background      │
                                   │ Rendering       │
                                   │ Worker          │
                                   │ (@Scheduled)    │
                                   └────────┬────────┘
                                            │
                    ┌───────────────────────┼───────────────────────┐
                    │                       │                       │
                    ▼                       ▼                       ▼
              ┌───────────┐          ┌───────────┐          ┌───────────┐
              │ Frame 1   │          │ Frame 2   │          │ Frame N   │
              │   PNG     │          │   PNG     │          │   PNG     │
              └─────┬─────┘          └─────┬─────┘          └─────┬─────┘
                    │                      │                      │
                    └──────────────────────┼──────────────────────┘
                                           │
                                           ▼
                                  ┌─────────────────┐
                                  │   SeaweedFS     │
                                  │     (S3)        │
                                  │                 │
                                  │ rendered/       │
                                  │  {sopUid}/      │
                                  │   frame_0001.png│
                                  │   thumbnail.jpg │
                                  └─────────────────┘
```

### 2.2 컴포넌트 역할

| 컴포넌트 | 역할 |
|----------|------|
| **InstanceService** | DICOM 업로드 처리, 렌더링 Job 등록 |
| **RenderingJob** | 렌더링 작업 큐 (DB 테이블) |
| **RenderingWorker** | 백그라운드 렌더링 실행 (@Scheduled) |
| **DicomRenderingService** | DCM4CHE 기반 프레임 추출 |
| **DicomStorageService** | S3 업로드/다운로드 |
| **DicomWebController** | API 엔드포인트 (Fallback 로직 포함) |

---

## 3. 데이터 모델

### 3.1 Instance 엔티티 확장

```java
@Entity
@Table(name = "instance")
public class Instance extends TenantAwareEntity {

    // ========== 기존 필드 (변경 없음) ==========

    @Column(name = "sop_instance_uid", nullable = false, unique = true)
    private String sopInstanceUid;

    @Column(name = "storage_path", length = 512)
    private String storagePath;  // 원본 DICOM 경로

    @Column(name = "number_of_frames")
    private Integer numberOfFrames;  // 총 프레임 수

    // ========== 기존 필드 (활용) ==========

    @Enumerated(EnumType.STRING)
    @Column(name = "transcoding_status")
    private TranscodingStatus transcodingStatus = TranscodingStatus.NONE;

    @Column(name = "thumbnail_path", length = 512)
    private String thumbnailPath;  // 썸네일 S3 경로

    // ========== 새로 추가할 필드 ==========

    /**
     * 렌더링된 프레임 저장 폴더 경로
     * 예: "rendered/1.2.840.113619.2.55.1.123456/"
     */
    @Column(name = "rendered_frames_path", length = 512)
    private String renderedFramesPath;

    /**
     * 렌더링 완료된 프레임 수 (진행률 표시용)
     */
    @Column(name = "rendered_frame_count")
    private Integer renderedFrameCount = 0;

    /**
     * 렌더링 시작 시각
     */
    @Column(name = "rendering_started_at")
    private LocalDateTime renderingStartedAt;

    /**
     * 렌더링 완료 시각
     */
    @Column(name = "rendering_completed_at")
    private LocalDateTime renderingCompletedAt;

    /**
     * 렌더링 실패 시 에러 메시지
     */
    @Column(name = "rendering_error_message", length = 1000)
    private String renderingErrorMessage;

    /**
     * 렌더링 재시도 횟수
     */
    @Column(name = "rendering_retry_count")
    private Integer renderingRetryCount = 0;
}
```

### 3.2 TranscodingStatus 열거형 (기존)

```java
public enum TranscodingStatus {
    NONE,       // 사전 렌더링 사용 안 함 (레거시)
    PENDING,    // 렌더링 대기 중
    PROCESSING, // 렌더링 진행 중
    COMPLETED,  // 렌더링 완료
    FAILED      // 렌더링 실패
}
```

### 3.3 RenderingJob 엔티티 (신규)

```java
/**
 * 렌더링 작업 큐 테이블
 *
 * Instance.transcodingStatus로도 관리 가능하지만,
 * 별도 테이블로 분리하면:
 * - 작업 우선순위 관리
 * - 실패 재시도 이력
 * - 배치 처리 최적화
 */
@Entity
@Table(name = "rendering_job", indexes = {
    @Index(name = "idx_rendering_job_status", columnList = "status, priority DESC, created_at"),
    @Index(name = "idx_rendering_job_instance", columnList = "instance_id")
})
public class RenderingJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_id", nullable = false)
    private Instance instance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status = JobStatus.QUEUED;

    /**
     * 우선순위 (높을수록 먼저 처리)
     * - 100: 긴급 (사용자가 현재 보고 있는 경우)
     * - 50: 높음 (최근 업로드)
     * - 10: 보통 (일반 업로드)
     * - 1: 낮음 (배치 업로드)
     */
    @Column(name = "priority")
    private Integer priority = 10;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    private Integer maxRetries = 3;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * 다음 실행 가능 시각 (재시도 백오프용)
     */
    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    public enum JobStatus {
        QUEUED,     // 대기 중
        PROCESSING, // 처리 중
        COMPLETED,  // 완료
        FAILED,     // 실패 (재시도 소진)
        CANCELLED   // 취소됨
    }
}
```

### 3.4 데이터베이스 마이그레이션

```sql
-- Instance 테이블 컬럼 추가
ALTER TABLE instance ADD COLUMN rendered_frames_path VARCHAR(512);
ALTER TABLE instance ADD COLUMN rendered_frame_count INT DEFAULT 0;
ALTER TABLE instance ADD COLUMN rendering_started_at DATETIME;
ALTER TABLE instance ADD COLUMN rendering_completed_at DATETIME;
ALTER TABLE instance ADD COLUMN rendering_error_message VARCHAR(1000);
ALTER TABLE instance ADD COLUMN rendering_retry_count INT DEFAULT 0;

-- RenderingJob 테이블 생성
CREATE TABLE rendering_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) NOT NULL,
    instance_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    priority INT DEFAULT 10,
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    error_message VARCHAR(1000),
    started_at DATETIME,
    completed_at DATETIME,
    next_attempt_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,

    CONSTRAINT fk_rendering_job_instance
        FOREIGN KEY (instance_id) REFERENCES instance(id),
    INDEX idx_rendering_job_status (status, priority DESC, created_at),
    INDEX idx_rendering_job_instance (instance_id)
);
```

---

## 4. S3 저장소 구조

### 4.1 디렉토리 구조

```
seaweedfs-bucket/
│
├── studies/                                    # 원본 DICOM 저장
│   └── {studyInstanceUid}/
│       └── series/
│           └── {seriesInstanceUid}/
│               └── instances/
│                   └── {sopInstanceUid}.dcm    # 원본 DICOM 파일
│
└── rendered/                                   # 렌더링 결과 저장
    └── {sopInstanceUid}/
        ├── metadata.json                       # 렌더링 메타데이터
        ├── thumbnail.jpg                       # 썸네일 (256x256)
        ├── frame_0001.png                      # 프레임 1 (원본 해상도)
        ├── frame_0002.png                      # 프레임 2
        ├── frame_0003.png                      # ...
        └── frame_0300.png                      # 프레임 N
```

### 4.2 메타데이터 파일 (metadata.json)

```json
{
  "sopInstanceUid": "1.2.840.113619.2.55.1.123456789",
  "studyInstanceUid": "1.2.840.113619.2.55.1.111",
  "seriesInstanceUid": "1.2.840.113619.2.55.1.222",
  "totalFrames": 300,
  "renderedFrames": 300,
  "imageWidth": 512,
  "imageHeight": 512,
  "bitsAllocated": 16,
  "transferSyntax": "1.2.840.10008.1.2.4.70",
  "transferSyntaxName": "JPEG Lossless SV1",
  "format": "PNG",
  "thumbnailFormat": "JPEG",
  "thumbnailSize": "256x256",
  "renderingTimeMs": 45000,
  "totalSizeBytes": 156000000,
  "averageFrameSizeBytes": 520000,
  "createdAt": "2025-01-08T10:30:00Z",
  "completedAt": "2025-01-08T10:30:45Z"
}
```

### 4.3 저장 공간 계산

| 항목 | 크기 |
|------|------|
| 원본 DICOM (압축) | 100MB |
| 프레임당 PNG (512x512, 16bit) | ~500KB |
| 300 프레임 PNG | 150MB |
| 썸네일 JPEG | ~20KB |
| **총 저장량** | ~250MB (원본의 2.5배) |

**대규모 환경 계산**:
- 10,000 인스턴스 (원본만): 1TB
- 10,000 인스턴스 (렌더링 포함): 2.5TB

---

## 5. 백그라운드 워커 구현

### 5.1 설계 원칙

**Spring @Scheduled 선택 이유** ([참고: Spring Boot Scheduling Best Practices](https://dev.to/dixitgurv/spring-boot-scheduling-best-practices-503h)):

| 방식 | 장점 | 단점 | 적합한 상황 |
|------|------|------|-------------|
| **@Scheduled** | 단순, 설정 쉬움 | 분산 불가 | POC, 단일 인스턴스 |
| **@Async** | 이벤트 기반 | 트랜잭션 복잡 | 사용자 트리거 작업 |
| **Message Queue** | 분산, 내구성 | 인프라 복잡 | 프로덕션, MSA |

**현재 프로젝트**: 학습/POC 목적 → **@Scheduled 선택**

### 5.2 RenderingWorker 구현

```java
/**
 * 프레임 사전 렌더링 백그라운드 워커
 *
 * 주기적으로 렌더링 큐를 폴링하여 작업 처리
 *
 * 참고:
 * - https://medium.com/@rakesh.mali/avoid-using-scheduled-for-background-tasks-in-spring-boot
 * - Scheduler-Worker 패턴 적용
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FrameRenderingWorker {

    private final RenderingJobRepository renderingJobRepository;
    private final InstanceRepository instanceRepository;
    private final DicomRenderingService dicomRenderingService;
    private final DicomStorageService dicomStorageService;

    /**
     * 동시 렌더링 작업 수 제한 (메모리 보호)
     * 100MB DICOM × 3 = 최대 ~2GB 피크 메모리
     */
    private static final int MAX_CONCURRENT_RENDERING = 3;

    private final Semaphore renderingSemaphore = new Semaphore(MAX_CONCURRENT_RENDERING);

    /**
     * 5초마다 렌더링 큐 폴링
     */
    @Scheduled(fixedDelay = 5000)
    public void processRenderingQueue() {
        if (!renderingSemaphore.tryAcquire()) {
            log.debug("Rendering slots full, skipping this cycle");
            return;
        }

        try {
            // 1. QUEUED 상태 Job 조회 (우선순위 순)
            List<RenderingJob> jobs = renderingJobRepository
                .findByStatusAndNextAttemptAtBeforeOrderByPriorityDescCreatedAtAsc(
                    JobStatus.QUEUED,
                    LocalDateTime.now(),
                    PageRequest.of(0, 1)  // 한 번에 1개씩
                );

            if (jobs.isEmpty()) {
                return;
            }

            RenderingJob job = jobs.get(0);
            processJob(job);

        } finally {
            renderingSemaphore.release();
        }
    }

    @Transactional
    protected void processJob(RenderingJob job) {
        Instance instance = job.getInstance();

        try {
            // 1. 상태 변경: QUEUED → PROCESSING
            job.setStatus(JobStatus.PROCESSING);
            job.setStartedAt(LocalDateTime.now());
            renderingJobRepository.save(job);

            instance.setTranscodingStatus(TranscodingStatus.PROCESSING);
            instance.setRenderingStartedAt(LocalDateTime.now());
            instanceRepository.save(instance);

            // 2. 렌더링 수행
            renderAllFrames(instance);

            // 3. 완료 처리
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            renderingJobRepository.save(job);

            instance.setTranscodingStatus(TranscodingStatus.COMPLETED);
            instance.setRenderingCompletedAt(LocalDateTime.now());
            instanceRepository.save(instance);

            log.info("Rendering completed: sopInstanceUid={}, frames={}",
                instance.getSopInstanceUid(), instance.getNumberOfFrames());

        } catch (Exception e) {
            handleRenderingFailure(job, instance, e);
        }
    }

    private void renderAllFrames(Instance instance) {
        int totalFrames = instance.getNumberOfFrames() != null
            ? instance.getNumberOfFrames() : 1;

        String basePath = "rendered/" + instance.getSopInstanceUid() + "/";
        instance.setRenderedFramesPath(basePath);

        // 1. 모든 프레임 렌더링
        for (int frame = 1; frame <= totalFrames; frame++) {
            byte[] pngBytes = dicomRenderingService.renderToPng(
                instance.getStoragePath(),
                frame
            );

            String frameKey = basePath + String.format("frame_%04d.png", frame);
            dicomStorageService.uploadRenderedFrame(frameKey, pngBytes, "image/png");

            // 진행률 업데이트
            instance.setRenderedFrameCount(frame);
            instanceRepository.save(instance);

            log.debug("Rendered frame {}/{}: {}", frame, totalFrames, frameKey);
        }

        // 2. 썸네일 생성 (첫 프레임 256x256 리사이즈)
        byte[] thumbnail = dicomRenderingService.renderToThumbnail(
            instance.getStoragePath(),
            1,
            256,
            256
        );

        String thumbnailKey = basePath + "thumbnail.jpg";
        dicomStorageService.uploadRenderedFrame(thumbnailKey, thumbnail, "image/jpeg");
        instance.setThumbnailPath(thumbnailKey);

        // 3. 메타데이터 JSON 저장
        String metadataJson = createMetadataJson(instance, totalFrames);
        String metadataKey = basePath + "metadata.json";
        dicomStorageService.uploadRenderedFrame(
            metadataKey,
            metadataJson.getBytes(StandardCharsets.UTF_8),
            "application/json"
        );
    }

    private void handleRenderingFailure(RenderingJob job, Instance instance, Exception e) {
        log.error("Rendering failed: sopInstanceUid={}",
            instance.getSopInstanceUid(), e);

        job.setRetryCount(job.getRetryCount() + 1);
        job.setErrorMessage(e.getMessage());

        if (job.getRetryCount() >= job.getMaxRetries()) {
            // 최대 재시도 초과 → 실패 처리
            job.setStatus(JobStatus.FAILED);
            instance.setTranscodingStatus(TranscodingStatus.FAILED);
            instance.setRenderingErrorMessage(e.getMessage());
        } else {
            // 재시도 대기 (지수 백오프)
            long delaySeconds = (long) Math.pow(2, job.getRetryCount()) * 30;
            job.setStatus(JobStatus.QUEUED);
            job.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));

            log.info("Scheduling retry {} for job {}, delay={}s",
                job.getRetryCount(), job.getId(), delaySeconds);
        }

        renderingJobRepository.save(job);
        instanceRepository.save(instance);
    }

    private String createMetadataJson(Instance instance, int totalFrames) {
        // ObjectMapper 사용하여 JSON 생성
        // ... 구현 생략
    }
}
```

### 5.3 메모리 최적화

**DCM4CHE 메모리 관리** ([참고: DCM4CHE Compressor](https://bradleyross.github.io/dcm4che/apidocs/org/dcm4che3/imageio/codec/Compressor.html)):

```java
/**
 * 메모리 효율적인 프레임 렌더링
 *
 * DCM4CHE의 getEstimatedNeededMemory()를 활용하여
 * 메모리 사용량 예측 및 동시 작업 수 조절
 */
@Service
@Slf4j
public class DicomRenderingService {

    /**
     * 대용량 파일 처리 시 스트리밍 방식 사용
     *
     * DCM4CHE emf2sf 도구 참고:
     * https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-emf2sf/README.md
     */
    public byte[] renderToPngOptimized(String storagePath, int frameNumber) {
        try (InputStream dicomStream = dicomStorageService.downloadDicomFile(storagePath)) {

            // 스트리밍 방식으로 ImageInputStream 생성
            // readAllBytes() 대신 직접 스트림 사용
            try (ImageInputStream iis = ImageIO.createImageInputStream(dicomStream)) {

                Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("DICOM");
                if (!readers.hasNext()) {
                    throw new BusinessException(DICOM_RENDER_FAILED, "DICOM ImageReader not found");
                }

                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis);

                    DicomImageReadParam param = (DicomImageReadParam) reader.getDefaultReadParam();

                    // 프레임 인덱스 (0-based)
                    int frameIndex = frameNumber - 1;
                    BufferedImage image = reader.read(frameIndex, param);

                    // PNG 인코딩
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "PNG", baos);

                    return baos.toByteArray();

                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException e) {
            throw new BusinessException(DICOM_RENDER_FAILED, e.getMessage());
        }
    }
}
```

---

## 6. API 변경

### 6.1 렌더링된 프레임 조회 API

```java
/**
 * WADO-RS Rendered 엔드포인트 (Fallback 전략 적용)
 */
@GetMapping("/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/frames/{frameNumber}/rendered")
@Operation(summary = "WADO-RS: Frame Rendered with Pre-rendering")
public ResponseEntity<?> renderFrame(
    @PathVariable String studyUID,
    @PathVariable String seriesUID,
    @PathVariable String sopInstanceUID,
    @PathVariable int frameNumber
) {
    Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
        .orElseThrow(() -> new BusinessException(INSTANCE_NOT_FOUND));

    // UID 계층 검증
    validateInstanceUIDs(instance, studyUID, seriesUID);

    // 프레임 번호 검증
    int totalFrames = instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1;
    if (frameNumber < 1 || frameNumber > totalFrames) {
        throw new BusinessException(INVALID_FRAME_NUMBER,
            "Frame " + frameNumber + " out of range (1-" + totalFrames + ")");
    }

    // ========== 사전 렌더링 결과 확인 ==========

    TranscodingStatus status = instance.getTranscodingStatus();

    // Case 1: 사전 렌더링 완료 → S3에서 직접 반환
    if (status == TranscodingStatus.COMPLETED) {
        return serveFromS3(instance, frameNumber);
    }

    // Case 2: 렌더링 진행 중 → 해당 프레임 완료 여부 확인
    if (status == TranscodingStatus.PROCESSING) {
        Integer renderedCount = instance.getRenderedFrameCount();
        if (renderedCount != null && frameNumber <= renderedCount) {
            // 이미 렌더링된 프레임 → S3에서 반환
            return serveFromS3(instance, frameNumber);
        }
    }

    // Case 3: PENDING, FAILED, NONE, 또는 아직 렌더링 안 된 프레임
    // → Fallback: 실시간 렌더링
    log.info("Fallback rendering: sopInstanceUid={}, frame={}, status={}",
        sopInstanceUID, frameNumber, status);

    byte[] pngBytes = dicomRenderingService.renderToPng(
        instance.getStoragePath(),
        frameNumber
    );

    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
        .body(pngBytes);
}

/**
 * S3에서 렌더링된 프레임 직접 반환
 */
private ResponseEntity<?> serveFromS3(Instance instance, int frameNumber) {
    String frameKey = instance.getRenderedFramesPath()
        + String.format("frame_%04d.png", frameNumber);

    // 옵션 1: Presigned URL 리다이렉트 (권장 - 백엔드 부하 감소)
    String presignedUrl = dicomStorageService.getPresignedUrl(frameKey, Duration.ofMinutes(5));
    return ResponseEntity.status(HttpStatus.FOUND)
        .header(HttpHeaders.LOCATION, presignedUrl)
        .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
        .build();

    // 옵션 2: 백엔드 프록시 (CORS 이슈 시)
    // Resource resource = dicomStorageService.downloadAsResource(frameKey);
    // return ResponseEntity.ok()
    //     .contentType(MediaType.IMAGE_PNG)
    //     .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
    //     .body(resource);
}
```

### 6.2 썸네일 API

```java
@GetMapping("/studies/{studyUID}/series/{seriesUID}/instances/{sopInstanceUID}/thumbnail")
@Operation(summary = "WADO-RS: Thumbnail (Pre-rendered)")
public ResponseEntity<?> getThumbnail(
    @PathVariable String studyUID,
    @PathVariable String seriesUID,
    @PathVariable String sopInstanceUID
) {
    Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
        .orElseThrow(() -> new BusinessException(INSTANCE_NOT_FOUND));

    // 사전 렌더링된 썸네일 존재 확인
    if (instance.getThumbnailPath() != null) {
        String presignedUrl = dicomStorageService.getPresignedUrl(
            instance.getThumbnailPath(),
            Duration.ofMinutes(30)
        );
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, presignedUrl)
            .build();
    }

    // Fallback: 실시간 썸네일 생성
    byte[] thumbnail = dicomRenderingService.renderToThumbnail(
        instance.getStoragePath(), 1, 256, 256
    );

    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_JPEG)
        .body(thumbnail);
}
```

### 6.3 렌더링 상태 조회 API (신규)

```java
@GetMapping("/instances/{sopInstanceUID}/rendering-status")
@Operation(summary = "렌더링 상태 조회")
public ApiResponse<RenderingStatusResponse> getRenderingStatus(
    @PathVariable String sopInstanceUID
) {
    Instance instance = instanceService.findBySopInstanceUid(sopInstanceUID)
        .orElseThrow(() -> new BusinessException(INSTANCE_NOT_FOUND));

    RenderingStatusResponse response = RenderingStatusResponse.builder()
        .sopInstanceUid(sopInstanceUID)
        .status(instance.getTranscodingStatus())
        .totalFrames(instance.getNumberOfFrames())
        .renderedFrames(instance.getRenderedFrameCount())
        .progressPercent(calculateProgress(instance))
        .startedAt(instance.getRenderingStartedAt())
        .completedAt(instance.getRenderingCompletedAt())
        .errorMessage(instance.getRenderingErrorMessage())
        .build();

    return ApiResponse.success(response);
}
```

---

## 7. 업로드 시 렌더링 Job 등록

### 7.1 InstanceService 수정

```java
@Service
@RequiredArgsConstructor
public class InstanceService {

    private final RenderingJobRepository renderingJobRepository;

    /**
     * DICOM 업로드 완료 후 렌더링 Job 등록
     */
    @Transactional
    public Instance uploadDicomFile(MultipartFile file) {
        // 1. 기존 업로드 로직
        Instance instance = saveInstance(file);

        // 2. 사전 렌더링 Job 등록
        schedulePreRendering(instance);

        return instance;
    }

    private void schedulePreRendering(Instance instance) {
        // 멀티프레임 또는 모든 인스턴스에 대해 렌더링 스케줄
        instance.setTranscodingStatus(TranscodingStatus.PENDING);
        instanceRepository.save(instance);

        RenderingJob job = new RenderingJob();
        job.setInstance(instance);
        job.setStatus(JobStatus.QUEUED);
        job.setPriority(calculatePriority(instance));
        job.setNextAttemptAt(LocalDateTime.now());

        renderingJobRepository.save(job);

        log.info("Scheduled pre-rendering: sopInstanceUid={}, frames={}",
            instance.getSopInstanceUid(), instance.getNumberOfFrames());
    }

    private int calculatePriority(Instance instance) {
        // 프레임 수가 적을수록 빨리 처리 (빠른 완료 가능)
        int frames = instance.getNumberOfFrames() != null ? instance.getNumberOfFrames() : 1;
        if (frames <= 10) return 50;      // 높음
        if (frames <= 100) return 30;     // 중간
        return 10;                         // 보통
    }
}
```

---

## 8. 이미지 포맷 선택

### 8.1 PNG vs JPEG 비교

**의료 영상에서의 포맷 선택** ([참고: DICOM file format quality study](https://pmc.ncbi.nlm.nih.gov/articles/PMC10642272/)):

| 포맷 | 압축 | 품질 | 파일 크기 | 권장 용도 |
|------|------|------|----------|----------|
| **PNG** | 무손실 | 원본 유지 | 큼 | 진단용 (권장) |
| **JPEG** | 손실 | 약간 열화 | 작음 | 썸네일, 미리보기 |
| **WebP** | 무손실/손실 | 우수 | 매우 작음 | 웹 최적화 (의료 미지원) |

**DICOM 표준 권장**: 진단 목적 이미지는 **무손실(Lossless)** 포맷 사용

### 8.2 구현 선택

```java
/**
 * 용도별 포맷 분리
 */
public class DicomRenderingService {

    // 뷰어용 프레임 → PNG (무손실)
    public byte[] renderToPng(String storagePath, int frameNumber) {
        BufferedImage image = extractFrame(storagePath, frameNumber);
        return encodeToPng(image);
    }

    // 썸네일용 → JPEG (손실, 작은 크기)
    public byte[] renderToThumbnail(String storagePath, int frameNumber,
                                     int width, int height) {
        BufferedImage image = extractFrame(storagePath, frameNumber);
        BufferedImage resized = resize(image, width, height);
        return encodeToJpeg(resized, 0.85f);  // 85% 품질
    }
}
```

---

## 9. 구현 단계

### Phase 1: 데이터 모델 (1-2일)

1. **Instance 엔티티 필드 추가**
   - `renderedFramesPath`, `renderedFrameCount`
   - `renderingStartedAt`, `renderingCompletedAt`
   - `renderingErrorMessage`, `renderingRetryCount`

2. **RenderingJob 엔티티 생성**
   - 큐 테이블 설계
   - Repository 생성

3. **Flyway 마이그레이션**
   - DDL 스크립트 작성

### Phase 2: 백그라운드 워커 (2-3일)

1. **FrameRenderingWorker 구현**
   - @Scheduled 폴링
   - Semaphore 기반 동시성 제어
   - 재시도 로직 (지수 백오프)

2. **DicomRenderingService 확장**
   - renderToThumbnail() 메서드
   - 메타데이터 JSON 생성

3. **DicomStorageService 확장**
   - uploadRenderedFrame() 메서드

### Phase 3: API 수정 (1-2일)

1. **DicomWebController 수정**
   - Fallback 로직 적용
   - serveFromS3() 구현
   - 썸네일 API 추가

2. **렌더링 상태 API**
   - 상태 조회 엔드포인트

### Phase 4: 업로드 연동 (1일)

1. **InstanceService 수정**
   - 업로드 후 Job 등록
   - 우선순위 계산

### Phase 5: 테스트 및 검증 (1-2일)

1. **단위 테스트**
   - 각 서비스 테스트

2. **통합 테스트**
   - 업로드 → 렌더링 → 조회 전체 흐름

3. **부하 테스트**
   - 동시 렌더링 메모리 확인
   - Fallback 시나리오 검증

---

## 10. 검증 체크리스트

### 10.1 기능 테스트

- [ ] DICOM 업로드 시 RenderingJob 생성 확인
- [ ] 백그라운드 워커가 Job을 가져와 처리하는지 확인
- [ ] 렌더링 완료 후 Instance.transcodingStatus = COMPLETED 확인
- [ ] S3에 PNG 파일 저장 확인
- [ ] 조회 API가 S3에서 직접 반환하는지 확인
- [ ] 렌더링 미완료 시 Fallback 동작 확인
- [ ] 렌더링 실패 시 재시도 동작 확인
- [ ] 썸네일 생성 및 조회 확인

### 10.2 성능 테스트

- [ ] 렌더링 완료된 프레임 응답 시간: < 100ms
- [ ] Fallback 응답 시간: < 3초
- [ ] 동시 렌더링 3개 시 메모리 사용량: < 4GB
- [ ] 300프레임 렌더링 완료 시간: < 2분

### 10.3 안정성 테스트

- [ ] 서버 재시작 후 PENDING Job 재처리 확인
- [ ] 렌더링 중 Instance 삭제 시 처리
- [ ] S3 업로드 실패 시 재시도 동작
- [ ] 동시 조회 + 렌더링 시 데이터 정합성

---

## 11. 참고 자료

### 11.1 DICOM 표준
- [DICOM Standard](https://www.dicomstandard.org/)
- [DICOMweb PS3.18](https://dicom.nema.org/medical/dicom/current/output/html/part18.html)

### 11.2 DCM4CHE
- [DCM4CHE GitHub](https://github.com/dcm4che/dcm4che)
- [MultiframeExtractor](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-emf/src/main/java/org/dcm4che3/emf/MultiframeExtractor.java)
- [emf2sf Tool](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-emf2sf/README.md)

### 11.3 Spring Boot
- [Task Execution and Scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)
- [Spring Scheduling Best Practices](https://dev.to/dixitgurv/spring-boot-scheduling-best-practices-503h)
- [Background Tasks with Spring Boot](https://www.geeksforgeeks.org/advance-java/spring-boot-handling-background-tasks-with-spring-boot/)

### 11.4 S3 최적화
- [AWS S3 Performance Guidelines](https://docs.aws.amazon.com/AmazonS3/latest/userguide/optimizing-performance-guidelines.html)
- [Multipart Upload Best Practices](https://repost.aws/knowledge-center/s3-upload-large-files)

### 11.5 의료 영상 품질
- [DICOM Image Quality Study](https://pmc.ncbi.nlm.nih.gov/articles/PMC10642272/)
- [Managing DICOM Images](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3354356/)
