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
     * Cluster 상태 조회
     *
     * <p>Master /cluster/status와 Volume Server /status를 조합하여 클러스터 상태를 조회합니다.
     *
     * @return Cluster 상태
     * @throws BusinessException SEAWEEDFS_UNAVAILABLE - SeaweedFS 서버 접근 불가
     */
    public ClusterStatusResponse getClusterStatus() {
        log.info("Getting cluster status from SeaweedFS");

        try {
            // 1. Master에서 isLeader 확인
            String clusterUrl = properties.getMasterUrl() + "/cluster/status";
            ResponseEntity<String> clusterResponse = restTemplate.getForEntity(clusterUrl, String.class);
            JsonNode clusterRoot = objectMapper.readTree(clusterResponse.getBody());
            boolean isLeader = clusterRoot.path("IsLeader").asBoolean(false);

            // 2. Volume Server에서 상세 정보 조회
            String volumeStatusUrl = properties.getVolumeUrl() + "/status";
            ResponseEntity<String> volumeResponse = restTemplate.getForEntity(volumeStatusUrl, String.class);
            JsonNode volumeRoot = objectMapper.readTree(volumeResponse.getBody());

            // 3. DiskStatuses에서 용량 추출
            JsonNode diskStatuses = volumeRoot.path("DiskStatuses");
            Long totalCapacity = 0L;
            Long usedSpace = 0L;
            Long freeSpace = 0L;
            if (diskStatuses.isArray() && diskStatuses.size() > 0) {
                JsonNode disk = diskStatuses.get(0);
                totalCapacity = disk.path("all").asLong(0);
                usedSpace = disk.path("used").asLong(0);
                freeSpace = disk.path("free").asLong(0);
            }

            // 4. Volumes 배열에서 Volume 수 및 파일 수 계산
            JsonNode volumes = volumeRoot.path("Volumes");
            int volumeCount = 0;
            long totalFiles = 0;
            long totalUsedVolumeSize = 0;
            if (volumes.isArray()) {
                volumeCount = volumes.size();
                for (JsonNode vol : volumes) {
                    totalFiles += vol.path("FileCount").asLong(0);
                    totalUsedVolumeSize += vol.path("Size").asLong(0);
                }
            }

            // 5. Health 판단: isLeader && Volume Server 응답 정상
            ClusterStatusResponse.HealthStatus health = isLeader && volumeCount > 0
                ? ClusterStatusResponse.HealthStatus.HEALTHY
                : ClusterStatusResponse.HealthStatus.DEGRADED;

            // 6. Master 노드 정보
            List<ClusterStatusResponse.MasterNode> masters = new ArrayList<>();
            masters.add(ClusterStatusResponse.MasterNode.builder()
                .address(properties.getMasterUrl().replace("http://", ""))
                .isLeader(isLeader)
                .status("active")
                .build());

            // 7. Volume Server 노드 정보
            List<ClusterStatusResponse.VolumeServerNode> volumeServers = new ArrayList<>();
            volumeServers.add(ClusterStatusResponse.VolumeServerNode.builder()
                .address(properties.getVolumeUrl().replace("http://", ""))
                .volumeCount(volumeCount)
                .usedDiskSize(usedSpace)
                .freeDiskSize(freeSpace)
                .status("active")
                .build());

            // 8. Filer 정보
            List<ClusterStatusResponse.FilerNode> filers = new ArrayList<>();
            filers.add(ClusterStatusResponse.FilerNode.builder()
                .address(properties.getFilerUrl().replace("http://", ""))
                .status("active")
                .build());

            // 9. 응답 구성
            ClusterStatusResponse response = ClusterStatusResponse.builder()
                .health(health)
                .masters(masters)
                .volumeServers(volumeServers)
                .filers(filers)
                .totalVolumes(volumeCount)
                .totalFiles(totalFiles)
                .totalUsedSize(usedSpace)
                .totalFreeSize(freeSpace)
                .totalCapacity(totalCapacity)
                .build();

            log.info("Cluster status: health={}, volumes={}, files={}, usedSize={}, freeSize={}, capacity={}",
                health, volumeCount, totalFiles, usedSpace, freeSpace, totalCapacity);

            return response;

        } catch (ResourceAccessException e) {
            log.error("SeaweedFS 서버에 접근할 수 없습니다", e);
            throw new BusinessException(
                MiniPacsErrorCode.SEAWEEDFS_UNAVAILABLE,
                "SeaweedFS 서버에 접근할 수 없습니다."
            );
        } catch (Exception e) {
            log.error("Failed to get cluster status", e);
            throw new BusinessException(
                MiniPacsErrorCode.SEAWEEDFS_RESPONSE_PARSE_ERROR,
                "Cluster 상태 조회에 실패했습니다."
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
