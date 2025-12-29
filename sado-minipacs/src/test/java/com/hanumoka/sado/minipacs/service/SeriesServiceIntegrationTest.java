package com.hanumoka.sado.minipacs.service;

import com.hanumoka.sado.minipacs.domain.entity.Patient;
import com.hanumoka.sado.minipacs.domain.entity.Series;
import com.hanumoka.sado.minipacs.domain.entity.Study;
import com.hanumoka.sado.minipacs.domain.repository.PatientRepository;
import com.hanumoka.sado.minipacs.domain.repository.SeriesRepository;
import com.hanumoka.sado.minipacs.domain.repository.StudyRepository;
import com.hanumoka.sado.minipacs.domain.service.PatientService;
import com.hanumoka.sado.minipacs.domain.service.SeriesService;
import com.hanumoka.sado.minipacs.domain.service.StudyService;
import com.hanumoka.sado.minipacs.support.BaseIntegrationTest;
import com.hanumoka.sado.minipacs.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SeriesService Integration Tests
 *
 * <p>Testing Strategy:
 * <ul>
 *   <li>Use real Spring context and database (extends BaseIntegrationTest)</li>
 *   <li>Test denormalized field updates from Series to Study</li>
 *   <li>Test cascade updates when Series is created/deleted</li>
 *   <li>Verify actual database state after operations</li>
 *   <li>@Transactional ensures automatic rollback after each test</li>
 * </ul>
 *
 * <p>Scenarios Tested:
 * <ul>
 *   <li>Series 생성 시 Study.numberOfSeries 증가</li>
 *   <li>Series 삭제 시 Study.numberOfSeries 감소</li>
 *   <li>여러 Series 추가/삭제 시 카운트 정확성</li>
 * </ul>
 */
@DisplayName("SeriesService Integration Tests")
class SeriesServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SeriesService seriesService;

    @Autowired
    private StudyService studyService;

    @Autowired
    private PatientService patientService;

    @Autowired
    private SeriesRepository seriesRepository;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private PatientRepository patientRepository;

    // ========== Series 생성 시 카운트 증가 ==========

    @Nested
    @DisplayName("Series 생성 시 Study.numberOfSeries 증가")
    class CreateSeriesTests {

        @Test
        @DisplayName("Series 생성 시 Study.numberOfSeries가 1 증가")
        void createSeries_IncreasesStudyCount() {
            // Given: Patient → Study 생성
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            // 초기 상태 확인
            assertThat(study.getNumberOfSeries()).isNull();

            // When: Series 생성
            Series series = new Series();
            series.setSeriesInstanceUid("1.2.3.4.5");
            series.setStudy(study);
            series = seriesService.createSeries(series);

            // Then: Study numberOfSeries가 1로 증가
            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfSeries()).isEqualTo(1);
        }

        @Test
        @DisplayName("여러 Series 생성 시 numberOfSeries 누적 증가")
        void createMultipleSeries_AccumulatesCorrectly() {
            // Given: Patient → Study 생성
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            // When: 3개의 Series 생성
            for (int i = 1; i <= 3; i++) {
                Series series = new Series();
                series.setSeriesInstanceUid("1.2.3.4." + i);
                series.setStudy(study);
                seriesService.createSeries(series);
            }

            // Then: Study numberOfSeries가 3
            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfSeries()).isEqualTo(3);
        }

        @Test
        @DisplayName("findOrCreateSeries() 사용 시에도 카운트 증가")
        void findOrCreateSeries_NewSeries_IncreasesCount() {
            // Given: Patient → Study 생성
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            // When: findOrCreateSeries() 호출 (신규 생성)
            Series series = seriesService.findOrCreateSeries(
                    "1.2.3.4.5",  // seriesInstanceUid
                    study,
                    1,  // seriesNumber
                    "US",  // modality
                    "Test Series",  // seriesDescription
                    "HEART"  // bodyPartExamined
            );

            // Then: Study numberOfSeries가 1로 증가
            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfSeries()).isEqualTo(1);
        }

        @Test
        @DisplayName("findOrCreateSeries() - 기존 Series 조회 시 카운트 증가 안 함")
        void findOrCreateSeries_ExistingSeries_DoesNotIncreaseCount() {
            // Given: 기존 Series가 이미 있는 상태
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            // 첫 번째 호출: 신규 생성
            Series series1 = seriesService.findOrCreateSeries(
                    "1.2.3.4.5",
                    study,
                    1,
                    "US",
                    "Test Series",
                    "HEART"
            );

            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfSeries()).isEqualTo(1);

            // When: 두 번째 호출 (같은 Series Instance UID) - 기존 조회
            Series series2 = seriesService.findOrCreateSeries(
                    "1.2.3.4.5",  // 동일 UID
                    study,
                    1,
                    "US",
                    "Test Series",
                    "HEART"
            );

            // Then: 같은 Series 반환, 카운트 증가 안 함
            assertThat(series1.getId()).isEqualTo(series2.getId());

            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfSeries()).isEqualTo(1);  // 여전히 1
        }

        @Test
        @DisplayName("Series 생성 시 양방향 관계 설정됨")
        void createSeries_SetsBidirectionalRelationship() {
            // Given: Patient → Study 생성
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            // When: Series 생성
            Series series = new Series();
            series.setSeriesInstanceUid("1.2.3.4.5");
            series.setStudy(study);
            series = seriesService.createSeries(series);

            // Then: 양방향 관계 설정 확인
            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getSeries()).hasSize(1);
            assertThat(study.getSeries().get(0).getId()).isEqualTo(series.getId());
        }
    }

    // ========== Series 삭제 시 카운트 감소 ==========

    @Nested
    @DisplayName("Series 삭제 시 Study.numberOfSeries 감소")
    class DeleteSeriesTests {

        @Test
        @DisplayName("Series 삭제 시 Study.numberOfSeries가 1 감소")
        void deleteSeries_DecreasesStudyCount() {
            // Given: Series가 1개 있는 Study
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            Series series = new Series();
            series.setSeriesInstanceUid("1.2.3.4.5");
            series.setStudy(study);
            series = seriesService.createSeries(series);

            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfSeries()).isEqualTo(1);

            // When: Series 삭제
            seriesService.deleteSeries(series.getId());

            // Then: Study numberOfSeries가 0으로 감소
            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfSeries()).isEqualTo(0);
        }

        @Test
        @DisplayName("여러 Series 중 일부 삭제 시 정확히 감소")
        void deleteOneSeries_FromMultiple_DecreasesCorrectly() {
            // Given: 3개 Series가 있는 Study
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            Series series1 = new Series();
            series1.setSeriesInstanceUid("1.2.3.4.5");
            series1.setStudy(study);
            seriesService.createSeries(series1);

            Series series2 = new Series();
            series2.setSeriesInstanceUid("1.2.3.4.6");
            series2.setStudy(study);
            series2 = seriesService.createSeries(series2);

            Series series3 = new Series();
            series3.setSeriesInstanceUid("1.2.3.4.7");
            series3.setStudy(study);
            seriesService.createSeries(series3);

            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfSeries()).isEqualTo(3);

            // When: 1개만 삭제
            seriesService.deleteSeries(series2.getId());

            // Then: 2로 감소
            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfSeries()).isEqualTo(2);
        }

        @Test
        @DisplayName("모든 Series 삭제 시 numberOfSeries는 0")
        void deleteAllSeries_SetsCountToZero() {
            // Given: 2개 Series가 있는 Study
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            Series series1 = new Series();
            series1.setSeriesInstanceUid("1.2.3.4.5");
            series1.setStudy(study);
            series1 = seriesService.createSeries(series1);

            Series series2 = new Series();
            series2.setSeriesInstanceUid("1.2.3.4.6");
            series2.setStudy(study);
            series2 = seriesService.createSeries(series2);

            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfSeries()).isEqualTo(2);

            // When: 모두 삭제
            seriesService.deleteSeries(series1.getId());
            seriesService.deleteSeries(series2.getId());

            // Then: numberOfSeries는 0
            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfSeries()).isEqualTo(0);
            assertThat(study.getSeries()).isEmpty();
        }
    }

    // ========== 복합 시나리오 ==========

    @Nested
    @DisplayName("Series 추가/삭제 복합 시나리오")
    class ComplexScenarioTests {

        @Test
        @DisplayName("Series 추가 후 삭제 반복 시 카운트 정확성")
        void addAndDeleteSeries_Repeatedly_MaintainsAccuracy() {
            // Given: Patient → Study 생성
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            // When: Series 3개 추가
            Series series1 = new Series();
            series1.setSeriesInstanceUid("1.2.3.4.5");
            series1.setStudy(study);
            series1 = seriesService.createSeries(series1);

            Series series2 = new Series();
            series2.setSeriesInstanceUid("1.2.3.4.6");
            series2.setStudy(study);
            series2 = seriesService.createSeries(series2);

            Series series3 = new Series();
            series3.setSeriesInstanceUid("1.2.3.4.7");
            series3.setStudy(study);
            series3 = seriesService.createSeries(series3);

            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfSeries()).isEqualTo(3);

            // When: 1개 삭제
            seriesService.deleteSeries(series2.getId());

            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfSeries()).isEqualTo(2);

            // When: 1개 더 추가
            Series series4 = new Series();
            series4.setSeriesInstanceUid("1.2.3.4.8");
            series4.setStudy(study);
            seriesService.createSeries(series4);

            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfSeries()).isEqualTo(3);

            // When: 2개 삭제
            seriesService.deleteSeries(series1.getId());
            seriesService.deleteSeries(series3.getId());

            // Then: 최종 1개 남음
            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfSeries()).isEqualTo(1);
        }

        @Test
        @DisplayName("Study에 여러 Series와 Instance가 있을 때 정합성 유지")
        void studyWithMultipleSeriesAndInstances_MaintainsConsistency() {
            // Given: Patient → Study 생성
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            // When: 2개 Series 생성
            Series series1 = new Series();
            series1.setSeriesInstanceUid("1.2.3.4.5");
            series1.setStudy(study);
            series1 = seriesService.createSeries(series1);

            Series series2 = new Series();
            series2.setSeriesInstanceUid("1.2.3.4.6");
            series2.setStudy(study);
            series2 = seriesService.createSeries(series2);

            // Then: numberOfSeries 확인
            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfSeries()).isEqualTo(2);

            // When: Series 1 삭제
            seriesService.deleteSeries(series1.getId());

            // Then: numberOfSeries 정확히 감소
            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfSeries()).isEqualTo(1);
            assertThat(study.getSeries()).hasSize(1);
            assertThat(study.getSeries().get(0).getId()).isEqualTo(series2.getId());
        }
    }
}
