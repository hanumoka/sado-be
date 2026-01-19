package com.hanumoka.sado.minipacs.dto.response.seaweedfs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Collection별 통계 응답 DTO
 *
 * <p>SeaweedFS Collection: Volume들의 논리적 그룹
 * <p>같은 Collection에 속한 Volume들의 통계를 집계하여 제공
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionStatsResponse {

    /** Collection 이름 (null이면 "default") */
    private String collection;

    /** Volume 수 */
    private int volumeCount;

    /** 총 파일 수 */
    private long totalFileCount;

    /** 총 사용량 (bytes) */
    private long totalUsedSize;

    /** 총 용량 (bytes) */
    private long totalSize;

    /** ReadWrite 상태 Volume 수 */
    private int readWriteCount;

    /** ReadOnly 상태 Volume 수 */
    private int readOnlyCount;

    /** 소속 Volume ID 목록 */
    private List<Long> volumeIds;

    /**
     * 사용률 계산 (%)
     *
     * @return 사용률 (0.0 ~ 100.0)
     */
    public double getUsagePercent() {
        if (totalSize == 0) {
            return 0.0;
        }
        return (double) totalUsedSize / totalSize * 100.0;
    }
}
