package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.auth.OidcServerMockResource;
import com.redhat.cloud.notifications.auth.rbac.workspace.RbacServerMockResource;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
@QuarkusTestResource(OidcServerMockResource.class)
@QuarkusTestResource(RbacServerMockResource.class)
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
    void testGetOrCreateSystemWorkspace_CreatesNew() {
        Workspace workspace = workspaceRepository.getOrCreateSystemWorkspace();

        assertNotNull(workspace);
        assertNotNull(workspace.getId());
        assertNull(workspace.getOrgId(), "System workspace should have NULL org_id");
        assertNotNull(workspace.getCreated());
    }

    @Test
    void testGetOrCreateSystemWorkspace_GetsExisting() {
        // Create system workspace first time
        Workspace workspace1 = workspaceRepository.getOrCreateSystemWorkspace();
        UUID systemWorkspaceId = workspace1.getId();

        // Call again - should return same workspace
        Workspace workspace2 = workspaceRepository.getOrCreateSystemWorkspace();

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
    void testAssignWorkspaceToEndpoints_OnlyUpdatesUnassigned() {
        String orgId = "test-org-partial";
        UUID workspaceId1 = UUID.randomUUID();
        UUID workspaceId2 = UUID.randomUUID();

        // Create workspaces
        Workspace workspace1 = workspaceRepository.createOrGetWorkspace(workspaceId1, orgId);
        Workspace workspace2 = workspaceRepository.createOrGetWorkspace(workspaceId2, orgId);

        // Create endpoints - some already assigned
        createEndpoint(orgId, "endpoint-already-assigned", workspace1);
        createEndpoint(orgId, "endpoint-unassigned-1", null);
        createEndpoint(orgId, "endpoint-unassigned-2", null);

        // Assign workspace2 to endpoints (should only affect unassigned ones)
        int updated = workspaceRepository.assignWorkspaceToEndpoints(workspaceId2, orgId);

        assertEquals(2, updated, "Should only update 2 unassigned endpoints");

        // Verify first endpoint still has workspace1
        Endpoint alreadyAssigned = getEndpointByName("endpoint-already-assigned");
        assertEquals(workspaceId1, alreadyAssigned.getWorkspace().getId());

        // Verify other endpoints have workspace2
        Endpoint newlyAssigned1 = getEndpointByName("endpoint-unassigned-1");
        assertEquals(workspaceId2, newlyAssigned1.getWorkspace().getId());
    }

    @Test
    void testAssignSystemWorkspace_OnlySystemEndpoints() {
        UUID systemWorkspaceId = UUID.randomUUID();
        Workspace systemWorkspace = workspaceRepository.createOrGetWorkspace(systemWorkspaceId, null);

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
    void testCountEndpointsWithoutWorkspace() {
        // Create mix of assigned and unassigned endpoints
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = workspaceRepository.createOrGetWorkspace(workspaceId, "org-1");

        createEndpoint("org-1", "assigned-1", workspace);
        createEndpoint("org-1", "assigned-2", workspace);
        createEndpoint("org-2", "unassigned-1", null);
        createEndpoint("org-2", "unassigned-2", null);
        createEndpoint("org-2", "unassigned-3", null);

        long count = workspaceRepository.countEndpointsWithoutWorkspace();

        assertEquals(3, count, "Should count 3 endpoints without workspace");
    }

    @Test
    void testGetDistinctOrgIdsFromEndpoints() {
        // Create endpoints for multiple orgs
        createEndpoint("org-alpha", "endpoint-1", null);
        createEndpoint("org-alpha", "endpoint-2", null);
        createEndpoint("org-beta", "endpoint-3", null);
        createEndpoint("org-gamma", "endpoint-4", null);
        createEndpoint("org-beta", "endpoint-5", null);
        createEndpoint(null, "system-endpoint", null); // System endpoint should be excluded

        List<String> orgIds = workspaceRepository.getDistinctOrgIdsFromEndpoints();

        assertEquals(3, orgIds.size(), "Should return 3 unique org IDs");
        assertTrue(orgIds.contains("org-alpha"));
        assertTrue(orgIds.contains("org-beta"));
        assertTrue(orgIds.contains("org-gamma"));
    }

    @Test
    void testWorkspaceOrgIdCanBeNull() {
        // Test that database schema allows org_id = NULL (for system workspace)
        UUID workspaceId = UUID.randomUUID();

        Workspace workspace = workspaceRepository.createOrGetWorkspace(workspaceId, null);

        assertNotNull(workspace.getId());
        assertNull(workspace.getOrgId(), "Database should allow NULL org_id");
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
            "SELECT e FROM Endpoint e WHERE e.name = :name", Endpoint.class)
            .setParameter("name", name)
            .getSingleResult();
    }
}
