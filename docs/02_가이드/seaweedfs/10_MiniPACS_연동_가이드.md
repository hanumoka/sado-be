# 10. MiniPACS 연동 가이드 (SADO 프로젝트)

> **학습 목표**: SADO MiniPACS 프로젝트에서 SeaweedFS를 실전 적용합니다.

---

## SADO 프로젝트 개요

### 프로젝트 정보

- **프로젝트명**: SADO (Smart AI-driven DICOM Orchestration)
- **목적**: MiniPACS 시스템 구축 (16주 학습 프로젝트)
- **현재 Week**: Week 4 (Domain Layer 완성)
- **다음 Week**: Week 4-6 (Storage Layer + SeaweedFS 연동)

### 아키텍처

```
┌────────────────────────────────────────┐
│  Frontend (React 19 + Cornerstone3D)  │
│  http://localhost:10300                │
└──────────────┬─────────────────────────┘
               │ REST API
               ↓
┌────────────────────────────────────────┐
│  Gateway (Spring Boot)                 │
│  http://localhost:10200                │
└──────────────┬─────────────────────────┘
               │
               ↓
┌────────────────────────────────────────┐
│  MiniPACS (Spring Boot)                │
│  http://localhost:10201                │
│                                         │
│  ┌──────────────────────────────────┐  │
│  │ Controller → Service → Repo      │  │
│  └──────┬────────────────┬──────────┘  │
│         │                │              │
└─────────┼────────────────┼──────────────┘
          │                │
          ↓                ↓
    ┌──────────┐    ┌──────────────┐
    │  MySQL   │    │  SeaweedFS   │
    │  (Meta)  │    │  (Files)     │
    └──────────┘    └──────────────┘
```

---

## DICOM 파일 저장 전략

### Entity 설계

**Instance 엔티티**:

```java
@Entity
@Table(name = "instance")
@EntityListeners(TenantEntityListener.class)
public class Instance extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // DICOM 메타데이터
    @Column(nullable = false, unique = true)
    private String sopInstanceUid;  // 1.2.840.10008.5.1.4.1.1.6.1

    @Column(nullable = false)
    private String sopClassUid;

    private Integer imageRows;
    private Integer imageColumns;
    private Integer numberOfFrames;
    private Double frameRate;

    // SeaweedFS 연동 ⭐
    @Column(nullable = false)
    private String storagePath;  // FID (예: "3,01637037d6")

    private String thumbnailPath;  // 썸네일 FID
    private String videoPath;      // 트랜스코딩 비디오 FID

    // Storage Tiering
    @Enumerated(EnumType.STRING)
    private StorageTier storageTier = StorageTier.HOT;

    @Enumerated(EnumType.STRING)
    private TranscodingStatus transcodingStatus = TranscodingStatus.NONE;

    // 관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    private Series series;
}
```

### 파일 경로 설계

**Option 1**: FID만 저장 (HTTP API 전용)

```java
Instance {
    storagePath = "3,01637037d6"  // SeaweedFS FID
}
```

**Option 2**: S3 경로 저장 (추천 - SADO 프로젝트) ⭐

```java
Instance {
    storagePath = "studies/{studyUID}/series/{seriesUID}/{sopInstanceUID}.dcm"
    thumbnailPath = "studies/{studyUID}/series/{seriesUID}/thumbnails/{sopInstanceUID}.jpg"
    videoPath = "studies/{studyUID}/series/{seriesUID}/videos/{sopInstanceUID}.mp4"
}
```

**경로 예시**:
```
studies/1.2.840.113619.2.55.3.604688119.868.1/
  └── series/1.2.840.113619.2.55.3.604688119.868.1.1/
      ├── 1.2.840.113619.2.55.3.604688119.868.1.1.1.dcm
      ├── 1.2.840.113619.2.55.3.604688119.868.1.1.2.dcm
      ├── thumbnails/
      │   ├── 1.2.840.113619.2.55.3.604688119.868.1.1.1.jpg
      │   └── 1.2.840.113619.2.55.3.604688119.868.1.1.2.jpg
      └── videos/
          └── 1.2.840.113619.2.55.3.604688119.868.1.1.1.mp4
```

**SADO 선택**: **Option 2** (S3 경로 저장) - Filer 필수
- ✅ DICOMweb 표준 준수 (path-based URL)
- ✅ OHIF Viewer S3 API 호환성
- ✅ AI 서비스 (sado_ai) 표준 S3 SDK 사용
- ✅ 썸네일/트랜스코딩 계층적 관리
- ✅ Web UI로 파일 브라우징 가능

---

## 구현 단계

### 1단계: Docker Compose 통합

**위치**: `C:\Users\amagr\project\sado\sado_be\docker-compose.yml`

**추가**:

```yaml
services:
  # 기존 mysql, kafka 아래에 추가

  seaweedfs-master:
    image: chrislusf/seaweedfs:latest
    container_name: sado-seaweedfs-master
    command: 'master -ip=seaweedfs-master -port=9333 -defaultReplication=000'
    ports:
      - "10400:9333"
      - "10401:19333"
    volumes:
      - seaweedfs-master-data:/data
    networks:
      - sado-network
    healthcheck:
      test: ["CMD", "wget", "-q", "-O-", "http://localhost:9333/cluster/healthz"]
      interval: 10s
      timeout: 5s
      retries: 3

  seaweedfs-volume:
    image: chrislusf/seaweedfs:latest
    container_name: sado-seaweedfs-volume
    command: 'volume -mserver=seaweedfs-master:9333 -port=8080 -ip=seaweedfs-volume -max=100'
    ports:
      - "10402:8080"
    volumes:
      - seaweedfs-volume-data:/data
    networks:
      - sado-network
    depends_on:
      seaweedfs-master:
        condition: service_healthy

  seaweedfs-filer:
    image: chrislusf/seaweedfs:latest
    container_name: sado-seaweedfs-filer
    command: 'filer -master=seaweedfs-master:9333 -ip=seaweedfs-filer -s3 -s3.port=8333'
    ports:
      - "10403:8888"     # HTTP API
      - "10404:18888"    # gRPC
      - "10405:8333"     # S3 API ⭐
    volumes:
      - seaweedfs-filer-data:/data
    networks:
      - sado-network
    depends_on:
      seaweedfs-master:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-q", "-O-", "http://localhost:8888/"]
      interval: 10s
      timeout: 5s
      retries: 3

volumes:
  # 기존 mysql-data 아래에 추가
  seaweedfs-master-data:
  seaweedfs-volume-data:
  seaweedfs-filer-data:
```

### 2단계: Dependencies 추가

**위치**: `C:\Users\amagr\project\sado\sado_be\sado-minipacs\build.gradle`

```gradle
dependencies {
    // 기존 dependencies...

    // AWS SDK for S3 (SeaweedFS S3 API 연동)
    implementation 'software.amazon.awssdk:s3:2.20.26'
    implementation 'software.amazon.awssdk:s3-presigner:2.20.26'
}
```

### 3단계: application.yml 설정

**위치**: `C:\Users\amagr\project\sado\sado_be\sado-minipacs\src\main\resources\application.yml`

```yaml
# SeaweedFS S3 API 설정
seaweedfs:
  s3:
    endpoint: http://localhost:10405
    region: us-east-1
    access-key: any        # SeaweedFS는 인증 불필요 (개발 환경)
    secret-key: any
    bucket: minipacs       # DICOM 파일 저장 버킷
  connection:
    timeout: 30000
    read-timeout: 60000
```

### 4단계: S3Client Configuration

**위치**: `C:\Users\amagr\project\sado\sado_be\sado-minipacs\src\main\java\com\hanumoka\sado\minipacs\infrastructure\config\SeaweedFsConfig.java`

```java
@Configuration
public class SeaweedFsConfig {

    @Value("${seaweedfs.s3.endpoint}")
    private String s3Endpoint;

    @Value("${seaweedfs.s3.region}")
    private String region;

    @Value("${seaweedfs.s3.access-key}")
    private String accessKey;

    @Value("${seaweedfs.s3.secret-key}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)  // Path-style: http://localhost:10405/bucket/key
                .build();

        return S3Client.builder()
                .endpointOverride(URI.create(s3Endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Config)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Presigner.builder()
                .endpointOverride(URI.create(s3Endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}
```

### 5단계: DicomStorageService 구현 (S3 API)

**위치**: `C:\Users\amagr\project\sado\sado_be\sado-minipacs\src\main\java\com\hanumoka\sado\minipacs\infrastructure\storage\DicomStorageService.java`

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class DicomStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${seaweedfs.s3.bucket}")
    private String bucket;

    /**
     * DICOM 파일 업로드 (S3 API)
     */
    public String uploadDicomFile(String studyUid, String seriesUid,
                                   String sopInstanceUid, MultipartFile file) throws IOException {
        String s3Key = buildDicomPath(studyUid, seriesUid, sopInstanceUid);

        log.info("Uploading DICOM to S3: {}, {} bytes", s3Key, file.getSize());

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType("application/dicom")
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(
                file.getInputStream(), file.getSize()));

        log.info("Uploaded DICOM with S3 key: {}", s3Key);
        return s3Key;
    }

    /**
     * DICOM 파일 다운로드 (S3 API)
     */
    public byte[] downloadDicomFile(String s3Key) throws IOException {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
        return response.readAllBytes();
    }

    /**
     * Pre-signed URL 생성 (OHIF Viewer용)
     */
    public String getPresignedUrl(String s3Key, Duration validity) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(validity)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * DICOM 파일 삭제
     */
    public void deleteDicomFile(String s3Key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        s3Client.deleteObject(request);
    }

    /**
     * S3 경로 생성
     */
    private String buildDicomPath(String studyUid, String seriesUid, String sopInstanceUid) {
        return String.format("studies/%s/series/%s/%s.dcm",
                             studyUid, seriesUid, sopInstanceUid);
    }

    /**
     * 썸네일 경로 생성
     */
    public String buildThumbnailPath(String studyUid, String seriesUid, String sopInstanceUid) {
        return String.format("studies/%s/series/%s/thumbnails/%s.jpg",
                             studyUid, seriesUid, sopInstanceUid);
    }
}
```

### 6단계: DICOM 업로드 Flow 구현

**InstanceController**:

```java
@PostMapping("/upload")
public ResponseEntity<ApiResponse<InstanceResponseDto>> uploadDicom(
        @RequestParam("file") MultipartFile file,
        @RequestParam("seriesId") Long seriesId
) {
    try {
        // 1. DICOM 파싱 (dcm4che)
        DicomInputStream dis = new DicomInputStream(file.getInputStream());
        Attributes attrs = dis.readDataset();

        String sopInstanceUid = attrs.getString(Tag.SOPInstanceUID);
        String sopClassUid = attrs.getString(Tag.SOPClassUID);

        // 2. Series 조회 (Study UID도 필요)
        Series series = seriesService.findById(seriesId);
        String studyUid = series.getStudy().getStudyInstanceUid();
        String seriesUid = series.getSeriesInstanceUid();

        // 3. SeaweedFS S3 업로드
        String s3Path = storageService.uploadDicomFile(
                studyUid, seriesUid, sopInstanceUid, file);

        // 4. Instance 엔티티 생성
        Instance instance = Instance.builder()
                .sopInstanceUid(sopInstanceUid)
                .sopClassUid(sopClassUid)
                .storagePath(s3Path)  // ⭐ S3 경로 저장
                .series(series)
                .build();

        Instance saved = instanceService.save(instance);

        // 5. 썸네일 생성 (비동기)
        thumbnailService.generateAsync(saved.getId(), studyUid, seriesUid, sopInstanceUid);

        return ResponseEntity.ok(ApiResponse.success(InstanceResponseDto.from(saved)));

    } catch (Exception e) {
        log.error("DICOM upload failed", e);
        return ResponseEntity.status(500)
                .body(ApiResponse.error(500000, "Upload failed: " + e.getMessage()));
    }
}
```

### 5단계: DICOM 다운로드 Flow

**InstanceController**:

```java
@GetMapping("/{instanceId}/download")
public ResponseEntity<byte[]> downloadDicom(@PathVariable Long instanceId) {
    try {
        // 1. Instance 조회
        Instance instance = instanceService.findById(instanceId);

        // 2. SeaweedFS에서 다운로드
        byte[] dicomData = storageService.downloadDicomFile(instance.getStoragePath());

        // 3. HTTP 응답
        return ResponseEntity.ok()
                .header("Content-Type", "application/dicom")
                .header("Content-Disposition",
                        "attachment; filename=\"" + instance.getSopInstanceUid() + ".dcm\"")
                .body(dicomData);

    } catch (Exception e) {
        log.error("DICOM download failed for instance {}", instanceId, e);
        return ResponseEntity.status(500).build();
    }
}
```

---

## 썸네일 및 트랜스코딩

### 썸네일 생성 서비스

```java
@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final DicomStorageService storageService;
    private final InstanceRepository instanceRepository;

    @Async
    public CompletableFuture<Void> generateAsync(Long instanceId, String originalFid) {
        try {
            // 1. DICOM 다운로드
            byte[] dicomData = storageService.downloadDicomFile(originalFid);

            // 2. 썸네일 생성 (dcm4che + ImageIO)
            byte[] thumbnail = generateThumbnail(dicomData, 256, 256);

            // 3. SeaweedFS 업로드
            String thumbnailFid = storageService.uploadThumbnail(thumbnail);

            // 4. Instance 업데이트
            Instance instance = instanceRepository.findById(instanceId).orElseThrow();
            instance.setThumbnailPath(thumbnailFid);
            instanceRepository.save(instance);

            log.info("Thumbnail generated for instance {}: {}", instanceId, thumbnailFid);

        } catch (Exception e) {
            log.error("Thumbnail generation failed for instance {}", instanceId, e);
        }
        return CompletableFuture.completedFuture(null);
    }

    private byte[] generateThumbnail(byte[] dicomData, int width, int height) {
        // dcm4che로 DICOM → BufferedImage → JPEG
        // ... 구현 생략
        return new byte[0];
    }
}
```

---

## Storage Tiering 전략

### HOT/WARM/COLD 분류

```java
public enum StorageTier {
    HOT,   // 최근 30일, 빠른 접근
    WARM,  // 30일~1년, 보통 접근
    COLD   // 1년 이상, Erasure Coding
}
```

### 자동 Tiering (Scheduler)

```java
@Scheduled(cron = "0 0 2 * * *")  // 매일 새벽 2시
public void tieringJob() {
    LocalDateTime warmThreshold = LocalDateTime.now().minusDays(30);
    LocalDateTime coldThreshold = LocalDateTime.now().minusYears(1);

    // HOT → WARM
    List<Instance> toWarm = instanceRepository
            .findByStorageTierAndCreatedAtBefore(StorageTier.HOT, warmThreshold);
    toWarm.forEach(i -> i.setStorageTier(StorageTier.WARM));

    // WARM → COLD
    List<Instance> toCold = instanceRepository
            .findByStorageTierAndCreatedAtBefore(StorageTier.WARM, coldThreshold);
    toCold.forEach(i -> i.setStorageTier(StorageTier.COLD));

    instanceRepository.saveAll(toWarm);
    instanceRepository.saveAll(toCold);

    log.info("Tiering completed: {} → WARM, {} → COLD", toWarm.size(), toCold.size());
}
```

---

## 테스트

### 통합 테스트

```java
@SpringBootTest
@Transactional
class DicomUploadIntegrationTest {

    @Autowired
    private DicomStorageService storageService;

    @Autowired
    private InstanceService instanceService;

    @Test
    void uploadAndDownloadDicomFile() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.dcm",
                "application/dicom",
                Files.readAllBytes(Paths.get("src/test/resources/sample.dcm"))
        );

        // When: Upload
        String fid = storageService.uploadDicomFile(file);
        assertThat(fid).isNotNull();

        // Then: Download
        byte[] downloaded = storageService.downloadDicomFile(fid);
        assertThat(downloaded).isNotEmpty();

        // Cleanup
        storageService.deleteDicomFile(fid);
    }
}
```

---

## 구현 체크리스트

### 필수 구현 (S3 API)

- [ ] Docker Compose에 SeaweedFS 추가 (Master, Volume, Filer)
  - [ ] Master: 포트 10400, 10401
  - [ ] Volume: 포트 10402
  - [ ] Filer: 포트 10403, 10404, 10405 (S3 API)
- [ ] build.gradle에 AWS SDK 의존성 추가
  - [ ] `software.amazon.awssdk:s3:2.20.26`
  - [ ] `software.amazon.awssdk:s3-presigner:2.20.26`
- [ ] application.yml S3 설정 (endpoint, bucket, credentials)
- [ ] SeaweedFsConfig 클래스 (S3Client, S3Presigner Bean)
- [ ] DicomStorageService 구현 (S3 API)
  - [ ] uploadDicomFile (PutObject)
  - [ ] downloadDicomFile (GetObject)
  - [ ] getPresignedUrl (OHIF Viewer용)
  - [ ] deleteDicomFile (DeleteObject)
- [ ] InstanceController 업로드/다운로드 API
- [ ] Instance.storagePath에 S3 경로 저장 (studies/{studyUID}/...)
- [ ] S3 Bucket 생성 (`curl -X PUT http://localhost:10405/minipacs`)
- [ ] 통합 테스트 작성 (S3 API 검증)

### 선택 구현 (Week 6+)

- [ ] 썸네일 생성 서비스 (비동기)
- [ ] 트랜스코딩 서비스 (멀티프레임 → MP4)
- [ ] Storage Tiering (HOT/WARM/COLD)
- [ ] Compaction 스케줄러
- [ ] 모니터링 (Prometheus + Grafana)

---

## 트러블슈팅

### 문제 1: DICOM 파일 업로드 실패

**증상**:
```
Failed to upload file: Connection refused
```

**해결**:
```bash
# SeaweedFS 컨테이너 확인
docker ps | grep seaweedfs

# Health Check
curl http://localhost:10400/cluster/status
curl http://localhost:10402/status
```

### 문제 2: MySQL에서 FID 조회 안 됨

**증상**:
```sql
SELECT * FROM instance WHERE storage_path = '3,01637037d6';
-- 결과 없음
```

**해결**:
```java
// Instance 저장 확인
log.info("Saved instance: id={}, fid={}", instance.getId(), instance.getStoragePath());
```

---

## 참고 자료

### SADO 프로젝트 문서

- `sado_docs/be/core/00_개발_방식_및_Claude_역할.md`
- `sado_docs/be/core/07_최종_구현_계획.md`
- `sado_docs/PORT_MAPPING.md`

### SeaweedFS 문서

- [HTTP REST API](https://deepwiki.com/seaweedfs/seaweedfs/3.1-http-rest-api)
- [Java Client](https://github.com/seaweedfs/seaweedfs/wiki/SeaweedFS-Java-Client)

---

## 다음 단계

### Week 5-6: Storage Layer 완성

1. **dcm4che 통합**: DICOM 파싱 및 검증
2. **DICOMWeb API**: QIDO-RS, WADO-RS, STOW-RS
3. **썸네일 생성**: 비동기 처리
4. **트랜스코딩**: 멀티프레임 → MP4
5. **통합 테스트**: DICOM 업로드 → 다운로드 → 뷰어

### Week 7-8: POC 완성

1. **Frontend 통합**: Cornerstone3D DICOM 뷰어
2. **성능 테스트**: 10만 개 DICOM 파일
3. **모니터링**: Grafana 대시보드
4. **문서화**: API 문서, 운영 가이드

---

**핵심 요약**:
- ✅ Instance.storagePath = S3 경로 (studies/{studyUID}/series/{seriesUID}/{sopInstanceUID}.dcm)
- ✅ DicomStorageService: S3 API (PutObject, GetObject, DeleteObject, Pre-signed URL)
- ✅ AWS SDK 사용 (S3Client, S3Presigner)
- ✅ Filer 필수 (DICOMweb, OHIF, AI 서비스 연동)
- ✅ 썸네일/트랜스코딩 계층적 관리 (thumbnails/, videos/)
- ✅ Storage Tiering (HOT/WARM/COLD)
- ✅ Docker Compose 통합 (포트 10400~10405)

---

**축하합니다! SeaweedFS 학습을 완료했습니다!** 🎉

이제 SADO MiniPACS 프로젝트에 SeaweedFS를 실전 적용할 준비가 되었습니다. 구현하면서 궁금한 점이 있다면 언제든지 질문하세요!
