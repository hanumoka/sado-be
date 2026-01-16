package com.hanumoka.sado.minipacs.infrastructure.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * SeaweedFS Admin API 설정
 *
 * <p>SeaweedFS 클러스터 관리를 위한 Master/Volume/Filer 엔드포인트 설정
 *
 * <p>단일 노드 설정 (하위 호환성):
 * <pre>
 * seaweedfs:
 *   admin:
 *     master-url: http://localhost:9333
 *     volume-url: http://localhost:8080
 *     filer-url: http://localhost:8888
 *     connection-timeout: 5000
 *     read-timeout: 30000
 * </pre>
 *
 * <p>다중 노드 설정 (권장):
 * <pre>
 * seaweedfs:
 *   admin:
 *     masters:
 *       - name: master-1
 *         url: http://seaweed-master-1:9333
 *       - name: master-2
 *         url: http://seaweed-master-2:9333
 *     volume-servers:
 *       - name: volume-1
 *         url: http://seaweed-volume-1:8080
 *     filers:
 *       - name: filer-1
 *         url: http://seaweed-filer-1:8888
 * </pre>
 *
 * <p>SeaweedFS 컴포넌트:
 * <ul>
 *   <li>Master: 메타데이터 관리, Volume 할당 (포트 9333)</li>
 *   <li>Volume Server: 실제 파일 저장 (포트 8080)</li>
 *   <li>Filer: 파일 시스템 인터페이스 (포트 8888)</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "seaweedfs.admin")
@Getter
@Setter
public class SeaweedFSAdminProperties {

    // ============================================================
    // 기존 단일 노드 설정 (하위 호환성 유지)
    // ============================================================

    /**
     * Master 서버 URL (단일 노드)
     *
     * <p>기본값: http://localhost:9333
     *
     * <p>용도: Volume 관리, Cluster 상태 조회
     */
    private String masterUrl = "http://localhost:9333";

    /**
     * Volume 서버 URL (단일 노드)
     *
     * <p>기본값: http://localhost:8080
     *
     * <p>용도: Volume 상세 정보 조회
     */
    private String volumeUrl = "http://localhost:8080";

    /**
     * Filer 서버 URL (단일 노드)
     *
     * <p>기본값: http://localhost:8888
     *
     * <p>용도: 디렉토리 탐색, 파일 목록 조회
     */
    private String filerUrl = "http://localhost:8888";

    // ============================================================
    // 다중 노드 설정 (신규)
    // ============================================================

    /**
     * Master 노드 목록 (다중 노드)
     *
     * <p>설정 시 masterUrl보다 우선 적용
     */
    private List<NodeConfig> masters = new ArrayList<>();

    /**
     * Volume Server 노드 목록 (다중 노드)
     *
     * <p>설정 시 volumeUrl보다 우선 적용
     */
    private List<NodeConfig> volumeServers = new ArrayList<>();

    /**
     * Filer 노드 목록 (다중 노드)
     *
     * <p>설정 시 filerUrl보다 우선 적용
     */
    private List<NodeConfig> filers = new ArrayList<>();

    // ============================================================
    // 타임아웃 설정
    // ============================================================

    /**
     * 연결 타임아웃 (밀리초)
     *
     * <p>기본값: 5000ms (5초)
     */
    private int connectionTimeout = 5000;

    /**
     * 읽기 타임아웃 (밀리초)
     *
     * <p>기본값: 30000ms (30초)
     */
    private int readTimeout = 30000;

    // ============================================================
    // 하위 호환성 메서드: 신규 설정이 없으면 기존 단일 URL 사용
    // ============================================================

    /**
     * 유효 Master 노드 목록 반환
     *
     * <p>다중 노드 설정이 있으면 해당 목록 반환,
     * 없으면 기존 masterUrl로 단일 노드 목록 생성
     *
     * @return Master 노드 목록
     */
    public List<NodeConfig> getEffectiveMasters() {
        if (masters != null && !masters.isEmpty()) {
            return masters;
        }
        return List.of(new NodeConfig("master-1", masterUrl, true));
    }

    /**
     * 유효 Volume Server 노드 목록 반환
     *
     * <p>다중 노드 설정이 있으면 해당 목록 반환,
     * 없으면 기존 volumeUrl로 단일 노드 목록 생성
     *
     * @return Volume Server 노드 목록
     */
    public List<NodeConfig> getEffectiveVolumeServers() {
        if (volumeServers != null && !volumeServers.isEmpty()) {
            return volumeServers;
        }
        return List.of(new NodeConfig("volume-1", volumeUrl, true));
    }

    /**
     * 유효 Filer 노드 목록 반환
     *
     * <p>다중 노드 설정이 있으면 해당 목록 반환,
     * 없으면 기존 filerUrl로 단일 노드 목록 생성
     *
     * @return Filer 노드 목록
     */
    public List<NodeConfig> getEffectiveFilers() {
        if (filers != null && !filers.isEmpty()) {
            return filers;
        }
        return List.of(new NodeConfig("filer-1", filerUrl, true));
    }

    // ============================================================
    // 내부 클래스: 노드 설정
    // ============================================================

    /**
     * 노드 설정
     *
     * <p>각 SeaweedFS 노드(Master/Volume/Filer)의 설정 정보
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeConfig {
        /**
         * 노드 이름 (식별용)
         *
         * <p>예: "master-1", "volume-seoul-1"
         */
        private String name;

        /**
         * 노드 URL
         *
         * <p>예: "http://seaweed-master-1:9333"
         */
        private String url;

        /**
         * 활성화 여부
         *
         * <p>기본값: true
         * <p>false로 설정 시 모니터링에서 제외
         */
        private boolean enabled = true;
    }
}
