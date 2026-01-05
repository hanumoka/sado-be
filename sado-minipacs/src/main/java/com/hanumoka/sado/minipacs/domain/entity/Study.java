package com.hanumoka.sado.minipacs.domain.entity;

import com.hanumoka.sado.common.entity.TenantAwareEntity;
import com.hanumoka.sado.minipacs.domain.util.CounterManager;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Application Domain Layer - 검사 비즈니스 엔티티
 * DICOM Storage Layer와 분리 (2-Layer 아키텍처)
 */
@Entity
@Table(
    name = "study",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_study_instance_uid",
        columnNames = {"tenant_id", "study_instance_uid"}
    ),
    indexes = {
        @Index(name = "idx_study_patient_date", columnList = "patient_id, study_date")
    }
)
@EntityListeners(com.hanumoka.sado.minipacs.domain.listener.PatientStatisticsListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Study extends TenantAwareEntity {

    // ========== DICOM Storage Layer 간접 참조 (Option B) ==========

    /**
     * DICOM Study UID (DicomMetadataRecord 조회 키)
     * DICOM 업로드 시 자동 추출 (필수)
     */
    @Column(name = "study_instance_uid", length = 256, nullable = false)
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

    /**
     * Optimistic Lock Version
     *
     * <p>CRITICAL: 동시성 제어
     * - Pessimistic Lock 대신 Optimistic Lock 사용
     * - 동시 Series 추가 시 OptimisticLockingFailureException 발생
     * - @Retryable로 자동 재시도
     * - Deadlock 위험 제거
     */
    @Version
    @Column(name = "version")
    private Long version;

    // ========== Series 관계 ==========

    /**
     * 검사의 시리즈 목록
     * 1개의 검사 → N개의 시리즈
     *
     * CRITICAL: CascadeType.ALL 제거 (데이터 무결성 보호)
     * - Study 삭제 시 Series/Instance 연쇄 삭제 방지
     * - orphanRemoval 제거하여 실수로 인한 데이터 손실 방지
     */
    @OneToMany(mappedBy = "study", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<Series> series = new ArrayList<>();

    // ========== 비즈니스 메서드 ==========

    /**
     * Series 추가 (양방향 관계 설정 + 카운터 증가)
     *
     * <p>검증:
     * <ul>
     *   <li>Series null 체크</li>
     *   <li>중복 추가 방지</li>
     *   <li>다른 Study에 이미 속한 경우 방지</li>
     * </ul>
     *
     * @param seriesItem 추가할 Series
     * @throws NullPointerException  seriesItem이 null인 경우
     * @throws IllegalStateException seriesItem이 이미 존재하거나 다른 Study에 속한 경우
     */
    public void addSeries(Series seriesItem) {
        // 선행조건 검증
        Objects.requireNonNull(seriesItem, "Series cannot be null");

        if (series.contains(seriesItem)) {
            throw new IllegalStateException("Series already exists in study");
        }

        if (seriesItem.getStudy() != null && !seriesItem.getStudy().equals(this)) {
            throw new IllegalStateException("Series already belongs to another study");
        }

        // 정상 로직
        series.add(seriesItem);
        seriesItem.setStudy(this);
        numberOfSeries = CounterManager.increment(numberOfSeries);
    }

    /**
     * Series 제거 (양방향 관계 해제 + 카운터 감소)
     *
     * <p>검증:
     * <ul>
     *   <li>Series null 체크</li>
     *   <li>Orphan Instance 보호 (Instance가 남아있으면 삭제 불가)</li>
     * </ul>
     *
     * @param seriesItem 제거할 Series
     * @throws NullPointerException  seriesItem이 null인 경우
     * @throws IllegalStateException seriesItem에 Instance가 남아있는 경우
     */
    public void removeSeries(Series seriesItem) {
        Objects.requireNonNull(seriesItem, "Series cannot be null");

        // Orphan Instance 보호
        if (!seriesItem.getInstances().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot remove series with " + seriesItem.getInstances().size() +
                            " instances. Delete instances first."
            );
        }

        boolean removed = series.remove(seriesItem);
        if (removed) {
            seriesItem.setStudy(null);
            numberOfSeries = CounterManager.decrement(numberOfSeries);
        }
    }

    /**
     * Instance 추가 시 numberOfInstances 증가
     * Series.addInstance()에서 호출됨
     */
    public void incrementInstanceCount() {
        numberOfInstances = CounterManager.increment(numberOfInstances);
    }

    /**
     * Instance 제거 시 numberOfInstances 감소
     * Series.removeInstance()에서 호출됨
     */
    public void decrementInstanceCount() {
        numberOfInstances = CounterManager.decrement(numberOfInstances);
    }

    /**
     * 정합성 복구: 실제 데이터 기반으로 카운트 재계산
     * 데이터 마이그레이션 또는 수동 복구 시 사용
     */
    public void recalculateCounts() {
        // numberOfSeries 재계산
        numberOfSeries = CounterManager.fromCollectionSize(series);

        // numberOfInstances 재계산 (모든 Series의 Instance 합)
        numberOfInstances = 0;
        if (series != null) {
            for (Series s : series) {
                if (s.getNumberOfInstances() != null) {
                    numberOfInstances += s.getNumberOfInstances();
                }
            }
        }
    }

    // ========== 컬렉션 접근 제어 ==========

    /**
     * Study의 Series 목록 조회
     *
     * <p>불변 뷰를 반환하여 외부에서 직접 수정할 수 없도록 보호합니다.
     * Series를 추가하거나 제거하려면 addSeries() 또는 removeSeries()를 사용하세요.
     *
     * @return Series 목록의 불변 뷰
     */
    public List<Series> getSeries() {
        return Collections.unmodifiableList(series);
    }

    // ========== Builder Pattern ==========

    /**
     * Study Entity Builder
     *
     * <p>유창한(fluent) API를 제공하여 Study 객체를 쉽게 생성할 수 있습니다.
     *
     * <p>사용 예시:
     * <pre>
     * {@code
     * Study study = Study.builder()
     *     .patient(patient)
     *     .studyInstanceUid("1.2.840.113619.2.55.1.123456")
     *     .studyDate(LocalDate.now())
     *     .studyDescription("Chest CT")
     *     .build();
     * }
     * </pre>
     */
    public static class Builder {
        private final Study study = new Study();

        public Builder patient(Patient patient) {
            study.patient = patient;
            return this;
        }

        public Builder studyInstanceUid(String studyInstanceUid) {
            study.studyInstanceUid = studyInstanceUid;
            return this;
        }

        public Builder studyDate(LocalDate studyDate) {
            study.studyDate = studyDate;
            return this;
        }

        public Builder studyDescription(String studyDescription) {
            study.studyDescription = studyDescription;
            return this;
        }

        /**
         * Study 객체 생성
         *
         * <p>필수 필드 검증을 수행합니다.
         *
         * @return 생성된 Study 객체
         * @throws NullPointerException 필수 필드가 null인 경우
         */
        public Study build() {
            Objects.requireNonNull(study.patient, "Patient is required");
            Objects.requireNonNull(study.studyInstanceUid, "Study Instance UID is required");
            return study;
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
