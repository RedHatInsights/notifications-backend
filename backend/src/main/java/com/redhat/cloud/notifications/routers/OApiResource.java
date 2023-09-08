package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.oapi.OApiFilter;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Serve the final OpenAPI documents.
 */
@Path("/api")
public class OApiResource {

    @Inject
    OApiFilter oApiFilter;

    @GET
    @Path("/{what}/{version}/openapi.json")
    @Produces(MediaType.APPLICATION_JSON)
    public String serveOpenAPI(@PathParam("what") String what, @PathParam("version") String version) {
        return oApiFilter.serveOpenApi(what, version);
    }

}
