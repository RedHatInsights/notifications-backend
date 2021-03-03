package com.redhat.cloud.notifications.rbac;

import com.redhat.cloud.notifications.models.Page;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.util.UUID;

@Path("/api/rbac/v1")
@RegisterRestClient(configKey = "rbac")
@RegisterProvider(RbacServiceToServiceAuthRequestFilter.class)
public interface RbacServiceToService {

    @GET
    @Path("/principals/") // trailing slash is required by api
    @Consumes("application/json")
    @Produces("application/json")
    Uni<Page<RbacUser>> getUsers(
            @HeaderParam("x-rh-rbac-account") String accountId,
            @QueryParam("admin_only") Boolean adminOnly,
            @QueryParam("offset") Integer offset
    );

    @GET
    @Path("/groups/") // trailing slash is required by api
    @Consumes("application/json")
    @Produces("application/json")
    Uni<Page<RbacGroup>> getGroups(
            @HeaderParam("x-rh-rbac-account") String accountId,
            @QueryParam("offset") Integer offset
    );

    @GET
    @Path("/groups/{groupId}/principals/") // trailing slash is required by api
    @Consumes("application/json")
    @Produces("application/json")
    Uni<Page<RbacUser>> getGroupUsers(
            @HeaderParam("x-rh-rbac-account") String accountId,
            @PathParam("groupId") UUID groupId,
            @QueryParam("offset") Integer offset
    );
}
