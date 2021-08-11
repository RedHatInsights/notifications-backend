package com.redhat.cloud.notifications.oapi;

import com.redhat.cloud.notifications.Constants;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class OApiFilter {

    public static final String NOTIFICATIONS = "notifications";
    public static final String INTEGRATIONS = "integrations";
    public static final String PRIVATE = "private";
    public static final String INTERNAL = "internal";

    static List<String> openApiOptions = List.of(INTEGRATIONS, NOTIFICATIONS, PRIVATE, INTERNAL);

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

    public Uni<String> serveOpenApi(String openApiOption) {
        if (!openApiOptions.contains(openApiOption)) {
            throw new WebApplicationException("No openapi file for [" + openApiOption + "] found.", 404);
        }

        return client.get("/openapi.json")
                .send()
                .onItem()
                .transform(response ->
                        filterJson(response.bodyAsJsonObject(), openApiOption)
                )
                .onItem().transform(JsonObject::encode);
    }

    private JsonObject filterJson(JsonObject oapiModelJson, String openApiOption) {

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

                            if (NOTIFICATIONS.equals(openApiOption) && path.startsWith(Constants.API_NOTIFICATIONS_V_1_0)) {
                                newPathValue = filterPrivateOperation(pathValue, true);
                            } else if (INTEGRATIONS.equals(openApiOption) && path.startsWith(Constants.API_INTEGRATIONS_V_1_0)) {
                                newPathValue = filterPrivateOperation(pathValue, true);
                            } else if (PRIVATE.equals(openApiOption)) {
                                newPathValue = filterPrivateOperation(pathValue, false);
                                mangledPath = path;
                            } else if (INTERNAL.equals(openApiOption) && path.startsWith(Constants.INTERNAL)) {
                                newPathValue = filterPrivateOperation(pathValue, true);
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
                .put("description", "The API for " + capitalize(openApiOption))
                .put("version", "1.0")
                .put("title", capitalize(openApiOption)));

        // Add servers section
        JsonArray serversArray = new JsonArray();

        if (openApiOption.equals(NOTIFICATIONS)) {
            serversArray.add(createProdServer(NOTIFICATIONS));
            serversArray.add(createDevServer(NOTIFICATIONS));
        } else if (openApiOption.equals(INTEGRATIONS)) {
            serversArray.add(createProdServer(INTEGRATIONS));
            serversArray.add(createDevServer(INTEGRATIONS));
        }

        root.put("servers", serversArray);

        return root;
    }

    private String capitalize(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
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

    private JsonObject createProdServer(String openApiOption) {
        JsonObject job = new JsonObject();
        job.put("url", "https://cloud.redhat.com");
        job.put("description", "Production Server");
        job.put("variables", new JsonObject()
                .put("basePath", new JsonObject()
                        .put("default", "/api/" + openApiOption + "/v1.0")));
        return job;
    }

    private JsonObject createDevServer(String openApiOption) {

        JsonObject job = new JsonObject();
        job.put("url", "http://localhost:{port}");
        job.put("description", "Development Server");
        job.put("variables", new JsonObject()
                .put("basePath", new JsonObject()
                        .put("default", "/api/" + openApiOption + "/v1.0"))
                .put("port", new JsonObject()
                        .put("default", "8080")));
        return job;
    }

    String mangle(String in) {
        String out = filterConstantsIfPresent(in);

        if (out != null && out.isEmpty()) {
            out = "/";
        }

        return out;
    }

    private String filterConstantsIfPresent(String in) {
        String out = null;
        if (in.startsWith(Constants.API_INTEGRATIONS_V_1_0)) {
            out = in.substring(Constants.API_INTEGRATIONS_V_1_0.length());
        } else if (in.startsWith(Constants.API_NOTIFICATIONS_V_1_0)) {
            out = in.substring(Constants.API_NOTIFICATIONS_V_1_0.length());
        } else if (in.startsWith(Constants.INTERNAL)) {
            out = in.substring(Constants.INTERNAL.length());
        }
        return out;
    }

}
