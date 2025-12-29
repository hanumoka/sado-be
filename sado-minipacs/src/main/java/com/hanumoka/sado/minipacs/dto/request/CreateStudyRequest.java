package com.hanumoka.sado.minipacs.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 검사 생성 요청 DTO
 */
@Getter
@Setter
public class CreateStudyRequest {

    // 1. 소속 환자 ID (필수)
    private Long patientId;

    // 2. DICOM Study UID (선택)
    private String studyInstanceUid;

    // 3. 검사 날짜 (선택)
    private LocalDate studyDate;

    // 4. 검사 설명 (선택)
    private String studyDescription;
}
