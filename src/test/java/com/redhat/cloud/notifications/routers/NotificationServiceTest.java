package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
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

        // Add mock-rbac-full access before each test, as some will clear this out,
        // as they only need partial access
        String tenant = "NotificationServiceTest";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);
        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

    }

    @BeforeAll
    void init() {
        helpers.createTestAppAndEventTypes();
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

        List<Application> applications = this.helpers.getApplications(ResourceHelpers.TEST_BUNDLE_NAME);
        UUID myOtherTesterApplicationId = applications.stream().filter(a -> a.getName().equals(ResourceHelpers.TEST_APP_NAME_2)).findFirst().get().getId();

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
            assertEquals(myOtherTesterApplicationId, ev.getApplication().getId());
        }

        assertTrue(eventTypes.length >= 100); // Depending on the test order, we might have existing application types also
    }

    @Test
    void testEventTypeFetchingByBundle() {

        List<Application> applications = this.helpers.getApplications(ResourceHelpers.TEST_BUNDLE_NAME);
        UUID myBundleId = applications.stream().filter(a -> a.getName().equals(this.helpers.TEST_APP_NAME_2)).findFirst().get().getBundleId();

        Response response = given()
                .when()
                .header(identityHeader)
                .contentType(ContentType.JSON)
                .queryParam("bundleId", myBundleId)
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .extract().response();

        EventType[] eventTypes = Json.decodeValue(response.getBody().asString(), EventType[].class);
        for (EventType ev : eventTypes) {
            assertTrue(ev.getApplication().getBundleId().equals(myBundleId));
        }

        assertTrue(eventTypes.length >= 100); // Depending on the test order, we might have existing application types also
    }

    @Test
    void testEventTypeFetchingByBundleAndApplicationId() {

        List<Application> applications = this.helpers.getApplications(ResourceHelpers.TEST_BUNDLE_NAME);
        UUID myOtherTesterApplicationId = applications.stream().filter(a -> a.getName().equals(this.helpers.TEST_APP_NAME_2)).findFirst().get().getId();
        UUID myBundleId = applications.stream().filter(a -> a.getName().equals(this.helpers.TEST_APP_NAME_2)).findFirst().get().getBundleId();

        Response response = given()
                .when()
                .header(identityHeader)
                .contentType(ContentType.JSON)
                .queryParam("bundleId", myBundleId)
                .queryParam("applicationIds", myOtherTesterApplicationId)
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .extract().response();

        EventType[] eventTypes = Json.decodeValue(response.getBody().asString(), EventType[].class);
        for (EventType ev : eventTypes) {
            assertTrue(ev.getApplication().getBundleId().equals(myBundleId));
            assertTrue(ev.getApplication().getId().equals(myOtherTesterApplicationId));
        }

        assertTrue(eventTypes.length >= 100); // Depending on the test order, we might have existing application types also
    }

    void testCreateSecuredDefaults() {
        // We need to clear out full access first
        mockServerConfig.clearRbac();

        String tenant = "testNonExistantDefaults";
        String userName = "user";
        String localIdentityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header localIdentityHeader = TestHelpers.createIdentityHeader(localIdentityHeaderValue);
        mockServerConfig.addMockRbacAccess(localIdentityHeaderValue, MockServerClientConfig.RbacAccess.READ_ACCESS);

        try {
            // Add non-existent endpointId to an account without default created
            given()
                    .header(localIdentityHeader)
                    .when()
                    .contentType(ContentType.JSON)
                    .put(String.format("/notifications/defaults/%s", UUID.randomUUID()))
                    .then()
                    .statusCode(403);
        } finally {
            mockServerConfig.clearRbac();
        }
    }

    @Test
    void testDeleteSecuredDefaults() {
        // We need to clear out full access first
        mockServerConfig.clearRbac();

        String tenant = "testNonExistantDefaults";
        String userName = "user";
        String localIdentityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header localIdentityHeader = TestHelpers.createIdentityHeader(localIdentityHeaderValue);
        mockServerConfig.addMockRbacAccess(localIdentityHeaderValue, MockServerClientConfig.RbacAccess.READ_ACCESS);

        try {
            // Add non-existant endpointId to an account without default created
            given()
                    .header(localIdentityHeader)
                    .when()
                    .contentType(ContentType.JSON)
                    .delete(String.format("/notifications/defaults/%s", UUID.randomUUID()))
                    .then()
                    .statusCode(403);
        } finally {
            mockServerConfig.clearRbac();
        }
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

    @Test
    void testGetEventTypesAffectedByEndpoint() {
        String tenant = "testGetEventTypesAffectedByEndpoint";
        String userName = "user";
        String localIdentityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header localIdentityHeader = TestHelpers.createIdentityHeader(localIdentityHeaderValue);
        mockServerConfig.addMockRbacAccess(localIdentityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        UUID applicationId = this.helpers.getApplications(ResourceHelpers.TEST_BUNDLE_NAME).stream().filter(a -> a.getName().equals(ResourceHelpers.TEST_APP_NAME_2)).findFirst().get().getId();
        UUID ep1 = this.helpers.createWebhookEndpoint(tenant);
        UUID ep2 = this.helpers.createWebhookEndpoint(tenant);
        UUID defaultEp = this.helpers.getDefaultEndpointId(tenant);
        List<EventType> eventTypesFromApp1 = this.helpers.getEventTypesForApplication(applicationId);
        EventType ev0 = eventTypesFromApp1.get(0);
        EventType ev1 = eventTypesFromApp1.get(1);

        // ep1 assigned to ev0; ep2 not assigned; default not assigned.
        this.helpers.assignEndpointToEventType(tenant, ep1, ev0.getId());
        Response response = given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep1.toString())
                .then()
                .statusCode(200)
                .extract().response();

        EventType[] eventTypes = Json.decodeValue(response.getBody().asString(), EventType[].class);
        assertEquals(1, eventTypes.length);
        assertEquals(ev0.getId(), eventTypes[0].getId());

        response = given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep2.toString())
                .then()
                .statusCode(200)
                .extract().response();

        eventTypes = Json.decodeValue(response.getBody().asString(), EventType[].class);
        assertEquals(0, eventTypes.length);

        // ep1 assigned to event ev0; ep2 assigned to default; default not assigned
        this.helpers.assignEndpointToDefault(tenant, ep2);
        response = given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep1.toString())
                .then()
                .statusCode(200)
                .extract().response();

        eventTypes = Json.decodeValue(response.getBody().asString(), EventType[].class);
        assertEquals(1, eventTypes.length);
        assertEquals(ev0.getId(), eventTypes[0].getId());

        response = given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep2.toString())
                .then()
                .statusCode(200)
                .extract().response();

        eventTypes = Json.decodeValue(response.getBody().asString(), EventType[].class);
        assertEquals(0, eventTypes.length);

        // ep1 assigned to ev0; ep2 assigned to default; default assigned to ev1
        this.helpers.assignEndpointToEventType(tenant, defaultEp, ev1.getId());
        response = given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep1.toString())
                .then()
                .statusCode(200)
                .extract().response();

        eventTypes = Json.decodeValue(response.getBody().asString(), EventType[].class);
        assertEquals(1, eventTypes.length);
        assertEquals(ev0.getId(), eventTypes[0].getId());

        response = given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep2.toString())
                .then()
                .statusCode(200)
                .extract().response();

        eventTypes = Json.decodeValue(response.getBody().asString(), EventType[].class);
        assertEquals(1, eventTypes.length);
        assertEquals(ev1.getId(), eventTypes[0].getId());

        // ep1 assigned to event app[0][0]; ep2 assigned to default; default assigned to app[0][1] & app[0][0]
        this.helpers.assignEndpointToEventType(tenant, defaultEp, ev0.getId());
        response = given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep1.toString())
                .then()
                .statusCode(200)
                .extract().response();

        eventTypes = Json.decodeValue(response.getBody().asString(), EventType[].class);
        assertEquals(1, eventTypes.length);
        assertEquals(ev0.getId(), eventTypes[0].getId());

        response = given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep2.toString())
                .then()
                .statusCode(200)
                .extract().response();

        eventTypes = Json.decodeValue(response.getBody().asString(), EventType[].class);
        assertEquals(2, eventTypes.length);
        assertEquals(ev0.getId(), eventTypes[1].getId());
        assertEquals(ev1.getId(), eventTypes[0].getId());
    }

    @Test
    void testGetApplicationFacets() {
        String tenant = "test";
        String userName = "user";
        String localIdentityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header localIdentityHeader = TestHelpers.createIdentityHeader(localIdentityHeaderValue);
        mockServerConfig.addMockRbacAccess(localIdentityHeaderValue, RbacAccess.READ_ACCESS);
        given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/facets/applications?bundleName=insights")
                .then()
                .statusCode(200);
    }

    @Test
    void testGetBundlesFacets() {
        String tenant = "test";
        String userName = "user";
        String localIdentityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header localIdentityHeader = TestHelpers.createIdentityHeader(localIdentityHeaderValue);
        mockServerConfig.addMockRbacAccess(localIdentityHeaderValue, RbacAccess.READ_ACCESS);
        given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/facets/bundles")
                .then()
                .statusCode(200);
    }

}
