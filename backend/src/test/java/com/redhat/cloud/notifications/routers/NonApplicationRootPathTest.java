package com.redhat.cloud.notifications.routers;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.config;
import static io.restassured.RestAssured.given;
import static io.restassured.config.RedirectConfig.redirectConfig;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.TEXT;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
public class NonApplicationRootPathTest {

    @BeforeAll
    static void beforeAll() {
        config = config().redirect(redirectConfig().followRedirects(false));
    }

    @Test
    void testHealth() {
        given()
                .when().get("/health")
                .then()
                .statusCode(503) // Because Kafka is DOWN during tests.
                .contentType(JSON)
                .body("status", containsString("DOWN"));
    }

    @Test
    void testMetrics() {
        given()
                .when().get("/metrics")
                .then()
                .statusCode(200)
                .contentType(TEXT);
    }

    @Test
    void testOpenApi() {
        given()
                .when().get("/openapi.json")
                .then()
                .statusCode(200)
                .contentType(JSON);
    }
}
