package com.redhat.cloud.notifications.routers.internal;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.redhat.cloud.notifications.TestHelpers.createTurnpikeIdentityHeader;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static io.restassured.http.ContentType.TEXT;
import static org.hamcrest.Matchers.in;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test Health checks and admin-down from admin-interface
 */
@QuarkusTest
public class HealthCheckTest {

    private static final Pattern PATTERN = Pattern.compile("\"name\": \"Database connections health check\",\\R[ ]+\"status\": \"UP\"");

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Test
    void testNormalHealth() {
        normalHealthCheck();
    }

    private String normalHealthCheck() {
        String body =
                when()
                        .get("/health")
                        .then()
                        .statusCode(in(new Integer[]{200, 503})) // may be 503 as there is no Kafka we can talk to
                        .extract().asString();
        assertFalse(body.contains("admin-down"));
        return body;
    }

    @Test
    void testAdminDown() {

        with()
                .queryParam("status", "admin-down")
                .header(createTurnpikeIdentityHeader("admin", adminRole))
                .when()
                .post("/internal/admin/status")
                .then()
                .statusCode(200)
                .contentType(TEXT);

        try {
            String body =
                    when()
                            .get("/health")
                            .then()
                            .statusCode(503)
                            .extract().asString();
            assertTrue(body.contains("admin-down"));
        } finally {
            with()
                    .queryParam("status", "ok")
                    .header(createTurnpikeIdentityHeader("admin", adminRole))
                    .when()
                    .post("/internal/admin/status")
                    .then()
                    .statusCode(200)
                    .contentType(TEXT);
        }
    }

    // Make sure we don't leak connections here
    @Test
    void testRepeatedHealth() {
        for (int i = 0; i < 150; i++) {
            String body = normalHealthCheck();
            assertTrue(PATTERN.matcher(body).find());
        }
    }
}
