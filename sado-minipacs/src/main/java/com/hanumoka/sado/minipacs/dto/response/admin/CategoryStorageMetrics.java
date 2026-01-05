package com.hanumoka.sado.minipacs.dto.response.admin;

import com.hanumoka.sado.minipacs.domain.enums.FileCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카테고리별 스토리지 사용량 메트릭 DTO
 *
 * <p>불변 DTO: setter가 없어 생성 후 수정 불가
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryStorageMetrics {

    /**
     * 파일 카테고리
     * 예: DICOM, AI_RESULT, CLINICAL_DOC, SYSTEM, EXPORT
     */
    private String category;

    /**
     * 파일 개수
     */
    private Long fileCount;

    /**
     * 총 크기 (bytes)
     */
    private Long totalSize;

    /**
     * JPA 생성자 표현식용 생성자
     * FileCategory enum을 String으로 변환
     *
     * @param category 파일 카테고리 enum
     * @param fileCount 파일 개수
     * @param totalSize 총 파일 크기 (bytes)
     */
    public CategoryStorageMetrics(FileCategory category, Long fileCount, Long totalSize) {
        this.category = category.name();
        this.fileCount = fileCount;
        this.totalSize = totalSize;
    }
}
