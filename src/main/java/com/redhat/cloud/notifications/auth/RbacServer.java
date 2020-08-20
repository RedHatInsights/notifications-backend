package com.redhat.cloud.notifications.auth;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Path("/api/rbac/v1")
@RegisterRestClient(configKey = "rbac")
// TODO Move this to the insights-common-java
public interface RbacServer {

    @GET
    @Path("/access/") // trailing slash is required by api
    @Consumes("application/json")
    @Produces("application/json")
    Uni<RbacRaw> getRbacInfo(@QueryParam("application") String application,
                             @HeaderParam(RHIdentityAuthMechanism.IDENTITY_HEADER) String rhIdentity

    );
}
