package com.redhat.cloud.notifications.auth.rbac;

import com.redhat.cloud.notifications.auth.rbac.workspace.RbacWorkspace;
import com.redhat.cloud.notifications.routers.models.Page;
import io.quarkus.oidc.client.filter.OidcClientFilter;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "rbac-authentication-oidc")
@RegisterProvider(RbacClientResponseFilter.class)
@OidcClientFilter
public interface RbacWorkspacesOidcClient {

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
        @HeaderParam("x-rh-rbac-org-id") String orgId,
        @QueryParam("type") String workspaceType,
        @QueryParam("offset") Integer offset,
        @QueryParam("limit") Integer limit
    );
}
