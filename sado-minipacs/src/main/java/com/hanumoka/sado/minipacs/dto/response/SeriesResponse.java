package com.hanumoka.sado.minipacs.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 시리즈 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeriesResponse {

    // 1. PK
    private Long id;

    // 1-1. UUID v7 (외부 노출용 식별자)
    private String uuid;

    // 2. 소속 검사 정보
    private Long studyId;
    private String studyDescription;
    private String patientName;

    // 3. DICOM 식별자
    private String seriesInstanceUid;

    // 4. 시리즈 정보
    private String modality;
    private String seriesDescription;
    private String bodyPartExamined;
    private String manufacturer;
    private String manufacturerModelName;
    private Integer seriesNumber;

    // 5. 통계 (역정규화)
    private Integer numberOfInstances;

    // 6. 감사 필드
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 7. 멀티테넌시
    private Long tenantId;
}
