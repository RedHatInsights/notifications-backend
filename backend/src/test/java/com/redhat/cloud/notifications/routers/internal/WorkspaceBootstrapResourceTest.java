package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.repositories.WorkspaceRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.routers.internal.WorkspaceBootstrapResource.BootstrapStatus;
import com.redhat.cloud.notifications.routers.internal.WorkspaceBootstrapResource.BootstrapSummary;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.TestHelpers.createTurnpikeIdentityHeader;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class WorkspaceBootstrapResourceTest extends DbIsolatedTest {

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    EntityManager em;

    @Inject
    WorkspaceRepository workspaceRepository;

    @Test
    void testBootstrapStatusEndpoint() {
        // Setup - Create endpoints without workspaces
        String orgId1 = "test-org-1";
        String orgId2 = "test-org-2";
        createEndpoint(orgId1, "endpoint-1");
        createEndpoint(orgId2, "endpoint-2");

        // Execute - Call GET /internal/workspace/status
        BootstrapStatus status = given()
            .basePath("/api/integrations/v1.0")
            .header(createTurnpikeIdentityHeader("admin", adminRole))
            .contentType(JSON)
            .when()
            .get("/internal/workspace/status")
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().as(BootstrapStatus.class);

        // Verify - Returns correct counts
        assertNotNull(status);
        assertTrue(status.endpointsWithoutWorkspace >= 2, "Should have at least 2 endpoints without workspace");
        assertTrue(status.totalOrgsWithEndpoints >= 2, "Should have at least 2 orgs with endpoints");
    }

    @Test
    void testBootstrapCreatesOrgWorkspaces() {
        // Setup - Create endpoints for multiple orgs
        String orgId1 = "test-org-bootstrap-1";
        String orgId2 = "test-org-bootstrap-2";
        createEndpoint(orgId1, "endpoint-1");
        createEndpoint(orgId1, "endpoint-2");
        createEndpoint(orgId2, "endpoint-3");

        long endpointsBeforeBootstrap = workspaceRepository.countEndpointsWithoutWorkspace();

        // Execute - Call bootstrap endpoint
        BootstrapSummary summary = given()
            .basePath("/api/integrations/v1.0")
            .header(createTurnpikeIdentityHeader("admin", adminRole))
            .contentType(JSON)
            .when()
            .post("/internal/workspace/bootstrap")
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().as(BootstrapSummary.class);

        // Verify - System workspace created
        assertNotNull(summary.systemWorkspaceId);

        // Verify - Workspace records created for each org
        assertTrue(summary.totalOrgsProcessed >= 2, "Should process at least 2 orgs");
        assertTrue(summary.workspacesCreated >= 2, "Should create at least 2 workspaces");

        // Verify - Endpoints assigned to correct workspaces
        assertTrue(summary.orgEndpointsAssigned >= 3, "Should assign at least 3 org endpoints");

        // Verify - Fewer endpoints without workspace after bootstrap
        long endpointsAfterBootstrap = workspaceRepository.countEndpointsWithoutWorkspace();
        assertTrue(endpointsAfterBootstrap < endpointsBeforeBootstrap,
            "Endpoints without workspace should decrease after bootstrap");
    }

    @Test
    void testBootstrapIdempotency() {
        // Setup - Create endpoints
        String orgId = "test-org-idempotent";
        createEndpoint(orgId, "endpoint-1");

        // Execute - Call bootstrap twice
        BootstrapSummary summary1 = given()
            .basePath("/api/integrations/v1.0")
            .header(createTurnpikeIdentityHeader("admin", adminRole))
            .contentType(JSON)
            .when()
            .post("/internal/workspace/bootstrap")
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().as(BootstrapSummary.class);

        BootstrapSummary summary2 = given()
            .basePath("/api/integrations/v1.0")
            .header(createTurnpikeIdentityHeader("admin", adminRole))
            .contentType(JSON)
            .when()
            .post("/internal/workspace/bootstrap")
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().as(BootstrapSummary.class);

        // Verify - Same system workspace ID both times
        assertEquals(summary1.systemWorkspaceId, summary2.systemWorkspaceId,
            "System workspace ID should be the same on second bootstrap");

        // Verify - No additional endpoints assigned on second run
        assertEquals(0, summary2.orgEndpointsAssigned,
            "Second bootstrap should not assign additional endpoints");
    }

    @Transactional
    void createEndpoint(String orgId, String name) {
        Endpoint endpoint = new Endpoint();
        endpoint.setOrgId(orgId);
        endpoint.setAccountId("test-account");
        endpoint.setName(name);
        endpoint.setDescription("Test endpoint for workspace bootstrap");
        endpoint.setType(WEBHOOK);

        WebhookProperties properties = new WebhookProperties();
        properties.setUrl("https://example.com/webhook");
        properties.setMethod(HttpType.POST);
        endpoint.setProperties(properties);

        em.persist(endpoint);
    }
}
