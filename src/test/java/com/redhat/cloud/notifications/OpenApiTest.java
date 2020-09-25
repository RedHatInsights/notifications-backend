package com.redhat.cloud.notifications;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.when;

@QuarkusTest
public class OpenApiTest {

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = "/api/notifications";
    }

    @Test
    void getOpenApiV1_0() {
        when()
                .get("/v1.0/openapi.json")
        .then()
                .statusCode(200);
    }

    // Test that the v1 -> v1.0 redirect works
    @Test
    void getOpenApiV1() {
        when()
                .get("/v1/openapi.json")
        .then()
                .statusCode(200);
    }
}
