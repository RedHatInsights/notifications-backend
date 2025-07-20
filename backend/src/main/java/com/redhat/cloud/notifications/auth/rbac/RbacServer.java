package com.redhat.cloud.notifications.auth.rbac;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.X_RH_IDENTITY_HEADER;

@RegisterRestClient(configKey = "rbac-authentication")
@RegisterProvider(RbacClientResponseFilter.class)
public interface RbacServer {

    @GET
    @Path("/api/rbac/v1/access/") // trailing slash is required by api
    @Consumes("application/json")
    @Produces("application/json")
    Uni<RbacRaw> getRbacInfo(@QueryParam("application") String application,
                             @HeaderParam(X_RH_IDENTITY_HEADER) String rhIdentity

    );

    @GET
    @Path("/api/rbac/v1/groups/{groupID}/") // trailing slash is required by api
    @Produces("application/json")
    Response getGroup(@PathParam("groupID") UUID groupId, @HeaderParam(X_RH_IDENTITY_HEADER) String rhIdentity);
}
