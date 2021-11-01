package com.redhat.cloud.notifications.routers;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class JsonParseExceptionMapperTest {

    @Test
    public void testCreateInvalidEventType() {
        String responseBody = given().contentType(JSON).body("I should be a valid event type JSON!").when()
                .post("/internal/eventTypes").then().statusCode(400).extract().body().asString();
        assertTrue(responseBody.contains("Unrecognized token 'I'"));
    }
}
