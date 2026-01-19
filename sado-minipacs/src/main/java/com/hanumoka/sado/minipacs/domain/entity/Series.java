package com.hanumoka.sado.minipacs.domain.entity;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import com.hanumoka.sado.minipacs.domain.util.CounterManager;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Application Domain Layer - 시리즈 엔티티
 * DICOM Series 단위 메타데이터 관리
 */
@Entity
@Table(
    name = "series",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_series_instance_uid",
        columnNames = {"tenant_id", "series_instance_uid"}
    ),
    indexes = {
        @Index(name = "idx_series_study_modality", columnList = "study_id, modality"),
        @Index(name = "idx_series_instance_uid", columnList = "series_instance_uid")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Series extends TenantAwareEntity {

    // ========== DICOM UID (간접 참조) ==========

    /**
     * DICOM SeriesInstanceUID (0020,000E)
     * DICOM 업로드 시 자동 추출 (필수)
     */
    @Column(name = "series_instance_uid", length = 256, nullable = false)
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

    // ========== 역정규화 카운터 제거 (2026-01-05) ==========
    // numberOfInstances 필드 제거 → COUNT 쿼리로 실시간 계산
    // @Version 제거 → Optimistic Lock 충돌 완전 제거

    // ========== Instance 관계 ==========

    /**
     * 시리즈의 영상 목록
     * 1개의 시리즈 → N개의 영상
     *
     * CRITICAL: CascadeType.ALL 제거 (데이터 무결성 보호)
     * - Series 삭제 시 Instance 연쇄 삭제 방지
     * - orphanRemoval 제거하여 실수로 인한 데이터 손실 방지
     */
    @OneToMany(mappedBy = "series", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<Instance> instances = new ArrayList<>();

    // ========== 비즈니스 메서드 ==========

    /**
     * Instance 추가 (양방향 관계 설정)
     *
     * <p>CRITICAL: 카운터 업데이트 제거 (2026-01-05)
     * - numberOfInstances 증가 제거 → Optimistic Lock 충돌 방지
     * - study.incrementInstanceCount() 호출 제거 → Study @Version 증가 방지
     * - Instance 개수는 @Transient 메서드로 실시간 계산
     *
     * <p>검증:
     * <ul>
     *   <li>Instance null 체크</li>
     *   <li>중복 추가 방지</li>
     *   <li>다른 Series에 이미 속한 경우 방지</li>
     * </ul>
     *
     * @param instance 추가할 Instance
     * @throws NullPointerException instance가 null인 경우
     * @throws IllegalStateException instance가 이미 존재하거나 다른 Series에 속한 경우
     */
    public void addInstance(Instance instance) {
        // 선행조건 검증
        Objects.requireNonNull(instance, "Instance cannot be null");

        if (instances.contains(instance)) {
            throw new IllegalStateException("Instance already exists in series");
        }

        if (instance.getSeries() != null && !instance.getSeries().equals(this)) {
            throw new IllegalStateException("Instance already belongs to another series");
        }

        // 정상 로직 - 양방향 관계만 설정
        instances.add(instance);
        instance.setSeries(this);

        // ✅ 카운터 업데이트 제거 → 동시성 충돌 완전 제거
        // numberOfInstances = CounterManager.increment(numberOfInstances);  // 제거됨
        // if (study != null) {
        //     study.incrementInstanceCount();  // 제거됨
        // }
    }

    /**
     * Instance 제거 (양방향 관계 해제)
     *
     * <p>CRITICAL: 카운터 업데이트 제거 (2026-01-05)
     * - numberOfInstances 감소 제거 → Optimistic Lock 충돌 방지
     * - study.decrementInstanceCount() 호출 제거 → Study @Version 증가 방지
     *
     * <p>검증:
     * <ul>
     *   <li>Instance null 체크</li>
     * </ul>
     *
     * @param instance 제거할 Instance
     * @throws NullPointerException instance가 null인 경우
     */
    public void removeInstance(Instance instance) {
        Objects.requireNonNull(instance, "Instance cannot be null");

        boolean removed = instances.remove(instance);
        if (removed) {
            instance.setSeries(null);

            // ✅ 카운터 업데이트 제거 → 동시성 충돌 완전 제거
            // numberOfInstances = CounterManager.decrement(numberOfInstances);  // 제거됨
            // if (study != null) {
            //     study.decrementInstanceCount();  // 제거됨
            // }
        }
    }

    /**
     * numberOfInstances 실시간 계산 (Transient)
     *
     * <p>역정규화 필드를 제거하고 컬렉션 기반으로 실시간 계산합니다.
     * Hibernate가 instances 컬렉션을 lazy loading하여 개수를 계산합니다.
     *
     * <p>성능 고려사항:
     * <ul>
     *   <li>컬렉션이 이미 로드된 경우: O(1) - 컬렉션 크기만 반환</li>
     *   <li>컬렉션 미로드 시: Repository COUNT 쿼리 사용 권장</li>
     * </ul>
     *
     * @return Instance 개수
     */
    @Transient
    public int getNumberOfInstances() {
        return instances != null ? instances.size() : 0;
    }

    // ========== 컬렉션 접근 제어 ==========

    /**
     * Series의 Instance 목록 조회
     *
     * <p>불변 뷰를 반환하여 외부에서 직접 수정할 수 없도록 보호합니다.
     * Instance를 추가하거나 제거하려면 addInstance() 또는 removeInstance()를 사용하세요.
     *
     * @return Instance 목록의 불변 뷰
     */
    public List<Instance> getInstances() {
        return Collections.unmodifiableList(instances);
    }

    // ========== Builder Pattern ==========

    /**
     * Series Entity Builder
     *
     * <p>유창한(fluent) API를 제공하여 Series 객체를 쉽게 생성할 수 있습니다.
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * Series series = Series.builder()
     *     .study(study)
     *     .seriesInstanceUid("1.2.840.113619.2.55.1.123456.789")
     *     .seriesNumber(1)
     *     .modality("CT")
     *     .seriesDescription("Chest")
     *     .build();
     * }
     * </pre>
     */
    public static class Builder {
        private final Series series = new Series();

        public Builder study(Study study) {
            series.study = study;
            return this;
        }

        public Builder seriesInstanceUid(String seriesInstanceUid) {
            series.seriesInstanceUid = seriesInstanceUid;
            return this;
        }

        public Builder modality(String modality) {
            series.modality = modality;
            return this;
        }

        public Builder seriesDescription(String seriesDescription) {
            series.seriesDescription = seriesDescription;
            return this;
        }

        public Builder bodyPartExamined(String bodyPartExamined) {
            series.bodyPartExamined = bodyPartExamined;
            return this;
        }

        public Builder manufacturer(String manufacturer) {
            series.manufacturer = manufacturer;
            return this;
        }

        public Builder manufacturerModelName(String manufacturerModelName) {
            series.manufacturerModelName = manufacturerModelName;
            return this;
        }

        public Builder seriesNumber(Integer seriesNumber) {
            series.seriesNumber = seriesNumber;
            return this;
        }

        /**
         * Series 객체 생성
         *
         * <p>필수 필드 검증을 수행합니다.
         *
         * @return 생성된 Series 객체
         * @throws NullPointerException 필수 필드가 null인 경우
         */
        public Series build() {
            Objects.requireNonNull(series.study, "Study is required");
            Objects.requireNonNull(series.seriesInstanceUid, "Series Instance UID is required");
            return series;
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
