package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.repositories.WorkspaceRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.routers.internal.WorkspaceBootstrapResource.BootstrapSummary;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.TestHelpers.createTurnpikeIdentityHeader;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void testBootstrapCreatesOrgWorkspaces() {
        // Setup - Create endpoints for multiple orgs
        String orgId1 = "test-org-bootstrap-1";
        String orgId2 = "test-org-bootstrap-2";
        createEndpoint(orgId1, "endpoint-1");
        createEndpoint(orgId1, "endpoint-2");
        createEndpoint(orgId2, "endpoint-3");

        // Execute - Call bootstrap endpoint
        BootstrapSummary summary = given()
                        .header(createTurnpikeIdentityHeader("admin", adminRole))
            .basePath(API_INTERNAL)
            .contentType(JSON)
            .when()
            .post("/workspace/bootstrap")
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().as(BootstrapSummary.class);

        // Verify - System workspace created
        assertNotNull(summary.systemWorkspaceId);

        // Verify - Workspace records created for each org
        assertTrue(summary.totalOrgsProcessed >= 2, "Should process at least 2 orgs");
        assertTrue(summary.orgsProcessedSuccessfully >= 2, "Should successfully process at least 2 orgs");

        // Verify - Endpoints assigned to correct workspaces
        assertTrue(summary.orgEndpointsAssigned >= 3, "Should assign at least 3 org endpoints");
    }

    @Test
    void testBootstrapIdempotency() {
        // Setup - Create both system and org endpoints
        createEndpoint(null, "system-endpoint-idempotent");
        String orgId = "test-org-idempotent";
        createEndpoint(orgId, "endpoint-1");

        // Execute - Call bootstrap twice
        BootstrapSummary summary1 = given()
                        .header(createTurnpikeIdentityHeader("admin", adminRole))
            .basePath(API_INTERNAL)
            .contentType(JSON)
            .when()
            .post("/workspace/bootstrap")
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().as(BootstrapSummary.class);

        BootstrapSummary summary2 = given()
                        .header(createTurnpikeIdentityHeader("admin", adminRole))
            .basePath(API_INTERNAL)
            .contentType(JSON)
            .when()
            .post("/workspace/bootstrap")
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().as(BootstrapSummary.class);

        // Verify - Same system workspace ID both times
        assertEquals(summary1.systemWorkspaceId, summary2.systemWorkspaceId,
            "System workspace ID should be the same on second bootstrap");

        // Verify - First run actually assigned endpoints
        assertEquals(1, summary1.systemEndpointsAssigned,
            "First bootstrap should assign 1 system endpoint");
        assertEquals(1, summary1.orgEndpointsAssigned,
            "First bootstrap should assign 1 org endpoint");

        // Verify - Second run skips already-assigned endpoints
        assertEquals(0, summary2.systemEndpointsAssigned,
            "Second bootstrap should not assign additional system endpoints");
        assertEquals(0, summary2.orgEndpointsAssigned,
            "Second bootstrap should not assign additional org endpoints");
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
            .basePath(API_INTERNAL)
            .contentType(JSON)
            .when()
            .post("/workspace/bootstrap")
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
            .basePath(API_INTERNAL)
            .contentType(JSON)
            .when()
            .post("/workspace/bootstrap")
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().as(BootstrapSummary.class);

        // Verify - Bootstrap succeeds with zero counts
        assertNotNull(summary.systemWorkspaceId, "System workspace should still be created");
        assertEquals(0, summary.systemEndpointsAssigned);
        assertEquals(0, summary.totalOrgsProcessed);
        assertEquals(0, summary.orgsProcessedSuccessfully);
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
            .basePath(API_INTERNAL)
            .contentType(JSON)
            .when()
            .post("/workspace/bootstrap")
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
            .basePath(API_INTERNAL)
            .contentType(JSON)
            .when()
            .post("/workspace/bootstrap")
            .then()
            .statusCode(200)
            .extract().as(BootstrapSummary.class);

        // Verify - ALL endpoints updated for consistency (not just new ones)
        // Bootstrap finds org has unassigned endpoints, so processes it
        // When processing, updates ALL endpoints for the org to guarantee consistency
        assertEquals(4, summary2.orgEndpointsAssigned,
            "Should update ALL 4 endpoints to guarantee workspace consistency");
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
            .basePath(API_INTERNAL)
            .contentType(JSON)
            .when()
            .post("/workspace/bootstrap")
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().as(BootstrapSummary.class);

        // Verify - All orgs processed
        assertTrue(summary.totalOrgsProcessed >= 5,
            "Should process at least 5 orgs");
        assertTrue(summary.orgsProcessedSuccessfully >= 5,
            "Should successfully process at least 5 orgs");

        // Verify - All endpoints assigned
        assertEquals(11, summary.orgEndpointsAssigned,
            "Should assign all 11 endpoints");
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
            .basePath(API_INTERNAL)
            .contentType(JSON)
            .when()
            .post("/workspace/bootstrap")
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
            .basePath(API_INTERNAL)
            .contentType(JSON)
            .when()
            .post("/workspace/bootstrap")
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

    @Test
    void testResetClearsAllWorkspacesAndUnassignsEndpoints() {
        // Setup - Create endpoints and bootstrap
        createEndpoint("org-reset-1", "endpoint-1");
        createEndpoint("org-reset-2", "endpoint-2");
        createEndpoint(null, "system-endpoint");

        // Bootstrap to assign workspaces
        given()
                        .header(createTurnpikeIdentityHeader("admin", adminRole))
            .basePath(API_INTERNAL)
            .contentType(JSON)
            .when()
            .post("/workspace/bootstrap")
            .then()
            .statusCode(200);

        // Capture workspace IDs and system workspace ID before reset
        String workspace1Before = getEndpointWorkspaceId("endpoint-1");
        String workspace2Before = getEndpointWorkspaceId("endpoint-2");
        String systemEndpointWorkspaceBefore = getEndpointWorkspaceId("system-endpoint");
        String systemWorkspaceIdBefore = workspaceRepository.getSystemWorkspace().getId().toString();

        assertNotNull(workspace1Before);
        assertNotNull(workspace2Before);
        assertNotNull(systemEndpointWorkspaceBefore);

        // Execute - Reset (returns 204 No Content)
        given()
                        .header(createTurnpikeIdentityHeader("admin", adminRole))
            .basePath(API_INTERNAL)
            .contentType(JSON)
            .when()
            .post("/workspace/reset")
            .then()
            .statusCode(204);

        // Clear entity manager cache so test can verify deletion
        em.clear();

        // Verify - Endpoints no longer have workspace assigned
        assertNull(getEndpointWorkspaceId("endpoint-1"),
            "endpoint-1 should have null workspace after reset");
        assertNull(getEndpointWorkspaceId("endpoint-2"),
            "endpoint-2 should have null workspace after reset");
        assertNull(getEndpointWorkspaceId("system-endpoint"),
            "system-endpoint should have null workspace after reset");

        // Verify - System workspace was deleted (reset is pure deletion)
        try {
            workspaceRepository.getSystemWorkspace();
            assertTrue(false, "System workspace should not exist after reset");
        } catch (IllegalStateException e) {
            // Expected - system workspace was deleted
            assertTrue(e.getMessage().contains("System workspace not found"));
        }

        // Run bootstrap again to restore system workspace
        given()
                        .header(createTurnpikeIdentityHeader("admin", adminRole))
            .basePath(API_INTERNAL)
            .contentType(JSON)
            .when()
            .post("/workspace/bootstrap")
            .then()
            .statusCode(200);

        // Verify - System workspace recreated with same ID (self-healing)
        String systemWorkspaceIdAfter = workspaceRepository.getSystemWorkspace().getId().toString();
        assertEquals(systemWorkspaceIdBefore, systemWorkspaceIdAfter,
            "System workspace ID should be recreated with same well-known UUID");
    }

    @Test
    void testResetThenBootstrapReassignsSameWorkspaceIds() {
        // Setup - Create endpoints for multiple orgs and system endpoint
        createEndpoint("org-cycle-1", "endpoint-cycle-1");
        createEndpoint("org-cycle-2", "endpoint-cycle-2");
        createEndpoint(null, "system-cycle");

        // Bootstrap
        given()
                        .header(createTurnpikeIdentityHeader("admin", adminRole))
            .basePath(API_INTERNAL)
            .contentType(JSON)
            .when()
            .post("/workspace/bootstrap")
            .then()
            .statusCode(200);

        // Capture all workspace IDs before reset
        String workspace1Before = getEndpointWorkspaceId("endpoint-cycle-1");
        String workspace2Before = getEndpointWorkspaceId("endpoint-cycle-2");
        String systemWorkspaceBefore = getEndpointWorkspaceId("system-cycle");
        String systemWorkspaceIdBefore = workspaceRepository.getSystemWorkspace().getId().toString();

        assertNotNull(workspace1Before);
        assertNotNull(workspace2Before);
        assertNotNull(systemWorkspaceBefore);

        // Execute - Reset
        given()
                        .header(createTurnpikeIdentityHeader("admin", adminRole))
            .basePath(API_INTERNAL)
            .contentType(JSON)
            .when()
            .post("/workspace/reset")
            .then()
            .statusCode(204);

        // Clear entity manager cache so test can verify deletion
        em.clear();

        // Verify - All endpoints unassigned
        assertNull(getEndpointWorkspaceId("endpoint-cycle-1"));
        assertNull(getEndpointWorkspaceId("endpoint-cycle-2"));
        assertNull(getEndpointWorkspaceId("system-cycle"));

        // Verify - System workspace deleted (reset is pure deletion)
        try {
            workspaceRepository.getSystemWorkspace();
            assertTrue(false, "System workspace should not exist after reset");
        } catch (IllegalStateException e) {
            // Expected - system workspace was deleted
        }

        // Execute - Bootstrap again (should recreate system workspace)
        BootstrapSummary summary = given()
                        .header(createTurnpikeIdentityHeader("admin", adminRole))
            .basePath(API_INTERNAL)
            .contentType(JSON)
            .when()
            .post("/workspace/bootstrap")
            .then()
            .statusCode(200)
            .extract().as(BootstrapSummary.class);

        // Verify - Re-bootstrapped successfully
        assertEquals(2, summary.totalOrgsProcessed,
            "Should process both orgs again");
        assertEquals(2, summary.orgsProcessedSuccessfully,
            "Should successfully assign workspaces");
        assertEquals(1, summary.systemEndpointsAssigned,
            "Should assign system endpoint");

        // Verify - System workspace recreated with same ID (self-healing)
        String systemWorkspaceIdAfterBootstrap = workspaceRepository.getSystemWorkspace().getId().toString();
        assertEquals(systemWorkspaceIdBefore, systemWorkspaceIdAfterBootstrap,
            "Bootstrap should recreate system workspace with same well-known UUID");

        // Verify - Same workspace IDs assigned (deterministic from fake RBAC)
        String workspace1After = getEndpointWorkspaceId("endpoint-cycle-1");
        String workspace2After = getEndpointWorkspaceId("endpoint-cycle-2");
        String systemWorkspaceAfter = getEndpointWorkspaceId("system-cycle");

        assertEquals(workspace1Before, workspace1After,
            "org-cycle-1 should get same workspace ID (deterministic from fake RBAC)");
        assertEquals(workspace2Before, workspace2After,
            "org-cycle-2 should get same workspace ID (deterministic from fake RBAC)");
        assertEquals(systemWorkspaceBefore, systemWorkspaceAfter,
            "system endpoint should get same system workspace ID");
        assertEquals(systemWorkspaceIdBefore, systemWorkspaceAfter,
            "system workspace ID should be the well-known constant");
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
