package com.redhat.cloud.notifications.routers.sources;

import com.redhat.cloud.notifications.auth.OidcServerMockResource;
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
 * <p>Integration test suite for {@link SourcesOidcService} that validates OIDC (OpenID Connect)
 * client credentials authentication is properly implemented and functioning.</p>
 *
 * <p><strong>Test Objectives:</strong></p>
 * <ul>
 *   <li>Verify that {@code @OidcClientFilter} annotation automatically injects bearer tokens</li>
 *   <li>Ensure all HTTP requests include the correct {@code Authorization: Bearer <token>} header</li>
 *   <li>Validate that OIDC authentication works across all CRUD operations (GET, POST, PUT, DELETE)</li>
 *   <li>Confirm proper integration between Quarkus OIDC client and Sources API endpoints</li>
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
 *   <li>SourcesOidcService method is called</li>
 *   <li>@OidcClientFilter intercepts the request</li>
 *   <li>Filter obtains access token from mock OIDC server</li>
 *   <li>Filter adds "Authorization: Bearer <token>" header to request</li>
 *   <li>Mock Sources API validates the bearer token and responds accordingly</li>
 * </ol>
 *
 * <p><strong>Test Validation Strategy:</strong><br>
 * The mock Sources API returns HTTP 401 (Unauthorized) for requests missing or containing
 * invalid authorization headers, and HTTP 2xx for requests with valid bearer tokens.
 * Successful test execution without exceptions indicates OIDC authentication is working.</p>
 */
@QuarkusTest
@QuarkusTestResource(OidcServerMockResource.class) // Provides mock OIDC server for token generation
@QuarkusTestResource(SourcesServerMockResource.class) // Provides mock Sources API with auth validation
public class SourcesOidcServiceTest {

    @Inject
    @RestClient
    SourcesOidcService sourcesOidcService;

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
     * No exception is thrown and a valid {@link Secret} object is returned with the expected ID
     * and password, confirming that OIDC authentication headers were properly included.</p>
     *
     * <p><strong>Failure Scenario:</strong><br>
     * If OIDC authentication fails (missing/invalid token), the mock Sources API returns
     * HTTP 401, causing this test to fail with a WebApplicationException.</p>
     */
    @Test
    @DisplayName("Should successfully call getById with OIDC authentication")
    void shouldSuccessfullyCallGetById() {
        // Execute the getById operation - OIDC filter should automatically add auth header
        var result = assertDoesNotThrow(() -> sourcesOidcService.getById(TEST_ORG_ID, 123L));

        // Validate that the request succeeded with proper OIDC authentication
        assertNotNull(result, "Secret should be returned when OIDC authentication succeeds");
        assertEquals(123L, result.id, "Returned secret should have the requested ID");
        assertEquals("test-password", result.password, "Mock server should return expected password");
    }

    /**
     * <p>Tests the {@code create} operation with OIDC authentication.</p>
     *
     * <p><strong>OIDC Behavior Tested:</strong></p>
     * <ul>
     *   <li>@OidcClientFilter automatically adds bearer token to POST request</li>
     *   <li>Authorization header enables successful creation of new secrets</li>
     *   <li>Mock Sources API validates token before processing the creation request</li>
     * </ul>
     *
     * <p><strong>Test Scenario:</strong><br>
     * Creates a new secret with authentication type 'secret_token' and validates that
     * the creation succeeds with proper OIDC authentication headers.</p>
     */
    @Test
    @DisplayName("Should successfully call create with OIDC authentication")
    void shouldSuccessfullyCallCreate() {
        // Prepare a new secret for creation
        Secret secret = new Secret();
        secret.password = "new-secret";
        secret.authenticationType = Secret.TYPE_SECRET_TOKEN;

        // Execute the create operation - OIDC filter should automatically add auth header
        var result = assertDoesNotThrow(() -> sourcesOidcService.create(TEST_ORG_ID, secret));

        // Validate that the creation succeeded with proper OIDC authentication
        assertNotNull(result, "Created secret should be returned when OIDC authentication succeeds");
        assertEquals(456L, result.id, "Mock server should assign expected ID to new secret");
        assertEquals("new-secret", result.password, "Created secret should retain the provided password");
    }

    /**
     * <p>Tests the {@code update} operation with OIDC authentication.</p>
     *
     * <p><strong>OIDC Behavior Tested:</strong></p>
     * <ul>
     *   <li>@OidcClientFilter automatically adds bearer token to PUT request</li>
     *   <li>Authorization header enables successful modification of existing secrets</li>
     *   <li>Mock Sources API validates token before processing the update request</li>
     * </ul>
     *
     * <p><strong>Test Scenario:</strong><br>
     * Updates an existing secret's password and validates that the update succeeds
     * with proper OIDC authentication headers.</p>
     */
    @Test
    @DisplayName("Should successfully call update with OIDC authentication")
    void shouldSuccessfullyCallUpdate() {
        // Prepare updated secret data
        Secret secret = new Secret();
        secret.password = "updated-secret";

        // Execute the update operation - OIDC filter should automatically add auth header
        var result = assertDoesNotThrow(() -> sourcesOidcService.update(TEST_ORG_ID, 123L, secret));

        // Validate that the update succeeded with proper OIDC authentication
        assertNotNull(result, "Updated secret should be returned when OIDC authentication succeeds");
        assertEquals(123L, result.id, "Updated secret should retain the original ID");
        assertEquals("updated-secret", result.password, "Secret should have the updated password");
    }

    /**
     * <p>Tests the {@code delete} operation with OIDC authentication.</p>
     *
     * <p><strong>OIDC Behavior Tested:</strong></p>
     * <ul>
     *   <li>@OidcClientFilter automatically adds bearer token to DELETE request</li>
     *   <li>Authorization header enables successful deletion of secrets</li>
     *   <li>Mock Sources API validates token before processing the deletion request</li>
     * </ul>
     *
     * <p><strong>Success Criteria:</strong><br>
     * No exception is thrown and the method completes successfully, indicating that
     * the mock Sources API accepted the deletion request with valid OIDC authentication.</p>
     *
     * <p><strong>Note:</strong><br>
     * DELETE operations typically return HTTP 204 (No Content) on success, so we only
     * validate that no authorization-related exceptions are thrown.</p>
     */
    @Test
    @DisplayName("Should successfully call delete with OIDC authentication")
    void shouldSuccessfullyCallDelete() {
        // Execute the delete operation - OIDC filter should automatically add auth header
        // Success is indicated by no exception being thrown (mock returns 204 for valid auth)
        assertDoesNotThrow(() -> sourcesOidcService.delete(TEST_ORG_ID, 123L),
                "Delete operation should succeed when OIDC authentication is properly configured");
    }
}
