package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.routers.models.Page;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/api/rbac/v1")
@RegisterRestClient(configKey = "rbac-s2s")
@RegisterProvider(AuthRequestFilter.class)
public interface RbacServiceToService {

    @GET
    @Path("/principals/") // trailing slash is required by api
    @Produces(MediaType.APPLICATION_JSON)
    Page<RbacUser> getUsers(
            @HeaderParam("x-rh-rbac-account") String accountId,
            @HeaderParam("x-rh-rbac-org-id") String orgId,
            @QueryParam("admin_only") Boolean adminOnly,
            @QueryParam("offset") Integer offset,
            @QueryParam("limit") Integer limit
    );

    @GET
    @Path("/groups/") // trailing slash is required by api
    @Produces(MediaType.APPLICATION_JSON)
    Page<RbacGroup> getGroups(
            @HeaderParam("x-rh-rbac-account") String accountId,
            @HeaderParam("x-rh-rbac-org-id") String orgId,
            @QueryParam("offset") Integer offset,
            @QueryParam("limit") Integer limit
    );

    @GET
    @Path("/groups/{groupId}/") // trailing slash is required by api
    @Produces(MediaType.APPLICATION_JSON)
    RbacGroup getGroup(
            @HeaderParam("x-rh-rbac-account") String accountId,
            @HeaderParam("x-rh-rbac-org-id") String orgId,
            @PathParam("groupId") UUID groupId
    );

    @GET
    @Path("/groups/{groupId}/principals/") // trailing slash is required by api
    @Produces(MediaType.APPLICATION_JSON)
    Page<RbacUser> getGroupUsers(
            @HeaderParam("x-rh-rbac-account") String accountId,
            @HeaderParam("x-rh-rbac-org-id") String orgId,
            @PathParam("groupId") UUID groupId,
            @QueryParam("offset") Integer offset,
            @QueryParam("limit") Integer limit
    );
}
