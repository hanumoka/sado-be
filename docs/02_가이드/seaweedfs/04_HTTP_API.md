# 04. SeaweedFS HTTP API

> **학습 목표**: HTTP REST API를 사용하여 파일 업로드/다운로드/관리를 마스터합니다.

---

## API 개요

SeaweedFS는 간단한 **HTTP REST API**를 제공합니다. Filer 없이 Object Store만 사용할 경우, Master와 Volume Server의 API만 사용합니다.

### API 엔드포인트

| 서버 | 기본 포트 | SADO 포트 | 주요 기능 |
|-----|----------|----------|----------|
| **Master** | 9333 | 10400 | FID 할당, Volume 조회 |
| **Volume** | 8080 | 10402 | 파일 업로드/다운로드/삭제 |
| **Filer** | 8888 | 10403 | (선택) 파일 시스템 인터페이스 |

---

## 파일 업로드 (2단계 프로세스)

### 개념

SeaweedFS의 파일 업로드는 **2단계**로 진행됩니다:

```
1단계: Master에게 FID 요청 (GET /dir/assign)
   ↓
2단계: Volume에 파일 업로드 (POST /{fid})
```

### 1단계: FID 할당

**요청**:
```bash
curl "http://localhost:10400/dir/assign"
```

**응답**:
```json
{
  "fid": "3,01637037d6",
  "url": "localhost:10402",
  "publicUrl": "localhost:10402",
  "count": 1
}
```

**파라미터**:
- `count`: 한 번에 받을 FID 개수 (기본값: 1)
- `replication`: 복제 정책 (예: `001`)
- `ttl`: Time-To-Live (예: `3h`)
- `dataCenter`: 데이터센터 지정
- `collection`: 컬렉션 이름

**예시 (옵션 포함)**:
```bash
curl "http://localhost:10400/dir/assign?count=5&replication=001&collection=photos"
```

### 2단계: 파일 업로드

**요청**:
```bash
curl -X POST \
  -F "file=@image.jpg" \
  "http://localhost:10402/3,01637037d6"
```

**응답**:
```json
{
  "name": "image.jpg",
  "size": 102400,
  "eTag": "d41d8cd98f00b204e9800998ecf8427e"
}
```

**Multipart Form 필드**:
- `file`: 파일 데이터 (필수)
- `name`: 파일 이름 (선택, 메타데이터용)

---

## 파일 다운로드

**요청**:
```bash
curl "http://localhost:10402/3,01637037d6" -o image.jpg
```

**헤더 추가 (조건부 다운로드)**:
```bash
# If-Modified-Since
curl -H "If-Modified-Since: Wed, 21 Oct 2025 07:28:00 GMT" \
  "http://localhost:10402/3,01637037d6"

# If-None-Match (ETag)
curl -H 'If-None-Match: "d41d8cd98f00b204e9800998ecf8427e"' \
  "http://localhost:10402/3,01637037d6"
```

**Range 요청 (부분 다운로드)**:
```bash
curl -H "Range: bytes=0-1023" "http://localhost:10402/3,01637037d6"
# 처음 1KB만 다운로드
```

---

## 파일 삭제

**요청**:
```bash
curl -X DELETE "http://localhost:10402/3,01637037d6"
```

**응답**:
```json
{
  "size": 102400
}
```

⚠️ **주의**: 삭제는 **논리적 삭제** (플래그만 표시). 공간 회수는 Compaction 필요.

---

## 파일 복사

**Filer API 사용** (선택적):
```bash
curl -X POST "http://localhost:10403/photos/copy.jpg?cp.from=/photos/original.jpg"
```

---

## 대용량 파일 처리

### 자동 청킹 (Auto Chunking)

**큰 파일 업로드** (예: 500MB DICOM):
```bash
# Master에서 FID 요청
curl "http://localhost:10400/dir/assign"
# 응답: {"fid": "3,01637037d6", ...}

# Volume에 업로드 (자동으로 청킹됨)
curl -X POST -F "file=@large_dicom.dcm" "http://localhost:10402/3,01637037d6"
```

SeaweedFS는 자동으로 큰 파일을 여러 Needle로 분할하고, 메타데이터로 연결합니다.

---

## Volume 위치 조회

**특정 Volume의 서버 위치 찾기**:
```bash
curl "http://localhost:10400/dir/lookup?volumeId=3"
```

**응답**:
```json
{
  "volumeId": "3",
  "locations": [
    {
      "url": "localhost:10402",
      "publicUrl": "localhost:10402"
    }
  ]
}
```

---

## 클러스터 상태 조회

```bash
# Master 상태
curl "http://localhost:10400/cluster/status"

# Volume 상태
curl "http://localhost:10402/status"

# Health Check
curl "http://localhost:10400/cluster/healthz"
curl "http://localhost:10402/healthz"
```

---

## 실전 예제: DICOM 파일 업로드

### Java 코드 (HttpURLConnection)

```java
public String uploadDicomFile(File dicomFile) throws IOException {
    // 1단계: FID 요청
    String assignUrl = "http://localhost:10400/dir/assign";
    String assignResponse = HttpClient.newHttpClient()
        .send(HttpRequest.newBuilder()
            .uri(URI.create(assignUrl))
            .GET()
            .build(),
            HttpResponse.BodyHandlers.ofString())
        .body();

    JsonNode json = objectMapper.readTree(assignResponse);
    String fid = json.get("fid").asText();
    String uploadUrl = "http://localhost:10402/" + fid;

    // 2단계: 파일 업로드
    HttpClient client = HttpClient.newHttpClient();
    MultipartBodyPublisher publisher = new MultipartBodyPublisher()
        .addPart("file", dicomFile.toPath());

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(uploadUrl))
        .header("Content-Type", "multipart/form-data; boundary=" + publisher.getBoundary())
        .POST(publisher.build())
        .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    return fid;  // MySQL에 저장
}
```

---

## 참고 자료

- [HTTP REST API - DeepWiki](https://deepwiki.com/seaweedfs/seaweedfs/3.1-http-rest-api)
- [Large File Handling](https://github.com/seaweedfs/seaweedfs/wiki/Large-File-Handling)

---

## 다음 단계

👉 **[05_Java_Spring_Boot_연동.md](./05_Java_Spring_Boot_연동.md)** - Spring Boot 통합

---

**핵심 요약**:
- ✅ 2단계 업로드: FID 할당 → 파일 전송
- ✅ 다운로드: GET /{fid}
- ✅ 삭제: DELETE /{fid} (논리적 삭제)
- ✅ 대용량 파일 자동 청킹
- ✅ Range, ETag 지원
