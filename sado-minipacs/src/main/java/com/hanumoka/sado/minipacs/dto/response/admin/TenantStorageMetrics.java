package com.hanumoka.sado.minipacs.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 테넌트별 스토리지 사용량 메트릭 DTO
 *
 * <p>테넌트별로 원본 파일(DICOM)과 사전렌더링 파일(SYSTEM)의
 * 사용량을 구분하여 제공합니다.
 *
 * <p>불변 DTO: setter가 없어 생성 후 수정 불가
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantStorageMetrics {

    /**
     * 테넌트 ID
     */
    private Long tenantId;

    /**
     * 테넌트 이름 (Optional)
     */
    private String tenantName;

    /**
     * 원본 파일 개수 (DICOM 카테고리)
     */
    private Long originalFileCount;

    /**
     * 원본 파일 총 크기 (bytes)
     */
    private Long originalSize;

    /**
     * 사전렌더링 파일 개수 (SYSTEM 카테고리)
     */
    private Long preRenderedFileCount;

    /**
     * 사전렌더링 파일 총 크기 (bytes)
     */
    private Long preRenderedSize;

    /**
     * 총 크기 (bytes) = originalSize + preRenderedSize
     */
    private Long totalSize;
}
