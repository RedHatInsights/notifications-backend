package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.oapi.OApiFilter;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serve the final OpenAPI documents.
 */
@Path("/api")
public class OApiService {

    @Inject
    OApiFilter oApiFilter;

    @GET
    @Path("/{what}/v1.0/openapi.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<String> serveOpenAPI(@PathParam("what") String what) {
        return oApiFilter.serveOpenApi(what);
    }

}
