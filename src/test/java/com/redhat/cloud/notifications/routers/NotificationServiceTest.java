package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.json.Json;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.inject.Inject;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NotificationServiceTest {
    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Inject
    ResourceHelpers helpers;

    private Header identityHeader;

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_1_0;
    }

    @BeforeAll
    void init() {
        helpers.createTestAppAndEventTypes();
        String tenant = "NotificationServiceTest";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);
    }

    @Test
    void testEventTypeFetching() {
        Response response = given()
                .when()
                .header(identityHeader)
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .extract().response();

        EventType[] eventTypes = Json.decodeValue(response.getBody().asString(), EventType[].class);
        assertTrue(eventTypes.length >= 200); // Depending on the test order, we might have existing application types also

        EventType policiesAll = eventTypes[0];
        assertNotNull(policiesAll.getId());
        assertNotNull(policiesAll.getApplication());
        assertNotNull(policiesAll.getApplication().getId());
    }

    @Test
    void testEventTypeFetchingByApplication() {

        List<Application> applications = this.helpers.getApplications();
        UUID myOtherTesterApplicationId = applications.stream().filter(a -> a.getName().equals(this.helpers.TEST_APP_NAME_2)).findFirst().get().getId();

        Response response = given()
                .when()
                .header(identityHeader)
                .contentType(ContentType.JSON)
                .queryParam("applicationIds", myOtherTesterApplicationId)
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .extract().response();

        EventType[] eventTypes = Json.decodeValue(response.getBody().asString(), EventType[].class);
        for (EventType ev : eventTypes) {
            assertTrue(ev.getApplication().getId().equals(myOtherTesterApplicationId));
        }

        assertTrue(eventTypes.length >= 100); // Depending on the test order, we might have existing application types also
    }

    @Test
    void testNonExistantDefaults() {
        String tenant = "testNonExistantDefaults";
        String userName = "user";
        String localIdentityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header localIdentityHeader = TestHelpers.createIdentityHeader(localIdentityHeaderValue);
        mockServerConfig.addMockRbacAccess(localIdentityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        // Add non-existant endpointId to an account without default created
        given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .put(String.format("/notifications/defaults/%s", UUID.randomUUID()))
                .then()
                .statusCode(400);

        // Create default endpoint
        Endpoint ep = new Endpoint();
        ep.setType(Endpoint.EndpointType.DEFAULT);
        ep.setName("Default endpoint");
        ep.setDescription("The ultimate fallback");
        ep.setEnabled(true);

        given()
                .basePath(TestConstants.API_INTEGRATIONS_V_1_0)
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(200);

        // Send non-existant UUID again
        given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .put(String.format("/notifications/defaults/%s", UUID.randomUUID()))
                .then()
                .statusCode(400);
    }
}
