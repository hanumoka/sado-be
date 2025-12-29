package com.hanumoka.sado.minipacs.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 환자 생성 요청 DTO
 */
@Getter
@Setter
public class CreatePatientRequest {

    // 1. DICOM PatientID (필수)
     private String dicomPatientId;

    // 2. Issuer of PatientID (선택)
     private String issuerOfPatientId;

    // 3. 환자 이름 (선택)
     private String patientName;

    // 4. 생년월일 (선택)
     private LocalDate patientBirthDate;

    // 5. 성별 (선택) - "M", "F", "O"
     private String patientSex;

    // 6. EMR 환자 ID (선택)
     private String emrPatientId;
}
