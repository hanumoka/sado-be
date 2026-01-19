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
        @Index(name = "idx_study_patient_date", columnList = "patient_id, study_date"),
        @Index(name = "idx_study_instance_uid", columnList = "study_instance_uid")
    }
)
// @EntityListeners 제거 (2026-01-05) - PatientStatisticsListener 제거로 인한 Deadlock 해결
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

    // ========== 역정규화 카운터 제거 (2026-01-05) ==========
    // numberOfSeries 필드 제거 → series.size()로 실시간 계산
    // numberOfInstances 필드 제거 → COUNT 쿼리로 실시간 계산
    // @Version 제거 → Optimistic Lock 충돌 완전 제거
    // 목적: Deadlock 완전 제거 + 동시성 극대화

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
     * Series 추가 (양방향 관계 설정)
     *
     * <p>CRITICAL: 카운터 업데이트 제거 (2026-01-05)
     * - numberOfSeries 증가 제거 → Deadlock 방지
     * - Series 개수는 series.size()로 실시간 계산
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

        // 정상 로직 - 양방향 관계만 설정
        series.add(seriesItem);
        seriesItem.setStudy(this);
        // numberOfSeries 카운터 업데이트 제거 (2026-01-05)
    }

    /**
     * Series 제거 (양방향 관계 해제)
     *
     * <p>CRITICAL: 카운터 업데이트 제거 (2026-01-05)
     * - numberOfSeries 감소 제거 → Deadlock 방지
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
            // numberOfSeries 카운터 업데이트 제거 (2026-01-05)
        }
    }

    // ========== Instance 카운터 메서드 제거 (2026-01-05) ==========
    // incrementInstanceCount() 제거 → Instance 추가 시 Study @Version 증가 안 함
    // decrementInstanceCount() 제거 → 동시성 충돌 원인 제거

    /**
     * @deprecated (2026-01-05) numberOfSeries 필드 제거로 인해 불필요
     * Series 개수는 series.size()로 실시간 계산됨
     */
    @Deprecated(since = "2026-01-05", forRemoval = true)
    public void recalculateCounts() {
        // numberOfSeries 필드 제거됨 → 메서드 불필요
        // numberOfInstances는 @Transient 메서드로 실시간 계산
    }

    /**
     * numberOfSeries 실시간 계산 (Transient)
     *
     * <p>역정규화 필드를 제거하고 컬렉션 기반으로 실시간 계산합니다.
     *
     * @return Series 개수
     */
    @Transient
    public int getNumberOfSeries() {
        return series != null ? series.size() : 0;
    }

    /**
     * numberOfInstances 실시간 계산 (Transient)
     *
     * <p>역정규화 필드를 제거하고 컬렉션 기반으로 실시간 계산합니다.
     * Hibernate가 series 컬렉션을 lazy loading하여 각 Series의 Instance 개수를 합산합니다.
     *
     * <p>성능 고려사항:
     * <ul>
     *   <li>N+1 문제 방지: JOIN FETCH로 series 로드 시 사용</li>
     *   <li>대용량 데이터: Repository COUNT 쿼리 사용 권장</li>
     * </ul>
     *
     * @return 모든 Series의 Instance 개수 합
     */
    @Transient
    public int getNumberOfInstances() {
        if (series == null || series.isEmpty()) {
            return 0;
        }

        return series.stream()
                .mapToInt(s -> s.getNumberOfInstances())
                .sum();
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
