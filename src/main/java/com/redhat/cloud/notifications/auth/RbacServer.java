package com.redhat.cloud.notifications.auth;

import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Path("/api/rbac/v1")
@RegisterRestClient(configKey = "rbac")
@RegisterProvider(RbacRestClientRequestFilter.class)
@RegisterProvider(RbacClientResponseFilter.class)
public interface RbacServer {

    @GET
    @Path("/access/") // trailing slash is required by api
    @Consumes("application/json")
    @Produces("application/json")
    @CacheResult(cacheName = "rbac-cache")
    Uni<RbacRaw> getRbacInfo(@QueryParam("application") String application,
                             @HeaderParam(RHIdentityAuthMechanism.IDENTITY_HEADER) String rhIdentity

    );
}
