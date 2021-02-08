package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
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
public class BundleServiceTest {

    public static final String INSIGHTS_1 = "insights1";

    @Test
    void testAdd() {
        Bundle returnedBundle = createBundle(INSIGHTS_1, 200);

        UUID bundleId = returnedBundle.getId();
        assertNotNull(bundleId);
        assertEquals(INSIGHTS_1, returnedBundle.getName());
        assertEquals(INSIGHTS_1.toUpperCase(), returnedBundle.getDisplay_name());

        given()
            .accept(ContentType.JSON)
        .when()
            .get("/internal/bundles/" + bundleId)
        .then()
            .statusCode(200);

        deleteBundleById(bundleId);

        when()
            .get("/internal/bundles/" + bundleId)
        .then()
            .statusCode(404);
    }

    @Test
    void testAddTwoBundles() {
        Bundle b1 = null;
        Bundle b2 = null;
        try {
            b1 = createBundle("b1", 200);
            b2 = createBundle("b2", 200);
        } finally {
            if (b1 != null) {
                deleteBundleById(b1.getId());
            }
            if (b2 != null) {
                deleteBundleById(b2.getId());
            }
        }
    }

    @Test
    void testNoAddSameBundleTwice() {
        Bundle b1 = null;
        try {
            b1 = createBundle("b1", 200);
            createBundle("b1", 500);
        } finally {
            if (b1 != null) {
                deleteBundleById(b1.getId());
            }
        }
    }

    @Test
    void testAddApplicationOnce() {

        Bundle returnedBundle = createBundle(INSIGHTS_1, 200);

        Application app = new Application();
        app.setBundleId(returnedBundle.getId());
        app.setName("test");
        app.setDisplay_name("..");

        try {
            Application created =
                given()
                    .body(app)
                    .contentType(ContentType.JSON)
                .when()
                    .post("/internal/bundles/" + returnedBundle.getId() + "/applications")
                .then()
                    .statusCode(200)
                    .extract().body().as(Application.class);
            app.setBundleId(created.getBundleId());
        } finally {
            deleteBundleById(returnedBundle.getId());

            when()
                    .get("/internal/applications/" + app.getId())
                .then()
                    .statusCode(404);
        }
    }

    @Test
    void testNoAddApplicationTwice() {

        Bundle returnedBundle = createBundle(INSIGHTS_1, 200);

        Application app = new Application();
        app.setBundleId(returnedBundle.getId());
        app.setName("test");
        app.setDisplay_name("..");

        try {
            given()
                    .body(app)
                    .contentType(ContentType.JSON)
                    .when().post("/internal/bundles/" + returnedBundle.getId() + "/applications")
                    .then()
                    .statusCode(200);

            given()
                    .body(app)
                    .contentType(ContentType.JSON)
                    .when().post("/internal/bundles/" + returnedBundle.getId() + "/applications")
                    .then()
                    .statusCode(500);
        } finally {
            deleteBundleById(returnedBundle.getId());
        }
    }

    @Test
    void testAddTwoApplicationTwoBundles() {

        Bundle returnedBundle1 = createBundle(INSIGHTS_1, 200);
        Bundle returnedBundle2 = createBundle("other_one", 200);

        Application app1 = new Application();
        app1.setBundleId(returnedBundle1.getId());
        app1.setName("test");
        app1.setDisplay_name("..");

        Application app2 = new Application();
        app2.setBundleId(returnedBundle2.getId());
        app2.setName("test");
        app2.setDisplay_name("..");

        try {
            given()
                    .body(app1)
                    .contentType(ContentType.JSON)
                    .when().post("/internal/bundles/" + returnedBundle1.getId() + "/applications")
                    .then()
                    .statusCode(200);

            given()
                    .body(app2)
                    .contentType(ContentType.JSON)
                    .when().post("/internal/bundles/" + returnedBundle2.getId() + "/applications")
                    .then()
                    .statusCode(200);
        } finally {
            deleteBundleById(returnedBundle1.getId());
            deleteBundleById(returnedBundle2.getId());
        }
    }

    private Bundle createBundle(String bundleName, int expectedReturnCode) {
        Bundle bundle = new Bundle();
        bundle.setName(bundleName);
        bundle.setDisplay_name(bundleName.toUpperCase());
        ExtractableResponse response = given()
                .body(bundle)
                .contentType(ContentType.JSON)
                .when()
                .post("/internal/bundles")
                .then()
                .statusCode(expectedReturnCode)
                .extract();

        if (expectedReturnCode == 200) {
            Bundle returned = response
                    .body().as(Bundle.class);
            return returned;
        } else {
            return null;
        }
    }

    private void deleteBundleById(UUID id) {
        when()
                .delete("/internal/bundles/" + id)
            .then()
                .statusCode(200);
    }
}
