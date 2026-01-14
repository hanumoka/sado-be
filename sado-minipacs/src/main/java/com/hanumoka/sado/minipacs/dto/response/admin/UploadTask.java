package com.hanumoka.sado.minipacs.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 업로드 작업 DTO
 *
 * <p>최근 업로드된 Instance의 정보와 렌더링 상태를 제공합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadTask {

    /**
     * Instance ID
     */
    private Long instanceId;

    /**
     * SOP Instance UID
     */
    private String sopInstanceUid;

    /**
     * Study 설명 (조회 편의용)
     */
    private String studyDescription;

    /**
     * Modality (CT, MR, US 등)
     */
    private String modality;

    /**
     * 파일 크기 (bytes)
     */
    private Long fileSize;

    /**
     * 업로드 시각
     */
    private LocalDateTime uploadedAt;

    /**
     * 렌더링 상태 (NONE, PENDING, PROCESSING, COMPLETED, FAILED)
     */
    private String renderingStatus;

    /**
     * 테넌트 ID
     */
    private Long tenantId;
}
