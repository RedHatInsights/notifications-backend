package com.redhat.cloud.notifications.routers;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;

/**
 * Serve the final OpenAPI documents.
 */
@Path("/api")
public class OApiService {

    @GET
    @Path("/{what}/v1.0/openapi.json")
    @Produces(MediaType.APPLICATION_JSON)
    public StreamingOutput serveOpenAPI(@PathParam("what") String what) {

        String resourceName = "/openapi." + what + ".json";
        URL target = getClass().getResource(resourceName);
        if (target == null) {
            throw new WebApplicationException(resourceName, NOT_FOUND);
        }

        return new StreamingOutput() {
            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                InputStream is = getClass().getResourceAsStream(resourceName);
                is.transferTo(output);
            }
        };
    }
}
