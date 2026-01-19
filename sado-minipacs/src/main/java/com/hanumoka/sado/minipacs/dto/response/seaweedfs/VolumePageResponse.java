package com.hanumoka.sado.minipacs.dto.response.seaweedfs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Volume 페이징 응답 DTO
 *
 * <p>Spring Page 구조와 유사하게 설계
 * <p>FE 페이지네이션 UI에 필요한 메타데이터 포함
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VolumePageResponse {

    /** Volume 목록 */
    private List<VolumeInfoResponse> content;

    /** 현재 페이지 (0부터 시작) */
    private int page;

    /** 페이지 크기 */
    private int size;

    /** 전체 요소 수 */
    private long totalElements;

    /** 전체 페이지 수 */
    private int totalPages;

    /** 첫 페이지 여부 */
    private boolean first;

    /** 마지막 페이지 여부 */
    private boolean last;

    /** 사용 가능한 Collection 목록 (필터 드롭다운용) */
    private List<String> availableCollections;

    /**
     * 정적 팩토리 메서드
     *
     * @param content 페이지 내용
     * @param page 현재 페이지
     * @param size 페이지 크기
     * @param totalElements 전체 요소 수
     * @param availableCollections 사용 가능한 Collection 목록
     * @return VolumePageResponse
     */
    public static VolumePageResponse of(
            List<VolumeInfoResponse> content,
            int page,
            int size,
            long totalElements,
            List<String> availableCollections
    ) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return VolumePageResponse.builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1 || totalPages == 0)
                .availableCollections(availableCollections)
                .build();
    }
}
