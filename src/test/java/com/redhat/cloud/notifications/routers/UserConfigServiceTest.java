package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.routers.models.SettingsValues;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class UserConfigServiceTest {

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_1_0;
    }

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Test
    void testSettings() {
        String tenant = "empty";
        String username = "user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, username);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);
        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        Response response = given()
                .header(identityHeader)
                .when().get("/user-config/email-preference")
                .then()
                .statusCode(200)
                .extract().response();

        assertEquals(
                "[false]",
                response.jsonPath().getString(valuePathFor("instantNotification"))
        );

        // Set instantNotification to true
        SettingsValues settingsValues = new SettingsValues();
        settingsValues.instantNotification = true;
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(settingsValues))
                .post("/user-config/email-preference")
                .then()
                .statusCode(200);

        // Check again with the api
        response = given()
                .header(identityHeader)
                .when().get("/user-config/email-preference")
                .then()
                .statusCode(200)
                .extract().response();

        assertEquals(
                "[true]",
                response.jsonPath().getString(valuePathFor("instantNotification"))
        );

        // Set instantNotification to false
        settingsValues = new SettingsValues();
        settingsValues.instantNotification = false;
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(settingsValues))
                .post("/user-config/email-preference")
                .then()
                .statusCode(200);

        // Check again with the api
        response = given()
                .header(identityHeader)
                .when().get("/user-config/email-preference")
                .then()
                .statusCode(200)
                .extract().response();

        assertEquals(
                "[false]",
                response.jsonPath().getString(valuePathFor("instantNotification"))
        );
    }

    private String valuePathFor(String field) {
        return String.format("findAll { e -> e.fields.findAll { f -> f.name.equals(\"%s\") } }[0].fields.initialValue ", field);
    }
}
