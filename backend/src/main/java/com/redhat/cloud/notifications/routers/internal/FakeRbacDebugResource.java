package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.rbac.workspace.FakeWorkspaceUtils;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import java.util.Map;
import java.util.UUID;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * Debug endpoint to view fake RBAC workspace mappings.
 * Only available when the "fake-rbac" profile is active.
 *
 * WARNING: This is a test utility and must be removed before production deployment.
 */
@Path(Constants.API_INTERNAL + "/fake-rbac")
@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
@IfBuildProfile("fake-rbac")
public class FakeRbacDebugResource {

    @GET
    @Path("/workspace-mappings")
    @Produces(APPLICATION_JSON)
    public Map<String, UUID> getWorkspaceMappings() {
        return FakeWorkspaceUtils.getAllMappings();
    }

    @GET
    @Path("/status")
    @Produces(APPLICATION_JSON)
    public StatusResponse getStatus() {
        StatusResponse response = new StatusResponse();
        response.enabled = true;
        response.message = "Fake RBAC workspace client is ACTIVE - this is for TESTING ONLY";
        response.totalMappings = FakeWorkspaceUtils.getAllMappings().size();
        return response;
    }

    public static class StatusResponse {
        public boolean enabled;
        public String message;
        public int totalMappings;
    }
}
