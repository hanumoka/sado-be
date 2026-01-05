package com.hanumoka.sado.minipacs.dto.request.seaweedfs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * SeaweedFS Volume 생성 요청 DTO
 *
 * <p>새로운 Volume을 생성하기 위한 요청 파라미터
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateVolumeRequest {

    /**
     * 생성할 Volume 개수
     *
     * <p>기본값: 1
     * <p>범위: 1-100
     */
    @Min(value = 1, message = "Volume count must be at least 1")
    @Max(value = 100, message = "Volume count must not exceed 100")
    @Builder.Default
    private Integer count = 1;

    /**
     * Collection 이름
     *
     * <p>예: "minipacs", "default"
     * <p>null이면 default collection 사용
     */
    private String collection;

    /**
     * Replication 전략
     *
     * <p>형식: XYZ (각 자리는 0-9)
     * <ul>
     *   <li>X: 다른 데이터센터 복제 수</li>
     *   <li>Y: 다른 랙 복제 수</li>
     *   <li>Z: 다른 서버 복제 수</li>
     * </ul>
     *
     * <p>예:
     * <ul>
     *   <li>"000": 복제 없음 (기본값, POC)</li>
     *   <li>"001": 동일 랙 내 다른 서버에 1개 복제</li>
     *   <li>"010": 다른 랙에 1개 복제</li>
     * </ul>
     */
    @Pattern(regexp = "^[0-9]{3}$", message = "Replication must be 3 digits (e.g., '000', '001')")
    @Builder.Default
    private String replication = "000";

    /**
     * TTL (Time To Live)
     *
     * <p>Volume 내 파일의 기본 생존 시간
     *
     * <p>형식:
     * <ul>
     *   <li>"3d": 3일</li>
     *   <li>"1w": 1주</li>
     *   <li>"2m": 2개월</li>
     *   <li>"1y": 1년</li>
     *   <li>null: 무제한 (기본값)</li>
     * </ul>
     */
    private String ttl;

    /**
     * Data Center 이름
     *
     * <p>특정 데이터센터에 Volume 생성
     * <p>null이면 자동 할당
     */
    private String dataCenter;
}
