package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test working with Bundles
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class BundleServiceTest extends DbIsolatedTest {

    /*
     * In the tests below, most JSON responses are verified using JsonObject/JsonArray instead of deserializing these
     * responses into model instances and checking their attributes values. That's because the model classes contain
     * attributes annotated with @JsonProperty(access = READ_ONLY) which can't be deserialized and therefore verified
     * here. The deserialization is still performed only to verify that the JSON responses data structure is correct.
     */

    public static final String INSIGHTS_1 = "insights1";

    @Test
    void testAddAndUpdateAndDeleteBundle() {
        JsonObject returnedBundle = createBundle(INSIGHTS_1, 200);

        String bundleId = returnedBundle.getString("id");
        assertNotNull(bundleId);
        assertEquals(INSIGHTS_1, returnedBundle.getString("name"));
        assertEquals(INSIGHTS_1.toUpperCase(), returnedBundle.getString("display_name"));

        given()
                .accept(ContentType.JSON)
                .when()
                .get("/internal/bundles/" + bundleId)
                .then()
                .statusCode(200);

        updateBundle(bundleId, 200);

        deleteBundleById(bundleId);

        when()
                .get("/internal/bundles/" + bundleId)
                .then()
                .statusCode(404);

        updateBundle(UUID.randomUUID().toString(), 404);
    }

    @Test
    void testAddTwoBundles() {
        createBundle("b1", 200);
        createBundle("b2", 200);
    }

    @Test
    void testNoAddSameBundleTwice() {
        createBundle("b1", 200);
        createBundle("b1", 500);
    }

    @Test
    void testAddApplicationOnce() {

        JsonObject returnedBundle = createBundle(INSIGHTS_1, 200);

        Application app = new Application();
        app.setBundleId(UUID.fromString(returnedBundle.getString("id")));
        app.setName("test");
        app.setDisplayName("..");

        try {
            Application created =
                    given()
                            .body(app)
                            .contentType(ContentType.JSON)
                            .when()
                            .post("/internal/bundles/" + returnedBundle.getString("id") + "/applications")
                            .then()
                            .statusCode(200)
                            .extract().body().as(Application.class);
            app.setBundleId(created.getBundleId());
        } finally {
            deleteBundleById(returnedBundle.getString("id"));

            when()
                    .get("/internal/applications/" + app.getId())
                    .then()
                    .statusCode(404);
        }
    }

    @Test
    void testNoAddApplicationTwice() {

        JsonObject returnedBundle = createBundle(INSIGHTS_1, 200);

        Application app = new Application();
        app.setBundleId(UUID.fromString(returnedBundle.getString("id")));
        app.setName("test");
        app.setDisplayName("..");

        try {
            given()
                    .body(app)
                    .contentType(ContentType.JSON)
                    .when().post("/internal/bundles/" + returnedBundle.getString("id") + "/applications")
                    .then()
                    .statusCode(200);

            given()
                    .body(app)
                    .contentType(ContentType.JSON)
                    .when().post("/internal/bundles/" + returnedBundle.getString("id") + "/applications")
                    .then()
                    .statusCode(500);
        } finally {
            deleteBundleById(returnedBundle.getString("id"));
        }
    }

    @Test
    void testAddTwoApplicationTwoBundles() {

        JsonObject returnedBundle1 = createBundle(INSIGHTS_1, 200);
        JsonObject returnedBundle2 = createBundle("other_one", 200);

        Application app1 = new Application();
        app1.setBundleId(UUID.fromString(returnedBundle1.getString("id")));
        app1.setName("test");
        app1.setDisplayName("..");

        Application app2 = new Application();
        app2.setBundleId(UUID.fromString(returnedBundle2.getString("id")));
        app2.setName("test");
        app2.setDisplayName("..");

        try {
            given()
                    .body(app1)
                    .contentType(ContentType.JSON)
                    .when().post("/internal/bundles/" + returnedBundle1.getString("id") + "/applications")
                    .then()
                    .statusCode(200);

            given()
                    .body(app2)
                    .contentType(ContentType.JSON)
                    .when().post("/internal/bundles/" + returnedBundle2.getString("id") + "/applications")
                    .then()
                    .statusCode(200);
        } finally {
            deleteBundleById(returnedBundle1.getString("id"));
            deleteBundleById(returnedBundle2.getString("id"));
        }
    }

    private JsonObject createBundle(String bundleName, int expectedReturnCode) {
        Bundle bundle = new Bundle();
        bundle.setName(bundleName);
        bundle.setDisplayName(bundleName.toUpperCase());
        ExtractableResponse response = given()
                .body(bundle)
                .contentType(ContentType.JSON)
                .when()
                .post("/internal/bundles")
                .then()
                .statusCode(expectedReturnCode)
                .extract();

        if (expectedReturnCode == 200) {
            JsonObject returned = new JsonObject(response
                    .body().asString());
            returned.mapTo(Bundle.class);
            return returned;
        } else {
            return null;
        }
    }

    private void deleteBundleById(String id) {
        when()
                .delete("/internal/bundles/" + id)
                .then()
                .statusCode(200);
    }

    private void updateBundle(String id, int expectedStatusCode) {
        String updatedName = "updated-name";
        String updatedDisplayName = "updatedDisplayName";

        Bundle bundle = new Bundle();
        bundle.setName(updatedName);
        bundle.setDisplayName(updatedDisplayName);

        given()
                .contentType(ContentType.JSON)
                .pathParam("id", id)
                .body(Json.encode(bundle))
                .put("/internal/bundles/{id}")
                .then()
                .statusCode(expectedStatusCode);

        if (expectedStatusCode >= 200 && expectedStatusCode < 300) {
            String responseBody = given()
                    .pathParam("id", id)
                    .get("/internal/bundles/{id}")
                    .then()
                    .statusCode(200)
                    .extract().body().asString();
            JsonObject jsonBundle = new JsonObject(responseBody);
            jsonBundle.mapTo(Bundle.class);
            assertEquals(updatedName, jsonBundle.getString("name"));
            assertEquals(updatedDisplayName, jsonBundle.getString("display_name"));
        }
    }
}
