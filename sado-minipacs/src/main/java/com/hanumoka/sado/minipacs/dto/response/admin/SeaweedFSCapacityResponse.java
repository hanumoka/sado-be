package com.hanumoka.sado.minipacs.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * SeaweedFS 물리적 스토리지 용량 응답 DTO
 *
 * <p>SeaweedFS 파일 스토리지의 실제 디스크 용량 정보
 *
 * <p>주의: FileAsset 테이블 기반 메타데이터 합계와는 다른 정보
 * <ul>
 *   <li>SeaweedFS 물리적 용량: 디스크 전체 크기 (OS 파일, 메타데이터, 오버헤드 포함)</li>
 *   <li>FileAsset 메타데이터: DB에 기록된 DICOM 파일 크기 합계</li>
 * </ul>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeaweedFSCapacityResponse {

    /**
     * 전체 용량 (bytes)
     *
     * <p>totalCapacity = usedSpace + freeSpace
     */
    private Long totalCapacity;

    /**
     * 사용 중인 공간 (bytes)
     */
    private Long usedSpace;

    /**
     * 여유 공간 (bytes)
     */
    private Long freeSpace;

    /**
     * 사용률 (퍼센트)
     *
     * <p>범위: 0.00 ~ 100.00
     *
     * <p>계산식: (usedSpace / totalCapacity) * 100
     */
    private Double percentUsed;
}
