package com.redhat.cloud.notifications.routers;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.TestHelpers.createTurnpikeIdentityHeader;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class JsonParseExceptionMapperTest {

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Test
    public void testCreateInvalidEventType() {
        String responseBody = given()
                .contentType(JSON)
                .header(createTurnpikeIdentityHeader("admin", adminRole))
                .body("I should be a valid event type JSON!")
                .when()
                .post("/internal/eventTypes")
                .then()
                .statusCode(400)
                .extract().body().asString();
        assertTrue(responseBody.contains("Unrecognized token 'I'"));
    }
}
