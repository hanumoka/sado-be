package com.hanumoka.sado.minipacs.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 검사 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyResponse {

    // 1. PK
    private Long id;

    // 2. 소속 환자 ID
    private Long patientId;

    // 3. DICOM 식별자
    private String studyInstanceUid;

    // 4. 검사 정보
    private LocalDate studyDate;
    private String studyDescription;

    // 5. 통계 (역정규화)
    private Integer numberOfSeries;
    private Integer numberOfInstances;

    // 6. 감사 필드
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 7. 멀티테넌시
    private Long tenantId;
}
