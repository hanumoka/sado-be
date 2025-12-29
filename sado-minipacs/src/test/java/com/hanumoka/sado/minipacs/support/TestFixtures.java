package com.hanumoka.sado.minipacs.support;

import com.hanumoka.sado.minipacs.domain.entity.Patient;
import com.hanumoka.sado.minipacs.domain.entity.Study;
import com.hanumoka.sado.minipacs.dto.request.CreatePatientRequest;
import com.hanumoka.sado.minipacs.dto.request.CreateStudyRequest;
import com.hanumoka.sado.minipacs.dto.request.UpdatePatientRequest;

import java.time.LocalDate;

/**
 * Test data factory for creating test fixtures
 *
 * Provides reusable test data builders to avoid duplication across test classes.
 *
 * <p>Benefits:
 * <ul>
 *   <li>Consistent test data across all tests</li>
 *   <li>Easy to maintain when entity structure changes</li>
 *   <li>Reduces code duplication</li>
 *   <li>Clear and readable test setup</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * {@code
 * // Create default patient
 * Patient patient = TestFixtures.createPatient();
 *
 * // Create patient with custom ID
 * Patient patient = TestFixtures.createPatient("CUSTOM-001");
 *
 * // Create request DTO
 * CreatePatientRequest request = TestFixtures.createPatientRequest();
 * }
 * </pre>
 */
public class TestFixtures {

    // ========== Patient Fixtures ==========

    /**
     * Create a default Patient entity for testing
     *
     * Default values:
     * - dicomPatientId: "TEST-P001"
     * - issuerOfPatientId: "TEST-HOSPITAL"
     * - patientName: "홍길동^Hong^Gildong"
     * - patientBirthDate: 1990-01-01
     * - patientSex: "M"
     *
     * @return Patient entity with default test data
     */
    public static Patient createPatient() {
        return Patient.builder()
                .dicomPatientId("TEST-P001")
                .issuerOfPatientId("TEST-HOSPITAL")
                .patientName("홍길동^Hong^Gildong")
                .patientBirthDate(LocalDate.of(1990, 1, 1))
                .patientSex("M")
                .build();
    }

    /**
     * Create a Patient entity with custom DICOM ID
     *
     * @param dicomPatientId Custom DICOM Patient ID
     * @return Patient entity with specified ID
     */
    public static Patient createPatient(String dicomPatientId) {
        return Patient.builder()
                .dicomPatientId(dicomPatientId)
                .issuerOfPatientId("TEST-HOSPITAL")
                .patientName("홍길동^Hong^Gildong")
                .patientBirthDate(LocalDate.of(1990, 1, 1))
                .patientSex("M")
                .build();
    }

    /**
     * Create a Patient entity with full customization
     *
     * @param dicomPatientId DICOM Patient ID
     * @param issuer Issuer of Patient ID
     * @param name Patient name (DICOM format: LastName^FirstName^MiddleName)
     * @param birthDate Patient birth date
     * @param sex Patient sex (M, F, O)
     * @return Patient entity with custom values
     */
    public static Patient createPatient(
            String dicomPatientId,
            String issuer,
            String name,
            LocalDate birthDate,
            String sex) {
        return Patient.builder()
                .dicomPatientId(dicomPatientId)
                .issuerOfPatientId(issuer)
                .patientName(name)
                .patientBirthDate(birthDate)
                .patientSex(sex)
                .build();
    }

    /**
     * Create a CreatePatientRequest DTO for testing
     *
     * Default values same as createPatient()
     *
     * @return CreatePatientRequest DTO with default test data
     */
    public static CreatePatientRequest createPatientRequest() {
        CreatePatientRequest request = new CreatePatientRequest();
        request.setDicomPatientId("TEST-P001");
        request.setIssuerOfPatientId("TEST-HOSPITAL");
        request.setPatientName("홍길동^Hong^Gildong");
        request.setPatientBirthDate(LocalDate.of(1990, 1, 1));
        request.setPatientSex("M");
        return request;
    }

    /**
     * Create an UpdatePatientRequest DTO for testing
     *
     * Only sets patientName and patientSex for partial update testing
     *
     * @return UpdatePatientRequest DTO for partial update
     */
    public static UpdatePatientRequest updatePatientRequest() {
        UpdatePatientRequest request = new UpdatePatientRequest();
        request.setPatientName("김철수^Kim^Chulsoo");
        request.setPatientSex("M");
        return request;
    }

    // ========== Study Fixtures ==========

    /**
     * Create a default Study entity for testing
     *
     * Default values:
     * - studyInstanceUid: "1.2.840.113619.2.55.1.1762295501.65.1234567890.1"
     * - studyDate: today
     * - studyDescription: "Chest CT"
     * - numberOfSeries: null (denormalized field, auto-updated by business methods)
     * - numberOfInstances: null (denormalized field, auto-updated by business methods)
     *
     * @param patient Associated patient (required)
     * @return Study entity with default test data
     */
    public static Study createStudy(Patient patient) {
        return Study.builder()
                .patient(patient)
                .studyInstanceUid("1.2.840.113619.2.55.1.1762295501.65.1234567890.1")
                .studyDate(LocalDate.now())
                .studyDescription("Chest CT")
                .build();
        // numberOfSeries and numberOfInstances are denormalized fields that should be null initially
        // They will be auto-updated by business methods (addSeries, incrementInstanceCount, etc.)
    }

    /**
     * Create a CreateStudyRequest DTO for testing
     *
     * @param patientId Associated patient ID (required)
     * @return CreateStudyRequest DTO with default test data
     */
    public static CreateStudyRequest createStudyRequest(Long patientId) {
        CreateStudyRequest request = new CreateStudyRequest();
        request.setPatientId(patientId);
        request.setStudyInstanceUid("1.2.840.113619.2.55.1.1762295501.65.1234567890.1");
        request.setStudyDate(LocalDate.now());
        request.setStudyDescription("Chest CT");
        return request;
    }

    // ========== Additional Test Data Helpers ==========

    /**
     * Generate unique DICOM Patient ID for concurrent tests
     *
     * Uses current timestamp to avoid conflicts
     *
     * @return Unique DICOM Patient ID
     */
    public static String generateUniqueDicomPatientId() {
        return "TEST-P-" + System.currentTimeMillis();
    }

    /**
     * Generate unique Study Instance UID for concurrent tests
     *
     * @return Unique Study Instance UID (DICOM format)
     */
    public static String generateUniqueStudyInstanceUid() {
        return "1.2.840.113619.2.55.1." + System.currentTimeMillis();
    }
}
