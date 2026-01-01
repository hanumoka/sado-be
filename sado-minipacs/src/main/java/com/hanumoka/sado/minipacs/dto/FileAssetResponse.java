package com.hanumoka.sado.minipacs.dto;

import com.hanumoka.sado.minipacs.domain.entity.FileAsset;
import com.hanumoka.sado.minipacs.domain.enums.FileCategory;
import com.hanumoka.sado.minipacs.domain.enums.FileStatus;
import com.hanumoka.sado.minipacs.domain.enums.ReferenceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * FileAsset 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileAssetResponse {

    private Long id;
    private FileCategory category;
    private ReferenceType referenceType;
    private Long referenceId;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private FileStatus status;
    private String storageTier;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessedAt;

    /**
     * Entity → Response DTO 변환
     */
    public static FileAssetResponse from(FileAsset entity) {
        return FileAssetResponse.builder()
                .id(entity.getId())
                .category(entity.getCategory())
                .referenceType(entity.getReferenceType())
                .referenceId(entity.getReferenceId())
                .fileName(entity.getFileName())
                .fileSize(entity.getFileSize())
                .mimeType(entity.getMimeType())
                .status(entity.getStatus())
                .storageTier(entity.getStorageTier())
                .expiresAt(entity.getExpiresAt())
                .createdAt(entity.getCreatedAt())
                .lastAccessedAt(entity.getLastAccessedAt())
                .build();
    }
}
