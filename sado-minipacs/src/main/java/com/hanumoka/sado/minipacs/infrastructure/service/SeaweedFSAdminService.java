package com.hanumoka.sado.minipacs.infrastructure.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanumoka.sado.common.exception.BusinessException;
import com.hanumoka.sado.minipacs.code.MiniPacsErrorCode;
import com.hanumoka.sado.minipacs.dto.request.seaweedfs.CreateVolumeRequest;
import com.hanumoka.sado.minipacs.dto.response.seaweedfs.ClusterStatusResponse;
import com.hanumoka.sado.minipacs.dto.response.seaweedfs.FilerEntryResponse;
import com.hanumoka.sado.minipacs.dto.response.seaweedfs.VolumeInfoResponse;
import com.hanumoka.sado.minipacs.infrastructure.config.SeaweedFSAdminProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
import java.util.List;

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
     * @return Volume 목록
     * @throws BusinessException SEAWEEDFS_UNAVAILABLE - SeaweedFS 서버 접근 불가
     * @throws BusinessException SEAWEEDFS_RESPONSE_PARSE_ERROR - 응답 파싱 실패
     */
    public List<VolumeInfoResponse> listVolumes() {
        log.info("Listing all volumes from SeaweedFS Master: {}", properties.getMasterUrl());

        try {
            String url = properties.getMasterUrl() + "/vol/status";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new BusinessException(
                    MiniPacsErrorCode.SEAWEEDFS_API_ERROR,
                    "Volume 목록 조회 실패: HTTP " + response.getStatusCode()
                );
            }

            return parseVolumesResponse(response.getBody());

        } catch (ResourceAccessException e) {
            log.error("SeaweedFS Master 서버에 접근할 수 없습니다: {}", properties.getMasterUrl(), e);
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
     * Volume 응답 파싱
     */
    private List<VolumeInfoResponse> parseVolumesResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            List<VolumeInfoResponse> volumes = new ArrayList<>();

            JsonNode volumesNode = root.path("Volumes");
            if (volumesNode.isArray()) {
                for (JsonNode volumeNode : volumesNode) {
                    VolumeInfoResponse volume = VolumeInfoResponse.builder()
                        .id(volumeNode.path("Id").asLong())
                        .size(volumeNode.path("Size").asLong())
                        .collection(volumeNode.path("Collection").asText(null))
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
     * Cluster 상태 조회
     *
     * @return Cluster 상태
     * @throws BusinessException SEAWEEDFS_UNAVAILABLE - SeaweedFS 서버 접근 불가
     */
    public ClusterStatusResponse getClusterStatus() {
        log.info("Getting cluster status from SeaweedFS Master: {}", properties.getMasterUrl());

        try {
            String url = properties.getMasterUrl() + "/cluster/status";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new BusinessException(
                    MiniPacsErrorCode.SEAWEEDFS_API_ERROR,
                    "Cluster 상태 조회 실패"
                );
            }

            return parseClusterStatusResponse(response.getBody());

        } catch (ResourceAccessException e) {
            log.error("SeaweedFS Master 서버에 접근할 수 없습니다", e);
            throw new BusinessException(
                MiniPacsErrorCode.SEAWEEDFS_UNAVAILABLE,
                "SeaweedFS 서버에 접근할 수 없습니다."
            );
        }
    }

    /**
     * Cluster 상태 응답 파싱
     */
    private ClusterStatusResponse parseClusterStatusResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // 1. Health 판단
            boolean isLeader = root.path("IsLeader").asBoolean(false);
            JsonNode topologyNode = root.path("Topology");
            ClusterStatusResponse.HealthStatus health = isLeader && topologyNode != null && !topologyNode.isMissingNode()
                ? ClusterStatusResponse.HealthStatus.HEALTHY
                : ClusterStatusResponse.HealthStatus.DEGRADED;

            // 2. Master 노드 정보
            List<ClusterStatusResponse.MasterNode> masters = new ArrayList<>();
            masters.add(ClusterStatusResponse.MasterNode.builder()
                .address(properties.getMasterUrl().replace("http://", ""))
                .isLeader(isLeader)
                .status("active")
                .build());

            // 3. Volume Server 정보 파싱
            List<ClusterStatusResponse.VolumeServerNode> volumeServers = new ArrayList<>();
            Long totalUsedSize = 0L;
            Long totalFreeSize = 0L;
            Integer totalVolumes = 0;

            // Topology → DataCenters → Racks → DataNodes 경로 탐색
            JsonNode dataCentersNode = topologyNode.path("DataCenters");
            if (dataCentersNode.isArray() && dataCentersNode.size() > 0) {
                for (JsonNode dcNode : dataCentersNode) {
                    JsonNode racksNode = dcNode.path("Racks");
                    if (racksNode.isArray()) {
                        for (JsonNode rackNode : racksNode) {
                            JsonNode dataNodesNode = rackNode.path("DataNodes");
                            if (dataNodesNode.isArray()) {
                                for (JsonNode dataNode : dataNodesNode) {
                                    // Volume 정보 추출
                                    String publicUrl = dataNode.path("PublicUrl").asText("");
                                    JsonNode volumesNode = dataNode.path("Volumes");

                                    Long usedDiskSize = volumesNode.path("UsedVolumeSize").asLong(0L);
                                    Long freeDiskSize = volumesNode.path("FreeVolumeSize").asLong(0L);
                                    Integer volumeCount = volumesNode.path("VolumeCount").asInt(0);

                                    // VolumeServerNode 생성
                                    volumeServers.add(ClusterStatusResponse.VolumeServerNode.builder()
                                        .address(publicUrl)
                                        .volumeCount(volumeCount)
                                        .usedDiskSize(usedDiskSize)
                                        .freeDiskSize(freeDiskSize)
                                        .status("active")
                                        .build());

                                    // 전체 용량 누적
                                    totalUsedSize += usedDiskSize;
                                    totalFreeSize += freeDiskSize;
                                    totalVolumes += volumeCount;
                                }
                            }
                        }
                    }
                }
            }

            // 4. Filer 정보 (간소화)
            List<ClusterStatusResponse.FilerNode> filers = new ArrayList<>();
            filers.add(ClusterStatusResponse.FilerNode.builder()
                .address(properties.getFilerUrl().replace("http://", ""))
                .status("active")
                .build());

            // 5. 응답 구성
            ClusterStatusResponse response = ClusterStatusResponse.builder()
                .health(health)
                .masters(masters)
                .volumeServers(volumeServers)
                .filers(filers)
                .totalVolumes(totalVolumes)
                .totalFiles(0L)  // 파일 개수는 별도 API 필요
                .totalUsedSize(totalUsedSize)
                .totalFreeSize(totalFreeSize)
                .totalCapacity(totalUsedSize + totalFreeSize)
                .build();

            log.info("Parsed cluster status: health={}, volumes={}, usedSize={}, freeSize={}, capacity={}",
                health, totalVolumes, totalUsedSize, totalFreeSize, response.getTotalCapacity());

            return response;

        } catch (Exception e) {
            log.error("Failed to parse cluster status response", e);
            throw new BusinessException(
                MiniPacsErrorCode.SEAWEEDFS_RESPONSE_PARSE_ERROR,
                "Cluster 상태 파싱에 실패했습니다."
            );
        }
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
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

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

                    FilerEntryResponse entry = FilerEntryResponse.builder()
                        .name(name.substring(name.lastIndexOf('/') + 1))
                        .isDirectory(isDir)
                        .size(size)
                        .modifiedTime(modifiedTime)
                        .mimeType(entryNode.path("Mime").asText("application/octet-stream"))
                        .fullPath(basePath.endsWith("/") ? basePath + name : basePath + "/" + name)
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
}
