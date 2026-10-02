package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.repositories.WorkspaceRepository;
import com.redhat.cloud.notifications.db.repositories.WorkspaceTestProfile;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.routers.internal.WorkspaceBootstrapResource.BootstrapStatus;
import com.redhat.cloud.notifications.routers.internal.WorkspaceBootstrapResource.BootstrapSummary;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(WorkspaceTestProfile.class)
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
                        .header(createTurnpikeIdentityHeader("admin", adminRole))
            .contentType(JSON)
            .when()
            .post("/internal/workspace/bootstrap")
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().as(BootstrapSummary.class);

        BootstrapSummary summary2 = given()
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

    @Test
    void testBootstrapAssignsSystemEndpointsToSystemWorkspace() {
        // Setup - Create system endpoints (org_id = NULL) and regular org endpoints
        createEndpoint(null, "system-endpoint-1");
        createEndpoint(null, "system-endpoint-2");
        createEndpoint("test-org-1", "org-endpoint-1");

        // Execute - Call bootstrap
        BootstrapSummary summary = given()
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

        // Verify - System endpoints assigned to system workspace
        assertEquals(2, summary.systemEndpointsAssigned,
            "Should assign 2 system endpoints to system workspace");

        // Verify - System workspace has NULL org_id in database
        String systemWorkspaceOrgId = getWorkspaceOrgId(summary.systemWorkspaceId);
        assertNull(systemWorkspaceOrgId, "System workspace should have NULL org_id");

        // Verify - System endpoints have the system workspace assigned
        assertEndpointHasWorkspace("system-endpoint-1", summary.systemWorkspaceId);
        assertEndpointHasWorkspace("system-endpoint-2", summary.systemWorkspaceId);
    }

    @Test
    void testBootstrapWithNoEndpoints() {
        // Execute - Call bootstrap with empty endpoints table
        BootstrapSummary summary = given()
                        .header(createTurnpikeIdentityHeader("admin", adminRole))
            .contentType(JSON)
            .when()
            .post("/internal/workspace/bootstrap")
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().as(BootstrapSummary.class);

        // Verify - Bootstrap succeeds with zero counts
        assertNotNull(summary.systemWorkspaceId, "System workspace should still be created");
        assertEquals(0, summary.systemEndpointsAssigned);
        assertEquals(0, summary.totalOrgsProcessed);
        assertEquals(0, summary.workspacesCreated);
        assertEquals(0, summary.orgEndpointsAssigned);
    }

    @Test
    void testBootstrapPartiallyAssignedEndpoints() {
        // Setup - Create some endpoints and run bootstrap
        String orgId = "test-org-partial";
        createEndpoint(orgId, "endpoint-1");
        createEndpoint(orgId, "endpoint-2");

        BootstrapSummary summary1 = given()
                        .header(createTurnpikeIdentityHeader("admin", adminRole))
            .contentType(JSON)
            .when()
            .post("/internal/workspace/bootstrap")
            .then()
            .statusCode(200)
            .extract().as(BootstrapSummary.class);

        assertEquals(2, summary1.orgEndpointsAssigned);

        // Create new endpoints after first bootstrap
        createEndpoint(orgId, "endpoint-3");
        createEndpoint(orgId, "endpoint-4");

        // Run bootstrap again
        BootstrapSummary summary2 = given()
                        .header(createTurnpikeIdentityHeader("admin", adminRole))
            .contentType(JSON)
            .when()
            .post("/internal/workspace/bootstrap")
            .then()
            .statusCode(200)
            .extract().as(BootstrapSummary.class);

        // Verify - Only new endpoints assigned
        assertEquals(2, summary2.orgEndpointsAssigned,
            "Should only assign 2 newly created endpoints");
    }

    @Test
    void testBootstrapWithMultipleOrgsAndEndpoints() {
        // Setup - Create endpoints for 5 orgs with varying counts
        createEndpoint("org-scale-1", "org1-endpoint-1");
        createEndpoint("org-scale-1", "org1-endpoint-2");
        createEndpoint("org-scale-1", "org1-endpoint-3");

        createEndpoint("org-scale-2", "org2-endpoint-1");
        createEndpoint("org-scale-2", "org2-endpoint-2");

        createEndpoint("org-scale-3", "org3-endpoint-1");

        createEndpoint("org-scale-4", "org4-endpoint-1");
        createEndpoint("org-scale-4", "org4-endpoint-2");
        createEndpoint("org-scale-4", "org4-endpoint-3");
        createEndpoint("org-scale-4", "org4-endpoint-4");

        createEndpoint("org-scale-5", "org5-endpoint-1");

        // Execute - Call bootstrap
        BootstrapSummary summary = given()
                        .header(createTurnpikeIdentityHeader("admin", adminRole))
            .contentType(JSON)
            .when()
            .post("/internal/workspace/bootstrap")
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().as(BootstrapSummary.class);

        // Verify - All orgs processed
        assertTrue(summary.totalOrgsProcessed >= 5,
            "Should process at least 5 orgs");
        assertTrue(summary.workspacesCreated >= 5,
            "Should create at least 5 workspaces");

        // Verify - All endpoints assigned
        assertEquals(11, summary.orgEndpointsAssigned,
            "Should assign all 11 endpoints");
        assertEquals(0, summary.endpointsWithoutWorkspace,
            "No endpoints should be left without workspace");
    }

    @Test
    void testBootstrapEnsuresSameWorkspaceForSameOrg() {
        // Setup - Create multiple endpoints for same org
        String orgId = "org-same-workspace";
        createEndpoint(orgId, "endpoint-1");
        createEndpoint(orgId, "endpoint-2");
        createEndpoint(orgId, "endpoint-3");

        // Execute - Call bootstrap
        given()
                        .header(createTurnpikeIdentityHeader("admin", adminRole))
            .contentType(JSON)
            .when()
            .post("/internal/workspace/bootstrap")
            .then()
            .statusCode(200);

        // Verify - All endpoints for same org have same workspace_id
        String workspaceId1 = getEndpointWorkspaceId("endpoint-1");
        String workspaceId2 = getEndpointWorkspaceId("endpoint-2");
        String workspaceId3 = getEndpointWorkspaceId("endpoint-3");

        assertEquals(workspaceId1, workspaceId2,
            "Endpoints in same org should have same workspace");
        assertEquals(workspaceId2, workspaceId3,
            "Endpoints in same org should have same workspace");
    }

    @Test
    void testBootstrapWithMixedEndpointTypes() {
        // Setup - Create endpoints of different types
        String orgId = "org-mixed-types";
        createEndpoint(orgId, "webhook-endpoint");
        createEmailEndpoint(orgId, "email-endpoint");
        createCamelEndpoint(orgId, "camel-slack-endpoint", "slack");

        // Execute - Call bootstrap
        BootstrapSummary summary = given()
                        .header(createTurnpikeIdentityHeader("admin", adminRole))
            .contentType(JSON)
            .when()
            .post("/internal/workspace/bootstrap")
            .then()
            .statusCode(200)
            .extract().as(BootstrapSummary.class);

        // Verify - All endpoint types assigned correctly
        assertTrue(summary.orgEndpointsAssigned >= 3,
            "All endpoint types should be assigned workspace");

        // Verify all have workspace assigned
        assertNotNull(getEndpointWorkspaceId("webhook-endpoint"));
        assertNotNull(getEndpointWorkspaceId("email-endpoint"));
        assertNotNull(getEndpointWorkspaceId("camel-slack-endpoint"));
    }

    // Helper methods

    @Transactional
    void createEndpoint(String orgId, String name) {
        Endpoint endpoint = new Endpoint();
        endpoint.setOrgId(orgId);
        endpoint.setAccountId(orgId != null ? "account-" + orgId : null);
        endpoint.setName(name);
        endpoint.setDescription("Test endpoint for workspace bootstrap");
        endpoint.setType(WEBHOOK);

        WebhookProperties properties = new WebhookProperties();
        properties.setUrl("https://example.com/webhook");
        properties.setMethod(HttpType.POST);
        endpoint.setProperties(properties);

        em.persist(endpoint);
    }

    @Transactional
    void createEmailEndpoint(String orgId, String name) {
        Endpoint endpoint = new Endpoint();
        endpoint.setOrgId(orgId);
        endpoint.setAccountId("account-" + orgId);
        endpoint.setName(name);
        endpoint.setDescription("Email endpoint for testing");
        endpoint.setType(com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION);
        em.persist(endpoint);
    }

    @Transactional
    void createCamelEndpoint(String orgId, String name, String subType) {
        Endpoint endpoint = new Endpoint();
        endpoint.setOrgId(orgId);
        endpoint.setAccountId("account-" + orgId);
        endpoint.setName(name);
        endpoint.setDescription("Camel endpoint for testing");
        endpoint.setType(com.redhat.cloud.notifications.models.EndpointType.CAMEL);
        endpoint.setSubType(subType);
        em.persist(endpoint);
    }

    @Transactional
    String getWorkspaceOrgId(java.util.UUID workspaceId) {
        com.redhat.cloud.notifications.models.Workspace workspace = em.find(
            com.redhat.cloud.notifications.models.Workspace.class, workspaceId);
        return workspace != null ? workspace.getOrgId() : null;
    }

    @Transactional
    void assertEndpointHasWorkspace(String endpointName, java.util.UUID expectedWorkspaceId) {
        Endpoint endpoint = em.createQuery(
            "SELECT e FROM Endpoint e WHERE e.name = :name", Endpoint.class)
            .setParameter("name", endpointName)
            .getSingleResult();

        assertNotNull(endpoint.getWorkspace(),
            "Endpoint " + endpointName + " should have workspace assigned");
        assertEquals(expectedWorkspaceId, endpoint.getWorkspace().getId(),
            "Endpoint " + endpointName + " should have correct workspace ID");
    }

    @Transactional
    String getEndpointWorkspaceId(String endpointName) {
        Endpoint endpoint = em.createQuery(
            "SELECT e FROM Endpoint e WHERE e.name = :name", Endpoint.class)
            .setParameter("name", endpointName)
            .getSingleResult();

        return endpoint.getWorkspace() != null
            ? endpoint.getWorkspace().getId().toString()
            : null;
    }
}
