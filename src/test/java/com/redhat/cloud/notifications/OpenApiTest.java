package com.redhat.cloud.notifications;

import com.reprezen.kaizen.oasparser.OpenApi3Parser;
import com.reprezen.kaizen.oasparser.model3.OpenApi3;
import com.reprezen.kaizen.oasparser.val.ValidationResults;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Set;

import static io.restassured.RestAssured.when;

@QuarkusTest
public class OpenApiTest {

    // QuarkusTest will inject the host+port for us.
   	@TestHTTPResource("/openapi.json")
    URL url;

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = "/api";
    }

//    @Test
    void getOpenApiV1_0Notifications() {
        when()
                .get("/notifications/v1.0/openapi.json")
        .then()
                .statusCode(200);
    }

//    @Test
    void getOpenApiV1_0Integrations() {
        when()
                .get("/integrations/v1.0/openapi.json")
        .then()
                .statusCode(200);
    }

    // Test that the v1 -> v1.0 redirect works
//    @Test
    void getOpenApiV1() {
        when()
                .get("/v1/openapi.json")
        .then()
                .statusCode(200);
    }

    private static final String OAPI_JSON = "openapi.json";
    private static final String TARGET_OPENAPI = "./target/openapi.json";


   	@Test
   	public void validateOpenApi() throws Exception {
        System.out.printf("Validating OpenAPI Model at %s\n", url);
        OpenApi3 model = new OpenApi3Parser().parse(url, true);
        if (!model.isValid()) {
            for (ValidationResults.ValidationItem item : model.getValidationItems()) {
                System.err.println(item);
            }
            Assert.fail("OpenAPI spec is not valid");
        }

        // Model is valid, now do more work


        // Now that the OpenAPI file has been validated, save a copy to the filesystem
        // This file is going to be uploaded in a regular CI build to know the API state
        // for a given build.
        InputStream in = url.openStream();
        JsonReader jsonReader = Json.createReader(in);
        JsonObject oapiModelJson = jsonReader.readObject();

        JsonObjectBuilder notificationsBuilder = Json.createObjectBuilder();
        JsonObjectBuilder integrationsBuilder = Json.createObjectBuilder();

        Set<String> keySet = oapiModelJson.keySet();
        for (String key: keySet) {
            System.out.println(key);
            JsonValue keyVal = oapiModelJson.get(key);
            switch (key) {
                case "components":
                case "openapi":
                    // We just copy all of them even if they may only apply to one
                    notificationsBuilder.add(key,keyVal);
                    integrationsBuilder.add(key,keyVal);
                    break;
                case "paths":
                    JsonObjectBuilder nPathObjectBuilder = Json.createObjectBuilder();
                    JsonObjectBuilder iPathObjectBuilder = Json.createObjectBuilder();

                    JsonObject pathsObject = (JsonObject) keyVal;
                    Set<String> paths = pathsObject.keySet();
                    for (String path : paths) {
                        JsonObject pathValue = (JsonObject) pathsObject.get(path);
                        if (path.startsWith("/api/notifications/v1.0")) {
                            nPathObjectBuilder.add(mangle(path),pathValue);
                        }
                        if (path.startsWith("/api/integrations/v1.0")) {
                            iPathObjectBuilder.add(mangle(path), pathValue);
                        }

                    }
                    notificationsBuilder.add("paths",nPathObjectBuilder);
                    integrationsBuilder.add("paths",iPathObjectBuilder);
                    break;
                case "servers":
                    // We need to change the base path


                    break;
                default:
                    System.err.println("   Not yet handled");
            }
        }

        // Add info section
        notificationsBuilder.add("info",Json.createObjectBuilder()
        .add("description","The API for Notifications")
        .add("version","1.0")
        .add("title","Notifications"));
        integrationsBuilder.add("info",Json.createObjectBuilder()
        .add("description","The API for Integrations")
        .add("version","1.0")
        .add("title","Integrations"));

        // Add servers section
        JsonArrayBuilder nServersArrayBuilder = Json.createArrayBuilder();
        JsonArrayBuilder iServersArrayBuilder = Json.createArrayBuilder();

        nServersArrayBuilder.add(createServer(true,"notifications"));
        nServersArrayBuilder.add(createServer(false,"notifications"));
        iServersArrayBuilder.add(createServer(true,"integations"));
        iServersArrayBuilder.add(createServer(false,"integations"));

        notificationsBuilder.add("servers",nServersArrayBuilder);
        integrationsBuilder.add("servers",iServersArrayBuilder);


        // write output
        FileOutputStream nStream = new FileOutputStream(new File("target/openapi.notifications.json"));
        JsonWriter notificationsWriter = Json.createWriter(nStream);
        FileOutputStream iStream = new FileOutputStream(new File("target/openapi.integrations.json"));
        JsonWriter integrationsWriter = Json.createWriter(iStream);

        notificationsWriter.writeObject(notificationsBuilder.build());
        integrationsWriter.writeObject(integrationsBuilder.build());

        notificationsWriter.close();
        integrationsWriter.close();
        nStream.close();
        iStream.close();
    }

    private JsonObjectBuilder createServer(boolean isProd, String what) {

   	    JsonObjectBuilder job = Json.createObjectBuilder();
   	    if (isProd) {
            job.add("url", "https://cloud.redhat.com");
            job.add("description", "Production Server");
            job.add("variables", Json.createObjectBuilder()
                    .add("basePath", Json.createObjectBuilder()
                            .add("default", "/api/" + what + "/v1.0")));
        }
        else {
            job.add("url", "http://localhost:{port}");
            job.add("description", "Development Server");
            job.add("variables", Json.createObjectBuilder()
                    .add("basePath", Json.createObjectBuilder()
                            .add("default", "/api/" + what + "/v1.0"))
                    .add("port", Json.createObjectBuilder()
                            .add("default","8080")));
        }
   	    return job;
    }

    private String mangle(String in) {
   	    String out = null;
   	    if (in.startsWith("/api/integrations/v1.0")) {
   	        out = in.substring("/api/integrations/v1.0".length());
        }
   	    if (in.startsWith("/api/notifications/v1.0")) {
   	        out = in.substring("/api/notifications/v1.0".length());
        }
   	    if (out != null && out.isEmpty()) {
   	        out = "/";
        }
   	    return out;
    }
}
