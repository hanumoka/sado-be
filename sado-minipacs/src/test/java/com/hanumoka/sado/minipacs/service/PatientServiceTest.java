package com.hanumoka.sado.minipacs.service;

import com.hanumoka.sado.common.exception.ResourceNotFoundException;
import com.hanumoka.sado.minipacs.domain.entity.Patient;
import com.hanumoka.sado.minipacs.domain.repository.PatientRepository;
import com.hanumoka.sado.minipacs.domain.service.PatientService;
import com.hanumoka.sado.minipacs.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * PatientService Unit Tests
 *
 * <p>Testing Strategy:
 * <ul>
 *   <li>Use Mockito to mock dependencies (Repository)</li>
 *   <li>Test business logic in isolation</li>
 *   <li>Test exception handling</li>
 *   <li>Test findOrCreate pattern (Identity Resolution)</li>
 *   <li>NO Spring context (fast tests)</li>
 * </ul>
 *
 * <p>Methods Tested:
 * <ul>
 *   <li>findById() - 환자 조회</li>
 *   <li>createPatient() - 환자 생성</li>
 *   <li>findOrCreatePatient() - Identity Resolution 로직</li>
 *   <li>updatePatient() - 환자 수정</li>
 *   <li>deletePatient() - 환자 삭제</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PatientService Unit Tests")
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private PatientService patientService;

    // ========== findById() ==========

    @Nested
    @DisplayName("findById() - ID로 환자 조회")
    class FindByIdTests {

        @Test
        @DisplayName("존재하는 ID로 조회 시 환자 반환")
        void findById_ExistingId_ReturnsPatient() {
            // Given: Repository가 환자 반환하도록 모킹
            Patient mockPatient = TestFixtures.createPatient();
            ReflectionTestUtils.setField(mockPatient, "id", 1L);
            when(patientRepository.findById(1L)).thenReturn(Optional.of(mockPatient));

            // When: Service 호출
            Patient result = patientService.findById(1L);

            // Then: 환자 반환 확인
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getDicomPatientId()).isEqualTo("TEST-P001");

            // Then: Repository 호출 검증
            verify(patientRepository, times(1)).findById(1L);
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회 시 ResourceNotFoundException 발생")
        void findById_NonExistingId_ThrowsException() {
            // Given: Repository가 빈 Optional 반환
            when(patientRepository.findById(99999L)).thenReturn(Optional.empty());

            // When & Then: 예외 발생 확인
            assertThatThrownBy(() -> patientService.findById(99999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Patient not found with id: 99999");

            // Then: Repository 호출 검증
            verify(patientRepository, times(1)).findById(99999L);
        }

        @Test
        @DisplayName("null ID로 조회 시 예외 발생")
        void findById_NullId_ThrowsException() {
            // When & Then: null ID 전달 시 예외
            assertThatThrownBy(() -> patientService.findById(null))
                    .isInstanceOf(Exception.class);
        }
    }

    // ========== createPatient() ==========

    @Nested
    @DisplayName("createPatient() - 환자 생성")
    class CreatePatientTests {

        @Test
        @DisplayName("환자 생성 성공")
        void createPatient_Success() {
            // Given: 생성할 환자 데이터
            Patient newPatient = TestFixtures.createPatient();

            // Given: Repository save 모킹
            Patient savedPatient = TestFixtures.createPatient();
            ReflectionTestUtils.setField(savedPatient, "id", 1L);
            when(patientRepository.save(any(Patient.class))).thenReturn(savedPatient);

            // When: Service 호출
            Patient result = patientService.createPatient(newPatient);

            // Then: 저장된 환자 반환 확인
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getDicomPatientId()).isEqualTo("TEST-P001");

            // Then: Repository save 호출 검증
            verify(patientRepository, times(1)).save(newPatient);
        }

        @Test
        @DisplayName("null 환자 생성 시 예외 발생")
        void createPatient_NullPatient_ThrowsException() {
            // When & Then: null 환자 전달 시 예외
            assertThatThrownBy(() -> patientService.createPatient(null))
                    .isInstanceOf(Exception.class);
        }
    }

    // ========== findOrCreatePatient() ==========

    @Nested
    @DisplayName("findOrCreatePatient() - Identity Resolution")
    class FindOrCreatePatientTests {

        @Test
        @DisplayName("기존 환자가 존재하면 조회하여 반환")
        void findOrCreatePatient_ExistingPatient_ReturnsExisting() {
            // Given: 기존 환자가 존재
            Patient existingPatient = TestFixtures.createPatient();
            ReflectionTestUtils.setField(existingPatient, "id", 1L);
            when(patientRepository.findByDicomPatientIdAndIssuerOfPatientId("TEST-P001", "TEST-HOSPITAL"))
                    .thenReturn(Optional.of(existingPatient));

            // When: findOrCreate 호출
            Patient result = patientService.findOrCreatePatient(
                    "TEST-P001",
                    "TEST-HOSPITAL",
                    "홍길동^Hong^Gildong",
                    LocalDate.of(1990, 1, 1),
                    "M"
            );

            // Then: 기존 환자 반환
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getDicomPatientId()).isEqualTo("TEST-P001");

            // Then: 새로 저장하지 않음 (saveAndFlush 호출 안 됨)
            verify(patientRepository, never()).saveAndFlush(any(Patient.class));
            verify(patientRepository, times(1))
                    .findByDicomPatientIdAndIssuerOfPatientId("TEST-P001", "TEST-HOSPITAL");
        }

        @Test
        @DisplayName("기존 환자가 없으면 새로 생성하여 반환")
        void findOrCreatePatient_NewPatient_CreatesNew() {
            // Given: 기존 환자 없음
            when(patientRepository.findByDicomPatientIdAndIssuerOfPatientId(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // Given: 새로 생성된 환자 모킹
            Patient newPatient = TestFixtures.createPatient();
            ReflectionTestUtils.setField(newPatient, "id", 2L);
            when(patientRepository.saveAndFlush(any(Patient.class))).thenReturn(newPatient);

            // When: findOrCreate 호출
            Patient result = patientService.findOrCreatePatient(
                    "NEW-P001",
                    "NEW-HOSPITAL",
                    "김영희^Kim^Younghee",
                    LocalDate.of(1995, 5, 15),
                    "F"
            );

            // Then: 새로 생성된 환자 반환
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(2L);

            // Then: saveAndFlush 호출 확인
            verify(patientRepository, times(1)).saveAndFlush(any(Patient.class));
            verify(patientRepository, times(1))
                    .findByDicomPatientIdAndIssuerOfPatientId("NEW-P001", "NEW-HOSPITAL");
        }

        @Test
        @DisplayName("DICOM 데이터로 새 환자 생성 시 필드 매핑 확인")
        void findOrCreatePatient_NewPatient_FieldMapping() {
            // Given: 기존 환자 없음
            when(patientRepository.findByDicomPatientIdAndIssuerOfPatientId(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // Given: saveAndFlush 호출 시 전달받은 객체 그대로 반환 (ArgumentCaptor 대신)
            when(patientRepository.saveAndFlush(any(Patient.class))).thenAnswer(invocation -> {
                Patient saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", 3L);
                return saved;
            });

            // When: findOrCreate 호출
            Patient result = patientService.findOrCreatePatient(
                    "FIELD-P001",
                    "FIELD-HOSPITAL",
                    "박민수^Park^Minsoo",
                    LocalDate.of(1988, 3, 20),
                    "M"
            );

            // Then: 필드 매핑 확인
            assertThat(result.getDicomPatientId()).isEqualTo("FIELD-P001");
            assertThat(result.getIssuerOfPatientId()).isEqualTo("FIELD-HOSPITAL");
            assertThat(result.getPatientName()).isEqualTo("박민수^Park^Minsoo");
            assertThat(result.getPatientBirthDate()).isEqualTo(LocalDate.of(1988, 3, 20));
            assertThat(result.getPatientSex()).isEqualTo("M");
        }

        @Test
        @DisplayName("Issuer가 null인 경우에도 정상 동작")
        void findOrCreatePatient_NullIssuer_Success() {
            // Given: Issuer가 null
            when(patientRepository.findByDicomPatientIdAndIssuerOfPatientId(anyString(), any()))
                    .thenReturn(Optional.empty());

            when(patientRepository.saveAndFlush(any(Patient.class))).thenAnswer(inv -> {
                Patient p = inv.getArgument(0);
                ReflectionTestUtils.setField(p, "id", 4L);
                return p;
            });

            // When: Issuer null로 호출
            Patient result = patientService.findOrCreatePatient(
                    "NULL-ISSUER-P001",
                    null,  // Issuer가 null
                    "이민수^Lee^Minsoo",
                    LocalDate.of(1992, 7, 10),
                    "M"
            );

            // Then: 정상 생성
            assertThat(result).isNotNull();
            assertThat(result.getIssuerOfPatientId()).isNull();
        }
    }

    // ========== updatePatient() ==========

    @Nested
    @DisplayName("updatePatient() - 환자 수정")
    class UpdatePatientTests {

        @Test
        @DisplayName("환자 수정 성공")
        void updatePatient_Success() {
            // Given: 수정할 환자 데이터
            Patient updatedPatient = TestFixtures.createPatient();
            ReflectionTestUtils.setField(updatedPatient, "id", 1L);
            updatedPatient.setPatientName("Updated Name");

            // Given: Repository 모킹
            when(patientRepository.findById(1L)).thenReturn(Optional.of(updatedPatient));
            when(patientRepository.save(any(Patient.class))).thenReturn(updatedPatient);

            // When: Service 호출
            Patient result = patientService.updatePatient(updatedPatient);

            // Then: 수정된 환자 반환
            assertThat(result).isNotNull();
            assertThat(result.getPatientName()).isEqualTo("Updated Name");

            // Then: save 호출 확인
            verify(patientRepository, times(1)).save(updatedPatient);
        }

        @Test
        @DisplayName("null 환자 수정 시 예외 발생")
        void updatePatient_NullPatient_ThrowsException() {
            // When & Then: null 환자 전달 시 예외
            assertThatThrownBy(() -> patientService.updatePatient(null))
                    .isInstanceOf(Exception.class);
        }
    }

    // ========== deletePatient() ==========

    @Nested
    @DisplayName("deletePatient() - 환자 삭제")
    class DeletePatientTests {

        @Test
        @DisplayName("환자 삭제 성공")
        void deletePatient_Success() {
            // Given: 삭제할 환자 존재
            Patient existingPatient = TestFixtures.createPatient();
            ReflectionTestUtils.setField(existingPatient, "id", 1L);
            when(patientRepository.findById(1L)).thenReturn(Optional.of(existingPatient));

            // When: Service 호출
            patientService.deletePatient(1L);

            // Then: delete 호출 확인
            verify(patientRepository, times(1)).findById(1L);
            verify(patientRepository, times(1)).delete(existingPatient);
        }

        @Test
        @DisplayName("존재하지 않는 환자 삭제 시 예외 발생")
        void deletePatient_NonExistingId_ThrowsException() {
            // Given: 환자가 존재하지 않음
            when(patientRepository.findById(99999L)).thenReturn(Optional.empty());

            // When & Then: 예외 발생 확인
            assertThatThrownBy(() -> patientService.deletePatient(99999L))
                    .isInstanceOf(ResourceNotFoundException.class);

            // Then: delete 호출 안 됨
            verify(patientRepository, never()).delete(any(Patient.class));
        }

        @Test
        @DisplayName("null ID 삭제 시 예외 발생")
        void deletePatient_NullId_ThrowsException() {
            // When & Then: null ID 전달 시 예외
            assertThatThrownBy(() -> patientService.deletePatient(null))
                    .isInstanceOf(Exception.class);
        }
    }

    // ========== findByDicomPatientId() ==========

    @Nested
    @DisplayName("findByDicomPatientId() - DICOM ID로 환자 조회")
    class FindByDicomPatientIdTests {

        @Test
        @DisplayName("DICOM Patient ID와 Issuer로 환자 조회 성공")
        void findByDicomPatientId_Success() {
            // Given: Repository가 환자 반환
            Patient mockPatient = TestFixtures.createPatient();
            ReflectionTestUtils.setField(mockPatient, "id", 1L);
            when(patientRepository.findByDicomPatientIdAndIssuerOfPatientId("TEST-P001", "TEST-HOSPITAL"))
                    .thenReturn(Optional.of(mockPatient));

            // When: Service 호출
            Optional<Patient> result = patientService.findByDicomPatientId("TEST-P001", "TEST-HOSPITAL");

            // Then: 환자 반환 확인
            assertThat(result).isPresent();
            assertThat(result.get().getDicomPatientId()).isEqualTo("TEST-P001");

            // Then: Repository 호출 검증
            verify(patientRepository, times(1))
                    .findByDicomPatientIdAndIssuerOfPatientId("TEST-P001", "TEST-HOSPITAL");
        }

        @Test
        @DisplayName("존재하지 않는 DICOM ID로 조회 시 빈 Optional 반환")
        void findByDicomPatientId_NotFound_ReturnsEmpty() {
            // Given: Repository가 빈 Optional 반환
            when(patientRepository.findByDicomPatientIdAndIssuerOfPatientId(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            // When: Service 호출
            Optional<Patient> result = patientService.findByDicomPatientId("NON-EXISTENT", "HOSPITAL");

            // Then: 빈 Optional 반환
            assertThat(result).isEmpty();
        }
    }

    // ========== findByEmrPatientId() ==========

    @Nested
    @DisplayName("findByEmrPatientId() - EMR ID로 환자 조회")
    class FindByEmrPatientIdTests {

        @Test
        @DisplayName("EMR Patient ID로 환자 조회 성공")
        void findByEmrPatientId_Success() {
            // Given: Repository가 환자 반환
            Patient mockPatient = TestFixtures.createPatient();
            ReflectionTestUtils.setField(mockPatient, "id", 1L);
            mockPatient.setEmrPatientId("EMR-12345");
            when(patientRepository.findByEmrPatientId("EMR-12345"))
                    .thenReturn(Optional.of(mockPatient));

            // When: Service 호출
            Optional<Patient> result = patientService.findByEmrPatientId("EMR-12345");

            // Then: 환자 반환 확인
            assertThat(result).isPresent();
            assertThat(result.get().getEmrPatientId()).isEqualTo("EMR-12345");

            // Then: Repository 호출 검증
            verify(patientRepository, times(1)).findByEmrPatientId("EMR-12345");
        }

        @Test
        @DisplayName("존재하지 않는 EMR ID로 조회 시 빈 Optional 반환")
        void findByEmrPatientId_NotFound_ReturnsEmpty() {
            // Given: Repository가 빈 Optional 반환
            when(patientRepository.findByEmrPatientId(anyString()))
                    .thenReturn(Optional.empty());

            // When: Service 호출
            Optional<Patient> result = patientService.findByEmrPatientId("NON-EXISTENT");

            // Then: 빈 Optional 반환
            assertThat(result).isEmpty();
        }
    }
}
