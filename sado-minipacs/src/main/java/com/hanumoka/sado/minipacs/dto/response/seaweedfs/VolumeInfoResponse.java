package com.hanumoka.sado.minipacs.dto.response.seaweedfs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * SeaweedFS Volume 정보 응답 DTO
 *
 * <p>Volume: SeaweedFS에서 파일을 저장하는 논리적 단위
 *
 * <p>Volume 구조:
 * <ul>
 *   <li>ID: Volume 고유 식별자 (예: 1, 2, 3)</li>
 *   <li>Size: Volume 최대 크기 (기본 30GB)</li>
 *   <li>Collection: 논리적 그룹 (예: "minipacs")</li>
 *   <li>Replication: 복제 전략 (예: "000" = 복제 없음)</li>
 * </ul>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VolumeInfoResponse {

    /**
     * Volume ID
     *
     * <p>예: 1, 2, 3
     */
    private Long id;

    /**
     * Volume 크기 (bytes)
     *
     * <p>기본값: 30GB (32212254720 bytes)
     */
    private Long size;

    /**
     * Collection 이름
     *
     * <p>예: "minipacs", "default"
     */
    private String collection;

    /**
     * Replication 전략
     *
     * <p>형식: XYZ
     * <ul>
     *   <li>X: 다른 데이터센터 복제 수</li>
     *   <li>Y: 다른 랙 복제 수</li>
     *   <li>Z: 다른 서버 복제 수</li>
     * </ul>
     *
     * <p>예시:
     * <ul>
     *   <li>"000": 복제 없음 (POC)</li>
     *   <li>"001": 동일 랙 내 다른 서버에 1개 복제</li>
     *   <li>"010": 다른 랙에 1개 복제</li>
     *   <li>"100": 다른 데이터센터에 1개 복제</li>
     * </ul>
     */
    private String replication;

    /**
     * Volume 상태
     *
     * <p>예: "ReadWrite", "ReadOnly"
     */
    private String status;

    /**
     * 파일 개수
     */
    private Long fileCount;

    /**
     * 사용된 크기 (bytes)
     */
    private Long usedSize;

    /**
     * Volume Server URL
     *
     * <p>예: "http://localhost:8080"
     */
    private String serverUrl;
}
