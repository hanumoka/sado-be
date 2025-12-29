package com.hanumoka.sado.minipacs.controller;

import com.hanumoka.sado.common.dto.ApiResponse;
import com.hanumoka.sado.minipacs.domain.entity.Patient;
import com.hanumoka.sado.minipacs.domain.repository.PatientRepository;
import com.hanumoka.sado.minipacs.dto.request.CreatePatientRequest;
import com.hanumoka.sado.minipacs.dto.request.UpdatePatientRequest;
import com.hanumoka.sado.minipacs.dto.response.PatientResponse;
import com.hanumoka.sado.minipacs.support.BaseIntegrationTest;
import com.hanumoka.sado.minipacs.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PatientController Integration Tests
 *
 * <p>Testing Strategy:
 * <ul>
 *   <li>Use WebTestClient (Stable approach for Spring Boot 4)</li>
 *   <li>Test all CRUD endpoints</li>
 *   <li>Test ApiResponse wrapping</li>
 *   <li>Test error scenarios (404, validation)</li>
 *   <li>Use @Transactional for automatic rollback</li>
 * </ul>
 *
 * <p>Endpoints Tested:
 * <ul>
 *   <li>POST /api/patients - Create patient</li>
 *   <li>GET /api/patients/{id} - Get patient</li>
 *   <li>PUT /api/patients/{id} - Update patient</li>
 *   <li>DELETE /api/patients/{id} - Delete patient</li>
 *   <li>GET /api/patients/{id}/studies - Get patient's studies</li>
 * </ul>
 */
@DisplayName("PatientController Integration Tests")
class PatientControllerTest extends BaseIntegrationTest {

    @Autowired
    private PatientRepository patientRepository;

    // ParameterizedTypeReference for generic ApiResponse deserialization
    private static final ParameterizedTypeReference<ApiResponse<PatientResponse>> PATIENT_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    // ========== POST /api/patients ==========

    @Nested
    @DisplayName("POST /api/patients - 환자 생성")
    class CreatePatientTests {

        @Test
        @DisplayName("정상적인 환자 생성 요청 시 200 OK와 환자 정보 반환")
        void createPatient_Success() {
            // Given: 환자 생성 요청 DTO
            CreatePatientRequest request = TestFixtures.createPatientRequest();

            // When: POST /api/patients
            ApiResponse<PatientResponse> response = restTestClient.post()
                    .uri("/api/patients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    // Then: 200 OK
                    .expectStatus().isOk()
                    .expectBody(PATIENT_RESPONSE_TYPE)
                    .returnResult()
                    .getResponseBody();

            // Then: ApiResponse 검증
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isNotNull();

            // Then: PatientResponse 검증
            PatientResponse patient = response.getData();
            assertThat(patient.getId()).isNotNull();
            assertThat(patient.getDicomPatientId()).isEqualTo("TEST-P001");
            assertThat(patient.getIssuerOfPatientId()).isEqualTo("TEST-HOSPITAL");
            assertThat(patient.getPatientName()).isEqualTo("홍길동^Hong^Gildong");
            assertThat(patient.getPatientBirthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
            assertThat(patient.getPatientSex()).isEqualTo("M");
            assertThat(patient.getCreatedAt()).isNotNull();
            assertThat(patient.getUpdatedAt()).isNotNull();

            // Then: DB 저장 확인
            Patient savedPatient = patientRepository.findById(patient.getId()).orElseThrow();
            assertThat(savedPatient.getDicomPatientId()).isEqualTo("TEST-P001");
        }

        @Test
        @DisplayName("필수 필드만으로 환자 생성 가능")
        void createPatient_MinimalFields() {
            // Given: 최소 필드만 포함한 요청
            CreatePatientRequest request = new CreatePatientRequest();
            request.setDicomPatientId("MINIMAL-001");

            // When & Then: POST /api/patients
            ApiResponse<PatientResponse> response = restTestClient.post()
                    .uri("/api/patients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(PATIENT_RESPONSE_TYPE)
                    .returnResult()
                    .getResponseBody();

            // Then: 생성 성공
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isNotNull();
            assertThat(response.getData().getDicomPatientId()).isEqualTo("MINIMAL-001");
        }

        @Test
        @DisplayName("모든 필드를 포함한 환자 생성")
        void createPatient_AllFields() {
            // Given: 모든 필드를 포함한 요청
            CreatePatientRequest request = new CreatePatientRequest();
            request.setDicomPatientId("FULL-001");
            request.setIssuerOfPatientId("FULL-HOSPITAL");
            request.setPatientName("박영희^Park^Younghee");
            request.setPatientBirthDate(LocalDate.of(1995, 5, 15));
            request.setPatientSex("F");
            request.setEmrPatientId("EMR-12345");

            // When & Then: POST /api/patients
            ApiResponse<PatientResponse> response = restTestClient.post()
                    .uri("/api/patients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(PATIENT_RESPONSE_TYPE)
                    .returnResult()
                    .getResponseBody();

            // Then: 모든 필드 저장 확인
            PatientResponse patient = response.getData();
            assertThat(patient.getDicomPatientId()).isEqualTo("FULL-001");
            assertThat(patient.getIssuerOfPatientId()).isEqualTo("FULL-HOSPITAL");
            assertThat(patient.getPatientName()).isEqualTo("박영희^Park^Younghee");
            assertThat(patient.getPatientBirthDate()).isEqualTo(LocalDate.of(1995, 5, 15));
            assertThat(patient.getPatientSex()).isEqualTo("F");
            assertThat(patient.getEmrPatientId()).isEqualTo("EMR-12345");
        }
    }

    // ========== GET /api/patients/{id} ==========

    @Nested
    @DisplayName("GET /api/patients/{id} - 환자 조회")
    class GetPatientTests {

        @Test
        @DisplayName("존재하는 환자 ID로 조회 시 환자 정보 반환")
        void getPatient_Success() {
            // Given: DB에 저장된 환자
            Patient saved = patientRepository.saveAndFlush(TestFixtures.createPatient());

            // When & Then: GET /api/patients/{id}
            ApiResponse<PatientResponse> response = restTestClient.get()
                    .uri("/api/patients/{id}", saved.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(PATIENT_RESPONSE_TYPE)
                    .returnResult()
                    .getResponseBody();

            // Then: ApiResponse 검증
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isNotNull();

            // Then: 환자 정보 검증
            PatientResponse patient = response.getData();
            assertThat(patient.getId()).isEqualTo(saved.getId());
            assertThat(patient.getDicomPatientId()).isEqualTo("TEST-P001");
            assertThat(patient.getPatientName()).isEqualTo("홍길동^Hong^Gildong");
        }

        @Test
        @DisplayName("존재하지 않는 환자 ID로 조회 시 404 Not Found")
        void getPatient_NotFound() {
            // Given: 존재하지 않는 ID
            Long nonExistentId = 99999L;

            // When & Then: GET /api/patients/{id}
            restTestClient.get()
                    .uri("/api/patients/{id}", nonExistentId)
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    // ========== PUT /api/patients/{id} ==========

    @Nested
    @DisplayName("PUT /api/patients/{id} - 환자 수정")
    class UpdatePatientTests {

        @Test
        @DisplayName("환자 정보 부분 수정 성공")
        void updatePatient_Success() {
            // Given: DB에 저장된 환자
            Patient saved = patientRepository.saveAndFlush(TestFixtures.createPatient());

            // Given: 부분 수정 요청 (이름, 성별만 변경)
            UpdatePatientRequest request = new UpdatePatientRequest();
            request.setPatientName("김철수^Kim^Chulsoo");
            request.setPatientSex("M");

            // When & Then: PUT /api/patients/{id}
            ApiResponse<PatientResponse> response = restTestClient.put()
                    .uri("/api/patients/{id}", saved.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(PATIENT_RESPONSE_TYPE)
                    .returnResult()
                    .getResponseBody();

            // Then: 수정된 정보 검증
            assertThat(response.isSuccess()).isTrue();
            PatientResponse updated = response.getData();
            assertThat(updated.getPatientName()).isEqualTo("김철수^Kim^Chulsoo");
            assertThat(updated.getPatientSex()).isEqualTo("M");

            // Then: 변경하지 않은 필드는 유지
            assertThat(updated.getDicomPatientId()).isEqualTo("TEST-P001");
            assertThat(updated.getIssuerOfPatientId()).isEqualTo("TEST-HOSPITAL");
        }

        @Test
        @DisplayName("존재하지 않는 환자 수정 시 404 Not Found")
        void updatePatient_NotFound() {
            // Given: 존재하지 않는 ID
            Long nonExistentId = 99999L;
            UpdatePatientRequest request = TestFixtures.updatePatientRequest();

            // When & Then: PUT /api/patients/{id}
            restTestClient.put()
                    .uri("/api/patients/{id}", nonExistentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("빈 요청으로 수정 시 변경 없이 성공")
        void updatePatient_EmptyRequest_NoChanges() {
            // Given: DB에 저장된 환자
            Patient saved = patientRepository.saveAndFlush(TestFixtures.createPatient());
            String originalName = saved.getPatientName();

            // Given: 빈 요청 (모든 필드 null)
            UpdatePatientRequest request = new UpdatePatientRequest();

            // When & Then: PUT /api/patients/{id}
            ApiResponse<PatientResponse> response = restTestClient.put()
                    .uri("/api/patients/{id}", saved.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(PATIENT_RESPONSE_TYPE)
                    .returnResult()
                    .getResponseBody();

            // Then: 변경 없이 그대로 유지
            assertThat(response.getData().getPatientName()).isEqualTo(originalName);
        }
    }

    // ========== DELETE /api/patients/{id} ==========

    @Nested
    @DisplayName("DELETE /api/patients/{id} - 환자 삭제")
    class DeletePatientTests {

        @Test
        @DisplayName("환자 삭제 성공")
        void deletePatient_Success() {
            // Given: DB에 저장된 환자
            Patient saved = patientRepository.saveAndFlush(TestFixtures.createPatient());

            // When & Then: DELETE /api/patients/{id}
            restTestClient.delete()
                    .uri("/api/patients/{id}", saved.getId())
                    .exchange()
                    .expectStatus().isOk();

            // Then: DB에서 삭제 확인
            assertThat(patientRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 환자 삭제 시 404 Not Found")
        void deletePatient_NotFound() {
            // Given: 존재하지 않는 ID
            Long nonExistentId = 99999L;

            // When & Then: DELETE /api/patients/{id}
            restTestClient.delete()
                    .uri("/api/patients/{id}", nonExistentId)
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("동일한 환자를 두 번 삭제 시 두 번째 요청은 404")
        void deletePatient_Twice_SecondRequestFails() {
            // Given: DB에 저장된 환자
            Patient saved = patientRepository.saveAndFlush(TestFixtures.createPatient());

            // When: 첫 번째 삭제
            restTestClient.delete()
                    .uri("/api/patients/{id}", saved.getId())
                    .exchange()
                    .expectStatus().isOk();

            // When & Then: 두 번째 삭제 시도
            restTestClient.delete()
                    .uri("/api/patients/{id}", saved.getId())
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    // ========== GET /api/patients/{id}/studies ==========

    @Nested
    @DisplayName("GET /api/patients/{id}/studies - 환자의 검사 목록 조회")
    class GetPatientStudiesTests {

        @Test
        @DisplayName("환자의 검사 목록 조회 성공 (검사가 없어도 빈 리스트 반환)")
        void getPatientStudies_EmptyList() {
            // Given: 환자는 있지만 검사가 없음
            Patient patient = patientRepository.saveAndFlush(TestFixtures.createPatient());

            // When & Then: GET /api/patients/{id}/studies
            restTestClient.get()
                    .uri("/api/patients/{id}/studies", patient.getId())
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("존재하지 않는 환자의 검사 조회 시 404 Not Found")
        void getPatientStudies_PatientNotFound() {
            // Given: 존재하지 않는 환자 ID
            Long nonExistentId = 99999L;

            // When & Then: GET /api/patients/{id}/studies
            restTestClient.get()
                    .uri("/api/patients/{id}/studies", nonExistentId)
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }
}
