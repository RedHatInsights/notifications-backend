package com.redhat.cloud.notifications.routers;

import io.smallrye.mutiny.Uni;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;

/**
 * Serve the final OpenAPI documents.
 */
@Path("/api")
public class OApiService {

    @GET
    @Path("/{what}/v1.0/openapi.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<String> serveOpenAPI(@PathParam("what") String what) {

        String resourceName = "/openapi." + what + ".json";

        return Uni.createFrom().item(resourceName)
                .onItem().transform(n ->
                        getClass().getResourceAsStream(n)
                )
                .onItem().ifNull().failWith(new WebApplicationException(resourceName, NOT_FOUND))
                .onItem().transform(j -> {
                    try {
                        return j.readAllBytes();
                    } catch (IOException e) {
                        return new byte[1];
                    }
                })
                .onItem().transform(String::new);
    }
}
