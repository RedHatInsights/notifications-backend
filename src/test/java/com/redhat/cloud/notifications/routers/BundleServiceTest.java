package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.Bundle;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
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

    @Test
    void testAdd() {
        Bundle bundle = new Bundle();
        bundle.setName("insights1");
        bundle.setDisplay_name("Insights1");
        Bundle returnedBundle =
            given()
                    .body(bundle)
                    .contentType(ContentType.JSON)
                .when().post("/internal/bundles")
                .then()
                    .statusCode(200)
                .extract().body().as(Bundle.class);

        UUID bundleId = returnedBundle.getId();
        assertNotNull(bundleId);
        assertEquals("insights1", returnedBundle.getName());
        assertEquals("Insights1", returnedBundle.getDisplay_name());

        given()
            .accept(ContentType.JSON)
        .when()
            .get("/internal/bundles/" + bundleId)
        .then()
            .statusCode(200);

        when()
                .delete("/internal/bundles/" + bundleId)
        .then()
                .statusCode(200);

        when()
            .get("/internal/bundles/" + bundleId)
        .then()
            .statusCode(204);

    }
}
