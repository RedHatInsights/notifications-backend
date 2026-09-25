package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Workspace;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class WorkspaceRepository {

    @Inject
    EntityManager entityManager;

    /**
     * Create or get existing workspace by UUID.
     * Used during bootstrap to ensure idempotency.
     */
    @Transactional
    public Workspace createOrGetWorkspace(UUID workspaceId, String orgId) {
        try {
            // Try to find existing workspace
            return entityManager.createQuery(
                "SELECT w FROM Workspace w WHERE w.id = :id", Workspace.class)
                .setParameter("id", workspaceId)
                .getSingleResult();
        } catch (NoResultException e) {
            // Create new workspace
            Workspace workspace = new Workspace();
            workspace.setId(workspaceId);
            workspace.setOrgId(orgId);
            entityManager.persist(workspace);
            Log.debugf("Created workspace: id=%s, orgId=%s", workspaceId, orgId);
            return workspace;
        }
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
        String hql = "UPDATE Endpoint e SET e.workspaceId = :workspaceId " +
                     "WHERE e.orgId = :orgId AND e.workspace IS NULL";
        return entityManager.createQuery(hql)
            .setParameter("workspaceId", workspaceId)
            .setParameter("orgId", orgId)
            .executeUpdate();
    }

    /**
     * Assign system workspace to system integrations (orgId IS NULL).
     */
    @Transactional
    public int assignSystemWorkspace(UUID systemWorkspaceId) {
        String hql = "UPDATE Endpoint e SET e.workspaceId = :workspaceId " +
                     "WHERE e.orgId IS NULL AND e.workspace IS NULL";
        return entityManager.createQuery(hql)
            .setParameter("workspaceId", systemWorkspaceId)
            .executeUpdate();
    }
}
