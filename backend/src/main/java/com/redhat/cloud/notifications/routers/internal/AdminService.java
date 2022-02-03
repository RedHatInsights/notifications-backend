package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.StuffHolder;
import com.redhat.cloud.notifications.auth.rbac.RbacIdentityProvider;
import com.redhat.cloud.notifications.auth.rbac.RbacRaw;
import com.redhat.cloud.notifications.auth.rbac.RbacServer;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.util.Optional;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

/**
 * Stuff around admin of the service and debugging
 */
@RolesAllowed(RbacIdentityProvider.RBAC_INTERNAL_UI_ADMIN)
@Path(API_INTERNAL + "/admin")
public class AdminService {

    @Inject
    @RestClient
    RbacServer rbacServer;

    @GET
    @Produces(APPLICATION_JSON)
    public Response debugRbac(@QueryParam("rhid") String rhid) {
        try {
            RbacRaw rbacRaw = rbacServer.getRbacInfo("notifications,integrations", rhid)
                    .await().atMost(Duration.ofSeconds(2L));
            return Response.ok(rbacRaw.data).build();
        } catch (Exception e) {
            return Response.serverError().entity("Rbac call failed -- see logs").build();
        }

    }

    @Path("/status")
    @POST
    @Produces(TEXT_PLAIN)
    public Response setAdminDown(@QueryParam("status") Optional<String> status) {

        Response.ResponseBuilder builder;

        StuffHolder th = StuffHolder.getInstance();

        switch (status.orElse("ok")) {
            case "ok":
                th.setDegraded(false);
                th.setAdminDown(false);
                builder = Response.ok()
                        .entity("Reset state to ok");
                break;
            case "degraded":
                th.setDegraded(true);
                builder = Response.ok()
                        .entity("Set degraded state");
                break;
            case "admin-down":
                th.setAdminDown(true);
                builder = Response.ok()
                        .entity("Set admin down state");
                break;
            default:
                builder = Response.status(Response.Status.BAD_REQUEST)
                        .entity("Unknown status passed");
        }

        return builder.build();
    }

}
