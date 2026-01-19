package com.hanumoka.sado.minipacs.infrastructure.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import com.hanumoka.sado.minipacs.dto.request.seaweedfs.CreateVolumeRequest;
import com.hanumoka.sado.minipacs.dto.response.seaweedfs.ClusterStatusResponse;
import com.hanumoka.sado.minipacs.dto.response.seaweedfs.FilerEntryResponse;
import com.hanumoka.sado.minipacs.dto.response.seaweedfs.VolumeInfoResponse;
import com.hanumoka.sado.minipacs.dto.response.seaweedfs.VolumePageResponse;
import com.hanumoka.sado.minipacs.dto.response.seaweedfs.CollectionStatsResponse;
import com.hanumoka.sado.minipacs.infrastructure.config.SeaweedFSAdminProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SeaweedFS Admin 서비스
 *
 * <p>SeaweedFS 클러스터 관리 기능을 제공합니다.
 *
 * <p>주요 기능:
 * <ul>
 *   <li>Volume 조회/생성/삭제</li>
 *   <li>Cluster 상태 모니터링</li>
 *   <li>Filer 디렉토리 탐색</li>
 * </ul>
 */
@Service
@Slf4j
public class SeaweedFSAdminService {

    private final SeaweedFSAdminProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    /**
     * RestTemplate 생성 (타임아웃 설정)
     */
    public SeaweedFSAdminService(
        SeaweedFSAdminProperties properties,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        // Manual RestTemplate creation with timeout configuration
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectionTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 모든 Volume 조회
     *
     * <p>Volume Server /status API에서 Volume 목록을 조회합니다.
     * (Master /vol/status는 중첩 객체 구조로 반환하지만, Volume Server /status는 배열로 반환)
     *
     * @return Volume 목록
     * @throws BusinessException SEAWEEDFS_UNAVAILABLE - SeaweedFS 서버 접근 불가
     * @throws BusinessException SEAWEEDFS_RESPONSE_PARSE_ERROR - 응답 파싱 실패
     */
    public List<VolumeInfoResponse> listVolumes() {
        log.info("Listing all volumes from SeaweedFS Volume Server: {}", properties.getVolumeUrl());

        try {
            // Volume Server /status API 사용 (Volumes가 배열로 반환됨)
            String url = properties.getVolumeUrl() + "/status";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new BusinessException(
                    MiniPacsErrorCode.SEAWEEDFS_API_ERROR,
                    "Volume 목록 조회 실패: HTTP " + response.getStatusCode()
                );
            }

            return parseVolumesResponse(response.getBody());

        } catch (ResourceAccessException e) {
            log.error("SeaweedFS Volume 서버에 접근할 수 없습니다: {}", properties.getVolumeUrl(), e);
            throw new BusinessException(
                MiniPacsErrorCode.SEAWEEDFS_UNAVAILABLE,
                "SeaweedFS 서버에 접근할 수 없습니다."
            );
        } catch (HttpClientErrorException e) {
            log.error("SeaweedFS API 호출 실패: {}", e.getMessage(), e);
            throw new BusinessException(
                MiniPacsErrorCode.SEAWEEDFS_API_ERROR,
                "SeaweedFS API 호출에 실패했습니다: " + e.getMessage()
            );
        }
    }

    /**
     * Volume 목록 조회 (페이징 + 필터링)
     *
     * <p>SeaweedFS는 페이징 API를 제공하지 않으므로,
     * 전체 데이터를 조회 후 메모리에서 필터링/페이징 수행
     *
     * <p>주의: Volume 수가 매우 많은 경우(1000+) 성능 고려 필요
     *
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기
     * @param collection Collection 필터 (null이면 전체)
     * @param status 상태 필터 (null이면 전체)
     * @param sortBy 정렬 기준: id, size, fileCount, usedSize, collection
     * @param order 정렬 순서: asc, desc
     * @return 페이징된 Volume 목록
     */
    public VolumePageResponse listVolumesPaged(
            int page,
            int size,
            String collection,
            String status,
            String sortBy,
            String order
    ) {
        // 1. 전체 Volume 조회
        List<VolumeInfoResponse> allVolumes = listVolumes();

        // 2. 사용 가능한 Collection 목록 추출 (필터 드롭다운용)
        List<String> availableCollections = allVolumes.stream()
                .map(VolumeInfoResponse::getCollection)
                .filter(c -> c != null && !c.isEmpty())
                .distinct()
                .sorted()
                .toList();

        // 3. 필터링
        List<VolumeInfoResponse> filtered = allVolumes.stream()
                .filter(v -> collection == null || collection.isEmpty() || collection.equals(v.getCollection()))
                .filter(v -> status == null || status.isEmpty() || status.equals(v.getStatus()))
                .toList();

        // 4. 정렬
        Comparator<VolumeInfoResponse> comparator = getVolumeComparator(sortBy);
        if ("desc".equalsIgnoreCase(order)) {
            comparator = comparator.reversed();
        }

        List<VolumeInfoResponse> sorted = filtered.stream()
                .sorted(comparator)
                .toList();

        // 5. 페이징
        int totalElements = sorted.size();
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<VolumeInfoResponse> pageContent;
        if (fromIndex >= totalElements) {
            pageContent = List.of();
        } else {
            pageContent = sorted.subList(fromIndex, toIndex);
        }

        log.debug("Volume pagination: page={}, size={}, filtered={}, total={}",
                page, size, pageContent.size(), totalElements);

        return VolumePageResponse.of(pageContent, page, size, totalElements, availableCollections);
    }

    /**
     * Volume 정렬 Comparator 반환
     *
     * @param sortBy 정렬 기준
     * @return Comparator
     */
    private Comparator<VolumeInfoResponse> getVolumeComparator(String sortBy) {
        return switch (sortBy.toLowerCase()) {
            case "size" -> Comparator.comparing(
                    VolumeInfoResponse::getSize,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "filecount" -> Comparator.comparing(
                    VolumeInfoResponse::getFileCount,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "usedsize" -> Comparator.comparing(
                    VolumeInfoResponse::getUsedSize,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "collection" -> Comparator.comparing(
                    VolumeInfoResponse::getCollection,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            default -> Comparator.comparing(
                    VolumeInfoResponse::getId,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        };
    }

    /**
     * Collection별 통계 조회
     *
     * <p>모든 Volume을 Collection별로 그룹화하여 통계를 집계합니다.
     *
     * @return Collection별 통계 목록 (사용량 내림차순 정렬)
     */
    public List<CollectionStatsResponse> getCollectionStats() {
        // 1. 전체 Volume 조회
        List<VolumeInfoResponse> allVolumes = listVolumes();

        // 2. Collection별로 그룹화 (null/empty는 "(default)"로 처리)
        Map<String, List<VolumeInfoResponse>> groupedByCollection = allVolumes.stream()
                .collect(Collectors.groupingBy(
                        v -> (v.getCollection() == null || v.getCollection().isEmpty())
                                ? "(default)"
                                : v.getCollection()
                ));

        // 3. 각 그룹별 통계 집계
        List<CollectionStatsResponse> stats = groupedByCollection.entrySet().stream()
                .map(entry -> {
                    String collectionName = entry.getKey();
                    List<VolumeInfoResponse> volumes = entry.getValue();

                    long totalFileCount = volumes.stream()
                            .mapToLong(v -> v.getFileCount() != null ? v.getFileCount() : 0)
                            .sum();

                    long totalUsedSize = volumes.stream()
                            .mapToLong(v -> v.getUsedSize() != null ? v.getUsedSize() : 0)
                            .sum();

                    long totalSize = volumes.stream()
                            .mapToLong(v -> v.getSize() != null ? v.getSize() : 0)
                            .sum();

                    int readWriteCount = (int) volumes.stream()
                            .filter(v -> "ReadWrite".equals(v.getStatus()))
                            .count();

                    int readOnlyCount = (int) volumes.stream()
                            .filter(v -> "ReadOnly".equals(v.getStatus()))
                            .count();

                    List<Long> volumeIds = volumes.stream()
                            .map(VolumeInfoResponse::getId)
                            .sorted()
                            .toList();

                    return CollectionStatsResponse.builder()
                            .collection(collectionName)
                            .volumeCount(volumes.size())
                            .totalFileCount(totalFileCount)
                            .totalUsedSize(totalUsedSize)
                            .totalSize(totalSize)
                            .readWriteCount(readWriteCount)
                            .readOnlyCount(readOnlyCount)
                            .volumeIds(volumeIds)
                            .build();
                })
                .sorted(Comparator.comparing(CollectionStatsResponse::getTotalUsedSize).reversed())
                .toList();

        log.debug("Collection stats calculated: {} collections from {} volumes",
                stats.size(), allVolumes.size());

        return stats;
    }

    /**
     * Volume 응답 파싱
     *
     * <p>Volume Server /status 응답의 Volumes 배열을 파싱합니다.
     */
    private List<VolumeInfoResponse> parseVolumesResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            List<VolumeInfoResponse> volumes = new ArrayList<>();

            JsonNode volumesNode = root.path("Volumes");
            if (volumesNode.isArray()) {
                for (JsonNode volumeNode : volumesNode) {
                    // Collection이 빈 문자열인 경우 null 처리
                    String collection = volumeNode.path("Collection").asText("");
                    if (collection.isEmpty()) {
                        collection = null;
                    }

                    VolumeInfoResponse volume = VolumeInfoResponse.builder()
                        .id(volumeNode.path("Id").asLong())
                        .size(volumeNode.path("Size").asLong())
                        .collection(collection)
                        .replication(volumeNode.path("ReplicaPlacement").asText("000"))
                        .status(volumeNode.path("ReadOnly").asBoolean() ? "ReadOnly" : "ReadWrite")
                        .fileCount(volumeNode.path("FileCount").asLong(0))
                        .usedSize(volumeNode.path("Size").asLong(0))
                        .serverUrl(properties.getVolumeUrl())
                        .build();

                    volumes.add(volume);
                }
            }

            log.info("Parsed {} volumes from response", volumes.size());
            return volumes;

        } catch (Exception e) {
            log.error("Failed to parse volumes response", e);
            throw new BusinessException(
                MiniPacsErrorCode.SEAWEEDFS_RESPONSE_PARSE_ERROR,
                "SeaweedFS 응답 파싱에 실패했습니다."
            );
        }
    }

    /**
     * Cluster 상태 조회 (다중 노드 지원)
     *
     * <p>설정된 모든 Master/Volume/Filer 노드의 상태를 조회하고
     * 클러스터 전체의 Health 상태를 계산합니다.
     *
     * <p>Volume Server 조회는 병렬로 처리되어 성능을 최적화합니다.
     *
     * @return Cluster 상태
     */
    public ClusterStatusResponse getClusterStatus() {
        log.info("Getting cluster status from SeaweedFS (multi-node)");

        List<ClusterStatusResponse.MasterNode> masterNodes = new ArrayList<>();
        List<ClusterStatusResponse.VolumeServerNode> volumeServerNodes = new ArrayList<>();
        List<ClusterStatusResponse.FilerNode> filerNodes = new ArrayList<>();

        // 1. 모든 Master 노드 순회
        for (SeaweedFSAdminProperties.NodeConfig master : properties.getEffectiveMasters()) {
            if (!master.isEnabled()) continue;
            ClusterStatusResponse.MasterNode node = checkMasterNode(master);
            masterNodes.add(node);
        }

        // 2. 모든 Volume Server 순회 (병렬 처리)
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<CompletableFuture<ClusterStatusResponse.VolumeServerNode>> volumeFutures =
                properties.getEffectiveVolumeServers().stream()
                    .filter(SeaweedFSAdminProperties.NodeConfig::isEnabled)
                    .map(vs -> CompletableFuture.supplyAsync(() -> checkVolumeServer(vs), executor))
                    .toList();

            volumeServerNodes = volumeFutures.stream()
                .map(CompletableFuture::join)
                .toList();
        } finally {
            executor.shutdown();
        }

        // 3. 모든 Filer 순회
        for (SeaweedFSAdminProperties.NodeConfig filer : properties.getEffectiveFilers()) {
            if (!filer.isEnabled()) continue;
            ClusterStatusResponse.FilerNode node = checkFilerNode(filer);
            filerNodes.add(node);
        }

        // 4. 클러스터 전체 Health 계산
        ClusterStatusResponse.HealthStatus health = calculateClusterHealth(
            masterNodes, volumeServerNodes, filerNodes
        );

        // 5. 클러스터 통계 집계
        ClusterStatusResponse.ClusterStats clusterStats = aggregateStats(volumeServerNodes);

        // 6. 응답 구성 (하위 호환성을 위해 기존 필드도 유지)
        ClusterStatusResponse response = ClusterStatusResponse.builder()
            .health(health)
            .masters(masterNodes)
            .volumeServers(volumeServerNodes)
            .filers(filerNodes)
            .clusterStats(clusterStats)
            .totalVolumes(clusterStats.getTotalVolumeCount())
            .totalFiles(clusterStats.getTotalFileCount())
            .totalUsedSize(clusterStats.getTotalUsedSpace())
            .totalFreeSize(clusterStats.getTotalCapacity() - clusterStats.getTotalUsedSpace())
            .totalCapacity(clusterStats.getTotalCapacity())
            .build();

        log.info("Cluster status: health={}, masters={}/{}, volumeServers={}/{}, filers={}/{}",
            health,
            masterNodes.stream().filter(m -> m.getStatus() == ClusterStatusResponse.NodeStatus.UP).count(),
            masterNodes.size(),
            volumeServerNodes.stream().filter(v -> v.getStatus() == ClusterStatusResponse.NodeStatus.UP).count(),
            volumeServerNodes.size(),
            filerNodes.stream().filter(f -> f.getStatus() == ClusterStatusResponse.NodeStatus.UP).count(),
            filerNodes.size()
        );

        return response;
    }

    /**
     * 개별 Master 노드 상태 체크
     *
     * @param config 노드 설정
     * @return Master 노드 상태
     */
    private ClusterStatusResponse.MasterNode checkMasterNode(SeaweedFSAdminProperties.NodeConfig config) {
        try {
            String statusUrl = config.getUrl() + "/cluster/status";
            ResponseEntity<String> response = restTemplate.getForEntity(statusUrl, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            return ClusterStatusResponse.MasterNode.builder()
                .name(config.getName())
                .address(config.getUrl())
                .isLeader(root.path("IsLeader").asBoolean(false))
                .leader(root.path("Leader").asText())
                .status(ClusterStatusResponse.NodeStatus.UP)
                .lastChecked(Instant.now())
                .build();
        } catch (Exception e) {
            log.warn("Master node {} is down: {}", config.getName(), e.getMessage());
            return ClusterStatusResponse.MasterNode.builder()
                .name(config.getName())
                .address(config.getUrl())
                .isLeader(false)
                .status(ClusterStatusResponse.NodeStatus.DOWN)
                .errorMessage(e.getMessage())
                .lastChecked(Instant.now())
                .build();
        }
    }

    /**
     * 개별 Volume Server 노드 상태 체크
     *
     * @param config 노드 설정
     * @return Volume Server 노드 상태
     */
    private ClusterStatusResponse.VolumeServerNode checkVolumeServer(SeaweedFSAdminProperties.NodeConfig config) {
        try {
            String statusUrl = config.getUrl() + "/status";
            ResponseEntity<String> response = restTemplate.getForEntity(statusUrl, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            // DiskStatuses에서 용량 추출
            JsonNode diskStatuses = root.path("DiskStatuses");
            long totalSize = 0, freeSize = 0, usedSize = 0;
            if (diskStatuses.isArray()) {
                for (JsonNode disk : diskStatuses) {
                    totalSize += disk.path("all").asLong(0);
                    freeSize += disk.path("free").asLong(0);
                    usedSize += disk.path("used").asLong(0);
                }
            }

            // Volumes 배열에서 Volume 수 계산
            JsonNode volumes = root.path("Volumes");
            int volumeCount = volumes.isArray() ? volumes.size() : 0;

            return ClusterStatusResponse.VolumeServerNode.builder()
                .name(config.getName())
                .address(config.getUrl())
                .volumeCount(volumeCount)
                .totalDiskSpace(totalSize)
                .usedDiskSize(usedSize)
                .freeDiskSize(freeSize)
                .status(ClusterStatusResponse.NodeStatus.UP)
                .lastChecked(Instant.now())
                .build();
        } catch (Exception e) {
            log.warn("Volume server {} is down: {}", config.getName(), e.getMessage());
            return ClusterStatusResponse.VolumeServerNode.builder()
                .name(config.getName())
                .address(config.getUrl())
                .volumeCount(0)
                .totalDiskSpace(0L)
                .usedDiskSize(0L)
                .freeDiskSize(0L)
                .status(ClusterStatusResponse.NodeStatus.DOWN)
                .errorMessage(e.getMessage())
                .lastChecked(Instant.now())
                .build();
        }
    }

    /**
     * 개별 Filer 노드 상태 체크
     *
     * @param config 노드 설정
     * @return Filer 노드 상태
     */
    private ClusterStatusResponse.FilerNode checkFilerNode(SeaweedFSAdminProperties.NodeConfig config) {
        try {
            // Filer health check - 루트 디렉토리 접근 테스트
            String checkUrl = config.getUrl() + "/?pretty=y";
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            restTemplate.exchange(checkUrl, HttpMethod.GET, requestEntity, String.class);

            return ClusterStatusResponse.FilerNode.builder()
                .name(config.getName())
                .address(config.getUrl())
                .status(ClusterStatusResponse.NodeStatus.UP)
                .lastChecked(Instant.now())
                .build();
        } catch (Exception e) {
            log.warn("Filer node {} is down: {}", config.getName(), e.getMessage());
            return ClusterStatusResponse.FilerNode.builder()
                .name(config.getName())
                .address(config.getUrl())
                .status(ClusterStatusResponse.NodeStatus.DOWN)
                .errorMessage(e.getMessage())
                .lastChecked(Instant.now())
                .build();
        }
    }

    /**
     * 클러스터 Health 상태 계산
     *
     * <p>계산 로직:
     * <ul>
     *   <li>CRITICAL: Leader 없음 또는 Volume Server 전체 다운</li>
     *   <li>DEGRADED: Master 과반 다운 또는 Filer 전체 다운</li>
     *   <li>WARNING: 일부 노드 다운 (서비스 가능)</li>
     *   <li>HEALTHY: 모든 노드 정상</li>
     * </ul>
     *
     * @param masters Master 노드 목록
     * @param volumeServers Volume Server 노드 목록
     * @param filers Filer 노드 목록
     * @return 클러스터 Health 상태
     */
    private ClusterStatusResponse.HealthStatus calculateClusterHealth(
        List<ClusterStatusResponse.MasterNode> masters,
        List<ClusterStatusResponse.VolumeServerNode> volumeServers,
        List<ClusterStatusResponse.FilerNode> filers
    ) {
        // Master: Leader가 1개 존재해야 함
        long activeMasters = masters.stream()
            .filter(m -> m.getStatus() == ClusterStatusResponse.NodeStatus.UP)
            .count();
        boolean hasLeader = masters.stream()
            .anyMatch(m -> m.getStatus() == ClusterStatusResponse.NodeStatus.UP && Boolean.TRUE.equals(m.getIsLeader()));

        // Volume Server: 최소 1개 정상
        long activeVolumes = volumeServers.stream()
            .filter(v -> v.getStatus() == ClusterStatusResponse.NodeStatus.UP)
            .count();

        // Filer: 최소 1개 정상
        long activeFilers = filers.stream()
            .filter(f -> f.getStatus() == ClusterStatusResponse.NodeStatus.UP)
            .count();

        // CRITICAL: Leader 없음 또는 Volume Server 전체 다운
        if (!hasLeader || activeVolumes == 0) {
            return ClusterStatusResponse.HealthStatus.CRITICAL;
        }

        // DEGRADED: Master 과반 다운 또는 Filer 전체 다운
        if (activeMasters < (masters.size() / 2) + 1 || activeFilers == 0) {
            return ClusterStatusResponse.HealthStatus.DEGRADED;
        }

        // WARNING: 일부 노드 다운
        if (activeMasters < masters.size() ||
            activeVolumes < volumeServers.size() ||
            activeFilers < filers.size()) {
            return ClusterStatusResponse.HealthStatus.WARNING;
        }

        // HEALTHY: 모든 노드 정상
        return ClusterStatusResponse.HealthStatus.HEALTHY;
    }

    /**
     * Volume Server 통계 집계
     *
     * @param volumeServers Volume Server 노드 목록
     * @return 집계된 클러스터 통계
     */
    private ClusterStatusResponse.ClusterStats aggregateStats(
        List<ClusterStatusResponse.VolumeServerNode> volumeServers
    ) {
        int totalVolumeCount = 0;
        long totalUsedSpace = 0;
        long totalCapacity = 0;

        for (ClusterStatusResponse.VolumeServerNode vs : volumeServers) {
            if (vs.getStatus() == ClusterStatusResponse.NodeStatus.UP) {
                totalVolumeCount += vs.getVolumeCount();
                totalUsedSpace += vs.getUsedDiskSize();
                totalCapacity += vs.getTotalDiskSpace();
            }
        }

        // 파일 수는 별도 API 호출이 필요하므로 0으로 설정
        // 필요시 Master /dir/status API로 조회 가능
        return ClusterStatusResponse.ClusterStats.builder()
            .totalVolumeCount(totalVolumeCount)
            .totalFileCount(0L)  // 별도 조회 필요
            .totalUsedSpace(totalUsedSpace)
            .totalCapacity(totalCapacity)
            .build();
    }

    /**
     * Filer 디렉토리 목록 조회
     *
     * @param path 디렉토리 경로 (예: "/minipacs", "/")
     * @return 파일/디렉토리 목록
     * @throws BusinessException SEAWEEDFS_UNAVAILABLE - SeaweedFS 서버 접근 불가
     */
    public List<FilerEntryResponse> listFilerDirectory(String path) {
        log.info("Listing filer directory: {} from {}", path, properties.getFilerUrl());

        try {
            String url = properties.getFilerUrl() + path + "?pretty=y";

            // SeaweedFS Filer는 Accept 헤더가 없으면 HTML을 반환하므로 명시적으로 JSON 요청
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                String.class
            );

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new BusinessException(
                    MiniPacsErrorCode.SEAWEEDFS_API_ERROR,
                    "Filer 디렉토리 조회 실패"
                );
            }

            return parseFilerDirectoryResponse(response.getBody(), path);

        } catch (ResourceAccessException e) {
            log.error("SeaweedFS Filer 서버에 접근할 수 없습니다", e);
            throw new BusinessException(
                MiniPacsErrorCode.SEAWEEDFS_UNAVAILABLE,
                "SeaweedFS Filer 서버에 접근할 수 없습니다."
            );
        }
    }

    /**
     * Filer 디렉토리 응답 파싱
     */
    private List<FilerEntryResponse> parseFilerDirectoryResponse(String responseBody, String basePath) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            List<FilerEntryResponse> entries = new ArrayList<>();

            JsonNode entriesNode = root.path("Entries");
            if (entriesNode.isArray()) {
                for (JsonNode entryNode : entriesNode) {
                    String name = entryNode.path("FullPath").asText();
                    if (name.isEmpty()) {
                        name = entryNode.path("Name").asText();
                    }

                    boolean isDir = entryNode.path("Mode").asLong() > 0x80000000L;
                    long size = entryNode.path("chunks").isArray()
                        ? entryNode.path("chunks").get(0).path("size").asLong(0)
                        : 0;

                    long mtimeSec = entryNode.path("Mtime").asLong(0);
                    LocalDateTime modifiedTime = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(mtimeSec),
                        ZoneId.systemDefault()
                    );

                    // SeaweedFS FullPath는 이미 절대 경로이므로 그대로 사용
                    String fullPath = entryNode.path("FullPath").asText();

                    FilerEntryResponse entry = FilerEntryResponse.builder()
                        .name(name.substring(name.lastIndexOf('/') + 1))
                        .isDirectory(isDir)
                        .size(size)
                        .modifiedTime(modifiedTime)
                        .mimeType(entryNode.path("Mime").asText("application/octet-stream"))
                        .fullPath(fullPath)
                        .build();

                    entries.add(entry);
                }
            }

            log.info("Parsed {} entries from filer directory {}", entries.size(), basePath);
            return entries;

        } catch (Exception e) {
            log.error("Failed to parse filer directory response", e);
            throw new BusinessException(
                MiniPacsErrorCode.SEAWEEDFS_RESPONSE_PARSE_ERROR,
                "Filer 디렉토리 응답 파싱에 실패했습니다."
            );
        }
    }

    /**
     * Volume 생성
     *
     * @param request Volume 생성 요청
     * @return 생성 결과 메시지
     * @throws BusinessException VOLUME_CREATE_FAILED - Volume 생성 실패
     */
    public String createVolumes(CreateVolumeRequest request) {
        log.info("Creating {} volumes: collection={}, replication={}",
                request.getCount(), request.getCollection(), request.getReplication());

        try {
            StringBuilder urlBuilder = new StringBuilder(properties.getMasterUrl() + "/vol/grow");
            urlBuilder.append("?count=").append(request.getCount());

            if (request.getCollection() != null) {
                urlBuilder.append("&collection=").append(request.getCollection());
            }
            if (request.getReplication() != null) {
                urlBuilder.append("&replication=").append(request.getReplication());
            }
            if (request.getTtl() != null) {
                urlBuilder.append("&ttl=").append(request.getTtl());
            }
            if (request.getDataCenter() != null) {
                urlBuilder.append("&dataCenter=").append(request.getDataCenter());
            }

            String url = urlBuilder.toString();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                throw new BusinessException(
                    MiniPacsErrorCode.VOLUME_CREATE_FAILED,
                    "Volume 생성 실패: HTTP " + response.getStatusCode()
                );
            }

            log.info("Volumes created successfully: {}", response.getBody());
            return response.getBody();

        } catch (ResourceAccessException e) {
            log.error("SeaweedFS Master 서버에 접근할 수 없습니다", e);
            throw new BusinessException(
                MiniPacsErrorCode.SEAWEEDFS_UNAVAILABLE,
                "SeaweedFS 서버에 접근할 수 없습니다."
            );
        } catch (HttpClientErrorException e) {
            log.error("Volume 생성 API 호출 실패", e);
            throw new BusinessException(
                MiniPacsErrorCode.VOLUME_CREATE_FAILED,
                "Volume 생성에 실패했습니다: " + e.getMessage()
            );
        }
    }

    /**
     * Volume 삭제
     *
     * <p>주의: Volume은 비어있어야 삭제 가능
     *
     * @param volumeId Volume ID
     * @throws BusinessException VOLUME_DELETE_FAILED - Volume 삭제 실패
     */
    public void deleteVolume(Long volumeId) {
        log.warn("Deleting volume: {}", volumeId);

        try {
            String url = properties.getMasterUrl() + "/vol/vacuum?volumeId=" + volumeId;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                throw new BusinessException(
                    MiniPacsErrorCode.VOLUME_DELETE_FAILED,
                    "Volume 삭제 실패: HTTP " + response.getStatusCode()
                );
            }

            log.info("Volume deleted successfully: volumeId={}", volumeId);

        } catch (ResourceAccessException e) {
            log.error("SeaweedFS Master 서버에 접근할 수 없습니다", e);
            throw new BusinessException(
                MiniPacsErrorCode.SEAWEEDFS_UNAVAILABLE,
                "SeaweedFS 서버에 접근할 수 없습니다."
            );
        } catch (HttpClientErrorException e) {
            log.error("Volume 삭제 API 호출 실패", e);
            throw new BusinessException(
                MiniPacsErrorCode.VOLUME_DELETE_FAILED,
                "Volume 삭제에 실패했습니다. Volume이 비어있는지 확인하세요."
            );
        }
    }

    /**
     * Volume Server 디스크 용량 조회
     *
     * @return 디스크 용량 정보 [totalCapacity, usedSpace, freeSpace]
     * @throws BusinessException SEAWEEDFS_UNAVAILABLE - SeaweedFS 서버 접근 불가
     */
    public Long[] getVolumeServerDiskCapacity() {
        log.info("Getting disk capacity from SeaweedFS Volume Server: {}", properties.getVolumeUrl());

        try {
            String url = properties.getVolumeUrl() + "/status";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new BusinessException(
                    MiniPacsErrorCode.SEAWEEDFS_API_ERROR,
                    "Volume Server 상태 조회 실패"
                );
            }

            return parseVolumeServerDiskCapacity(response.getBody());

        } catch (ResourceAccessException e) {
            log.error("SeaweedFS Volume 서버에 접근할 수 없습니다", e);
            throw new BusinessException(
                MiniPacsErrorCode.SEAWEEDFS_UNAVAILABLE,
                "SeaweedFS 서버에 접근할 수 없습니다."
            );
        }
    }

    /**
     * Volume Server 응답에서 디스크 용량 파싱
     *
     * @param responseBody Volume Server /status 응답
     * @return [totalCapacity, usedSpace, freeSpace]
     */
    private Long[] parseVolumeServerDiskCapacity(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // DiskStatuses 배열에서 첫 번째 디스크 정보 추출
            JsonNode diskStatusesNode = root.path("DiskStatuses");
            if (diskStatusesNode.isArray() && diskStatusesNode.size() > 0) {
                JsonNode diskStatus = diskStatusesNode.get(0);

                Long totalCapacity = diskStatus.path("all").asLong(0L);
                Long usedSpace = diskStatus.path("used").asLong(0L);
                Long freeSpace = diskStatus.path("free").asLong(0L);

                log.info("Disk capacity: total={}, used={}, free={}", totalCapacity, usedSpace, freeSpace);

                return new Long[] { totalCapacity, usedSpace, freeSpace };
            }

            log.warn("No DiskStatuses found in response");
            return new Long[] { 0L, 0L, 0L };

        } catch (Exception e) {
            log.error("Failed to parse volume server status response", e);
            throw new BusinessException(
                MiniPacsErrorCode.SEAWEEDFS_RESPONSE_PARSE_ERROR,
                "Volume Server 응답 파싱에 실패했습니다."
            );
        }
    }

    // ============================================================
    // Filer 파일 삭제/다운로드
    // ============================================================

    /**
     * Filer 파일 삭제
     *
     * <p>SeaweedFS Filer에서 파일을 삭제합니다.
     *
     * @param path 삭제할 파일 경로 (예: "/buckets/minipacs/studies/.../instance.dcm")
     * @throws BusinessException FILE_NOT_FOUND - 파일이 존재하지 않음
     * @throws BusinessException STORAGE_DELETE_FAILED - 삭제 실패
     */
    public void deleteFilerFile(String path) {
        log.warn("Deleting file from Filer: {}", path);

        try {
            String url = properties.getFilerUrl() + path;

            restTemplate.delete(url);

            log.info("File deleted successfully from Filer: {}", path);

        } catch (HttpClientErrorException.NotFound e) {
            log.error("File not found in Filer: {}", path);
            throw new BusinessException(
                MiniPacsErrorCode.FILE_NOT_FOUND,
                "파일을 찾을 수 없습니다: " + path
            );
        } catch (ResourceAccessException e) {
            log.error("SeaweedFS Filer 서버에 접근할 수 없습니다", e);
            throw new BusinessException(
                MiniPacsErrorCode.SEAWEEDFS_UNAVAILABLE,
                "SeaweedFS Filer 서버에 접근할 수 없습니다."
            );
        } catch (Exception e) {
            log.error("Failed to delete file from Filer: {}", path, e);
            throw new BusinessException(
                MiniPacsErrorCode.STORAGE_DELETE_FAILED,
                "파일 삭제에 실패했습니다: " + path
            );
        }
    }

    /**
     * Filer 파일 다운로드 URL 생성
     *
     * <p>SeaweedFS Filer의 파일 다운로드 URL을 반환합니다.
     * <p>주의: 이 URL은 직접 접근 가능한 URL로, 인증이 필요한 경우 별도 처리 필요
     *
     * @param path 파일 경로 (예: "/buckets/minipacs/studies/.../instance.dcm")
     * @return 다운로드 URL
     */
    public String getFilerFileDownloadUrl(String path) {
        // Filer URL + 파일 경로
        return properties.getFilerUrl() + path;
    }

    /**
     * Filer URL 반환 (Frontend에서 직접 접근용)
     *
     * @return Filer base URL
     */
    public String getFilerUrl() {
        return properties.getFilerUrl();
    }
}
