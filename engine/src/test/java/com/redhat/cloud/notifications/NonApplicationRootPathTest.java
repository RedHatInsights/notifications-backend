package com.redhat.cloud.notifications;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.config;
import static io.restassured.RestAssured.given;
import static io.restassured.config.RedirectConfig.redirectConfig;
import static io.restassured.http.ContentType.JSON;

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
                .statusCode(200)
                .contentType(JSON);
    }

    @Test
    void testMetrics() {
        given()
                .when().get("/metrics")
                .then()
                .statusCode(200)
                .contentType("application/openmetrics-text; version=1.0.0; charset=utf-8");
    }
}
