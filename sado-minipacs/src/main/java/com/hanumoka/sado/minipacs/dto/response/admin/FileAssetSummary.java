package com.hanumoka.sado.minipacs.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * FileAsset 요약 정보 DTO (Tiering 관리용)
 *
 * <p>불변 DTO: setter가 없어 생성 후 수정 불가
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileAssetSummary {

    /**
     * FileAsset ID
     */
    private Long id;

    /**
     * 스토리지 경로 (S3 Key)
     * <p>예: "dicom/123/instance.dcm"
     */
    private String storagePath;

    /**
     * 파일 카테고리
     */
    private String fileCategory;

    /**
     * 스토리지 Tier (HOT, WARM, COLD)
     */
    private String storageTier;

    /**
     * 파일 크기 (bytes)
     */
    private Long fileSize;

    /**
     * 마지막 접근 시간
     */
    private LocalDateTime lastAccessedAt;

    /**
     * 생성 시간
     */
    private LocalDateTime createdAt;
}
