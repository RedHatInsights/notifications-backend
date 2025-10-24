package com.redhat.cloud.notifications.connector.authentication.secrets;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * <p>Integration test for {@link SourcesOidcClient} that verifies OIDC authentication headers
 * are properly added to HTTP requests. The Sources server mock returns 401 for missing/invalid
 * Authorization headers and 200 for correct ones.</p>
 *
 * <p><strong>Test Objectives:</strong></p>
 * <ul>
 *   <li>Verify that {@code @OidcClientFilter} annotation automatically injects bearer tokens</li>
 *   <li>Ensure all HTTP requests include the correct {@code Authorization: Bearer <token>} header</li>
 *   <li>Validate that OIDC authentication works with Sources API endpoints</li>
 *   <li>Confirm proper integration between Quarkus OIDC client and Sources API</li>
 * </ul>
 *
 * <p><strong>Mock Setup:</strong></p>
 * <ul>
 *   <li>{@link OidcServerMockResource} - Simulates an OIDC provider that issues access tokens</li>
 *   <li>{@link SourcesServerMockResource} - Simulates the Sources API with authorization validation</li>
 * </ul>
 *
 * <p><strong>Authentication Flow Tested:</strong></p>
 * <ol>
 *   <li>SourcesOidcClient method is called</li>
 *   <li>@OidcClientFilter intercepts the request</li>
 *   <li>Filter obtains access token from mock OIDC server</li>
 *   <li>Filter adds "Authorization: Bearer <token>" header to request</li>
 *   <li>Mock Sources API validates the bearer token and responds accordingly</li>
 * </ol>
 */
@QuarkusTest
@QuarkusTestResource(OidcServerMockResource.class)
@QuarkusTestResource(SourcesServerMockResource.class)
public class SourcesOidcClientTest {

    @Inject
    @RestClient
    SourcesOidcClient sourcesOidcClient;

    private static final String TEST_ORG_ID = "test-org-id";

    /**
     * <p>Tests the {@code getById} operation with OIDC authentication.</p>
     *
     * <p><strong>OIDC Behavior Tested:</strong></p>
     * <ul>
     *   <li>@OidcClientFilter automatically obtains an access token from the mock OIDC server</li>
     *   <li>Filter injects "Authorization: Bearer <token>" header into the GET request</li>
     *   <li>Mock Sources API validates the bearer token before returning secret data</li>
     * </ul>
     *
     * <p><strong>Success Criteria:</strong><br>
     * No exception is thrown and a valid {@link SourcesSecret} object is returned with the expected
     * username and password, confirming that OIDC authentication headers were properly included.</p>
     *
     * <p><strong>Failure Scenario:</strong><br>
     * If OIDC authentication fails (missing/invalid token), the mock Sources API returns
     * HTTP 401, causing this test to fail with a WebApplicationException.</p>
     */
    @Test
    @DisplayName("Should successfully call getById with OIDC authentication")
    void shouldSuccessfullyCallGetById() {
        // Execute the getById operation - OIDC filter should automatically add auth header
        var result = assertDoesNotThrow(() -> sourcesOidcClient.getById(TEST_ORG_ID, 123L));

        // Validate that the request succeeded with proper OIDC authentication
        assertNotNull(result, "SourcesSecret should be returned when OIDC authentication succeeds");
        assertEquals("test-username", result.username, "Mock server should return expected username");
        assertEquals("test-password", result.password, "Mock server should return expected password");
    }
}
