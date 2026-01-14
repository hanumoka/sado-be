package com.hanumoka.sado.minipacs.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사전렌더링 작업 DTO
 *
 * <p>진행 중인 렌더링 작업의 상세 정보를 제공합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenderingTask {

    /**
     * Instance ID
     */
    private Long instanceId;

    /**
     * SOP Instance UID
     */
    private String sopInstanceUid;

    /**
     * Study 설명
     */
    private String studyDescription;

    /**
     * Modality (CT, MR, US 등)
     */
    private String modality;

    /**
     * 렌더링 상태 (PENDING, PROCESSING)
     */
    private String status;

    /**
     * 총 프레임 수
     */
    private Integer totalFrames;

    /**
     * 렌더링 완료된 프레임 수
     */
    private Integer renderedFrames;

    /**
     * 렌더링된 파일 총 크기 (bytes)
     */
    private Long renderedSize;

    /**
     * 렌더링 시작 시각 (또는 등록 시각)
     */
    private LocalDateTime startedAt;

    /**
     * 진행률 (0-100)
     */
    private Integer progressPercent;
}
