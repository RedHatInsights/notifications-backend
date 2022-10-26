package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.routers.models.Page;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.UUID;

@Path("/api/rbac/v1")
@RegisterRestClient(configKey = "rbac-s2s")
@RegisterProvider(AuthRequestFilter.class)
public interface RbacServiceToService {

    @GET
    @Path("/groups/{groupId}/") // trailing slash is required by api
    @Produces(MediaType.APPLICATION_JSON)
    RbacGroup getGroup(
            @HeaderParam("x-rh-rbac-org-id") String orgId,
            @PathParam("groupId") UUID groupId
    );

    @GET
    @Path("/groups/{groupId}/principals/") // trailing slash is required by api
    @Produces(MediaType.APPLICATION_JSON)
    Page<RbacUser> getGroupUsers(
            @HeaderParam("x-rh-rbac-org-id") String orgId,
            @PathParam("groupId") UUID groupId,
            @QueryParam("offset") Integer offset,
            @QueryParam("limit") Integer limit
    );
}
