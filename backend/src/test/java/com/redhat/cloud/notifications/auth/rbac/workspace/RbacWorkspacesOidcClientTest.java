package com.redhat.cloud.notifications.auth.rbac.workspace;

import com.redhat.cloud.notifications.auth.OidcServerMockResource;
import com.redhat.cloud.notifications.auth.rbac.RbacWorkspacesOidcClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test for RbacWorkspacesOidcClient that verifies OIDC authentication headers
 * are properly added to HTTP requests. The RBAC server mock returns 401 for missing/invalid
 * Authorization headers and 200 for correct ones.
 */
@QuarkusTest
@QuarkusTestResource(OidcServerMockResource.class)
@QuarkusTestResource(RbacServerMockResource.class)
public class RbacWorkspacesOidcClientTest {

    @Inject
    @RestClient
    RbacWorkspacesOidcClient rbacClient;

    private static final String TEST_ORG_ID = "test-org-id";

    @Test
    @DisplayName("Should successfully call getWorkspaces with OIDC authentication")
    void shouldSuccessfullyCallGetWorkspaces() {
        // If OIDC headers are missing, the mock server will return 401 and this will throw an exception
        // If OIDC headers are present and correct, the mock server returns 200 and this succeeds
        var result = assertDoesNotThrow(() -> rbacClient.getWorkspaces(TEST_ORG_ID, "default", 0, 2));
        assertNotNull(result);
        assertNotNull(result.getData());
    }
}
