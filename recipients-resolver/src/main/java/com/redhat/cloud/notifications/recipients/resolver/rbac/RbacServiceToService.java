package com.redhat.cloud.notifications.recipients.resolver.rbac;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/api/rbac/v1")
public interface RbacServiceToService {

    @GET
    @Path("/principals/") // trailing slash is required by api
    @Produces(MediaType.APPLICATION_JSON)
    Page<RbacUser> getUsers(
            @HeaderParam("x-rh-rbac-org-id") String orgId,
            @QueryParam("admin_only") Boolean adminOnly,
            @QueryParam("offset") Integer offset,
            @QueryParam("limit") Integer limit
    );

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
            @QueryParam("admin_only") Boolean adminOnly,
            @QueryParam("offset") Integer offset,
            @QueryParam("limit") Integer limit
    );
}
