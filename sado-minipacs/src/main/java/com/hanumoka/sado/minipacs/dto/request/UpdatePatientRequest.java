package com.hanumoka.sado.minipacs.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 환자 수정 요청 DTO
 */
@Getter
@Setter
public class UpdatePatientRequest {


    // UpdatePatientRequest는 CreatePatientRequest와 거의 동일하지만,
    // 모든 필드가 선택(Optional)입니다.
    // 클라이언트가 수정하고 싶은 필드만 보내면 됩니다.

    // 1. DICOM PatientID (선택)
     private String dicomPatientId;

    // 2. Issuer of PatientID (선택)
     private String issuerOfPatientId;

    // 3. 환자 이름 (선택)
     private String patientName;

    // 4. 생년월일 (선택)
     private LocalDate patientBirthDate;

    // 5. 성별 (선택)
     private String patientSex;

    // 6. EMR 환자 ID (선택)
     private String emrPatientId;

    // 7. 매핑 신뢰도 (선택)
     private Double matchingConfidence;

    // 8. 매핑 상태 (선택) - "UNMAPPED", "AUTO_MATCHED", etc.
     private String matchingStatus;
}
