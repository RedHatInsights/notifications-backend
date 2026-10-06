package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Workspace;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class WorkspaceRepository {

    /**
     * Well-known UUID for the system workspace (org_id IS NULL).
     * This constant ensures all bootstrap requests attempt to create the same
     * system workspace ID, making ON CONFLICT idempotent across concurrent calls.
     *
     * Generated deterministically from "notifications-system-workspace" using
     * UUID v3 (name-based MD5). Same input always produces the same UUID.
     */
    private static final UUID SYSTEM_WORKSPACE_ID = UUID.nameUUIDFromBytes(
        "notifications-system-workspace".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    );

    @Inject
    EntityManager entityManager;

    /**
     * Create or get existing workspace by UUID for a specific org.
     * Used during bootstrap to ensure idempotency.
     *
     * IMPORTANT: This method is for org-specific workspaces only (orgId must NOT be null).
     * For the system workspace (orgId IS NULL), use getOrCreateSystemWorkspace() instead.
     *
     * Concurrency-safe: Uses INSERT ... ON CONFLICT DO NOTHING to handle
     * concurrent bootstrap requests attempting to create the same workspace.
     * The winning insert succeeds; losers see no-op, then all read back the row.
     */
    @Transactional
    public Workspace createOrGetWorkspace(UUID workspaceId, String orgId) {
        if (orgId == null) {
            throw new IllegalArgumentException(
                "orgId must not be null. Use getOrCreateSystemWorkspace() for the system workspace."
            );
        }

        // Use native SQL with ON CONFLICT to handle concurrent inserts
        // If workspace already exists (concurrent request won), this is a no-op
        entityManager.createNativeQuery(
            "INSERT INTO workspace (id, org_id, created) " +
            "VALUES (:id, :orgId, NOW()) " +
            "ON CONFLICT (id) DO NOTHING")
            .setParameter("id", workspaceId)
            .setParameter("orgId", orgId)
            .executeUpdate();

        // Always read back to get the workspace (whether just inserted or pre-existing)
        Workspace workspace = entityManager.find(Workspace.class, workspaceId);
        if (workspace == null) {
            throw new IllegalStateException(
                "Workspace not found after insert: id=" + workspaceId + ", orgId=" + orgId
            );
        }

        Log.debugf("Workspace ready: id=%s, orgId=%s", workspaceId, orgId);
        return workspace;
    }

    /**
     * Get or create the system workspace (org_id IS NULL).
     * Returns the existing system workspace if one exists, otherwise creates one.
     *
     * Concurrency-safe: Uses a well-known constant UUID and INSERT ... ON CONFLICT
     * to handle concurrent bootstrap requests. The partial unique index on
     * workspace((1)) WHERE org_id IS NULL ensures only one system workspace can exist.
     * All concurrent requests converge on the same system workspace ID.
     */
    @Transactional
    public Workspace getOrCreateSystemWorkspace() {
        // Use native SQL with ON CONFLICT to handle concurrent inserts
        // All requests use the same well-known SYSTEM_WORKSPACE_ID
        entityManager.createNativeQuery(
            "INSERT INTO workspace (id, org_id, created) " +
            "VALUES (:id, NULL, NOW()) " +
            "ON CONFLICT (id) DO NOTHING")
            .setParameter("id", SYSTEM_WORKSPACE_ID)
            .executeUpdate();

        // Always read back to get the system workspace
        Workspace workspace = entityManager.find(Workspace.class, SYSTEM_WORKSPACE_ID);
        if (workspace == null) {
            throw new IllegalStateException(
                "System workspace not found after insert: id=" + SYSTEM_WORKSPACE_ID
            );
        }

        Log.debugf("System workspace ready: id=%s", workspace.getId());
        return workspace;
    }

    /**
     * Get distinct org IDs from endpoints table.
     * Used by bootstrap to identify which orgs need workspace records.
     */
    public List<String> getDistinctOrgIdsFromEndpoints() {
        String hql = "SELECT DISTINCT e.orgId FROM Endpoint e " +
                     "WHERE e.orgId IS NOT NULL ORDER BY e.orgId";
        return entityManager.createQuery(hql, String.class).getResultList();
    }

    /**
     * Count endpoints without workspace assignment.
     */
    public long countEndpointsWithoutWorkspace() {
        String hql = "SELECT COUNT(e) FROM Endpoint e WHERE e.workspace IS NULL";
        return entityManager.createQuery(hql, Long.class).getSingleResult();
    }

    /**
     * Bulk update endpoints to assign workspace.
     * More efficient than loading/updating entities one by one.
     */
    @Transactional
    public int assignWorkspaceToEndpoints(UUID workspaceId, String orgId) {
        // Get the workspace entity to set the relationship
        Workspace workspace = entityManager.getReference(Workspace.class, workspaceId);

        String hql = "UPDATE Endpoint e SET e.workspace = :workspace " +
                     "WHERE e.orgId = :orgId AND e.workspace IS NULL";
        return entityManager.createQuery(hql)
            .setParameter("workspace", workspace)
            .setParameter("orgId", orgId)
            .executeUpdate();
    }

    /**
     * Assign system workspace to system integrations (orgId IS NULL).
     */
    @Transactional
    public int assignSystemWorkspace(UUID systemWorkspaceId) {
        // Get the workspace entity to set the relationship
        Workspace workspace = entityManager.getReference(Workspace.class, systemWorkspaceId);

        String hql = "UPDATE Endpoint e SET e.workspace = :workspace " +
                     "WHERE e.orgId IS NULL AND e.workspace IS NULL";
        return entityManager.createQuery(hql)
            .setParameter("workspace", workspace)
            .executeUpdate();
    }
}
