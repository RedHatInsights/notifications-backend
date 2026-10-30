package com.redhat.cloud.notifications.routers.internal;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.db.repositories.WorkspaceRepository;
import com.redhat.cloud.notifications.models.Workspace;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(Constants.API_INTERNAL + "/workspace")
@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
public class WorkspaceBootstrapResource {

    @Inject
    WorkspaceRepository workspaceRepository;

    @Inject
    WorkspaceUtils workspaceUtils;

    /**
     * Bootstrap workspace assignments for all existing endpoints.
     *
     * This is a self-healing operation that:
     * 1. Ensures system workspace exists (creates if missing, e.g., after reset)
     * 2. Assigns system workspace to system integrations (if any need assignment)
     * 3. For each org with endpoints: fetches workspace UUID from RBAC,
     *    creates workspace record, assigns to org's endpoints
     *
     * Safe to run multiple times (idempotent).
     * Self-healing: recreates system workspace if deleted (e.g., by /reset).
     *
     * Transaction strategy: This method is NOT @Transactional. Each repository
     * method manages its own transaction. This ensures:
     * - System workspace creation commits independently
     * - Each org's workspace assignment commits independently
     * - One org failing doesn't roll back other orgs
     * - RBAC calls happen outside of database transactions (no long-running txns)
     * - No transaction timeout with many orgs
     */
    @POST
    @Path("/bootstrap")
    @Produces(APPLICATION_JSON)
    public BootstrapSummary bootstrapWorkspaces() {

        BootstrapSummary summary = new BootstrapSummary();

        // Step 1: Get or create system workspace
        // Normally created by migration V1.143.0, but we recreate if missing (e.g., after reset)
        Workspace systemWorkspace;
        try {
            systemWorkspace = workspaceRepository.getSystemWorkspace();
        } catch (IllegalStateException e) {
            Log.warnf("System workspace not found, recreating: %s", e.getMessage());
            workspaceRepository.createSystemWorkspace();
            systemWorkspace = workspaceRepository.getSystemWorkspace();
        }
        summary.systemWorkspaceId = systemWorkspace.getId();

        if (workspaceRepository.hasSystemEndpointsNeedingAssignment()) {
            int systemEndpointsUpdated = workspaceRepository.assignSystemWorkspace(
                systemWorkspace.getId()
            );
            summary.systemEndpointsAssigned = systemEndpointsUpdated;
        } else {
            summary.systemEndpointsAssigned = 0;
        }

        // Step 2: Process each org that has endpoints needing workspace assignment
        List<String> orgIds = workspaceRepository.getOrgIdsNeedingWorkspaceAssignment();
        summary.totalOrgsProcessed = orgIds.size();

        for (String orgId : orgIds) {
            try {
                // Fetch workspace UUID from RBAC
                UUID workspaceId = workspaceUtils.getDefaultWorkspaceId(orgId);

                // Handle null workspace ID (can happen for very old orgs in production)
                if (workspaceId == null) {
                    Log.warnf("RBAC returned null workspace ID for org_id: %s - skipping", orgId);
                    summary.addError(orgId, "RBAC returned null workspace ID (legacy org)");
                    continue;
                }

                // Create or get workspace record
                Workspace workspace = workspaceRepository.createOrGetWorkspace(
                    workspaceId, orgId
                );

                // Assign to all endpoints for this org
                int endpointsUpdated = workspaceRepository.assignWorkspaceToEndpoints(
                    workspace.getId(), orgId
                );

                summary.orgEndpointsAssigned += endpointsUpdated;
                summary.orgsProcessedSuccessfully++;

            } catch (Exception e) {
                Log.errorf(e, "Failed to bootstrap workspace for org_id: %s", orgId);
                String errorMessage = e.getClass().getSimpleName() + ": " +
                    (e.getMessage() != null ? e.getMessage() : "No error message");
                summary.addError(orgId, errorMessage);
            }
        }

        Log.infof("Workspace bootstrap completed: %s", summary);
        return summary;
    }

    /**
     * Reset workspace assignments - clear all workspaces and unassign all endpoints.
     *
     * This is a pure cleanup/deletion operation that:
     * 1. Deletes all workspace records (which cascades to set endpoint.workspace_id = NULL via FK constraint)
     *
     * Use cases:
     * - Testing: reset to clean state between test runs
     * - Rollback: undo workspace feature before making workspace_id NOT NULL
     * - Re-bootstrap: clear bad data and start fresh
     *
     * After reset, endpoints are back to pre-bootstrap state.
     * The next /bootstrap call will recreate the system workspace and reassign all endpoints.
     *
     * WARNING: This is a destructive operation. All workspace assignments are lost.
     */
    @POST
    @Path("/reset")
    public void resetWorkspaces() {

        // Count what we're about to reset
        long workspaceCount = workspaceRepository.countAllWorkspaces();
        long endpointCount = workspaceRepository.countEndpointsWithWorkspace();

        Log.infof("Starting workspace reset: %d workspaces, %d endpoints with workspace_id",
            workspaceCount, endpointCount);

        // Delete all workspaces
        // FK constraint ON DELETE SET NULL automatically sets endpoint.workspace_id = NULL
        workspaceRepository.deleteAllWorkspaces();

        Log.infof("Workspace reset completed: deleted %d workspaces, reset %d endpoints",
            workspaceCount, endpointCount);
    }

    // Inner classes for response DTOs
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class BootstrapSummary {
        public UUID systemWorkspaceId;
        public int systemEndpointsAssigned;
        public int totalOrgsProcessed;
        public int orgsProcessedSuccessfully;
        public int orgEndpointsAssigned;
        public Map<String, String> errors = new HashMap<>();

        public void addError(String orgId, String message) {
            errors.put(orgId, message);
        }

        @Override
        public String toString() {
            return String.format(
                "BootstrapSummary{systemWorkspaceId=%s, systemEndpointsAssigned=%d, " +
                "totalOrgsProcessed=%d, orgsProcessedSuccessfully=%d, orgEndpointsAssigned=%d, " +
                "errorCount=%d}",
                systemWorkspaceId,
                systemEndpointsAssigned,
                totalOrgsProcessed,
                orgsProcessedSuccessfully,
                orgEndpointsAssigned,
                errors.size()
            );
        }
    }
}
