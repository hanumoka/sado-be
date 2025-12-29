package com.hanumoka.sado.minipacs.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

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
public abstract class BaseIntegrationTest {

    @Autowired
    private WebApplicationContext context;

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
}
