package com.hanumoka.sado.minipacs.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanumoka.sado.minipacs.storage.strategy.StorageAccessStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Base class for integration tests
 *
 * Features:
 * - Starts embedded web server with random port
 * - Provides RestTestClient for HTTP testing (Spring Framework 7 / Spring Boot 4.0)
 * - Rollback transactions after each test
 * - Active "test" profile
 *
 * Note: RestTestClient is the latest Spring Framework 7 testing approach
 *
 * Usage:
 * <pre>
 * {@code
 * @DisplayName("PatientController Integration Tests")
 * class PatientControllerTest extends BaseIntegrationTest {
 *     @Test
 *     void testCreatePatient() {
 *         restTestClient.post()
 *             .uri("/api/patients")
 *             .contentType(MediaType.APPLICATION_JSON)
 *             .body(request)
 *             .exchange()
 *             .expectStatus().isOk()
 *             .expectBody(PATIENT_RESPONSE_TYPE)
 *             .returnResult()
 *             .getResponseBody();
 *     }
 * }
 * }
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional  // Rollback after each test for clean state
@Import(BaseIntegrationTest.S3ClientTestConfig.class)
public abstract class BaseIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    /**
     * Mock S3Client for testing
     *
     * <p>테스트 환경에서는 실제 SeaweedFS 연결이 필요 없으므로 Mock으로 대체합니다.
     * <p>DicomStorageService를 테스트할 때는 이 Mock을 사용하여 동작을 정의할 수 있습니다.
     *
     * <p>예시:
     * <pre>
     * given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
     *     .willReturn(PutObjectResponse.builder().build());
     * </pre>
     */
    @Autowired
    protected S3Client s3Client;

    /**
     * Mock S3Presigner for testing
     *
     * <p>Pre-signed URL 생성 테스트를 위한 Mock입니다.
     *
     * <p>예시:
     * <pre>
     * given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
     *     .willReturn(mockPresignedRequest);
     * </pre>
     */
    @Autowired
    protected S3Presigner s3Presigner;

    /**
     * RestTestClient instance for making HTTP requests
     *
     * Built from WebApplicationContext to test against the full Spring context
     * Spring Framework 7 official HTTP test client
     */
    protected RestTestClient restTestClient;

    /**
     * Set up RestTestClient before each test
     *
     * Binds RestTestClient to the application context
     */
    @BeforeEach
    void setUpRestTestClient() {
        this.restTestClient = RestTestClient.bindToApplicationContext(context).build();
    }

    /**
     * S3Client Mock Configuration for Testing
     *
     * <p>테스트 환경에서 실제 SeaweedFS 연결 대신 Mockito Mock을 제공합니다.
     * <p>@Primary를 사용하여 실제 SeaweedFsS3Config보다 우선순위를 높입니다.
     */
    @TestConfiguration
    static class S3ClientTestConfig {

        @Bean
        @Primary
        public S3Client s3Client() {
            return Mockito.mock(S3Client.class);
        }

        @Bean
        @Primary
        public S3Presigner s3Presigner() {
            return Mockito.mock(S3Presigner.class);
        }

        @Bean
        @Primary
        public StorageAccessStrategy storageAccessStrategy() {
            return Mockito.mock(StorageAccessStrategy.class);
        }

        @Bean
        @Primary
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
