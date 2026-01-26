# 11. SADO 스토리지 모니터링 현황 및 개선 계획

> **최종 업데이트**: 2026-01-19
> **목적**: SADO MiniPACS 프로젝트의 SeaweedFS 모니터링 현황 분석 및 개선 로드맵

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|----------|
| 2026-01-19 | 1.1 | **Phase 1 구현 완료** - Volume 페이지네이션, Collection 통계, 용량 경고 UI |
| 2026-01-19 | 1.0 | 최초 작성 - 현황 분석 및 개선 로드맵 수립 |

---

## 1. 현황 요약

### 1.1 전체 평가

| 평가 항목 | 점수 | 현재 상태 |
|----------|------|----------|
| **기본 모니터링** | ★★★★☆ | Volume 목록, 클러스터 상태, 용량 표시 |
| **상세 메트릭** | ★★☆☆☆ | 개별 Volume I/O, 성능 메트릭 부족 |
| **시계열 데이터** | ★☆☆☆☆ | Prometheus 연동 없음 |
| **경고/알림** | ★★★☆☆ | 70%/85% 용량 임계값 경고 배너 **(Phase 1 완료)** |
| **확장성** | ★★★★☆ | 페이지네이션/필터링 지원 **(Phase 1 완료)** |

### 1.2 구현 완료 기능

```
✅ 클러스터 상태 조회 (Master/Volume/Filer 노드)
✅ Health 상태 계산 (HEALTHY/WARNING/DEGRADED/CRITICAL)
✅ Volume 목록 조회 (ID, 크기, 파일 수, Collection)
✅ SeaweedFS 물리적 용량 표시 (전체/사용/여유)
✅ Filer 파일 탐색기 (조회/다운로드/삭제)
✅ Volume 생성/삭제 API
✅ 다중 노드 설정 지원

# Phase 1 완료 (2026-01-19)
✅ Volume 페이지네이션 및 필터링 (Collection, 상태, 정렬)
✅ Collection별 통계 API 및 카드 뷰 UI
✅ 용량 임계값 경고 배너 (70% 주의, 85% 위험)
```

### 1.3 미구현 기능

```
# Phase 2 (추가 인프라 불필요)
❌ 개별 Volume 상세 정보 모달
❌ Volume Server 상세 메트릭

# Phase 3 (Prometheus/Grafana/Alertmanager 필요)
❌ Prometheus 메트릭 연동
❌ 시계열 트렌드 (Volume 용량 변화)
❌ 개별 Volume 성능 메트릭 (Read/Write TPS, Latency)
❌ Volume Server CPU/Memory/Disk I/O
❌ Grafana 대시보드
❌ Alertmanager 알림

# Phase 4 (고급 기능)
❌ 용량 예측 (머신러닝 기반)
❌ 자동 Volume 확장
❌ 이상 탐지
```

---

## 2. 아키텍처 분석

### 2.1 현재 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend (React)                         │
├─────────────────────────────────────────────────────────────┤
│  StorageManagePage.tsx                                      │
│  └─ SeaweedFS 탭                                            │
│     ├─ 클러스터 상태 (30초 폴링)                            │
│     ├─ Volume 목록 (수동 새로고침)                          │
│     └─ Filer 탐색기                                         │
└─────────────────────────────────────────────────────────────┘
                           │
                           │ REST API (HTTP Polling)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                 Backend (Spring Boot)                       │
├─────────────────────────────────────────────────────────────┤
│  SeaweedFSAdminController                                   │
│  └─ SeaweedFSAdminService                                   │
│     ├─ getClusterStatus()    → 노드 상태 병렬 조회         │
│     ├─ listVolumes()         → Volume Server /status 파싱  │
│     └─ getCapacity()         → 디스크 용량 조회            │
└─────────────────────────────────────────────────────────────┘
                           │
                           │ HTTP API (직접 호출)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    SeaweedFS Cluster                        │
├─────────────────────────────────────────────────────────────┤
│  Master          Volume Server         Filer               │
│  :9333           :8080                  :8888               │
│  /cluster/status /status               /?pretty=y          │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 문제점

| 문제 | 영향 | 심각도 |
|------|------|--------|
| **Prometheus 미연동** | 시계열 데이터 없음, 트렌드 분석 불가 | 높음 |
| **단일 API 호출** | 대량 Volume 시 응답 지연 | 중간 |
| **페이지네이션 없음** | 1000+ Volume 시 UI 성능 저하 | 높음 |
| **메트릭 부족** | 성능 문제 조기 감지 불가 | 높음 |

---

## 3. SeaweedFS 네이티브 모니터링 vs 현재 구현

### 3.1 SeaweedFS가 제공하는 Prometheus 메트릭

SeaweedFS는 각 컴포넌트별 Prometheus 메트릭 엔드포인트를 제공합니다:

```bash
# 메트릭 포트 활성화
weed master -metricsPort=9321
weed volume -metricsPort=9322
weed filer  -metricsPort=9323
```

**Master 메트릭**:
| 메트릭 | 설명 |
|--------|------|
| `seaweedfs_master_volumes_total` | 전체 Volume 수 |
| `seaweedfs_master_volumes_by_collection` | Collection별 Volume 수 |
| `seaweedfs_master_leader` | Leader 여부 (1/0) |
| `seaweedfs_master_raft_*` | Raft 합의 상태 |

**Volume Server 메트릭**:
| 메트릭 | 설명 |
|--------|------|
| `seaweedfs_volume_server_volumes_total` | 서버별 Volume 수 |
| `seaweedfs_volume_server_max_volumes` | 최대 Volume 수 |
| `seaweedfs_volume_server_disk_used_bytes` | 디스크 사용량 |
| `seaweedfs_volume_read_requests_total` | 읽기 요청 수 |
| `seaweedfs_volume_write_requests_total` | 쓰기 요청 수 |
| `seaweedfs_volume_request_duration_seconds` | 요청 응답 시간 |

**Filer 메트릭**:
| 메트릭 | 설명 |
|--------|------|
| `seaweedfs_filer_requests_total` | 요청 수 |
| `seaweedfs_filer_request_duration_seconds` | 응답 시간 |

### 3.2 현재 SADO 구현의 한계

```java
// 현재: SeaweedFS HTTP API 직접 호출
// Master: GET /cluster/status
// Volume: GET /status
// → 스냅샷 데이터만 획득, 시계열 불가

// 미사용: Prometheus 메트릭 엔드포인트
// GET :9321/metrics (Master)
// GET :9322/metrics (Volume)
// → TPS, Latency, 상세 메트릭 접근 불가
```

---

## 4. 대량 Volume 대응 문제

### 4.1 현재 구현의 확장성 한계

**시나리오**: 10개 Volume Server, 각 100개 Volume = 1,000개 Volume

| 현재 구현 | 문제점 |
|----------|--------|
| `GET /status` 전체 반환 | 1,000개 JSON 파싱 → 메모리 사용 증가 |
| 단일 테이블 렌더링 | 1,000 행 DOM → UI 렌더링 지연 |
| 필터/검색 없음 | 특정 Volume 찾기 어려움 |
| 정렬 고정 | 문제 Volume 식별 어려움 |
| Collection 그룹핑 없음 | 논리적 구조 파악 불가 |

### 4.2 현재 API 응답 예시

```json
// GET /api/admin/seaweedfs/volumes
{
  "data": [
    {"id": 1, "collection": "minipacs", "size": 32212254720, ...},
    {"id": 2, ...},
    // ... 1,000개 모두 반환 (페이지네이션 없음)
  ]
}
```

---

## 5. 개선 계획

### 5.1 Phase 1: 즉시 개선 (1-2주)

#### 5.1.1 Volume 페이지네이션 및 필터링

**BE 변경** (`SeaweedFSAdminController.java`):
```java
@GetMapping("/volumes")
public PageResponse<VolumeInfoResponse> listVolumes(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "50") int size,
    @RequestParam(required = false) String collection,
    @RequestParam(required = false) String status,
    @RequestParam(defaultValue = "id") String sortBy,
    @RequestParam(defaultValue = "asc") String order
)
```

**FE 변경** (`StorageManagePage.tsx`):
```tsx
<VolumeTable
  data={volumes}
  pagination={{ page, size, totalElements }}
  filters={[
    { field: 'collection', options: collections },
    { field: 'status', options: ['ReadWrite', 'ReadOnly'] }
  ]}
  sortable={['id', 'size', 'fileCount', 'usedSize']}
/>
```

#### 5.1.2 Collection별 통계 API

**신규 API**: `GET /api/admin/seaweedfs/collections/stats`

```java
public record CollectionStatsResponse(
    String collection,
    int volumeCount,
    long totalSize,
    long usedSize,
    long fileCount,
    List<String> volumeServers  // 분포된 서버 목록
) {}
```

**UI**: Collection 카드 뷰 추가
```
┌─────────────────────────────────────────────────────┐
│ Collection: minipacs                                │
│ ├─ Volumes: 15                                      │
│ ├─ Files: 125,000                                   │
│ ├─ Size: 450 GB / 500 GB (90%)                     │
│ └─ Servers: volume-1, volume-2, volume-3           │
└─────────────────────────────────────────────────────┘
```

#### 5.1.3 용량 임계값 경고

**경고 레벨**:
```typescript
interface StorageAlerts {
  // Volume Server 레벨
  serverWarning: 70,   // 70% → 노란색 배너
  serverCritical: 85,  // 85% → 빨간색 배너

  // Cluster 전체 레벨
  clusterWarning: 60,
  clusterCritical: 75,

  // 개별 Volume 레벨
  volumeWarning: 80,
  volumeCritical: 95,
}
```

**UI**: 경고 배너 추가
```tsx
{capacity.percentUsed >= 85 && (
  <Alert variant="destructive">
    <AlertTriangle className="h-4 w-4" />
    <AlertTitle>저장 공간 위험</AlertTitle>
    <AlertDescription>
      사용률 {capacity.percentUsed}% - 즉시 용량 확장 필요
    </AlertDescription>
  </Alert>
)}
```

### 5.2 Phase 2: 상세 메트릭 (2-4주)

#### 5.2.1 개별 Volume 상세 정보

**신규 API**: `GET /api/admin/seaweedfs/volumes/{volumeId}/detail`

```java
public record VolumeDetailResponse(
    int id,
    String collection,
    String replication,
    String status,          // ReadWrite, ReadOnly
    long size,
    long usedSize,
    long fileCount,
    String serverUrl,
    // 추가 정보
    LocalDateTime createdAt,
    LocalDateTime lastModifiedAt,
    List<String> replicaLocations,
    VolumePerformance performance
) {}

public record VolumePerformance(
    double readRequestsPerSec,
    double writeRequestsPerSec,
    double avgReadLatencyMs,
    double avgWriteLatencyMs
) {}
```

#### 5.2.2 Volume Server 상세 메트릭

**확장된 VolumeServerNode**:
```typescript
interface VolumeServerDetailedMetrics {
  // 기존
  name: string
  address: string
  volumeCount: number
  totalDiskSpace: number
  usedDiskSize: number

  // 추가
  maxVolumes: number
  cpuUsagePercent: number
  memoryUsagePercent: number
  diskIoReadMBps: number
  diskIoWriteMBps: number
  networkInMBps: number
  networkOutMBps: number
  activeConnections: number
}
```

### 5.3 Phase 3: Prometheus 통합 (4-6주)

#### 5.3.1 아키텍처 변경

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend (React)                         │
│  └─ Grafana 임베딩 또는 자체 시계열 차트                    │
└─────────────────────────────────────────────────────────────┘
                           │
           ┌───────────────┴───────────────┐
           │                               │
           ▼                               ▼
┌──────────────────────┐     ┌──────────────────────┐
│  SADO Backend API    │     │     Prometheus       │
│  (현재 메트릭)       │     │  (시계열 메트릭)     │
└──────────────────────┘     └──────────────────────┘
           │                               │
           └───────────────┬───────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    SeaweedFS Cluster                        │
│  Master:9321/metrics  Volume:9322/metrics  Filer:9323/metrics│
└─────────────────────────────────────────────────────────────┘
```

#### 5.3.2 Docker Compose 확장

```yaml
# docker-compose.monitoring.yml
services:
  prometheus:
    image: prom/prometheus:v2.47.0
    container_name: sado-prometheus
    ports:
      - "10500:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - ./alerts:/etc/prometheus/alerts
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.retention.time=30d'
    networks:
      - sado-network

  grafana:
    image: grafana/grafana:10.2.0
    container_name: sado-grafana
    ports:
      - "10501:3000"
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./grafana/datasources:/etc/grafana/provisioning/datasources
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    networks:
      - sado-network

  alertmanager:
    image: prom/alertmanager:v0.26.0
    container_name: sado-alertmanager
    ports:
      - "10502:9093"
    volumes:
      - ./alertmanager.yml:/etc/alertmanager/alertmanager.yml
    networks:
      - sado-network
```

#### 5.3.3 Prometheus 설정

```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager:9093']

rule_files:
  - /etc/prometheus/alerts/*.yml

scrape_configs:
  - job_name: 'seaweedfs-master'
    static_configs:
      - targets: ['seaweedfs-master:9321']
    relabel_configs:
      - source_labels: [__address__]
        target_label: instance
        regex: '([^:]+):\d+'
        replacement: '${1}'

  - job_name: 'seaweedfs-volume'
    static_configs:
      - targets:
          - 'seaweedfs-volume-1:9322'
          - 'seaweedfs-volume-2:9322'

  - job_name: 'seaweedfs-filer'
    static_configs:
      - targets: ['seaweedfs-filer:9323']
```

#### 5.3.4 Alert 규칙

```yaml
# alerts/seaweedfs.yml
groups:
  - name: seaweedfs-capacity
    rules:
      - alert: SeaweedFSDiskWarning
        expr: |
          (seaweedfs_volume_server_disk_used_bytes /
           seaweedfs_volume_server_disk_total_bytes) > 0.7
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Volume Server {{ $labels.instance }} 디스크 70% 초과"
          description: "현재 사용률: {{ $value | humanizePercentage }}"

      - alert: SeaweedFSDiskCritical
        expr: |
          (seaweedfs_volume_server_disk_used_bytes /
           seaweedfs_volume_server_disk_total_bytes) > 0.85
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Volume Server {{ $labels.instance }} 디스크 85% 초과"

      - alert: SeaweedFSNoLeader
        expr: sum(seaweedfs_master_leader) == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "SeaweedFS Master Leader 없음"

      - alert: SeaweedFSVolumeServerDown
        expr: up{job="seaweedfs-volume"} == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Volume Server {{ $labels.instance }} 다운"
```

#### 5.3.5 메트릭 프록시 API

SADO Backend에서 Prometheus 쿼리를 래핑:

```java
@RestController
@RequestMapping("/api/admin/metrics")
public class PrometheusProxyController {

    @GetMapping("/timeseries")
    public TimeseriesResponse queryTimeseries(
        @RequestParam String query,    // PromQL
        @RequestParam String start,    // ISO timestamp
        @RequestParam String end,
        @RequestParam String step      // 1m, 5m, 1h
    ) {
        // Prometheus /api/v1/query_range 호출
    }

    @GetMapping("/volumes/{volumeId}/performance")
    public VolumePerformanceTimeseries getVolumePerformance(
        @PathVariable int volumeId,
        @RequestParam(defaultValue = "1h") String range
    ) {
        // 미리 정의된 PromQL 쿼리 실행
        // - rate(seaweedfs_volume_read_requests_total{volume_id="X"}[5m])
        // - rate(seaweedfs_volume_write_requests_total{volume_id="X"}[5m])
    }
}
```

### 5.4 Phase 4: 고급 기능 (6주+)

#### 5.4.1 용량 예측

```java
public record CapacityForecast(
    LocalDate predictedExhaustionDate,
    double dailyGrowthRate,           // bytes/day
    double confidenceInterval,        // 0.0 ~ 1.0
    List<ForecastPoint> forecast      // 7/30/90일 예측
) {}
```

#### 5.4.2 자동 Volume 확장

```yaml
# application.yml
seaweedfs:
  auto-scaling:
    enabled: true
    trigger:
      disk-usage-percent: 80
      max-volumes-percent: 90
    action:
      volumes-to-create: 5
      collection: minipacs
      replication: "001"
```

#### 5.4.3 이상 탐지

```java
@Scheduled(fixedRate = 300000)  // 5분마다
public void detectAnomalies() {
    // 1. 급격한 용량 증가 감지
    // 2. 비정상적인 Read/Write 패턴
    // 3. 특정 Volume의 I/O 집중
}
```

---

## 6. UI 개선 설계

### 6.1 Volume 목록 개선

**현재**:
```
┌──────────────────────────────────────────────────────┐
│ ID │ Collection │ 파일 수 │ 크기 │ 서버           │
├──────────────────────────────────────────────────────┤
│ 1  │ minipacs   │ 150     │ 30GB │ localhost:8080 │
│ 2  │ minipacs   │ 200     │ 45GB │ localhost:8080 │
│ ...                                                  │
└──────────────────────────────────────────────────────┘
```

**개선**:
```
┌──────────────────────────────────────────────────────────────┐
│ [Collection ▼] [Status ▼] [Server ▼]    🔍 검색...  [새로고침]│
├──────────────────────────────────────────────────────────────┤
│ ID ▲ │ Collection │ 상태      │ 사용률        │ 파일 수  │ 서버    │
├──────────────────────────────────────────────────────────────┤
│ 1    │ minipacs   │ ● RW      │ ████████░░ 80%│ 150      │ vol-1  │
│ 2    │ minipacs   │ ● RW      │ █████░░░░░ 50%│ 200      │ vol-1  │
│ 3    │ archive    │ ○ RO      │ ██████████ 95%│ 500      │ vol-2  │
├──────────────────────────────────────────────────────────────┤
│                            < 1 2 3 ... 20 >   50/page ▼       │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 Collection 뷰 추가

```
┌───────────────────┐ ┌───────────────────┐ ┌───────────────────┐
│ 📁 minipacs       │ │ 📁 archive        │ │ 📁 temp           │
│                   │ │                   │ │                   │
│ Volumes: 15       │ │ Volumes: 8        │ │ Volumes: 3        │
│ Files: 125,000    │ │ Files: 50,000     │ │ Files: 1,200      │
│ ████████░░ 450GB  │ │ ██████░░░░ 200GB  │ │ ██░░░░░░░░ 15GB   │
│ / 500GB           │ │ / 300GB           │ │ / 100GB           │
│                   │ │                   │ │                   │
│ Servers: 3        │ │ Servers: 2        │ │ Servers: 1        │
└───────────────────┘ └───────────────────┘ └───────────────────┘
```

### 6.3 Volume 상세 모달

```
┌─────────────────────────────────────────────────────────────────┐
│ Volume #15 상세 정보                                        [X] │
├─────────────────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ 기본 정보                                                   │ │
│ ├─────────────────────────────────────────────────────────────┤ │
│ │ Collection: minipacs     Replication: 001                  │ │
│ │ Status: ReadWrite        Server: seaweedfs-volume-1:8080   │ │
│ │ Created: 2026-01-15      Last Modified: 2026-01-19         │ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ 용량                                                        │ │
│ ├─────────────────────────────────────────────────────────────┤ │
│ │ ████████████████████████████░░░░░░░░░░ 75%                 │ │
│ │ Used: 24 GB / Total: 32 GB    Files: 15,234                │ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ 성능 (최근 1시간)                                          │ │
│ ├─────────────────────────────────────────────────────────────┤ │
│ │     Read TPS                   Write TPS                   │ │
│ │   ╭──────────╮               ╭──────────╮                  │ │
│ │   │    ╱╲    │               │   ╱╲╱╲  │                  │ │
│ │   │   ╱  ╲   │               │  ╱    ╲ │                  │ │
│ │   │__╱    ╲__│               │_╱      ╲│                  │ │
│ │   ╰──────────╯               ╰──────────╯                  │ │
│ │   Avg: 120 req/s             Avg: 45 req/s                 │ │
│ │   P95 Latency: 15ms          P95 Latency: 25ms             │ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ 복제 상태                                                   │ │
│ ├─────────────────────────────────────────────────────────────┤ │
│ │ ● Primary: seaweedfs-volume-1:8080                         │ │
│ │ ● Replica: seaweedfs-volume-2:8080 (Synced)                │ │
│ └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## 7. 구현 우선순위 및 일정

| Phase | 기능 | 우선순위 | 예상 기간 | 상태 |
|-------|------|----------|----------|------|
| **1.1** | Volume 페이지네이션 | 높음 | 3일 | ✅ 완료 (2026-01-19) |
| **1.2** | Collection별 통계 API | 높음 | 2일 | ✅ 완료 (2026-01-19) |
| **1.3** | 용량 임계값 경고 | 높음 | 1일 | ✅ 완료 (2026-01-19) |
| **2.1** | Volume 상세 정보 | 중간 | 3일 | ⬜ 대기 |
| **2.2** | Volume Server 상세 메트릭 | 중간 | 2일 | ⬜ 대기 |
| **3.1** | Prometheus 연동 | 중간 | 5일 | ⬜ 대기 (인프라 필요) |
| **3.2** | Grafana 대시보드 | 중간 | 2일 | ⬜ 대기 (인프라 필요) |
| **3.3** | Alert 규칙 설정 | 중간 | 2일 | ⬜ 대기 (인프라 필요) |
| **4.1** | 용량 예측 | 낮음 | 5일 | ⬜ 대기 |
| **4.2** | 자동 Volume 확장 | 낮음 | 3일 | ⬜ 대기 |

---

## 8. 참고 자료

- [SeaweedFS System Metrics](https://github.com/seaweedfs/seaweedfs/wiki/System-Metrics)
- [SeaweedFS Grafana Dashboard](https://github.com/seaweedfs/seaweedfs/blob/master/other/metrics/grafana_seaweedfs.json)
- [Volume Management](https://github.com/seaweedfs/seaweedfs/wiki/Volume-Management)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/naming/)

---

## 9. 관련 문서

- [07_모니터링_및_운영.md](./07_모니터링_및_운영.md) - 일반 SeaweedFS 모니터링 가이드
- [02_핵심_컴포넌트.md](./02_핵심_컴포넌트.md) - Master/Volume/Filer 아키텍처
- [10_MiniPACS_연동_가이드.md](./10_MiniPACS_연동_가이드.md) - SADO 프로젝트 연동

---

**요약**:
- **Phase 1 완료 (2026-01-19)**: Volume 페이지네이션, Collection 통계, 용량 경고 UI
- Phase 2: 추가 인프라 없이 상세 정보 기능 확장 가능
- Phase 3+: Prometheus/Grafana/Alertmanager 인프라 구축 후 진행

**Phase 1 구현 상세**:
| 기능 | BE 변경 | FE 변경 |
|------|---------|---------|
| Volume 페이지네이션 | `VolumePageResponse` DTO, `/volumes/page` 엔드포인트 | 필터/정렬/페이지네이션 UI |
| Collection 통계 | `CollectionStatsResponse` DTO, `/collections/stats` 엔드포인트 | Collection 카드 뷰 |
| 용량 경고 | - | 70%/85% 임계값 배너 |
