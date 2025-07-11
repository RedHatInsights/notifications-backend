package com.redhat.cloud.notifications.oapi;

import com.redhat.cloud.notifications.Constants;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class OApiFilter {

    public static final String NOTIFICATIONS = "notifications";
    public static final String INTEGRATIONS = "integrations";
    public static final String PRIVATE = "private";
    public static final String INTERNAL = "internal";

    static List<String> openApiOptions = List.of(INTEGRATIONS, NOTIFICATIONS, PRIVATE, INTERNAL);
    static String INTEGRATIONS_DESCRIPTION = "The API for Integrations provides endpoints that you can use to create and manage integrations between third-party applications and the Red Hat Hybrid Cloud Console.";
    static String NOTIFICATIONS_DESCRIPTION = "The API for Notifications provides endpoints that you can use to create and manage event notifications between third-party applications and the Red Hat Hybrid Cloud Console.";

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

    public String serveOpenApi(String openApiOption, String version) {
        if (!openApiOptions.contains(openApiOption)) {
            throw new WebApplicationException("No openapi file for [" + openApiOption + "] found.", 404);
        }

        HttpResponse<Buffer> response = client.get("/openapi.json").sendAndAwait();
        return filterJson(response.bodyAsJsonObject(), openApiOption, version).encode();
    }

    private JsonObject filterJson(JsonObject oapiModelJson, String openApiOption, String version) {

        final JsonObject root = new JsonObject();
        JsonObject components = new JsonObject();
        Set<String> objectSchemasToKeep = new HashSet<>();
        oapiModelJson.stream().forEach(entry -> {
            String key = entry.getKey();
            switch (key) {
                case "components":
                    components.put(key, entry.getValue());
                    break;
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
                            String mangledPath = mangle(path, openApiOption, version);

                            if (mangledPath != null) {
                                if (PRIVATE.equals(openApiOption)) {
                                    newPathValue = filterPrivateOperation(pathValue, false);
                                    mangledPath = path;
                                } else if (INTERNAL.equals(openApiOption) && path.startsWith(Constants.API_INTERNAL)) {
                                    newPathValue = filterPrivateOperation(pathValue, true);
                                } else if (path.startsWith(buildPath(openApiOption, version))) {
                                    newPathValue = filterPrivateOperation(pathValue, true);
                                }

                                if (newPathValue != null) {
                                    objectSchemasToKeep.addAll(findSchemas(newPathValue));
                                    pathObject2.put(mangledPath, newPathValue);
                                }
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

        if (root.getJsonObject("paths").isEmpty()) {
            throw new NotFoundException();
        }

        JsonObject schemaAsComponents = filterUsedSchemasOnly(components, objectSchemasToKeep);
        root.put("components", schemaAsComponents);

        JsonObject rootWithoutDTO = removeSchemaDTOextWhenPossible(root);

        JsonObject info = new JsonObject()
                .put("version", version == null ? "v1.0" : version)
                .put("title", capitalize(openApiOption));

        // Add servers section
        JsonArray serversArray = new JsonArray();
        String infoDescription = capitalize(openApiOption);

        if (openApiOption.equals(NOTIFICATIONS)) {
            serversArray.add(createProdServer(NOTIFICATIONS, version));
            serversArray.add(createDevServer(NOTIFICATIONS, version));
            infoDescription = NOTIFICATIONS_DESCRIPTION;
        } else if (openApiOption.equals(INTEGRATIONS)) {
            serversArray.add(createProdServer(INTEGRATIONS, version));
            serversArray.add(createDevServer(INTEGRATIONS, version));
            infoDescription = INTEGRATIONS_DESCRIPTION;
        }

        // Add info section
        info.put("description", infoDescription);
        rootWithoutDTO.put("info", info);

        rootWithoutDTO.put("servers", serversArray);

        return rootWithoutDTO;
    }

    private JsonObject removeSchemaDTOextWhenPossible(JsonObject root) {
        if (root.getJsonObject("components") == null || root.getJsonObject("components").getJsonObject("schemas") == null) {
            return root;
        }

        String rootStr = root.toString();
        List<String> schemasNames = root.getJsonObject("components").getJsonObject("schemas").stream().map(entry -> entry.getKey()).toList();
        for (String schemaName : schemasNames) {
            if (schemaName.endsWith("DTO") && !schemasNames.contains(schemaName.replace("DTO", ""))) {
                rootStr = rootStr.replaceAll(schemaName, schemaName.replace("DTO", ""));
            }
        }
        return new JsonObject(rootStr);
    }

    private JsonObject filterUsedSchemasOnly(JsonObject components, Set<String> objectSchemasToKeep) {
        Set<String> returnedSet = new HashSet<>();
        Map<String, Object> schemasAsMap = components.getJsonObject("components").getJsonObject("schemas").getMap();
        objectSchemasToKeep.stream().forEach(s -> findDependencies(returnedSet, s, schemasAsMap));
        objectSchemasToKeep.addAll(returnedSet);

        JsonObject schemaComponents = new JsonObject();

        objectSchemasToKeep.stream().sorted().forEach(schemaName ->
            schemaComponents.put(
                schemaName,
                components.getJsonObject("components").getJsonObject("schemas").getJsonObject(schemaName)
            )
        );

        JsonObject schemaAsComponents = new JsonObject();
        schemaAsComponents.put("schemas", schemaComponents);
        return schemaAsComponents;
    }

    private void findDependencies(Set<String> returnedSet,  final String schemaDependenciesToFind, final Map<String, Object> schemasAsMap) {
        Map<String, HashMap> jsonSchema = (HashMap) schemasAsMap.get(schemaDependenciesToFind);
        Map<String, HashMap> properties = jsonSchema.get("properties");
        if (null != properties) {
            properties.keySet().stream().forEach(prop -> {
                Map<String, Object> propertyValue = (HashMap) properties.get(prop);

                if (null != propertyValue.get("$ref")) {
                    String refToFetch = removeSchemaPrefix(propertyValue.get("$ref").toString());
                    returnedSet.add(refToFetch);
                    findDependencies(returnedSet, refToFetch, schemasAsMap);
                }
                if (null != propertyValue.get("items") && null != propertyValue.get("items")) {
                    Object refToFetchObj = ((HashMap) propertyValue.get("items")).get("$ref");
                    saveFoundDependency(returnedSet, schemasAsMap, refToFetchObj);
                }
                if (null != propertyValue.get("allOf")) {
                    ((List) propertyValue.get("allOf")).stream().forEach(entryMap -> {
                        saveFoundDependency(returnedSet, schemasAsMap, ((HashMap) entryMap).get("$ref"));
                    });
                }
                if (null != propertyValue.get("oneOf")) {
                    ((List) propertyValue.get("oneOf")).stream().forEach(entryMap -> {
                        saveFoundDependency(returnedSet, schemasAsMap, ((HashMap) entryMap).get("$ref"));
                    });
                }
                if (null != propertyValue.get("additionalProperties") && null != ((HashMap) propertyValue.get("additionalProperties")).get("$ref")) {
                    saveFoundDependency(returnedSet, schemasAsMap, ((HashMap) propertyValue.get("additionalProperties")).get("$ref"));
                }
            });
        }
    }

    private void saveFoundDependency(Set<String> returnedSet, Map<String, Object> schemasAsMap, Object refToFetchObj) {
        if (null != refToFetchObj) {
            String refToFetch = removeSchemaPrefix(refToFetchObj.toString());
            if (!returnedSet.contains(refToFetch)) {
                returnedSet.add(refToFetch);
                findDependencies(returnedSet, refToFetch, schemasAsMap);
            }
        }
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

    private String removeSchemaPrefix(String schema) {
        return schema.replace("#/components/schemas/", "");
    }

    private Set<String> findSchemas(JsonObject pathObject) {
        Set<String> schemasToKeep = new HashSet<>();
        pathObject.stream().forEach(entry -> {
            JsonObject operation = (JsonObject) entry.getValue();

            JsonArray parameters = operation.getJsonArray("parameters");
            if (parameters != null) {
                parameters.stream().forEach(parameter -> {
                    try {
                        String schemaRef = ((JsonObject) parameter)
                            .getJsonObject("schema")
                            .getString("$ref");
                        schemasToKeep.add(removeSchemaPrefix(schemaRef));
                    } catch (NullPointerException e) {
                        Log.debug("No linked ref for query parameter " + ((JsonObject) parameter).getString("name") + " of operation " + operation.getString("operationId"));
                    }
                });

                parameters.stream().forEach(parameter -> {
                    try {
                        String schemaRef = ((JsonObject) parameter)
                            .getJsonObject("schema")
                            .getJsonObject("items")
                            .getString("$ref");
                        schemasToKeep.add(removeSchemaPrefix(schemaRef));
                    } catch (NullPointerException e) {
                        Log.debug("No linked ref for query parameter " + ((JsonObject) parameter).getString("name") + " of operation " + operation.getString("operationId"));
                    }
                });
            }

            JsonObject requestBody = operation.getJsonObject("requestBody");
            try {
                String schemaRef = requestBody
                    .getJsonObject("content")
                    .getJsonObject("application/json")
                    .getJsonObject("schema")
                    .getString("$ref");
                schemasToKeep.add(removeSchemaPrefix(schemaRef));
            } catch (NullPointerException e) {
                Log.debug("No linked ref for request of " + operation.getString("operationId"));
            }

            JsonObject responses = operation.getJsonObject("responses");
            responses.stream().forEach(responseCodeEntry -> {
                JsonObject responseFormat = (JsonObject) responseCodeEntry.getValue();
                try {
                    String schemaRef = responseFormat
                        .getJsonObject("content")
                        .getJsonObject("application/json")
                        .getJsonObject("schema")
                        .getString("$ref");
                    schemasToKeep.add(removeSchemaPrefix(schemaRef));
                } catch (NullPointerException e) {
                    Log.debug("No linked ref for response code " + responseCodeEntry + " of operation " + operation.getString("operationId"));
                }

                try {
                    String schemaRef = responseFormat
                        .getJsonObject("content")
                        .getJsonObject("application/json")
                        .getJsonObject("schema")
                        .getJsonObject("items")
                        .getString("$ref");
                    schemasToKeep.add(removeSchemaPrefix(schemaRef));
                } catch (NullPointerException e) {
                    Log.debug("No linked ref for response code " + responseCodeEntry + " of operation " + operation.getString("operationId"));
                }
            });
        });
        return schemasToKeep;
    }


    private JsonObject createProdServer(String openApiOption, String version) {
        JsonObject job = new JsonObject();
        job.put("url", "https://console.redhat.com/{basePath}");
        job.put("description", "Production Server");
        job.put("variables", new JsonObject()
                .put("basePath", new JsonObject()
                        .put("default", buildPath(openApiOption, version))));
        return job;
    }

    private JsonObject createDevServer(String openApiOption, String version) {

        JsonObject job = new JsonObject();
        job.put("url", "http://localhost:{port}/{basePath}");
        job.put("description", "Development Server");
        job.put("variables", new JsonObject()
                .put("basePath", new JsonObject()
                        .put("default", buildPath(openApiOption, version)))
                .put("port", new JsonObject()
                        .put("default", "8080")));
        return job;
    }

    String mangle(String in, String openApiOption, String version) {
        String out = filterConstantsIfPresent(in, openApiOption, version);

        if (out != null && out.isEmpty()) {
            out = "/";
        }

        return out;
    }

    private String filterConstantsIfPresent(String in, String what, String version) {
        String[] paths;

        // Private is a regular API that is hidden
        if (what.equals(PRIVATE)) {
            paths = new String[]{buildPath(INTEGRATIONS, version), buildPath(NOTIFICATIONS, version)};
        } else {
            paths = new String[]{buildPath(what, version)};
        }

        for (String path: paths) {
            if (in.startsWith(path)) {
                return in.substring(path.length());
            }
        }

        return null;
    }

    private String buildPath(String openApiOption, String version) {
        if (version == null) {
            // Using root for non versioned API (internal)
            return "/" + openApiOption;
        }
        return "/api/%s/%s".formatted(openApiOption, version);
    }
}
