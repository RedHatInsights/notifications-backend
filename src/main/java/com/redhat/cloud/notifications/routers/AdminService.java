package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.StuffHolder;
import com.redhat.cloud.notifications.auth.RbacRaw;
import com.redhat.cloud.notifications.auth.RbacServer;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Optional;

/**
 * Stuff around admin of the service and debugging
 */
@Path("/internal/admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AdminService {

    @Inject
    @RestClient
    RbacServer rbacServer;

    @GET
    public Uni<Response> debugRbac(@QueryParam("rhid") String rhid) {

        Uni<RbacRaw> rbacRawUni = rbacServer.getRbacInfo("notifications,integrations", rhid);

        return rbacRawUni
                .onItem()
                .transform(r -> Response.ok(r.data).build())
                .onFailure()
                .call(t -> Uni.createFrom().item(Response.serverError().entity("Rbac call failed -- see logs").build()));

    }

    @Path("/status")
    @POST
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
