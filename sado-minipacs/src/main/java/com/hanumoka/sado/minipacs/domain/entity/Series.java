package com.hanumoka.sado.minipacs.domain.entity;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Application Domain Layer - 시리즈 엔티티
 * DICOM Series 단위 메타데이터 관리
 */
@Entity
@Table(name = "series")
@Getter
@Setter
@NoArgsConstructor
public class Series extends TenantAwareEntity {

    // ========== DICOM UID (간접 참조) ==========

    /**
     * DICOM SeriesInstanceUID (0020,000E)
     * DicomMetadataRecord 조회 키 (간접 참조)
     */
    @Column(name = "series_instance_uid", length = 256)
    private String seriesInstanceUid;

    // ========== Study 관계 (Application Layer 내 직접 FK) ==========

    /**
     * 소속 검사
     * N개의 시리즈 → 1개의 검사
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_id", nullable = false)
    private Study study;

    // ========== 시리즈 정보 ==========

    /**
     * Modality (0008,0060)
     * US (초음파), CT, MR, XA (혈관조영), DX (디지털 X-ray) 등
     */
    @Column(name = "modality", length = 16)
    private String modality;

    /**
     * Series Description (0008,103E)
     * 예: "4-Chamber View", "Apical View"
     */
    @Column(name = "series_description", length = 512)
    private String seriesDescription;

    /**
     * Body Part Examined (0018,0015)
     * 예: HEART, CHEST, HEAD
     */
    @Column(name = "body_part_examined", length = 64)
    private String bodyPartExamined;

    /**
     * Manufacturer (0008,0070)
     * 장비 제조사 (예: GE, Philips, Siemens)
     */
    @Column(name = "manufacturer", length = 256)
    private String manufacturer;

    /**
     * Manufacturer Model Name (0008,1090)
     * 장비 모델명
     */
    @Column(name = "manufacturer_model_name", length = 256)
    private String manufacturerModelName;

    /**
     * Series Number (0020,0011)
     * 시리즈 번호 (검사 내 순서)
     */
    @Column(name = "series_number")
    private Integer seriesNumber;

    /**
     * Number of Instances
     * 역정규화: 통계용 (실시간 COUNT 대신 캐싱)
     */
    @Column(name = "number_of_instances")
    private Integer numberOfInstances;

    // ========== Instance 관계 ==========

    /**
     * 시리즈의 영상 목록
     * 1개의 시리즈 → N개의 영상
     */
    @OneToMany(mappedBy = "series", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Instance> instances = new ArrayList<>();

    // ========== 비즈니스 메서드 ==========

    /**
     * Instance 추가 및 numberOfInstances 증가
     */
    public void addInstance(Instance instance) {
        instances.add(instance);
        instance.setSeries(this);
        if (numberOfInstances == null) {
            numberOfInstances = 0;
        }
        numberOfInstances++;
    }

    /**
     * Instance 제거 및 numberOfInstances 감소
     */
    public void removeInstance(Instance instance) {
        instances.remove(instance);
        instance.setSeries(null);
        if (numberOfInstances != null && numberOfInstances > 0) {
            numberOfInstances--;
        }
    }
}
