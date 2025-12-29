package com.hanumoka.sado.minipacs.entity;

import com.hanumoka.sado.minipacs.domain.entity.Instance;
import com.hanumoka.sado.minipacs.domain.entity.Series;
import com.hanumoka.sado.minipacs.domain.entity.Study;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Study Entity Unit Tests
 *
 * <p>Testing Strategy:
 * <ul>
 *   <li>Test entity business methods (no mocking needed)</li>
 *   <li>Test denormalized field update logic</li>
 *   <li>Test count increment/decrement operations</li>
 *   <li>Test recalculate methods for data consistency</li>
 * </ul>
 *
 * <p>Methods Tested:
 * <ul>
 *   <li>addSeries() - 시리즈 추가 및 numberOfSeries 증가</li>
 *   <li>removeSeries() - 시리즈 제거 및 numberOfSeries 감소</li>
 *   <li>incrementInstanceCount() - Instance 카운트 증가</li>
 *   <li>decrementInstanceCount() - Instance 카운트 감소</li>
 *   <li>recalculateCounts() - 정합성 복구</li>
 * </ul>
 */
@DisplayName("Study Entity Unit Tests")
class StudyTest {

    // ========== incrementInstanceCount() ==========

    @Nested
    @DisplayName("incrementInstanceCount() - Instance 카운트 증가")
    class IncrementInstanceCountTests {

        @Test
        @DisplayName("null에서 시작하면 1로 초기화")
        void incrementInstanceCount_FromNull_InitializesToOne() {
            // Given: numberOfInstances가 null인 Study
            Study study = new Study();
            assertThat(study.getNumberOfInstances()).isNull();

            // When: incrementInstanceCount() 호출
            study.incrementInstanceCount();

            // Then: 1로 초기화됨
            assertThat(study.getNumberOfInstances()).isEqualTo(1);
        }

        @Test
        @DisplayName("기존 값에서 1씩 증가")
        void incrementInstanceCount_FromExisting_IncrementsByOne() {
            // Given: numberOfInstances가 5인 Study
            Study study = new Study();
            study.setNumberOfInstances(5);

            // When: incrementInstanceCount() 호출
            study.incrementInstanceCount();

            // Then: 6으로 증가
            assertThat(study.getNumberOfInstances()).isEqualTo(6);
        }

        @Test
        @DisplayName("여러 번 호출 시 누적 증가")
        void incrementInstanceCount_MultipleCalls_AccumulatesCorrectly() {
            // Given: numberOfInstances가 null인 Study
            Study study = new Study();

            // When: incrementInstanceCount() 3번 호출
            study.incrementInstanceCount();
            study.incrementInstanceCount();
            study.incrementInstanceCount();

            // Then: 3으로 누적 증가
            assertThat(study.getNumberOfInstances()).isEqualTo(3);
        }

        @Test
        @DisplayName("0에서 시작하면 1로 증가")
        void incrementInstanceCount_FromZero_IncreasesToOne() {
            // Given: numberOfInstances가 0인 Study
            Study study = new Study();
            study.setNumberOfInstances(0);

            // When: incrementInstanceCount() 호출
            study.incrementInstanceCount();

            // Then: 1로 증가
            assertThat(study.getNumberOfInstances()).isEqualTo(1);
        }
    }

    // ========== decrementInstanceCount() ==========

    @Nested
    @DisplayName("decrementInstanceCount() - Instance 카운트 감소")
    class DecrementInstanceCountTests {

        @Test
        @DisplayName("null에서는 감소하지 않음")
        void decrementInstanceCount_FromNull_RemainsNull() {
            // Given: numberOfInstances가 null인 Study
            Study study = new Study();
            assertThat(study.getNumberOfInstances()).isNull();

            // When: decrementInstanceCount() 호출
            study.decrementInstanceCount();

            // Then: null 유지 (감소하지 않음)
            assertThat(study.getNumberOfInstances()).isNull();
        }

        @Test
        @DisplayName("0에서는 감소하지 않음 (음수 방지)")
        void decrementInstanceCount_FromZero_RemainsZero() {
            // Given: numberOfInstances가 0인 Study
            Study study = new Study();
            study.setNumberOfInstances(0);

            // When: decrementInstanceCount() 호출
            study.decrementInstanceCount();

            // Then: 0 유지 (음수 방지)
            assertThat(study.getNumberOfInstances()).isEqualTo(0);
        }

        @Test
        @DisplayName("기존 값에서 1씩 감소")
        void decrementInstanceCount_FromExisting_DecrementsByOne() {
            // Given: numberOfInstances가 10인 Study
            Study study = new Study();
            study.setNumberOfInstances(10);

            // When: decrementInstanceCount() 호출
            study.decrementInstanceCount();

            // Then: 9로 감소
            assertThat(study.getNumberOfInstances()).isEqualTo(9);
        }

        @Test
        @DisplayName("여러 번 호출 시 누적 감소")
        void decrementInstanceCount_MultipleCalls_AccumulatesCorrectly() {
            // Given: numberOfInstances가 5인 Study
            Study study = new Study();
            study.setNumberOfInstances(5);

            // When: decrementInstanceCount() 3번 호출
            study.decrementInstanceCount();
            study.decrementInstanceCount();
            study.decrementInstanceCount();

            // Then: 2로 누적 감소
            assertThat(study.getNumberOfInstances()).isEqualTo(2);
        }

        @Test
        @DisplayName("1에서 0으로 감소 가능")
        void decrementInstanceCount_FromOne_DecreasesToZero() {
            // Given: numberOfInstances가 1인 Study
            Study study = new Study();
            study.setNumberOfInstances(1);

            // When: decrementInstanceCount() 호출
            study.decrementInstanceCount();

            // Then: 0으로 감소 (정상)
            assertThat(study.getNumberOfInstances()).isEqualTo(0);
        }
    }

    // ========== recalculateCounts() ==========

    @Nested
    @DisplayName("recalculateCounts() - 정합성 복구")
    class RecalculateCountsTests {

        @Test
        @DisplayName("Series가 없으면 모든 카운트 0")
        void recalculateCounts_NoSeries_SetsCountsToZero() {
            // Given: Series가 없는 Study
            Study study = new Study();
            study.setNumberOfSeries(999);  // 잘못된 값
            study.setNumberOfInstances(999);  // 잘못된 값

            // When: recalculateCounts() 호출
            study.recalculateCounts();

            // Then: 모든 카운트 0으로 재계산됨
            assertThat(study.getNumberOfSeries()).isEqualTo(0);
            assertThat(study.getNumberOfInstances()).isEqualTo(0);
        }

        @Test
        @DisplayName("실제 Series 개수로 numberOfSeries 재계산")
        void recalculateCounts_WithSeries_UpdatesNumberOfSeries() {
            // Given: 3개의 Series가 있는 Study
            Study study = new Study();
            study.setNumberOfSeries(999);  // 잘못된 값

            Series series1 = new Series();
            Series series2 = new Series();
            Series series3 = new Series();

            study.addSeries(series1);
            study.addSeries(series2);
            study.addSeries(series3);

            // numberOfSeries를 다시 잘못된 값으로 설정 (정합성 깨짐 시뮬레이션)
            study.setNumberOfSeries(999);

            // When: recalculateCounts() 호출
            study.recalculateCounts();

            // Then: 실제 Series 개수로 재계산됨
            assertThat(study.getNumberOfSeries()).isEqualTo(3);
        }

        @Test
        @DisplayName("각 Series의 Instance 개수를 합산하여 numberOfInstances 재계산")
        void recalculateCounts_WithSeriesAndInstances_UpdatesNumberOfInstances() {
            // Given: Study와 Series 생성
            Study study = new Study();
            study.setNumberOfInstances(999);  // 잘못된 값

            // Series 1: 5개 Instance
            Series series1 = new Series();
            series1.setNumberOfInstances(5);
            study.addSeries(series1);

            // Series 2: 3개 Instance
            Series series2 = new Series();
            series2.setNumberOfInstances(3);
            study.addSeries(series2);

            // Series 3: 7개 Instance
            Series series3 = new Series();
            series3.setNumberOfInstances(7);
            study.addSeries(series3);

            // numberOfInstances를 다시 잘못된 값으로 설정 (정합성 깨짐 시뮬레이션)
            study.setNumberOfInstances(999);

            // When: recalculateCounts() 호출
            study.recalculateCounts();

            // Then: 실제 Instance 합계로 재계산됨 (5 + 3 + 7 = 15)
            assertThat(study.getNumberOfInstances()).isEqualTo(15);
        }

        @Test
        @DisplayName("Series의 numberOfInstances가 null인 경우 0으로 계산")
        void recalculateCounts_WithNullInstanceCounts_TreatsAsZero() {
            // Given: Study와 Series 생성
            Study study = new Study();

            // Series 1: numberOfInstances가 null
            Series series1 = new Series();
            series1.setNumberOfInstances(null);
            study.addSeries(series1);

            // Series 2: numberOfInstances가 5
            Series series2 = new Series();
            series2.setNumberOfInstances(5);
            study.addSeries(series2);

            // When: recalculateCounts() 호출
            study.recalculateCounts();

            // Then: null은 0으로 계산되어 총합 5
            assertThat(study.getNumberOfInstances()).isEqualTo(5);
            assertThat(study.getNumberOfSeries()).isEqualTo(2);
        }

        @Test
        @DisplayName("모든 Series의 numberOfInstances가 null이면 numberOfInstances는 0")
        void recalculateCounts_AllNullInstanceCounts_SetsToZero() {
            // Given: Study와 Series 생성 (모든 Series의 numberOfInstances가 null)
            Study study = new Study();

            Series series1 = new Series();
            series1.setNumberOfInstances(null);
            study.addSeries(series1);

            Series series2 = new Series();
            series2.setNumberOfInstances(null);
            study.addSeries(series2);

            // When: recalculateCounts() 호출
            study.recalculateCounts();

            // Then: numberOfInstances는 0, numberOfSeries는 2
            assertThat(study.getNumberOfInstances()).isEqualTo(0);
            assertThat(study.getNumberOfSeries()).isEqualTo(2);
        }
    }

    // ========== addSeries() & removeSeries() ==========

    @Nested
    @DisplayName("addSeries() & removeSeries() - Series 추가/제거")
    class AddRemoveSeriesTests {

        @Test
        @DisplayName("addSeries() 호출 시 numberOfSeries 증가")
        void addSeries_IncreasesNumberOfSeries() {
            // Given: Study 생성
            Study study = new Study();

            // When: Series 추가
            Series series = new Series();
            study.addSeries(series);

            // Then: numberOfSeries가 1로 증가
            assertThat(study.getNumberOfSeries()).isEqualTo(1);
            assertThat(study.getSeries()).hasSize(1);
            assertThat(series.getStudy()).isEqualTo(study);
        }

        @Test
        @DisplayName("removeSeries() 호출 시 numberOfSeries 감소")
        void removeSeries_DecreasesNumberOfSeries() {
            // Given: Series가 1개 있는 Study
            Study study = new Study();
            Series series = new Series();
            study.addSeries(series);

            assertThat(study.getNumberOfSeries()).isEqualTo(1);

            // When: Series 제거
            study.removeSeries(series);

            // Then: numberOfSeries가 0으로 감소
            assertThat(study.getNumberOfSeries()).isEqualTo(0);
            assertThat(study.getSeries()).isEmpty();
            assertThat(series.getStudy()).isNull();
        }

        @Test
        @DisplayName("여러 Series 추가 시 numberOfSeries 정확히 계산")
        void addMultipleSeries_CalculatesCorrectly() {
            // Given: Study 생성
            Study study = new Study();

            // When: 3개 Series 추가
            study.addSeries(new Series());
            study.addSeries(new Series());
            study.addSeries(new Series());

            // Then: numberOfSeries가 3
            assertThat(study.getNumberOfSeries()).isEqualTo(3);
            assertThat(study.getSeries()).hasSize(3);
        }
    }
}
