package com.hanumoka.sado.minipacs.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 모니터링 작업 현황 응답 DTO
 *
 * <p>업로드 및 사전렌더링 작업 현황을 통합하여 제공합니다.
 * 프론트엔드에서 5초 간격으로 폴링하여 실시간 상태를 표시합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringTasksResponse {

    /**
     * 최근 업로드 목록 (최근 1시간)
     */
    private List<UploadTask> uploadTasks;

    /**
     * 렌더링 진행 중인 작업 목록 (PENDING + PROCESSING)
     */
    private List<RenderingTask> renderingTasks;

    /**
     * 작업 요약 통계
     */
    private TaskSummary summary;
}
