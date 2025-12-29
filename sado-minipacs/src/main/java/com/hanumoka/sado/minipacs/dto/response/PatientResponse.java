package com.hanumoka.sado.minipacs.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 환자 응답 DTO
 */
@Getter
@Builder
public class PatientResponse {

    // Response DTO는 Entity의 모든 정보를 포함합니다 (Read용)

    // 1. PK (필수)
     private Long id;

    // 2. DICOM 식별자
     private String dicomPatientId;
     private String issuerOfPatientId;
     private String issuerTypeCode;

    // 3. 환자 정보
     private String patientName;
     private LocalDate patientBirthDate;
     private String patientSex;

    // 4. EMR 매핑
     private String emrPatientId;
     private Double matchingConfidence;
     private String matchingStatus;

    // 5. 감사(Audit) 필드 - BaseEntity에서 상속받는 필드들
     private LocalDateTime createdAt;
     private LocalDateTime updatedAt;

    // 6. 멀티테넌시
     private Long tenantId;
}
