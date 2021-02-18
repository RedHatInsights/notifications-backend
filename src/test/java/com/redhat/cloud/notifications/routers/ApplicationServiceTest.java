package com.redhat.cloud.notifications.routers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.JacksonCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ApplicationServiceTest extends DbIsolatedTest {

    /*
     * In the tests below, most JSON responses are verified using JsonObject/JsonArray instead of deserializing these
     * responses into model instances and checking their attributes values. That's because the model classes contain
     * attributes annotated with @JsonProperty(access = READ_ONLY) which can't be deserialized and therefore verified
     * here. The deserialization is still performed only to verify that the JSON responses data structure is correct.
     */

    static final String APP_NAME = "policies-application-service-test";
    static final String EVENT_TYPE_NAME = "policy-triggered";
    public static final String BUNDLE_NAME = "insights-test";

    @Test
    void testPoliciesApplicationAddingAndDeletion() {
        Bundle bundle = new Bundle();
        bundle.setName(BUNDLE_NAME);
        bundle.setDisplayName("Insights");
        Response response =
                given()
                        .body(bundle)
                        .contentType(ContentType.JSON)
                        .when().post("/internal/bundles")
                        .then()
                        .statusCode(200)
                        .extract().response();
        JsonObject returnedBundle = new JsonObject(response.body().asString());
        returnedBundle.mapTo(Bundle.class);

        Application app = new Application();
        app.setName(APP_NAME);
        app.setDisplayName("The best app");
        app.setBundleId(UUID.fromString(returnedBundle.getString("id")));

        // All of these are without identityHeader

        // Now create an application
        response = given()
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(app))
                .post("/internal/applications")
                .then()
                .statusCode(200)
                .extract().response();

        JsonObject appResponse = new JsonObject(response.getBody().asString());
        appResponse.mapTo(Application.class);
        assertNotNull(appResponse.getString("id"));
        assertEquals(returnedBundle.getString("id"), appResponse.getString("bundle_id"));

        // Fetch the applications to check they were really added
        response =
                given()
                        // Set header to x-rh-identity
                        .when()
                        .get("/internal/applications?bundleName=" + BUNDLE_NAME)
                        .then()
                        .statusCode(200)
                        .extract().response();
        assertNotNull(response);
        JsonArray apps = new JsonArray(response.body().asString());
        apps.getJsonObject(0).mapTo(Application.class);
        assertEquals(1, apps.size());
        assertEquals(returnedBundle.getString("id"), apps.getJsonObject(0).getString("bundle_id"));
        assertEquals(appResponse.getString("id"), apps.getJsonObject(0).getString("id"));

        // Check, we can get it by its id.
        given()
                .when()
                .get("/internal/applications/" + appResponse.getString("id"))
                .then()
                .statusCode(200);

        // Create eventType
        EventType eventType = new EventType();
        eventType.setName(EVENT_TYPE_NAME);
        eventType.setDisplayName("Policies will take care of the rules");
        eventType.setDescription("This is the description of the rule");

        response = given()
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(eventType))
                .post(String.format("/internal/applications/%s/eventTypes", appResponse.getString("id")))
                .then()
                .statusCode(200)
                .extract().response();

        JsonObject typeResponse = new JsonObject(response.getBody().asString());
        typeResponse.mapTo(EventType.class);
        assertNotNull(typeResponse.getString("id"));
        assertEquals(eventType.getDescription(), typeResponse.getString("description"));

        // Now delete the app and verify that it is gone along with the eventType
        given()
                .when()
                .delete("/internal/applications/" + appResponse.getString("id"))
                .then()
                .statusCode(200);

        // Check that get by Id does not return a 200, as it is gone.
        given()
                .when()
                .get("/internal/applications/" + appResponse.getString("id"))
                .then()
                .statusCode(204); // TODO api reports a 204 "empty response", but should return a 404

        // Now check that the eventTypes for that id is also empty
        response =
                given()
                        .when()
                        .get("/internal/applications/" + appResponse.getString("id") + "/eventTypes")
                        .then()
                        .statusCode(200)
                        .extract().response();
        JsonArray list = new JsonArray(response.body().asString());

        assertEquals(0, list.size());

    }


    @Test
    void testPoliciesEventTypeDelete() {
        Bundle bundle = new Bundle();
        bundle.setName(BUNDLE_NAME);
        bundle.setDisplayName("Insights");
        Bundle returnedBundle =
            given()
                .body(bundle)
                .contentType(ContentType.JSON)
                .when().post("/internal/bundles")
                .then()
                .statusCode(200)
                .extract().body().as(Bundle.class);

        Application app = new Application();
        app.setName(APP_NAME);
        app.setDisplayName("The best app");
        app.setBundleId(returnedBundle.getId());

        // All of these are without identityHeader

        // Now create an application
        Response response = given()
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(app))
                .post("/internal/applications")
                .then()
                .statusCode(200)
                .extract().response();

        Application appResponse = Json.decodeValue(response.getBody().asString(), Application.class);

        // Fetch the applications to check they were really added
        Response resp =
            given()
                    // Set header to x-rh-identity
                    .when()
                    .get("/internal/applications?bundleName=" + BUNDLE_NAME)
                    .then()
                    .statusCode(200)
                    .extract().response();
        List apps = Json.decodeValue(resp.getBody().asString(), List.class);
        Map<String, Object> map = (Map<String, Object>) apps.get(0);
        Map<String, Object> appMap = (Map<String, Object>) map.get("entity");

        // Create eventType
        EventType eventType = new EventType();
        eventType.setName(EVENT_TYPE_NAME);
        eventType.setDisplayName("Policies will take care of the rules");
        eventType.setDescription("This is the description of the rule");

        response = given()
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(eventType))
                .post(String.format("/internal/applications/%s/eventTypes", appResponse.getId()))
                .then()
                .statusCode(200)
                .extract().response();

        EventType typeResponse = Json.decodeValue(response.getBody().asString(), EventType.class);

        response = given()
        .when()
        .get(String.format("/internal/applications/%s/eventTypes", appResponse.getId()))
        .then()
        .statusCode(200)
        .extract().response();

        List<EventType> list = JacksonCodec.decodeValue(response.getBody().asString(), new TypeReference<List<EventType>>() { });
        assertEquals(list.size(), 1);

        given()
            .when()
            .delete(String.format("/internal/applications/%s/eventTypes/%s", appResponse.getId(), typeResponse.getId()))
            .then()
            .statusCode(200)
            .extract().response();

        response = given()
                .when()
                .get(String.format("/internal/applications/%s/eventTypes", appResponse.getId()))
                .then()
                .statusCode(200)
                .extract().response();

        list = JacksonCodec.decodeValue(response.getBody().asString(), new TypeReference<List<EventType>>() { });
        assertEquals(list.size(), 0);
    }


    @Test
    void testGetApplicationsRequiresBundleName() {
        // Check that the get applications won't work without bundleName
        given()
                // Set header to x-rh-identity
                .when()
                .get("/internal/applications")
                .then()
                .statusCode(400);
    }

    @Test
    void testGetApplicationsReturnApplications() {
        String LOCAL_BUNDLE_NAME = "insights-return-app";

        Bundle bundle = new Bundle();
        bundle.setName(LOCAL_BUNDLE_NAME);
        bundle.setDisplayName("Insights");
        Response bundleResponse =
                given()
                        .body(bundle)
                        .contentType(ContentType.JSON)
                        .when().post("/internal/bundles")
                        .then()
                        .statusCode(200)
                        .extract().response();
        JsonObject returnedBundle = new JsonObject(bundleResponse.body().asString());
        returnedBundle.mapTo(Bundle.class);

        for (int i = 0; i < 10; ++i) {
            String LOCAL_APP_NAME = "my-app-" + i;
            Application app = new Application();
            app.setName(LOCAL_APP_NAME);
            app.setDisplayName("The best app");
            app.setBundleId(UUID.fromString(returnedBundle.getString("id")));

            given()
                    .when()
                    .contentType(ContentType.JSON)
                    .body(Json.encode(app))
                    .post("/internal/applications")
                    .then()
                    .statusCode(200);
        }

        assertDoesNotThrow(() -> {
            Response response = given()
                    // Set header to x-rh-identity
                    .when()
                    .get("/internal/applications?bundleName=" + LOCAL_BUNDLE_NAME)
                    .then()
                    .statusCode(200)
                    .extract().response();

            List<Application> applications = response.jsonPath().getList(".", Application.class);

            assertNotNull(applications);
            assertEquals(10, applications.size());
        });

    }

    @Test
    void testGetApplicationsReturn404WhenNotFound() {
        String LOCAL_BUNDLE_NAME = "bundle-without-apps";

        Bundle bundle = new Bundle();
        bundle.setName(LOCAL_BUNDLE_NAME);
        bundle.setDisplayName("Insights");
        Bundle returnedBundle =
                given()
                        .body(bundle)
                        .contentType(ContentType.JSON)
                        .when().post("/internal/bundles")
                        .then()
                        .statusCode(200)
                        .extract().body().as(Bundle.class);

        given()
                // Set header to x-rh-identity
                .when()
                .get("/internal/applications?bundleName=" + LOCAL_BUNDLE_NAME)
                .then()
                .statusCode(404);
    }
}
