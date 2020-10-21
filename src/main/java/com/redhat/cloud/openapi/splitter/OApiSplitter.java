package com.redhat.cloud.openapi.splitter;

import com.redhat.cloud.notifications.Constants;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Helper to split the Openapi.json file from Quarkus into two
 * separate files that can then be served independently depending
 * on the endpoint requested.
 */
public class OApiSplitter {

    public static void main(String[] args) throws Exception {

        if (args.length < 2) {
            System.err.println("Needed arguments: path-to-openapi.json base-path-for-target-files");
            System.exit(1);
        }
        File file = new File(args[0]);
        File targetPath = new File(args[1]);
        System.out.println("Using the openapi file at >" + file.getAbsolutePath() + "< and target path >" + targetPath + "<");
        split(file, targetPath);
    }

    private static void split(File inputFile, File targetPath) throws IOException {
        InputStream in = new FileInputStream(inputFile);
        JsonReader jsonReader = Json.createReader(in);
        JsonObject oapiModelJson = jsonReader.readObject();

        JsonObjectBuilder notificationsBuilder = Json.createObjectBuilder();
        JsonObjectBuilder integrationsBuilder = Json.createObjectBuilder();

        Set<String> keySet = oapiModelJson.keySet();
        for (String key : keySet) {
            JsonValue keyVal = oapiModelJson.get(key);
            switch (key) {
                case "components":
                case "openapi":
                    // We just copy all of them even if they may only apply to one
                    notificationsBuilder.add(key, keyVal);
                    integrationsBuilder.add(key, keyVal);
                    break;
                case "paths":
                    JsonObjectBuilder nPathObjectBuilder = Json.createObjectBuilder();
                    JsonObjectBuilder iPathObjectBuilder = Json.createObjectBuilder();

                    JsonObject pathsObject = (JsonObject) keyVal;
                    Set<String> paths = pathsObject.keySet();
                    for (String path : paths) {
                        JsonObject pathValue = (JsonObject) pathsObject.get(path);
                        if (!path.endsWith("openapi.json")) { // Skip the openapi endpoint
                            if (path.startsWith(Constants.API_NOTIFICATIONS_V_1_0)) {
                                nPathObjectBuilder.add(mangle(path), pathValue);
                            }
                            if (path.startsWith(Constants.API_INTEGRATIONS_V_1_0)) {
                                iPathObjectBuilder.add(mangle(path), pathValue);
                            }
                        }
                    }
                    notificationsBuilder.add("paths", nPathObjectBuilder);
                    integrationsBuilder.add("paths", iPathObjectBuilder);
                    break;
                case "info":
                case "servers":
                    // Nothing. We handle info and servers below.
                    break;
                default:
                    throw new IllegalStateException("Unknown OpenAPI top-level element " + key);
            }
        }

        // Add info section
        notificationsBuilder.add("info", Json.createObjectBuilder()
                .add("description", "The API for Notifications")
                .add("version", "1.0")
                .add("title", "Notifications"));
        integrationsBuilder.add("info", Json.createObjectBuilder()
                .add("description", "The API for Integrations")
                .add("version", "1.0")
                .add("title", "Integrations"));

        // Add servers section
        JsonArrayBuilder nServersArrayBuilder = Json.createArrayBuilder();
        JsonArrayBuilder iServersArrayBuilder = Json.createArrayBuilder();

        nServersArrayBuilder.add(createServer(true, "notifications"));
        nServersArrayBuilder.add(createServer(false, "notifications"));
        iServersArrayBuilder.add(createServer(true, "integrations"));
        iServersArrayBuilder.add(createServer(false, "integrations"));

        notificationsBuilder.add("servers", nServersArrayBuilder);
        integrationsBuilder.add("servers", iServersArrayBuilder);

        // write output
        FileOutputStream nStream = new FileOutputStream(new File(targetPath, "openapi.notifications.json"));
        JsonWriter notificationsWriter = Json.createWriter(nStream);
        FileOutputStream iStream = new FileOutputStream(new File(targetPath, "openapi.integrations.json"));
        JsonWriter integrationsWriter = Json.createWriter(iStream);

        notificationsWriter.writeObject(notificationsBuilder.build());
        integrationsWriter.writeObject(integrationsBuilder.build());

        notificationsWriter.close();
        integrationsWriter.close();
        nStream.close();
        iStream.close();
    }

    private static JsonObjectBuilder createServer(boolean isProd, String what) {

        JsonObjectBuilder job = Json.createObjectBuilder();
        if (isProd) {
            job.add("url", "https://cloud.redhat.com");
            job.add("description", "Production Server");
            job.add("variables", Json.createObjectBuilder()
                    .add("basePath", Json.createObjectBuilder()
                            .add("default", "/api/" + what + "/v1.0")));
        } else {
            job.add("url", "http://localhost:{port}");
            job.add("description", "Development Server");
            job.add("variables", Json.createObjectBuilder()
                    .add("basePath", Json.createObjectBuilder()
                            .add("default", "/api/" + what + "/v1.0"))
                    .add("port", Json.createObjectBuilder()
                            .add("default", "8080")));
        }
        return job;
    }

    private static String mangle(String in) {
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
