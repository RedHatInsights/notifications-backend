package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.models.Application;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@Path("/applications")
public class ApplicationService {

    @GET
    public Multi<Application> getApplications(@Context SecurityContext ctx) {
        // Return configured with per tenant endpoints?
    }

    @POST
    public Uni<Response> addApplication() {
        // We need to ensure that the x-rh-identity isn't present here
    }

    @GET
    @Path("/{id}")
    public Uni<Application> getApplication(@Context SecurityContext ctx, @PathParam("id") String id) {

    }
}
