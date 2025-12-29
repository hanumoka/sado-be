package com.hanumoka.sado.minipacs.service;

import com.hanumoka.sado.minipacs.domain.entity.Instance;
import com.hanumoka.sado.minipacs.domain.entity.Patient;
import com.hanumoka.sado.minipacs.domain.entity.Series;
import com.hanumoka.sado.minipacs.domain.entity.Study;
import com.hanumoka.sado.minipacs.domain.repository.InstanceRepository;
import com.hanumoka.sado.minipacs.domain.repository.PatientRepository;
import com.hanumoka.sado.minipacs.domain.repository.SeriesRepository;
import com.hanumoka.sado.minipacs.domain.repository.StudyRepository;
import com.hanumoka.sado.minipacs.domain.service.InstanceService;
import com.hanumoka.sado.minipacs.domain.service.PatientService;
import com.hanumoka.sado.minipacs.domain.service.SeriesService;
import com.hanumoka.sado.minipacs.domain.service.StudyService;
import com.hanumoka.sado.minipacs.support.BaseIntegrationTest;
import com.hanumoka.sado.minipacs.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InstanceService Integration Tests
 *
 * <p>Testing Strategy:
 * <ul>
 *   <li>Use real Spring context and database (extends BaseIntegrationTest)</li>
 *   <li>Test denormalized field updates across entity hierarchy</li>
 *   <li>Test cascade updates from Instance → Series → Study</li>
 *   <li>Verify actual database state after operations</li>
 *   <li>@Transactional ensures automatic rollback after each test</li>
 * </ul>
 *
 * <p>Scenarios Tested:
 * <ul>
 *   <li>Instance 생성 시 Series와 Study의 카운트가 모두 증가</li>
 *   <li>Instance 삭제 시 Series와 Study의 카운트가 모두 감소</li>
 *   <li>여러 Series에 Instance 추가 시 Study 카운트는 모든 Instance 합계</li>
 * </ul>
 */
@DisplayName("InstanceService Integration Tests")
class InstanceServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private InstanceService instanceService;

    @Autowired
    private SeriesService seriesService;

    @Autowired
    private StudyService studyService;

    @Autowired
    private PatientService patientService;

    @Autowired
    private InstanceRepository instanceRepository;

    @Autowired
    private SeriesRepository seriesRepository;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private PatientRepository patientRepository;

    // ========== Instance 생성 시 카운트 증가 ==========

    @Nested
    @DisplayName("Instance 생성 시 역정규화 필드 자동 업데이트")
    class CreateInstanceTests {

        @Test
        @DisplayName("Instance 생성 시 Series.numberOfInstances 증가")
        void createInstance_IncreasesSeriesCount() {
            // Given: Patient → Study → Series 생성
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            Series series = new Series();
            series.setSeriesInstanceUid("1.2.3.4.5");
            series.setStudy(study);
            series = seriesService.createSeries(series);

            // 초기 상태 확인
            assertThat(series.getNumberOfInstances()).isNull();

            // When: Instance 생성
            Instance instance = new Instance();
            instance.setSopInstanceUid("1.2.3.4.5.1");
            instance.setSeries(series);
            instance = instanceService.createInstance(instance);

            // Then: Series numberOfInstances가 1로 증가
            series = seriesRepository.findById(series.getId()).orElseThrow();
            assertThat(series.getNumberOfInstances()).isEqualTo(1);
        }

        @Test
        @DisplayName("Instance 생성 시 Study.numberOfInstances도 증가")
        void createInstance_IncreasesStudyCount() {
            // Given: Patient → Study → Series 생성
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            Series series = new Series();
            series.setSeriesInstanceUid("1.2.3.4.5");
            series.setStudy(study);
            series = seriesService.createSeries(series);

            // 초기 상태 확인
            assertThat(study.getNumberOfInstances()).isNull();
            assertThat(series.getNumberOfInstances()).isNull();

            // When: Instance 생성
            Instance instance = new Instance();
            instance.setSopInstanceUid("1.2.3.4.5.1");
            instance.setSeries(series);
            instance = instanceService.createInstance(instance);

            // Then: Series와 Study 모두 numberOfInstances가 1로 증가
            series = seriesRepository.findById(series.getId()).orElseThrow();
            study = studyRepository.findById(study.getId()).orElseThrow();

            assertThat(series.getNumberOfInstances()).isEqualTo(1);
            assertThat(study.getNumberOfInstances()).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 Series에 여러 Instance 생성 시 누적 증가")
        void createMultipleInstances_InSameSeries_AccumulatesCorrectly() {
            // Given: Patient → Study → Series 생성
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            Series series = new Series();
            series.setSeriesInstanceUid("1.2.3.4.5");
            series.setStudy(study);
            series = seriesService.createSeries(series);

            // When: 3개의 Instance 생성
            for (int i = 1; i <= 3; i++) {
                Instance instance = new Instance();
                instance.setSopInstanceUid("1.2.3.4.5." + i);
                instance.setSeries(series);
                instanceService.createInstance(instance);
            }

            // Then: Series와 Study 모두 numberOfInstances가 3
            series = seriesRepository.findById(series.getId()).orElseThrow();
            study = studyRepository.findById(study.getId()).orElseThrow();

            assertThat(series.getNumberOfInstances()).isEqualTo(3);
            assertThat(study.getNumberOfInstances()).isEqualTo(3);
        }

        @Test
        @DisplayName("findOrCreateInstance() 사용 시에도 카운트 증가")
        void findOrCreateInstance_NewInstance_IncreasesCount() {
            // Given: Patient → Study → Series 생성
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            Series series = new Series();
            series.setSeriesInstanceUid("1.2.3.4.5");
            series.setStudy(study);
            series = seriesService.createSeries(series);

            // When: findOrCreateInstance() 호출 (신규 생성)
            Instance instance = instanceService.findOrCreateInstance(
                    "1.2.3.4.5.1",  // sopInstanceUid
                    series,
                    1,  // instanceNumber
                    "1.2.840.10008.5.1.4.1.1.3.1",  // sopClassUid
                    "/storage/test.dcm",  // storagePath
                    1024L  // fileSize
            );

            // Then: Series와 Study 카운트 증가
            series = seriesRepository.findById(series.getId()).orElseThrow();
            study = studyRepository.findById(study.getId()).orElseThrow();

            assertThat(series.getNumberOfInstances()).isEqualTo(1);
            assertThat(study.getNumberOfInstances()).isEqualTo(1);
        }

        @Test
        @DisplayName("findOrCreateInstance() - 기존 Instance 조회 시 카운트 증가 안 함")
        void findOrCreateInstance_ExistingInstance_DoesNotIncreaseCount() {
            // Given: 기존 Instance가 이미 있는 상태
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            Series series = new Series();
            series.setSeriesInstanceUid("1.2.3.4.5");
            series.setStudy(study);
            series = seriesService.createSeries(series);

            // 첫 번째 호출: 신규 생성
            Instance instance1 = instanceService.findOrCreateInstance(
                    "1.2.3.4.5.1",
                    series,
                    1,
                    "1.2.840.10008.5.1.4.1.1.3.1",
                    "/storage/test.dcm",
                    1024L
            );

            series = seriesRepository.findById(series.getId()).orElseThrow();
            assertThat(series.getNumberOfInstances()).isEqualTo(1);

            // When: 두 번째 호출 (같은 SOP Instance UID) - 기존 조회
            Instance instance2 = instanceService.findOrCreateInstance(
                    "1.2.3.4.5.1",  // 동일 UID
                    series,
                    1,
                    "1.2.840.10008.5.1.4.1.1.3.1",
                    "/storage/test.dcm",
                    1024L
            );

            // Then: 같은 Instance 반환, 카운트 증가 안 함
            assertThat(instance1.getId()).isEqualTo(instance2.getId());

            series = seriesRepository.findById(series.getId()).orElseThrow();
            study = studyRepository.findById(study.getId()).orElseThrow();

            assertThat(series.getNumberOfInstances()).isEqualTo(1);  // 여전히 1
            assertThat(study.getNumberOfInstances()).isEqualTo(1);  // 여전히 1
        }
    }

    // ========== Instance 삭제 시 카운트 감소 ==========

    @Nested
    @DisplayName("Instance 삭제 시 역정규화 필드 자동 업데이트")
    class DeleteInstanceTests {

        @Test
        @DisplayName("Instance 삭제 시 Series.numberOfInstances 감소")
        void deleteInstance_DecreasesSeriesCount() {
            // Given: Instance가 1개 있는 Series
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            Series series = new Series();
            series.setSeriesInstanceUid("1.2.3.4.5");
            series.setStudy(study);
            series = seriesService.createSeries(series);

            Instance instance = new Instance();
            instance.setSopInstanceUid("1.2.3.4.5.1");
            instance.setSeries(series);
            instance = instanceService.createInstance(instance);

            series = seriesRepository.findById(series.getId()).orElseThrow();
            assertThat(series.getNumberOfInstances()).isEqualTo(1);

            // When: Instance 삭제
            instanceService.deleteInstance(instance.getId());

            // Then: Series numberOfInstances가 0으로 감소
            series = seriesRepository.findById(series.getId()).orElseThrow();
            assertThat(series.getNumberOfInstances()).isEqualTo(0);
        }

        @Test
        @DisplayName("Instance 삭제 시 Study.numberOfInstances도 감소")
        void deleteInstance_DecreasesStudyCount() {
            // Given: Instance가 1개 있는 Study
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            Series series = new Series();
            series.setSeriesInstanceUid("1.2.3.4.5");
            series.setStudy(study);
            series = seriesService.createSeries(series);

            Instance instance = new Instance();
            instance.setSopInstanceUid("1.2.3.4.5.1");
            instance.setSeries(series);
            instance = instanceService.createInstance(instance);

            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfInstances()).isEqualTo(1);

            // When: Instance 삭제
            instanceService.deleteInstance(instance.getId());

            // Then: Study numberOfInstances도 0으로 감소
            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfInstances()).isEqualTo(0);
        }

        @Test
        @DisplayName("여러 Instance 중 일부 삭제 시 정확히 감소")
        void deleteOneInstance_FromMultiple_DecreasesCorrectly() {
            // Given: 3개 Instance가 있는 Series
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            Series series = new Series();
            series.setSeriesInstanceUid("1.2.3.4.5");
            series.setStudy(study);
            series = seriesService.createSeries(series);

            Instance instance1 = new Instance();
            instance1.setSopInstanceUid("1.2.3.4.5.1");
            instance1.setSeries(series);
            instanceService.createInstance(instance1);

            Instance instance2 = new Instance();
            instance2.setSopInstanceUid("1.2.3.4.5.2");
            instance2.setSeries(series);
            instance2 = instanceService.createInstance(instance2);

            Instance instance3 = new Instance();
            instance3.setSopInstanceUid("1.2.3.4.5.3");
            instance3.setSeries(series);
            instanceService.createInstance(instance3);

            series = seriesRepository.findById(series.getId()).orElseThrow();
            assertThat(series.getNumberOfInstances()).isEqualTo(3);

            // When: 1개만 삭제
            instanceService.deleteInstance(instance2.getId());

            // Then: 2로 감소
            series = seriesRepository.findById(series.getId()).orElseThrow();
            study = studyRepository.findById(study.getId()).orElseThrow();

            assertThat(series.getNumberOfInstances()).isEqualTo(2);
            assertThat(study.getNumberOfInstances()).isEqualTo(2);
        }
    }

    // ========== 여러 Series에 Instance 추가 ==========

    @Nested
    @DisplayName("여러 Series에 Instance 추가 시 Study 카운트 합산")
    class MultipleSeriesTests {

        @Test
        @DisplayName("여러 Series에 Instance 추가 시 Study.numberOfInstances는 전체 합계")
        void multipleSeriesWithInstances_StudyCountIsTotalSum() {
            // Given: 1개 Study에 2개 Series 생성
            Patient patient = patientService.createPatient(TestFixtures.createPatient());
            Study study = TestFixtures.createStudy(patient);
            study = studyService.createStudy(study);

            // Series 1
            Series series1 = new Series();
            series1.setSeriesInstanceUid("1.2.3.4.5");
            series1.setStudy(study);
            series1 = seriesService.createSeries(series1);

            // Series 2
            Series series2 = new Series();
            series2.setSeriesInstanceUid("1.2.3.4.6");
            series2.setStudy(study);
            series2 = seriesService.createSeries(series2);

            // When: Series 1에 3개 Instance 추가
            for (int i = 1; i <= 3; i++) {
                Instance instance = new Instance();
                instance.setSopInstanceUid("1.2.3.4.5." + i);
                instance.setSeries(series1);
                instanceService.createInstance(instance);
            }

            // When: Series 2에 2개 Instance 추가
            for (int i = 1; i <= 2; i++) {
                Instance instance = new Instance();
                instance.setSopInstanceUid("1.2.3.4.6." + i);
                instance.setSeries(series2);
                instanceService.createInstance(instance);
            }

            // Then: 각 Series는 자신의 Instance 개수만 카운트
            series1 = seriesRepository.findById(series1.getId()).orElseThrow();
            series2 = seriesRepository.findById(series2.getId()).orElseThrow();

            assertThat(series1.getNumberOfInstances()).isEqualTo(3);
            assertThat(series2.getNumberOfInstances()).isEqualTo(2);

            // Then: Study는 전체 Instance 개수를 카운트 (3 + 2 = 5)
            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfInstances()).isEqualTo(5);
        }

        @Test
        @DisplayName("여러 Series에서 Instance 삭제 시 Study 카운트 정확히 감소")
        void deleteInstancesFromMultipleSeries_StudyCountDecreasesCorrectly() {
            // Given: 2개 Series에 각각 Instance 추가
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

            // Series 1에 2개 Instance
            Instance instance1_1 = new Instance();
            instance1_1.setSopInstanceUid("1.2.3.4.5.1");
            instance1_1.setSeries(series1);
            instance1_1 = instanceService.createInstance(instance1_1);

            Instance instance1_2 = new Instance();
            instance1_2.setSopInstanceUid("1.2.3.4.5.2");
            instance1_2.setSeries(series1);
            instanceService.createInstance(instance1_2);

            // Series 2에 2개 Instance
            Instance instance2_1 = new Instance();
            instance2_1.setSopInstanceUid("1.2.3.4.6.1");
            instance2_1.setSeries(series2);
            instanceService.createInstance(instance2_1);

            Instance instance2_2 = new Instance();
            instance2_2.setSopInstanceUid("1.2.3.4.6.2");
            instance2_2.setSeries(series2);
            instanceService.createInstance(instance2_2);

            study = studyRepository.findById(study.getId()).orElseThrow();
            assertThat(study.getNumberOfInstances()).isEqualTo(4);

            // When: Series 1에서 1개 삭제
            instanceService.deleteInstance(instance1_1.getId());

            // Then: Study 카운트는 3으로 감소
            study = studyRepository.findById(study.getId()).orElseThrow();
            series1 = seriesRepository.findById(series1.getId()).orElseThrow();
            series2 = seriesRepository.findById(series2.getId()).orElseThrow();

            assertThat(series1.getNumberOfInstances()).isEqualTo(1);
            assertThat(series2.getNumberOfInstances()).isEqualTo(2);
            assertThat(study.getNumberOfInstances()).isEqualTo(3);  // 1 + 2
        }
    }
}
