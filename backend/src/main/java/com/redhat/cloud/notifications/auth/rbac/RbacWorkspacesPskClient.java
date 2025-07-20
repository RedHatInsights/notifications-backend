package com.redhat.cloud.notifications.auth.rbac;

import com.redhat.cloud.notifications.auth.rbac.workspace.RbacWorkspace;
import com.redhat.cloud.notifications.routers.models.Page;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "rbac-authentication")
@RegisterProvider(RbacClientResponseFilter.class)
public interface RbacWorkspacesPskClient {

    /**
     * Gets the organization's workspaces.
     * @param orgId the organization ID to get the workspaces from.
     * @param workspaceType the type of the workspace to fetch.
     * @return a page of workspaces.
     */
    @GET
    @Path("/api/rbac/v2/workspaces/")
    @Produces(MediaType.APPLICATION_JSON)
    Page<RbacWorkspace> getWorkspaces(
        @HeaderParam("x-rh-rbac-psk") String rbacPsk,
        @HeaderParam("x-rh-rbac-client-id") String clientId,
        @HeaderParam("x-rh-rbac-org-id") String orgId,
        @QueryParam("type") String workspaceType,
        @QueryParam("offset") Integer offset,
        @QueryParam("limit") Integer limit
    );
}
