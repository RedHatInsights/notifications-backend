package com.redhat.cloud.notifications.recipients.resolver.rbac;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test for RbacServiceToServiceOidc that verifies OIDC authentication headers
 * are properly added to HTTP requests. The RBAC server mock returns 401 for missing/invalid
 * Authorization headers and 200 for correct ones.
 */
@QuarkusTest
@QuarkusTestResource(OidcServerMockResource.class)
@QuarkusTestResource(RbacServerMockResource.class)
public class RbacServiceToServiceOidcTest {

    @Inject
    @RestClient
    RbacServiceToServiceOidc rbacClient;

    private static final String TEST_ORG_ID = "test-org-id";

    @Test
    @DisplayName("Should successfully call getUsers with OIDC authentication")
    void shouldSuccessfullyCallGetUsers() {
        // If OIDC headers are missing, the mock server will return 401 and this will throw an exception
        // If OIDC headers are present and correct, the mock server returns 200 and this succeeds
        var result = assertDoesNotThrow(() -> rbacClient.getUsers(TEST_ORG_ID, false, 0, 10));
        assertNotNull(result);
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("Should successfully call getGroup with OIDC authentication")
    void shouldSuccessfullyCallGetGroup() {
        // If OIDC headers are missing, the mock server will return 401 and this will throw an exception
        // If OIDC headers are present and correct, the mock server returns 200 and this succeeds
        var result = assertDoesNotThrow(() -> rbacClient.getGroup(TEST_ORG_ID, UUID.randomUUID()));
        assertNotNull(result);
        assertNotNull(result.getName());
    }

    @Test
    @DisplayName("Should successfully call getGroupUsers with OIDC authentication")
    void shouldSuccessfullyCallGetGroupUsers() {
        // If OIDC headers are missing, the mock server will return 401 and this will throw an exception
        // If OIDC headers are present and correct, the mock server returns 200 and this succeeds
        var result = assertDoesNotThrow(() -> rbacClient.getGroupUsers(TEST_ORG_ID, UUID.randomUUID(), true, 5, 20));
        assertNotNull(result);
        assertNotNull(result.getData());
    }
}
