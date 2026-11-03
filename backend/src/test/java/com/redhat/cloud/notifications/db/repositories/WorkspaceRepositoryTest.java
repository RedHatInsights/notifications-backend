package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.models.Workspace;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class WorkspaceRepositoryTest extends DbIsolatedTest {

    @Inject
    WorkspaceRepository workspaceRepository;

    @Inject
    EntityManager em;

    @Test
    void testCreateOrGetWorkspace_CreatesNew() {
        UUID workspaceId = UUID.randomUUID();
        String orgId = "test-org-create";

        Workspace workspace = workspaceRepository.createOrGetWorkspace(workspaceId, orgId);

        assertNotNull(workspace);
        assertEquals(workspaceId, workspace.getId());
        assertEquals(orgId, workspace.getOrgId());
        assertNotNull(workspace.getCreated());
    }

    @Test
    void testCreateOrGetWorkspace_GetsExisting() {
        UUID workspaceId = UUID.randomUUID();
        String orgId = "test-org-existing";

        // Create first time
        Workspace workspace1 = workspaceRepository.createOrGetWorkspace(workspaceId, orgId);

        // Call again with same UUID - should return existing
        Workspace workspace2 = workspaceRepository.createOrGetWorkspace(workspaceId, orgId);

        // Verify it's the same workspace (idempotency)
        assertEquals(workspace1.getId(), workspace2.getId());
        assertEquals(workspace1.getOrgId(), workspace2.getOrgId());
        // Note: Not comparing timestamps due to potential precision differences between
        // Java LocalDateTime and PostgreSQL timestamp. The ID match proves idempotency.
    }

    @Test
    void testCreateOrGetWorkspace_DetectsWorkspaceUuidCollision() {
        UUID workspaceId = UUID.randomUUID();
        String orgId1 = "test-org-first";
        String orgId2 = "test-org-second";

        // Create workspace for first org
        workspaceRepository.createOrGetWorkspace(workspaceId, orgId1);

        // Try to create workspace with same UUID for different org
        // This should fail with clear error message (detects RBAC bug or data corruption)
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> workspaceRepository.createOrGetWorkspace(workspaceId, orgId2),
            "Should detect workspace UUID collision across different orgs"
        );

        // Verify error message is helpful for debugging
        assertTrue(exception.getMessage().contains("Workspace UUID collision detected"));
        assertTrue(exception.getMessage().contains(workspaceId.toString()));
        assertTrue(exception.getMessage().contains(orgId1));
        assertTrue(exception.getMessage().contains(orgId2));
    }

    @Test
    void testGetSystemWorkspace_ReturnsMigrationCreatedWorkspace() {
        // System workspace is created by migration V1.143.0
        Workspace workspace = workspaceRepository.getSystemWorkspace();

        assertNotNull(workspace);
        assertNotNull(workspace.getId());
        assertNull(workspace.getOrgId(), "System workspace should have NULL org_id");
        assertNotNull(workspace.getCreated());
    }

    @Test
    void testGetSystemWorkspace_Idempotent() {
        // Get system workspace multiple times - should always return the same one
        Workspace workspace1 = workspaceRepository.getSystemWorkspace();
        UUID systemWorkspaceId = workspace1.getId();

        // Call again - should return same workspace
        Workspace workspace2 = workspaceRepository.getSystemWorkspace();

        assertEquals(systemWorkspaceId, workspace2.getId(),
            "Should return same system workspace UUID");
        assertNull(workspace2.getOrgId());
    }

    @Test
    void testAssignWorkspaceToEndpoints_BulkUpdate() {
        String orgId = "test-org-bulk";
        UUID workspaceId = UUID.randomUUID();

        // Create workspace
        Workspace workspace = workspaceRepository.createOrGetWorkspace(workspaceId, orgId);

        // Create multiple endpoints for this org without workspace
        createEndpoint(orgId, "endpoint-1", null);
        createEndpoint(orgId, "endpoint-2", null);
        createEndpoint(orgId, "endpoint-3", null);

        // Assign workspace to all endpoints
        int updated = workspaceRepository.assignWorkspaceToEndpoints(workspaceId, orgId);

        assertEquals(3, updated, "Should update 3 endpoints");

        // Verify all endpoints have workspace assigned
        List<Endpoint> endpoints = getEndpointsByOrgId(orgId);
        assertEquals(3, endpoints.size());
        for (Endpoint endpoint : endpoints) {
            assertNotNull(endpoint.getWorkspace());
            assertEquals(workspaceId, endpoint.getWorkspace().getId());
        }
    }

    @Test
    void testAssignWorkspaceToEndpoints_UpdatesAllEndpointsForConsistency() {
        String orgId = "test-org-consistency";
        UUID workspaceId1 = UUID.randomUUID();
        UUID workspaceId2 = UUID.randomUUID();

        // Create workspaces
        Workspace workspace1 = workspaceRepository.createOrGetWorkspace(workspaceId1, orgId);
        Workspace workspace2 = workspaceRepository.createOrGetWorkspace(workspaceId2, orgId);

        // Create endpoints - some already assigned to workspace1
        createEndpoint(orgId, "endpoint-already-assigned", workspace1);
        createEndpoint(orgId, "endpoint-unassigned-1", null);
        createEndpoint(orgId, "endpoint-unassigned-2", null);

        // Assign workspace2 to ALL endpoints for this org (guarantees consistency)
        int updated = workspaceRepository.assignWorkspaceToEndpoints(workspaceId2, orgId);

        assertEquals(3, updated, "Should update ALL 3 endpoints for consistency");

        // Verify ALL endpoints now have workspace2 (consistency guarantee)
        Endpoint endpoint1 = getEndpointByName("endpoint-already-assigned");
        assertEquals(workspaceId2, endpoint1.getWorkspace().getId(),
            "Previously assigned endpoint should be updated to new workspace");

        Endpoint endpoint2 = getEndpointByName("endpoint-unassigned-1");
        assertEquals(workspaceId2, endpoint2.getWorkspace().getId());

        Endpoint endpoint3 = getEndpointByName("endpoint-unassigned-2");
        assertEquals(workspaceId2, endpoint3.getWorkspace().getId());
    }

    @Test
    void testAssignSystemWorkspace_OnlySystemEndpoints() {
        Workspace systemWorkspace = workspaceRepository.getSystemWorkspace();
        UUID systemWorkspaceId = systemWorkspace.getId();

        // Create mix of system (org_id=NULL) and org endpoints
        createEndpoint(null, "system-endpoint-1", null);
        createEndpoint(null, "system-endpoint-2", null);
        createEndpoint("test-org", "org-endpoint", null);

        // Assign system workspace
        int updated = workspaceRepository.assignSystemWorkspace(systemWorkspaceId);

        assertEquals(2, updated, "Should only update 2 system endpoints (org_id IS NULL)");

        // Verify system endpoints have system workspace
        Endpoint sysEndpoint1 = getEndpointByName("system-endpoint-1");
        assertEquals(systemWorkspaceId, sysEndpoint1.getWorkspace().getId());
        assertNull(sysEndpoint1.getOrgId());

        // Verify org endpoint is unaffected
        Endpoint orgEndpoint = getEndpointByName("org-endpoint");
        assertNull(orgEndpoint.getWorkspace(), "Org endpoint should still be unassigned");
        assertEquals("test-org", orgEndpoint.getOrgId());
    }

    @Test
    void testGetOrgIdsNeedingWorkspaceAssignment() {
        // Create endpoints without workspace assignments for multiple orgs
        createEndpoint("org-alpha", "endpoint-1", null);
        createEndpoint("org-alpha", "endpoint-2", null);
        createEndpoint("org-beta", "endpoint-3", null);
        createEndpoint("org-gamma", "endpoint-4", null);
        createEndpoint("org-beta", "endpoint-5", null);
        createEndpoint(null, "system-endpoint", null); // System endpoint should be excluded

        List<String> orgIds = workspaceRepository.getOrgIdsNeedingWorkspaceAssignment();

        assertEquals(3, orgIds.size(), "Should return 3 unique org IDs that need workspace assignment");
        assertTrue(orgIds.contains("org-alpha"));
        assertTrue(orgIds.contains("org-beta"));
        assertTrue(orgIds.contains("org-gamma"));
    }

    @Test
    void testGetOrgIdsNeedingWorkspaceAssignment_ExcludesFullyAssignedOrgs() {
        // Create workspace for org-delta
        UUID workspaceDelta = UUID.randomUUID();
        Workspace wsDelta = workspaceRepository.createOrGetWorkspace(workspaceDelta, "org-delta");

        // Create endpoints:
        // - org-epsilon: all endpoints WITHOUT workspace (should be included)
        // - org-delta: all endpoints WITH workspace (should be excluded)
        // - org-zeta: SOME endpoints with workspace, SOME without (should be included)
        createEndpoint("org-epsilon", "endpoint-unassigned-1", null);
        createEndpoint("org-epsilon", "endpoint-unassigned-2", null);

        createEndpoint("org-delta", "endpoint-assigned-1", wsDelta);
        createEndpoint("org-delta", "endpoint-assigned-2", wsDelta);

        UUID workspaceZeta = UUID.randomUUID();
        Workspace wsZeta = workspaceRepository.createOrGetWorkspace(workspaceZeta, "org-zeta");
        createEndpoint("org-zeta", "endpoint-mixed-1", wsZeta);
        createEndpoint("org-zeta", "endpoint-mixed-2", null); // One unassigned

        List<String> orgIds = workspaceRepository.getOrgIdsNeedingWorkspaceAssignment();

        assertEquals(2, orgIds.size(), "Should only return orgs with at least one unassigned endpoint");
        assertTrue(orgIds.contains("org-epsilon"), "Org with all unassigned endpoints should be included");
        assertTrue(orgIds.contains("org-zeta"), "Org with partially assigned endpoints should be included");
        assertTrue(!orgIds.contains("org-delta"), "Org with all assigned endpoints should be excluded");
    }

    @Test
    void testHasSystemEndpointsNeedingAssignment_WithUnassignedSystemEndpoints() {
        // Create system endpoints without workspace assignment
        createEndpoint(null, "system-endpoint-1", null);
        createEndpoint(null, "system-endpoint-2", null);
        createEndpoint("org-regular", "regular-endpoint", null); // Regular endpoint should not affect result

        boolean hasUnassigned = workspaceRepository.hasSystemEndpointsNeedingAssignment();

        assertTrue(hasUnassigned, "Should return true when system endpoints have no workspace");
    }

    @Test
    void testHasSystemEndpointsNeedingAssignment_AllSystemEndpointsAssigned() {
        // Get system workspace
        Workspace systemWorkspace = workspaceRepository.getSystemWorkspace();

        // Create system endpoints WITH workspace assignment
        createEndpoint(null, "system-endpoint-1", systemWorkspace);
        createEndpoint(null, "system-endpoint-2", systemWorkspace);
        createEndpoint("org-regular", "regular-endpoint", null); // Unassigned org endpoint should not affect result

        boolean hasUnassigned = workspaceRepository.hasSystemEndpointsNeedingAssignment();

        assertFalse(hasUnassigned, "Should return false when all system endpoints have workspace");
    }

    @Test
    void testHasSystemEndpointsNeedingAssignment_NoSystemEndpoints() {
        // Create only regular org endpoints
        createEndpoint("org-alpha", "endpoint-1", null);
        createEndpoint("org-beta", "endpoint-2", null);

        boolean hasUnassigned = workspaceRepository.hasSystemEndpointsNeedingAssignment();

        assertFalse(hasUnassigned, "Should return false when there are no system endpoints");
    }

    @Test
    void testSystemWorkspaceHasNullOrgId() {
        // Test that the system workspace (created by migration V1.143.0) has org_id = NULL
        // The unique constraint ensures only ONE workspace can have org_id IS NULL
        Workspace systemWorkspace = workspaceRepository.getSystemWorkspace();

        assertNotNull(systemWorkspace.getId());
        assertNull(systemWorkspace.getOrgId(), "System workspace should have NULL org_id");
    }

    @Test
    void testSystemWorkspaceConstantMatchesMigration() {
        // CRITICAL: Verify that WorkspaceRepository.SYSTEM_WORKSPACE_ID matches
        // the UUID hardcoded in migration V1.143.0
        //
        // If this test fails, it means the Java constant and SQL migration are out of sync.
        // Both must use the same UUID derived from:
        // UUID.nameUUIDFromBytes("notifications-system-workspace".getBytes(UTF_8))
        //
        // Expected: 5d3d4d41-a502-36a6-b724-3b1fcfe41a1a

        UUID expectedSystemWorkspaceId = UUID.fromString("5d3d4d41-a502-36a6-b724-3b1fcfe41a1a");

        // Calculate what the Java code generates
        UUID javaCalculatedId = UUID.nameUUIDFromBytes(
            "notifications-system-workspace".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        // Verify Java calculation matches expected value
        assertEquals(expectedSystemWorkspaceId, javaCalculatedId,
            "Java-calculated UUID must match migration hardcoded value");

        // Verify database has the expected UUID (migration inserted it)
        Workspace systemWorkspace = workspaceRepository.getSystemWorkspace();
        assertEquals(expectedSystemWorkspaceId, systemWorkspace.getId(),
            "Migration V1.143.0 must have inserted workspace with expected UUID");
    }

    @Test
    void testBulkUpdateSetsWorkspaceRelationship() {
        String orgId = "test-org-relationship";
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = workspaceRepository.createOrGetWorkspace(workspaceId, orgId);

        createEndpoint(orgId, "endpoint-for-relationship", null);

        // Bulk update sets the workspace relationship
        int updated = workspaceRepository.assignWorkspaceToEndpoints(workspaceId, orgId);
        assertEquals(1, updated);

        // Verify workspace relationship is set (not just workspace_id column)
        Endpoint endpoint = getEndpointByName("endpoint-for-relationship");
        assertNotNull(endpoint.getWorkspace(), "Workspace relationship should be set");
        assertEquals(workspaceId, endpoint.getWorkspace().getId());
        assertEquals(orgId, endpoint.getWorkspace().getOrgId());
    }

    @Test
    void testAssignWorkspaceWithMultipleOrgs() {
        // Create workspaces for multiple orgs
        UUID workspace1Id = UUID.randomUUID();
        UUID workspace2Id = UUID.randomUUID();
        Workspace workspace1 = workspaceRepository.createOrGetWorkspace(workspace1Id, "org-1");
        Workspace workspace2 = workspaceRepository.createOrGetWorkspace(workspace2Id, "org-2");

        // Create endpoints for each org
        createEndpoint("org-1", "org1-endpoint-1", null);
        createEndpoint("org-1", "org1-endpoint-2", null);
        createEndpoint("org-2", "org2-endpoint-1", null);

        // Assign workspace1 to org-1 endpoints
        int updated1 = workspaceRepository.assignWorkspaceToEndpoints(workspace1Id, "org-1");
        assertEquals(2, updated1);

        // Assign workspace2 to org-2 endpoints
        int updated2 = workspaceRepository.assignWorkspaceToEndpoints(workspace2Id, "org-2");
        assertEquals(1, updated2);

        // Verify org-1 endpoints have workspace1
        Endpoint org1Endpoint = getEndpointByName("org1-endpoint-1");
        assertEquals(workspace1Id, org1Endpoint.getWorkspace().getId());

        // Verify org-2 endpoint has workspace2
        Endpoint org2Endpoint = getEndpointByName("org2-endpoint-1");
        assertEquals(workspace2Id, org2Endpoint.getWorkspace().getId());
    }

    // Helper methods

    @Transactional
    void createEndpoint(String orgId, String name, Workspace workspace) {
        Endpoint endpoint = new Endpoint();
        endpoint.setOrgId(orgId);
        endpoint.setAccountId(orgId != null ? "account-" + orgId : null);
        endpoint.setName(name);
        endpoint.setDescription("Test endpoint");
        endpoint.setType(WEBHOOK);
        endpoint.setWorkspace(workspace);

        WebhookProperties properties = new WebhookProperties();
        properties.setUrl("https://example.com/webhook");
        properties.setMethod(HttpType.POST);
        endpoint.setProperties(properties);

        em.persist(endpoint);
    }

    @Transactional
    List<Endpoint> getEndpointsByOrgId(String orgId) {
        return em.createQuery(
            "SELECT e FROM Endpoint e WHERE e.orgId = :orgId", Endpoint.class)
            .setParameter("orgId", orgId)
            .getResultList();
    }

    @Transactional
    Endpoint getEndpointByName(String name) {
        return em.createQuery(
            "SELECT e FROM Endpoint e LEFT JOIN FETCH e.workspace WHERE e.name = :name", Endpoint.class)
            .setParameter("name", name)
            .getSingleResult();
    }
}
