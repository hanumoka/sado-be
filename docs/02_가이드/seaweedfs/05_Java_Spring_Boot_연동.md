# 05. Java 및 Spring Boot 연동

> **학습 목표**: Spring Boot 프로젝트에 SeaweedFS를 통합하여 DICOM 파일을 저장하고 조회합니다.

---

## Java Client 라이브러리

### 사용 가능한 라이브러리

| 라이브러리 | Maven Artifact | 상태 | 추천 |
|----------|---------------|------|------|
| **공식 Client** | `com.seaweedfs:seaweedfs-client` | 활발 | ⭐⭐⭐ |
| **Lokra Client** | `org.lokra.seaweedfs:seaweedfs-client` | 유지보수 | ⭐⭐ |
| **커스텀 구현** | - | - | ⭐⭐⭐⭐ (SADO 추천) |

**SADO 프로젝트 선택**: **커스텀 구현** (HTTP API 직접 호출)
- ✅ 학습 목적에 부합
- ✅ 의존성 최소화
- ✅ 완전한 제어

---

## Gradle 의존성 (커스텀 구현)

**build.gradle**:
```gradle
dependencies {
    // HTTP 클라이언트 (이미 포함된 경우 생략)
    implementation 'org.springframework.boot:spring-boot-starter-web'

    // JSON 처리 (이미 포함)
    implementation 'com.fasterxml.jackson.core:jackson-databind'

    // 추가 불필요 (Spring WebClient 또는 RestTemplate 사용)
}
```

---

## Configuration (설정 클래스)

### application.yml

```yaml
seaweedfs:
  master:
    url: http://localhost:10400
  volume:
    url-pattern: http://localhost:{port}  # {port}는 동적 대체
  connection:
    timeout: 30000
    read-timeout: 60000
  upload:
    retry:
      max-attempts: 3
      backoff-delay: 1000
```

### SeaweedFsProperties

```java
package com.hanumoka.sado.minipacs.infrastructure.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "seaweedfs")
public class SeaweedFsProperties {
    private Master master = new Master();
    private Volume volume = new Volume();
    private Connection connection = new Connection();
    private Upload upload = new Upload();

    @Data
    public static class Master {
        private String url;
    }

    @Data
    public static class Volume {
        private String urlPattern;
    }

    @Data
    public static class Connection {
        private int timeout = 30000;
        private int readTimeout = 60000;
    }

    @Data
    public static class Upload {
        private Retry retry = new Retry();

        @Data
        public static class Retry {
            private int maxAttempts = 3;
            private long backoffDelay = 1000;
        }
    }
}
```

---

## Storage Service 구현

### DicomStorageService

```java
package com.hanumoka.sado.minipacs.infrastructure.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

@Slf4j
@Service
@RequiredArgsConstructor
public class DicomStorageService {

    private final SeaweedFsProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * DICOM 파일 업로드
     *
     * @param file MultipartFile
     * @return FID (File ID)
     */
    public String uploadDicomFile(MultipartFile file) throws IOException {
        log.info("Uploading DICOM file: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());

        // 1단계: FID 할당
        String fid = assignFileId();
        log.debug("Assigned FID: {}", fid);

        // 2단계: 파일 업로드
        uploadToVolume(fid, file);
        log.info("Successfully uploaded file with FID: {}", fid);

        return fid;
    }

    /**
     * DICOM 파일 다운로드
     *
     * @param fid File ID
     * @return 파일 바이트 배열
     */
    public byte[] downloadDicomFile(String fid) throws IOException {
        log.info("Downloading DICOM file with FID: {}", fid);

        String volumeUrl = resolveVolumeUrl(fid);
        String downloadUrl = volumeUrl + "/" + fid;

        ResponseEntity<byte[]> response = restTemplate.getForEntity(downloadUrl, byte[].class);

        if (response.getStatusCode() == HttpStatus.OK) {
            log.info("Successfully downloaded file with FID: {}", fid);
            return response.getBody();
        } else {
            throw new IOException("Failed to download file: " + response.getStatusCode());
        }
    }

    /**
     * DICOM 파일 삭제
     *
     * @param fid File ID
     */
    public void deleteDicomFile(String fid) throws IOException {
        log.info("Deleting DICOM file with FID: {}", fid);

        String volumeUrl = resolveVolumeUrl(fid);
        String deleteUrl = volumeUrl + "/" + fid;

        restTemplate.delete(deleteUrl);
        log.info("Successfully deleted file with FID: {}", fid);
    }

    /**
     * 1단계: Master에서 FID 할당
     */
    private String assignFileId() throws IOException {
        String assignUrl = properties.getMaster().getUrl() + "/dir/assign";

        try {
            String response = restTemplate.getForObject(assignUrl, String.class);
            JsonNode json = objectMapper.readTree(response);
            return json.get("fid").asText();
        } catch (Exception e) {
            log.error("Failed to assign FID", e);
            throw new IOException("Failed to assign FID: " + e.getMessage(), e);
        }
    }

    /**
     * 2단계: Volume Server에 파일 업로드
     */
    private void uploadToVolume(String fid, MultipartFile file) throws IOException {
        String volumeUrl = resolveVolumeUrl(fid);
        String uploadUrl = volumeUrl + "/" + fid;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(uploadUrl, requestEntity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IOException("Failed to upload file: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to upload file to volume", e);
            throw new IOException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    /**
     * FID에서 Volume URL 추출
     * FID 형식: "volumeId,needleId" (예: "3,01637037d6")
     */
    private String resolveVolumeUrl(String fid) throws IOException {
        String volumeId = fid.split(",")[0];

        // Master에 Volume 위치 조회
        String lookupUrl = properties.getMaster().getUrl() + "/dir/lookup?volumeId=" + volumeId;

        try {
            String response = restTemplate.getForObject(lookupUrl, String.class);
            JsonNode json = objectMapper.readTree(response);
            String publicUrl = json.get("locations").get(0).get("publicUrl").asText();

            // URL 패턴에서 포트 추출 및 대체
            return "http://" + publicUrl;
        } catch (Exception e) {
            log.error("Failed to resolve volume URL for FID: {}", fid, e);
            throw new IOException("Failed to resolve volume URL: " + e.getMessage(), e);
        }
    }
}
```

### RestTemplate 설정

```java
package com.hanumoka.sado.minipacs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000);  // 30초
        factory.setReadTimeout(60000);     // 60초
        return new RestTemplate(factory);
    }
}
```

---

## Controller 통합

### InstanceController 수정

```java
package com.hanumoka.sado.minipacs.controller;

import com.hanumoka.sado.common.dto.ApiResponse;
import com.hanumoka.sado.minipacs.infrastructure.storage.DicomStorageService;
import com.hanumoka.sado.minipacs.domain.service.InstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/instances")
@RequiredArgsConstructor
public class InstanceController {

    private final InstanceService instanceService;
    private final DicomStorageService storageService;

    /**
     * DICOM 파일 업로드
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> uploadDicom(
            @RequestParam("file") MultipartFile file
    ) {
        try {
            // 1. SeaweedFS에 파일 업로드
            String fid = storageService.uploadDicomFile(file);

            // 2. DICOM 메타데이터 파싱 (dcm4che 사용 - 나중에 구현)
            // DicomMetadata metadata = dicomParser.parse(file);

            // 3. Instance 엔티티 저장
            // Instance instance = instanceService.createInstance(metadata, fid);

            return ResponseEntity.ok(ApiResponse.success(fid));
        } catch (Exception e) {
            log.error("Failed to upload DICOM file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500000, "파일 업로드 실패: " + e.getMessage()));
        }
    }

    /**
     * DICOM 파일 다운로드
     */
    @GetMapping("/{instanceId}/download")
    public ResponseEntity<byte[]> downloadDicom(@PathVariable Long instanceId) {
        try {
            // 1. Instance 조회
            // Instance instance = instanceService.findById(instanceId);

            // 2. SeaweedFS에서 파일 다운로드
            String fid = "3,01637037d6";  // instance.getStoragePath()
            byte[] fileData = storageService.downloadDicomFile(fid);

            return ResponseEntity.ok()
                    .header("Content-Type", "application/dicom")
                    .header("Content-Disposition", "attachment; filename=\"dicom.dcm\"")
                    .body(fileData);
        } catch (Exception e) {
            log.error("Failed to download DICOM file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
```

---

## 통합 테스트

### DicomStorageServiceTest

```java
package com.hanumoka.sado.minipacs.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DicomStorageServiceTest {

    @Autowired
    private DicomStorageService storageService;

    @Test
    void uploadAndDownloadDicomFile() throws Exception {
        // Given
        byte[] content = "DICOM file content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.dcm",
                "application/dicom",
                content
        );

        // When: 업로드
        String fid = storageService.uploadDicomFile(file);
        assertThat(fid).isNotNull();
        assertThat(fid).contains(",");

        // Then: 다운로드
        byte[] downloaded = storageService.downloadDicomFile(fid);
        assertThat(downloaded).isEqualTo(content);

        // Cleanup: 삭제
        storageService.deleteDicomFile(fid);
    }
}
```

> **참고**: 위의 HTTP API 구현은 **학습/교육 목적**입니다. 프로덕션 환경에서는 아래의 **S3 API 구현**을 권장합니다.

---

## S3 API 연동 (프로덕션 권장)

### S3 API를 사용하는 이유

**HTTP API 대비 장점**:
1. **표준 호환성**: 산업 표준 S3 API 사용
2. **DICOMweb 준수**: OHIF Viewer 등 표준 뷰어 통합
3. **계층적 경로**: 파일 시스템처럼 구조화된 경로
4. **Pre-signed URL**: 시간 제한 보안 URL 생성
5. **AI 서비스 호환**: Python boto3 등 표준 SDK 사용 가능

### Gradle 의존성

```gradle
dependencies {
    // AWS SDK for S3 (SeaweedFS S3 API 호환)
    implementation 'software.amazon.awssdk:s3:2.20.26'
    implementation 'software.amazon.awssdk:s3-presigner:2.20.26'
}
```

### application.yml 설정

```yaml
seaweedfs:
  s3:
    endpoint: http://localhost:10405  # Filer S3 API
    region: us-east-1
    access-key: any        # SeaweedFS 인증 불필요 (개발 환경)
    secret-key: any
    bucket: minipacs       # DICOM 파일 저장 버킷
  connection:
    timeout: 30000
    read-timeout: 60000
```

### S3Client Configuration

```java
package com.hanumoka.sado.minipacs.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class SeaweedFsS3Config {

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

        // Path-style access 활성화 (SeaweedFS 필수)
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
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

### DicomStorageService (S3 API 버전)

```java
package com.hanumoka.sado.minipacs.infrastructure.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class DicomStorageServiceS3 {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${seaweedfs.s3.bucket}")
    private String bucket;

    /**
     * DICOM 파일 업로드 (S3 PutObject)
     */
    public String uploadDicomFile(String studyUid, String seriesUid,
                                   String sopInstanceUid, MultipartFile file) throws IOException {
        String s3Key = buildDicomPath(studyUid, seriesUid, sopInstanceUid);

        log.info("Uploading DICOM to S3: bucket={}, key={}, size={} bytes",
                 bucket, s3Key, file.getSize());

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType("application/dicom")
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(
                file.getInputStream(), file.getSize()));

        log.info("Successfully uploaded DICOM to S3: {}", s3Key);
        return s3Key;
    }

    /**
     * DICOM 파일 다운로드 (S3 GetObject)
     */
    public byte[] downloadDicomFile(String s3Key) throws IOException {
        log.info("Downloading DICOM from S3: bucket={}, key={}", bucket, s3Key);

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        byte[] data = s3Client.getObject(request, ResponseTransformer.toBytes()).asByteArray();

        log.info("Successfully downloaded DICOM from S3: {} ({} bytes)", s3Key, data.length);
        return data;
    }

    /**
     * Pre-signed URL 생성 (OHIF Viewer, 외부 접근용)
     */
    public String getPresignedUrl(String s3Key, Duration validity) {
        log.info("Generating pre-signed URL for S3 key: {}, validity: {}", s3Key, validity);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(validity)
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        String url = presignedRequest.url().toString();

        log.info("Generated pre-signed URL: {}", url);
        return url;
    }

    /**
     * DICOM 파일 삭제 (S3 DeleteObject)
     */
    public void deleteDicomFile(String s3Key) {
        log.info("Deleting DICOM from S3: bucket={}, key={}", bucket, s3Key);

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        s3Client.deleteObject(request);

        log.info("Successfully deleted DICOM from S3: {}", s3Key);
    }

    /**
     * S3 경로 생성 (계층적 구조)
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

    /**
     * 트랜스코딩 비디오 경로 생성
     */
    public String buildVideoPath(String studyUid, String seriesUid, String sopInstanceUid) {
        return String.format("studies/%s/series/%s/videos/%s.mp4",
                             studyUid, seriesUid, sopInstanceUid);
    }
}
```

### 사용 예제 (Controller)

```java
@RestController
@RequestMapping("/api/instances")
@RequiredArgsConstructor
public class InstanceController {

    private final DicomStorageServiceS3 storageService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadDicom(
            @RequestParam("file") MultipartFile file,
            @RequestParam("studyUid") String studyUid,
            @RequestParam("seriesUid") String seriesUid,
            @RequestParam("sopInstanceUid") String sopInstanceUid
    ) throws IOException {
        String s3Path = storageService.uploadDicomFile(studyUid, seriesUid, sopInstanceUid, file);
        return ResponseEntity.ok(s3Path);
    }

    @GetMapping("/{instanceId}/download")
    public ResponseEntity<byte[]> downloadDicom(@PathVariable Long instanceId) throws IOException {
        // 1. Instance 조회하여 s3Key 가져오기
        String s3Key = "studies/1.2.840.../series/.../instance.dcm";  // 실제로는 DB에서 조회

        // 2. S3에서 다운로드
        byte[] dicomData = storageService.downloadDicomFile(s3Key);

        // 3. HTTP 응답
        return ResponseEntity.ok()
                .header("Content-Type", "application/dicom")
                .header("Content-Disposition", "attachment; filename=\"image.dcm\"")
                .body(dicomData);
    }

    @GetMapping("/{instanceId}/presigned-url")
    public ResponseEntity<String> getPresignedUrl(@PathVariable Long instanceId) {
        String s3Key = "studies/1.2.840.../series/.../instance.dcm";  // DB에서 조회
        String url = storageService.getPresignedUrl(s3Key, Duration.ofHours(1));
        return ResponseEntity.ok(url);
    }
}
```

### S3 Bucket 초기화

```bash
# SeaweedFS S3 API로 버킷 생성
curl -X PUT http://localhost:10405/minipacs

# 확인
curl http://localhost:10405/
```

**응답**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<ListAllMyBucketsResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
  <Buckets>
    <Bucket>
      <Name>minipacs</Name>
      <CreationDate>2025-12-30T00:00:00.000Z</CreationDate>
    </Bucket>
  </Buckets>
</ListAllMyBucketsResult>
```

---

## 참고 자료

- [SeaweedFS Java Client](https://github.com/seaweedfs/seaweedfs/wiki/SeaweedFS-Java-Client)
- [pink-lucifer/seaweedfs-clients](https://github.com/pink-lucifer/seaweedfs-clients)

---

## 다음 단계

👉 **[06_복제_및_고가용성.md](./06_복제_및_고가용성.md)** - 프로덕션 HA 구성

---

**핵심 요약**:

**HTTP API 구현 (학습/교육용)**:
- ✅ 커스텀 구현 (HTTP API 직접 호출)
- ✅ DicomStorageService: upload/download/delete
- ✅ RestTemplate 사용
- ✅ FID → MySQL 저장 (Instance.storagePath)

**S3 API 구현 (프로덕션 권장)**:
- ✅ AWS SDK for S3 (standard API)
- ✅ S3Client + S3Presigner Bean 구성
- ✅ PutObject, GetObject, DeleteObject
- ✅ Pre-signed URL 지원 (OHIF Viewer 연동)
- ✅ 계층적 S3 경로 (studies/{studyUID}/...)
- ✅ DICOMweb 표준 준수
