package com.redhat.cloud.notifications.routers;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static org.hamcrest.Matchers.in;

/**
 * Test Health checks and admin-down from admin-interface
 */
@QuarkusTest
public class HealthCheckTest {

    @Test
    void testNormalHealth() {
        String body =
                when()
                        .get("/health")
                        .then()
                        .statusCode(in(new Integer[]{200, 503})) // may be 503 as there is no Kafka we can talk to
                        .extract().asString();
        Assertions.assertFalse(body.contains("admin-down"));
    }

    @Test
    void testAdminDown() {

        with()
                .queryParam("status", "admin-down")
                .accept("application/json")
                .contentType("application/json")
                .when()
                .post("/admin/status")
                .then()
                .statusCode(200);

        try {
            String body =
                    when()
                            .get("/health")
                            .then()
                            .statusCode(503)
                            .extract().asString();
            Assertions.assertTrue(body.contains("admin-down"));
        } finally {
            with()
                    .queryParam("status", "ok")
                    .accept("application/json")
                    .contentType("application/json")
                    .when()
                    .post("/admin/status")
                    .then()
                    .statusCode(200);
        }
    }
}
