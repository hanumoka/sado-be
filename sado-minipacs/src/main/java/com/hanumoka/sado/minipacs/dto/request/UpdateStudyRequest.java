package com.hanumoka.sado.minipacs.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 검사 수정 요청 DTO
 */
@Getter
@Setter
public class UpdateStudyRequest {

    // 모든 필드 선택(Optional) - 부분 업데이트 지원

    // 1. DICOM Study UID (선택)
    private String studyInstanceUid;

    // 2. 검사 날짜 (선택)
    private LocalDate studyDate;

    // 3. 검사 설명 (선택)
    private String studyDescription;
}
