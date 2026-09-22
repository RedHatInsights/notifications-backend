package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.db.repositories.WorkspaceRepository;
import com.redhat.cloud.notifications.models.Workspace;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
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
     * This is a one-time operation that:
     * 1. Creates system workspace and assigns to system integrations
     * 2. For each org with endpoints: fetches workspace UUID from RBAC,
     *    creates workspace record, assigns to org's endpoints
     *
     * Safe to run multiple times (idempotent).
     */
    @POST
    @Path("/bootstrap")
    @Produces(APPLICATION_JSON)
    @Transactional
    public BootstrapSummary bootstrapWorkspaces() {

        BootstrapSummary summary = new BootstrapSummary();

        // Step 1: Create system workspace (org_id = null)
        UUID systemWorkspaceId = UUID.randomUUID();
        Workspace systemWorkspace = workspaceRepository.createOrGetWorkspace(
            systemWorkspaceId, null
        );

        int systemEndpointsUpdated = workspaceRepository.assignSystemWorkspace(
            systemWorkspace.getId()
        );
        summary.systemWorkspaceId = systemWorkspace.getId();
        summary.systemEndpointsAssigned = systemEndpointsUpdated;

        // Step 2: Process each distinct org_id
        List<String> orgIds = workspaceRepository.getDistinctOrgIdsFromEndpoints();
        summary.totalOrgsProcessed = orgIds.size();

        for (String orgId : orgIds) {
            try {
                // Fetch workspace UUID from RBAC
                UUID workspaceId = workspaceUtils.getDefaultWorkspaceId(orgId);

                // Create or get workspace record
                Workspace workspace = workspaceRepository.createOrGetWorkspace(
                    workspaceId, orgId
                );

                // Assign to all endpoints for this org
                int endpointsUpdated = workspaceRepository.assignWorkspaceToEndpoints(
                    workspace.getId(), orgId
                );

                summary.orgEndpointsAssigned += endpointsUpdated;
                summary.workspacesCreated++;

            } catch (Exception e) {
                Log.errorf(e, "Failed to bootstrap workspace for org_id: %s", orgId);
                summary.addError(orgId, e.getMessage());
            }
        }

        summary.endpointsWithoutWorkspace = workspaceRepository.countEndpointsWithoutWorkspace();

        Log.infof("Workspace bootstrap completed: %s", summary);
        return summary;
    }

    /**
     * Get current bootstrap status without making changes.
     */
    @GET
    @Path("/status")
    @Produces(APPLICATION_JSON)
    public BootstrapStatus getBootstrapStatus() {
        BootstrapStatus status = new BootstrapStatus();
        status.endpointsWithoutWorkspace = workspaceRepository.countEndpointsWithoutWorkspace();
        status.totalOrgsWithEndpoints = workspaceRepository.getDistinctOrgIdsFromEndpoints().size();
        return status;
    }

    // Inner classes for response DTOs
    public static class BootstrapSummary {
        public UUID systemWorkspaceId;
        public int systemEndpointsAssigned;
        public int totalOrgsProcessed;
        public int workspacesCreated;
        public int orgEndpointsAssigned;
        public long endpointsWithoutWorkspace;
        public Map<String, String> errors = new HashMap<>();

        public void addError(String orgId, String message) {
            errors.put(orgId, message);
        }
    }

    public static class BootstrapStatus {
        public long endpointsWithoutWorkspace;
        public int totalOrgsWithEndpoints;
    }
}
