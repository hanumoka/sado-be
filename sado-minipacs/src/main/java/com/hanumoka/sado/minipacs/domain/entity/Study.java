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

    // ========== Patient 관계 (Application Layer 내 직접 FK) ==========

    /**
     * 소속 환자
     * N개의 검사 → 1명의 환자
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

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

    /**
     * Number of Series
     * 역정규화: 통계용 (실시간 COUNT 대신 캐싱)
     */
    @Column(name = "number_of_series")
    private Integer numberOfSeries;

    /**
     * Number of Instances
     * 역정규화: 통계용
     */
    @Column(name = "number_of_instances")
    private Integer numberOfInstances;

    // ========== Series 관계 ==========

    /**
     * 검사의 시리즈 목록
     * 1개의 검사 → N개의 시리즈
     */
    @OneToMany(mappedBy = "study", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Series> series = new ArrayList<>();

    // ========== 비즈니스 메서드 ==========

    /**
     * Series 추가 및 numberOfSeries 증가
     */
    public void addSeries(Series seriesItem) {
        series.add(seriesItem);
        seriesItem.setStudy(this);
        if (numberOfSeries == null) {
            numberOfSeries = 0;
        }
        numberOfSeries++;
    }

    /**
     * Series 제거 및 numberOfSeries 감소
     */
    public void removeSeries(Series seriesItem) {
        series.remove(seriesItem);
        seriesItem.setStudy(null);
        if (numberOfSeries != null && numberOfSeries > 0) {
            numberOfSeries--;
        }
    }
}
