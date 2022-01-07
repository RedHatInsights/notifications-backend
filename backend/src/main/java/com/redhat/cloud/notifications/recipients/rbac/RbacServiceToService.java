package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.recipients.rbac.pojo.ITUser;
import com.redhat.cloud.notifications.routers.models.Page;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/v2")
@RegisterRestClient(configKey = "rbac-s2s")
@RegisterProvider(AuthRequestFilter.class)
public interface RbacServiceToService {

    @POST
    @Path("/findUsers/") // trailing slash is required by api
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Page<RbacUser>> getUsers(ITUser ITUser);

    @GET
    @Path("/groups/") // trailing slash is required by api
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Page<RbacGroup>> getGroups(
            @HeaderParam("x-rh-rbac-account") String accountId,
            @QueryParam("offset") Integer offset,
            @QueryParam("limit") Integer limit
    );

    @GET
    @Path("/groups/{groupId}/") // trailing slash is required by api
    @Produces(MediaType.APPLICATION_JSON)
    Uni<RbacGroup> getGroup(
            @HeaderParam("x-rh-rbac-account") String accountId,
            @PathParam("groupId") UUID groupId
    );


    @GET
    @Path("/groups/{groupId}/principals/") // trailing slash is required by api
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Page<RbacUser>> getGroupUsers(
            @HeaderParam("x-rh-rbac-account") String accountId,
            @PathParam("groupId") UUID groupId,
            @QueryParam("offset") Integer offset,
            @QueryParam("limit") Integer limit
    );
}
