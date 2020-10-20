package com.redhat.cloud.notifications.routers;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;


/**
 * Serve the final OpenAPI documents.
 */
@Path("/api")
public class OApiService {

    static List<String> whats = new ArrayList<>(2);

    static {
        whats.add("integrations");
        whats.add("notifications");
    }

    @Inject
    Vertx vertx;

    @GET
    @Path("/{what}/v1.0/openapi.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<String> serveOpenAPI(@PathParam("what") String what) {

        if (!whats.contains(what)) {
            throw new WebApplicationException(404);
        }

        String resourceName = "openapi." + what + ".json";

        return vertx.fileSystem().readFile(resourceName)
                .onItem().transform(b -> b.toString("UTF-8"));
    }
}
