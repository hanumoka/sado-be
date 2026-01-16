package com.hanumoka.sado.minipacs.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 환자 생성 요청 DTO
 */
@Getter
@Setter
public class CreatePatientRequest {

    // 1. DICOM PatientID (필수) - DICOM 표준 최대 64자
    @NotBlank(message = "DICOM Patient ID는 필수입니다")
    @Size(max = 64, message = "DICOM Patient ID는 최대 64자까지 가능합니다")
    private String dicomPatientId;

    // 2. Issuer of PatientID (선택) - DICOM 표준 최대 64자
    @Size(max = 64, message = "Issuer of Patient ID는 최대 64자까지 가능합니다")
    private String issuerOfPatientId;

    // 3. 환자 이름 (선택) - DICOM 표준 최대 256자 (PN VR)
    @Size(max = 256, message = "환자 이름은 최대 256자까지 가능합니다")
    private String patientName;

    // 4. 생년월일 (선택) - 미래 날짜 불가
    @PastOrPresent(message = "생년월일은 미래 날짜일 수 없습니다")
    private LocalDate patientBirthDate;

    // 5. 성별 (선택) - "M", "F", "O" (DICOM 표준)
    @Pattern(regexp = "^[MFO]?$", message = "성별은 M(남성), F(여성), O(기타) 중 하나여야 합니다")
    private String patientSex;

    // 6. EMR 환자 ID (선택)
    @Size(max = 64, message = "EMR 환자 ID는 최대 64자까지 가능합니다")
    private String emrPatientId;
}
