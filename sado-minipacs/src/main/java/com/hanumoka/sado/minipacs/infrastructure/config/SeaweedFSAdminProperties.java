package com.hanumoka.sado.minipacs.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SeaweedFS Admin API 설정
 *
 * <p>SeaweedFS 클러스터 관리를 위한 Master/Volume/Filer 엔드포인트 설정
 *
 * <p>application.yml 설정 예시:
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

    /**
     * Master 서버 URL
     *
     * <p>기본값: http://localhost:9333
     *
     * <p>용도: Volume 관리, Cluster 상태 조회
     */
    private String masterUrl = "http://localhost:9333";

    /**
     * Volume 서버 URL
     *
     * <p>기본값: http://localhost:8080
     *
     * <p>용도: Volume 상세 정보 조회
     */
    private String volumeUrl = "http://localhost:8080";

    /**
     * Filer 서버 URL
     *
     * <p>기본값: http://localhost:8888
     *
     * <p>용도: 디렉토리 탐색, 파일 목록 조회
     */
    private String filerUrl = "http://localhost:8888";

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
}
