package com.hanumoka.sado.minipacs.dto.response.seaweedfs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * SeaweedFS Cluster 상태 응답 DTO
 *
 * <p>SeaweedFS 클러스터 전체의 상태 및 구성 정보
 *
 * <p>다중 노드 환경을 지원하며, 각 노드의 상태와
 * 클러스터 전체의 Health 상태를 제공합니다.
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
     *   <li>WARNING: 일부 노드 다운 (서비스 가능)</li>
     *   <li>DEGRADED: Quorum 위험 또는 Filer 없음</li>
     *   <li>CRITICAL: Leader 없음 또는 Volume Server 없음</li>
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
     * 클러스터 통계 요약
     */
    private ClusterStats clusterStats;

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
     * Cluster Health 상태
     */
    public enum HealthStatus {
        /**
         * 모든 노드 정상 작동
         */
        HEALTHY,

        /**
         * 일부 노드 다운 (서비스 가능)
         */
        WARNING,

        /**
         * Quorum 위험 또는 Filer 없음
         */
        DEGRADED,

        /**
         * Leader 없음 또는 Volume Server 없음 (서비스 불가)
         */
        CRITICAL
    }

    /**
     * 노드 상태
     */
    public enum NodeStatus {
        UP, DOWN
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
         * 노드 이름 (설정에서 지정)
         *
         * <p>예: "master-1"
         */
        private String name;

        /**
         * Master 주소
         *
         * <p>예: "http://localhost:9333"
         */
        private String address;

        /**
         * Leader 여부
         */
        private Boolean isLeader;

        /**
         * 현재 Leader 주소
         *
         * <p>이 노드가 인식하는 Leader의 주소
         */
        private String leader;

        /**
         * 노드 상태
         */
        private NodeStatus status;

        /**
         * 에러 메시지 (DOWN 상태일 경우)
         */
        private String errorMessage;

        /**
         * 마지막 상태 체크 시간
         */
        private Instant lastChecked;
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
         * 노드 이름 (설정에서 지정)
         *
         * <p>예: "volume-1"
         */
        private String name;

        /**
         * Volume Server 주소
         *
         * <p>예: "http://localhost:8080"
         */
        private String address;

        /**
         * Volume 수
         */
        private Integer volumeCount;

        /**
         * 전체 디스크 용량 (bytes)
         */
        private Long totalDiskSpace;

        /**
         * 사용 중인 디스크 크기 (bytes)
         */
        private Long usedDiskSize;

        /**
         * 여유 디스크 크기 (bytes)
         */
        private Long freeDiskSize;

        /**
         * 노드 상태
         */
        private NodeStatus status;

        /**
         * 에러 메시지 (DOWN 상태일 경우)
         */
        private String errorMessage;

        /**
         * 마지막 상태 체크 시간
         */
        private Instant lastChecked;
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
         * 노드 이름 (설정에서 지정)
         *
         * <p>예: "filer-1"
         */
        private String name;

        /**
         * Filer 주소
         *
         * <p>예: "http://localhost:8888"
         */
        private String address;

        /**
         * 노드 상태
         */
        private NodeStatus status;

        /**
         * 에러 메시지 (DOWN 상태일 경우)
         */
        private String errorMessage;

        /**
         * 마지막 상태 체크 시간
         */
        private Instant lastChecked;
    }

    /**
     * 클러스터 통계 요약
     *
     * <p>모든 Volume Server를 합산한 클러스터 전체 통계
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterStats {
        /**
         * 전체 Volume 수
         */
        private Integer totalVolumeCount;

        /**
         * 전체 파일 수
         */
        private Long totalFileCount;

        /**
         * 전체 사용 용량 (bytes)
         */
        private Long totalUsedSpace;

        /**
         * 전체 용량 (bytes)
         */
        private Long totalCapacity;
    }
}
