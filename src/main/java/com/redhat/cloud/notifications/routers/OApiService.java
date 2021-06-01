package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serve the final OpenAPI documents.
 */
@Path("/api")
public class OApiService {

    public static final String NOTIFICATIONS = "notifications";
    public static final String INTEGRATIONS = "integrations";
    public static final String PRIVATE = "private";

    static List<String> whats = new ArrayList<>(3);

    static {
        whats.add(INTEGRATIONS);
        whats.add(NOTIFICATIONS);
        whats.add(PRIVATE);
    }

    @Inject
    Vertx vertx;

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8085")
    Integer port;

    private WebClient client;

    @PostConstruct
    void initialize() {

        this.client = WebClient.create(vertx,
                new WebClientOptions().setDefaultHost("localhost").setDefaultPort(port));
    }

    @GET
    @Path("/{what}/v1.0/openapi.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<String> serveOpenAPI(@PathParam("what") String what) {

        if (!whats.contains(what)) {
            throw new WebApplicationException("No openapi file for [" + what + "] found.", 404);
        }

        return client.get("/openapi.json")
                .send()
                .onItem()
                .transform(response ->
                        filterJson(response.bodyAsJsonObject(), what)
                )
                .onItem().transform(JsonObject::encode);
    }

    private JsonObject filterJson(JsonObject oapiModelJson, String what) {

        JsonObject root = new JsonObject();

        oapiModelJson.stream().forEach(entry -> {
            String key = entry.getKey();
            switch (key) {
                case "components":
                case "openapi":
                    // We just copy all of them even if they may only apply to one
                    root.put(key, entry.getValue());
                    break;
                case "tags":
                    JsonArray tags = (JsonArray) entry.getValue();
                    JsonArray filteredTags = new JsonArray(tags
                            .stream()
                            .filter(o -> !((JsonObject) o).getString("name").equals(PRIVATE))
                            .collect(Collectors.toList()));
                    if (filteredTags.size() > 0) {
                        root.put(key, filteredTags);
                    }
                    break;
                case "paths":
                    JsonObject pathObject2 = new JsonObject();
                    JsonObject pathsObjectIn = (JsonObject) entry.getValue();
                    pathsObjectIn.stream().forEach(pathEntry -> {
                        String path = pathEntry.getKey();

                        JsonObject pathValue = (JsonObject) pathEntry.getValue();
                        if (!path.endsWith("openapi.json")) { // Skip the openapi endpoint
                            JsonObject newPathValue = null;
                            String mangledPath = mangle(path);

                            if (NOTIFICATIONS.equals(what) && path.startsWith(Constants.API_NOTIFICATIONS_V_1_0)) {
                                newPathValue = filterPrivateOperation(pathValue, true);
                            } else if (INTEGRATIONS.equals(what) && path.startsWith(Constants.API_INTEGRATIONS_V_1_0)) {
                                newPathValue = filterPrivateOperation(pathValue, true);
                            } else if (PRIVATE.equals(what)) {
                                newPathValue = filterPrivateOperation(pathValue, false);
                                mangledPath = path;
                            }

                            if (newPathValue != null) {
                                pathObject2.put(mangledPath, newPathValue);
                            }
                        }
                    });
                    root.put("paths", pathObject2);
                    break;
                case "info":
                case "servers":
                    // Nothing. We handle info and servers below.
                    break;
                default:
                    throw new IllegalStateException("Unknown OpenAPI top-level element " + key);
            }
        });

        // Add info section
        root.put("info", new JsonObject()
                .put("description", "The API for " + uppify(what))
                .put("version", "1.0")
                .put("title", uppify(what)));

        // Add servers section
        JsonArray serversArray = new JsonArray();

        if (what.equals(NOTIFICATIONS)) {
            serversArray.add(createServer(true, NOTIFICATIONS));
            serversArray.add(createServer(false, NOTIFICATIONS));
        } else if (what.equals(INTEGRATIONS)) {
            serversArray.add(createServer(true, INTEGRATIONS));
            serversArray.add(createServer(false, INTEGRATIONS));
        }

        root.put("servers", serversArray);

        return root;
    }

    private String uppify(String what) {
        return what.substring(0, 1).toUpperCase() + what.substring(1);
    }

    private JsonObject filterPrivateOperation(JsonObject pathObject, boolean removePrivate) {
        JsonObject newPathObject = new JsonObject();
        pathObject.stream().forEach(entry -> {
            String verb = entry.getKey();
            JsonObject operation = (JsonObject) entry.getValue();
            boolean isPrivate = operation.containsKey("tags") && operation.getJsonArray("tags").contains(PRIVATE);
            if (isPrivate != removePrivate) {
                newPathObject.put(verb, operation);
            }
        });

        if (newPathObject.size() == 0) {
            return null;
        }

        return newPathObject;
    }

    private JsonObject createServer(boolean isProd, String what) {

        JsonObject job = new JsonObject();
        if (isProd) {
            job.put("url", "https://cloud.redhat.com");
            job.put("description", "Production Server");
            job.put("variables", new JsonObject()
                    .put("basePath", new JsonObject()
                            .put("default", "/api/" + what + "/v1.0")));
        } else {
            job.put("url", "http://localhost:{port}");
            job.put("description", "Development Server");
            job.put("variables", new JsonObject()
                    .put("basePath", new JsonObject()
                            .put("default", "/api/" + what + "/v1.0"))
                    .put("port", new JsonObject()
                            .put("default", "8080")));
        }
        return job;
    }

    private String mangle(String in) {
        String out = null;
        if (in.startsWith(Constants.API_INTEGRATIONS_V_1_0)) {
            out = in.substring(Constants.API_INTEGRATIONS_V_1_0.length());
        }
        if (in.startsWith(Constants.API_NOTIFICATIONS_V_1_0)) {
            out = in.substring(Constants.API_NOTIFICATIONS_V_1_0.length());
        }
        if (out != null && out.isEmpty()) {
            out = "/";
        }
        return out;
    }

}
