package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.json.Json;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ApplicationServiceTest {

    static final String APP_NAME = "PoliciesApplicationServiceTest";
    static final String EVENT_TYPE_NAME = "policy-triggered";

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Test
    void testPoliciesApplicationAdding() {
        Bundle bundle = new Bundle();
        bundle.setName("insights-test");
        bundle.setDisplay_name("Insights");
        Bundle returnedBundle =
            given()
                    .body(bundle)
                    .contentType(ContentType.JSON)
                .when().post("/internal/bundles")
                .then()
                    .statusCode(200)
                .extract().body().as(Bundle.class);

        Application app = new Application();
        app.setName(APP_NAME);
        app.setDisplay_name("The best app");
        app.setBundleId(returnedBundle.getId());

        // All of these are without identityHeader
        given()
                // Set header to x-rh-identity
                .when().get("/internal/applications")
                .then()
                .statusCode(200);

        Response response = given()
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(app))
                .post("/internal/applications")
                .then()
                .statusCode(200)
                .extract().response();

        Application appResponse = Json.decodeValue(response.getBody().asString(), Application.class);
        assertNotNull(appResponse.getId());
        assertEquals(returnedBundle.getId(), appResponse.getBundleId());

        // Fetch the applications to check they were really added

        // Create eventType
        EventType eventType = new EventType();
        eventType.setName(EVENT_TYPE_NAME);
        eventType.setDisplay_name("Policies will take care of the rules");

        response = given()
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(eventType))
                .post(String.format("/internal/applications/%s/eventTypes", appResponse.getId()))
                .then()
                .statusCode(200)
                .extract().response();

        EventType typeResponse = Json.decodeValue(response.getBody().asString(), EventType.class);
        assertNotNull(typeResponse.getId());
    }
}
