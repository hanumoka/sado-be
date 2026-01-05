package com.hanumoka.sado.minipacs.domain.entity;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Application Domain Layer - 환자 엔티티
 * DICOM PatientID와 EMR 환자 ID 매핑 관리
 *
 *   | 관계              | 설명 | 비유                             |
 *   |-------------------|------|----------------------------------|
 *   | Patient → Study   | 1:N  | 1명의 환자 → 여러 번 검사        |
 *   | Study → Series    | 1:N  | 1번의 검사 → 여러 각도/시점 촬영 |
 *   | Series → Instance | 1:N  | 1개의 시리즈 → 여러 장의 영상    |
 */
@Entity
@Table(
    name = "patient",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_patient_identity",
        columnNames = {"tenant_id", "dicom_patient_id", "issuer_of_patient_id"}
    ),
    indexes = {
        @Index(name = "idx_patient_emr_id", columnList = "emr_patient_id"),
        @Index(name = "idx_patient_dicom_id", columnList = "tenant_id, dicom_patient_id")
    }
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

    // ========== 통계 필드 제거 (2026-01-05) ==========
    // studiesCount, lastStudyDate 제거 → COUNT 쿼리로 실시간 계산
    // PatientStatisticsListener 제거 → Deadlock 원인 제거
    // 필요시: studyRepository.countByPatientId(patientId) 사용

    // ========== 관계 ==========

    /**
     * 환자의 검사 목록
     * 1명의 환자 → N개의 검사
     *
     * CRITICAL: CascadeType.ALL 제거 (데이터 무결성 보호)
     * - 의료 데이터는 WORM (Write Once Read Many) 정책 필요
     * - Patient 삭제 시 Study/Series/Instance 연쇄 삭제 방지
     * - orphanRemoval도 제거하여 실수로 인한 데이터 손실 방지
     * - 명시적 삭제만 허용 (Soft Delete 권장)
     *
     * CRITICAL: @BatchSize 추가 (N+1 쿼리 방지)
     * - PatientStatisticsListener에서 patient.getStudies() 호출 시
     * - 100명 조회 시 100번 쿼리 → 10번 쿼리 (IN 절 사용)
     * - 성능 10배 개선
     */
    @OneToMany(mappedBy = "patient", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @org.hibernate.annotations.BatchSize(size = 10)
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

    // ========== 컬렉션 접근 제어 ==========

    /**
     * 환자의 검사 목록 조회
     *
     * <p>불변 뷰를 반환하여 외부에서 직접 수정할 수 없도록 보호합니다.
     * 검사를 추가하거나 제거하려면 addStudy() 또는 removeStudy()를 사용하세요.
     *
     * @return 검사 목록의 불변 뷰
     */
    public List<Study> getStudies() {
        return Collections.unmodifiableList(studies);
    }

    // ========== 양방향 관계 관리 메서드 ==========

    /**
     * 검사 추가 (양방향 관계 설정)
     *
     * <p>환자에게 검사를 추가하고 양방향 관계를 설정합니다.
     * 중복 추가나 null을 방지하기 위한 검증을 수행합니다.
     *
     * @param study 추가할 검사
     * @throws NullPointerException study가 null인 경우
     * @throws IllegalStateException study가 이미 존재하는 경우
     */
    public void addStudy(Study study) {
        Objects.requireNonNull(study, "Study cannot be null");

        if (studies.contains(study)) {
            throw new IllegalStateException("Study already exists for this patient");
        }

        studies.add(study);
        study.setPatient(this);
    }

    /**
     * 검사 제거 (양방향 관계 해제)
     *
     * <p>환자에서 검사를 제거하고 양방향 관계를 해제합니다.
     *
     * @param study 제거할 검사
     * @throws NullPointerException study가 null인 경우
     */
    public void removeStudy(Study study) {
        Objects.requireNonNull(study, "Study cannot be null");

        boolean removed = studies.remove(study);
        if (removed) {
            study.setPatient(null);
        }
    }

    // ========== Builder Pattern ==========

    /**
     * Patient Entity Builder
     *
     * <p>유창한(fluent) API를 제공하여 Patient 객체를 쉽게 생성할 수 있습니다.
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * Patient patient = Patient.builder()
     *     .dicomPatientId("P-001")
     *     .issuerOfPatientId("HOSPITAL-A")
     *     .patientName("홍길동^Hong^Gildong")
     *     .patientBirthDate(LocalDate.of(1990, 1, 1))
     *     .patientSex("M")
     *     .build();
     * }
     * </pre>
     */
    public static class Builder {
        private final Patient patient = new Patient();

        public Builder dicomPatientId(String dicomPatientId) {
            patient.dicomPatientId = dicomPatientId;
            return this;
        }

        public Builder issuerOfPatientId(String issuerOfPatientId) {
            patient.issuerOfPatientId = issuerOfPatientId;
            return this;
        }

        public Builder issuerTypeCode(String issuerTypeCode) {
            patient.issuerTypeCode = issuerTypeCode;
            return this;
        }

        public Builder patientName(String patientName) {
            patient.patientName = patientName;
            return this;
        }

        public Builder patientBirthDate(LocalDate patientBirthDate) {
            patient.patientBirthDate = patientBirthDate;
            return this;
        }

        public Builder patientSex(String patientSex) {
            patient.patientSex = patientSex;
            return this;
        }

        public Builder emrPatientId(String emrPatientId) {
            patient.emrPatientId = emrPatientId;
            return this;
        }

        public Builder matchingConfidence(Double matchingConfidence) {
            patient.matchingConfidence = matchingConfidence;
            return this;
        }

        public Builder matchingStatus(MatchingStatus matchingStatus) {
            patient.matchingStatus = matchingStatus;
            return this;
        }

        // studiesCount, lastStudyDate Builder 메서드 제거 (2026-01-05)

        /**
         * Patient 객체 생성
         *
         * @return 생성된 Patient 객체
         */
        public Patient build() {
            // DICOM Patient ID는 nullable (연구용 DICOM은 PatientID 없을 수 있음)
            return patient;
        }
    }

    /**
     * Builder 인스턴스 생성
     *
     * @return 새로운 Builder 인스턴스
     */
    public static Builder builder() {
        return new Builder();
    }
}
