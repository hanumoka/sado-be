package com.hanumoka.sado.minipacs.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 작업 현황 요약 DTO
 *
 * <p>업로드 및 사전렌더링 작업의 상태별 카운트를 제공합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSummary {

    /**
     * 대기 중인 작업 수 (PENDING)
     */
    private int totalPending;

    /**
     * 진행 중인 작업 수 (PROCESSING)
     */
    private int totalProcessing;

    /**
     * 최근 1시간 내 완료된 작업 수
     */
    private int recentCompleted;

    /**
     * 최근 1시간 내 실패한 작업 수
     */
    private int recentFailed;
}
