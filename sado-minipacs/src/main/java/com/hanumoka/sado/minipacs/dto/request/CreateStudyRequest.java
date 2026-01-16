package com.hanumoka.sado.minipacs.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @NotNull(message = "환자 ID는 필수입니다")
    private Long patientId;

    // 2. DICOM Study UID (선택) - OID 형식: 숫자와 점으로 구성, 최대 64자
    @Size(max = 64, message = "Study Instance UID는 최대 64자까지 가능합니다")
    @Pattern(regexp = "^([0-9]+(\\.[0-9]+)*)?$", message = "Study Instance UID는 유효한 DICOM UID 형식이어야 합니다")
    private String studyInstanceUid;

    // 3. 검사 날짜 (선택) - 미래 날짜 불가
    @PastOrPresent(message = "검사 날짜는 미래 날짜일 수 없습니다")
    private LocalDate studyDate;

    // 4. 검사 설명 (선택) - DICOM LO VR 최대 64자
    @Size(max = 64, message = "검사 설명은 최대 64자까지 가능합니다")
    private String studyDescription;
}
