package com.hanumoka.sado.minipacs.domain.entity;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Application Domain Layer - 검사 비즈니스 엔티티
 * DICOM Storage Layer와 분리 (2-Layer 아키텍처)
 */
@Entity
@Table(name = "study")
@Getter
@Setter
@NoArgsConstructor
public class Study extends TenantAwareEntity {

    // ========== DICOM Storage Layer 간접 참조 (Option B) ==========

    /**
     * DICOM Study UID (DicomMetadataRecord 조회 키)
     * nullable: Study 생성 후 DICOM 업로드 가능
     */
    @Column(name = "study_instance_uid", length = 256)
    private String studyInstanceUid;

    // ========== Application Domain 필드 ==========

    /**
     * 검사 날짜
     */
    @Column(name = "study_date")
    private LocalDate studyDate;

    /**
     * 검사 설명
     */
    @Column(name = "study_description", length = 255)
    private String studyDescription;

    // ========== 향후 추가 예정 ==========

    // TODO: PatientEntity 생성 후 관계 추가
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "patient_id")
    // private Patient patient;
}
