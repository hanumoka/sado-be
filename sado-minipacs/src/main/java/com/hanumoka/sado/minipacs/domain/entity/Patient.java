package com.hanumoka.sado.minipacs.domain.entity;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Application Domain Layer - 환자 엔티티
 * DICOM PatientID와 EMR 환자 ID 매핑 관리
 */
@Entity
@Table(
    name = "patient",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_patient_identity",
        columnNames = {"tenant_id", "dicom_patient_id", "issuer_of_patient_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class Patient extends TenantAwareEntity {

    // ========== DICOM 환자 식별자 ==========

    /**
     * DICOM PatientID (0010,0020)
     * nullable: 연구용 DICOM은 PatientID 없을 수 있음
     */
    @Column(name = "dicom_patient_id", length = 256)
    private String dicomPatientId;

    /**
     * Issuer of PatientID (0010,0021)
     * 환자 ID 발급 기관 (다중 병원 환경 필수)
     *
     * 예시:
     *   병원 A: PatientID "12345", Issuer "HOSPITAL_A"
     *   병원 B: PatientID "12345", Issuer "HOSPITAL_B"
     *   → 완전히 다른 환자
     */
    @Column(name = "issuer_of_patient_id", length = 256)
    private String issuerOfPatientId;

    /**
     * Issuer Type Code (HL7 CX 데이터 타입)
     * 예: "ISO", "DNS", "URI"
     */
    @Column(name = "issuer_type_code", length = 64)
    private String issuerTypeCode;

    // ========== 환자 기본 정보 (DICOM에서 추출) ==========

    /**
     * 환자 이름 (0010,0010)
     */
    @Column(name = "patient_name", length = 512)
    private String patientName;

    /**
     * 환자 생년월일 (0010,0030)
     * DICOM 형식 "19900101" → LocalDate로 파싱
     */
    @Column(name = "patient_birth_date")
    private LocalDate patientBirthDate;

    /**
     * 환자 성별 (0010,0040)
     * M (Male), F (Female), O (Other)
     */
    @Column(name = "patient_sex", length = 16)
    private String patientSex;

    // ========== EMR 환자 매핑 ==========

    /**
     * EMR 시스템의 환자 ID
     * nullable: 매핑 전에는 null
     */
    @Column(name = "emr_patient_id", length = 100)
    private String emrPatientId;

    /**
     * 매핑 신뢰도 (0.0 ~ 1.0)
     * 예: 이름+생년월일+성별 일치 = 0.95
     */
    @Column(name = "matching_confidence")
    private Double matchingConfidence;

    /**
     * 매핑 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "matching_status", length = 50)
    private MatchingStatus matchingStatus;

    // ========== 관계 ==========

    /**
     * 환자의 검사 목록
     * 1명의 환자 → N개의 검사
     */
    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Study> studies = new ArrayList<>();

    // ========== Enum 정의 ==========

    /**
     * 환자 매핑 상태
     */
    public enum MatchingStatus {
        /**
         * 미매핑 (DICOM만 존재)
         */
        UNMAPPED,

        /**
         * 자동 매핑 완료 (신뢰도 >= 95%)
         */
        AUTO_MATCHED,

        /**
         * 수동 검토 필요 (신뢰도 90~95%)
         */
        REQUIRES_REVIEW,

        /**
         * 수동 확정 완료
         */
        MANUALLY_CONFIRMED
    }
}
