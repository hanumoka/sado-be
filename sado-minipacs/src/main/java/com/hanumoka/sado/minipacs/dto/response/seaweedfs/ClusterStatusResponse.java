package com.hanumoka.sado.minipacs.dto.response.seaweedfs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SeaweedFS Cluster 상태 응답 DTO
 *
 * <p>SeaweedFS 클러스터 전체의 상태 및 구성 정보
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterStatusResponse {

    /**
     * Cluster 전체 상태
     *
     * <p>값:
     * <ul>
     *   <li>HEALTHY: 모든 노드 정상</li>
     *   <li>DEGRADED: 일부 노드 비정상</li>
     *   <li>DOWN: 클러스터 다운</li>
     * </ul>
     */
    private HealthStatus health;

    /**
     * Master 서버 목록
     */
    private List<MasterNode> masters;

    /**
     * Volume 서버 목록
     */
    private List<VolumeServerNode> volumeServers;

    /**
     * Filer 서버 목록
     */
    private List<FilerNode> filers;

    /**
     * 전체 Volume 수
     */
    private Integer totalVolumes;

    /**
     * 전체 파일 수
     */
    private Long totalFiles;

    /**
     * 전체 사용 용량 (bytes)
     */
    private Long totalUsedSize;

    /**
     * 전체 여유 용량 (bytes)
     */
    private Long totalFreeSize;

    /**
     * 전체 용량 (bytes)
     *
     * <p>totalCapacity = totalUsedSize + totalFreeSize
     */
    private Long totalCapacity;

    /**
     * Cluster 상태
     */
    public enum HealthStatus {
        /**
         * 모든 노드 정상 작동
         */
        HEALTHY,

        /**
         * 일부 노드 비정상 (서비스는 가능)
         */
        DEGRADED,

        /**
         * Cluster 전체 다운
         */
        DOWN
    }

    /**
     * Master 노드 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MasterNode {
        /**
         * Master 주소
         *
         * <p>예: "localhost:9333"
         */
        private String address;

        /**
         * Leader 여부
         */
        private Boolean isLeader;

        /**
         * 상태
         *
         * <p>예: "active", "standby"
         */
        private String status;
    }

    /**
     * Volume Server 노드 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeServerNode {
        /**
         * Volume Server 주소
         *
         * <p>예: "localhost:8080"
         */
        private String address;

        /**
         * Volume 수
         */
        private Integer volumeCount;

        /**
         * 사용 중인 디스크 크기 (bytes)
         */
        private Long usedDiskSize;

        /**
         * 여유 디스크 크기 (bytes)
         */
        private Long freeDiskSize;

        /**
         * 상태
         *
         * <p>예: "active", "readonly", "offline"
         */
        private String status;
    }

    /**
     * Filer 노드 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilerNode {
        /**
         * Filer 주소
         *
         * <p>예: "localhost:8888"
         */
        private String address;

        /**
         * 상태
         *
         * <p>예: "active", "offline"
         */
        private String status;
    }
}
