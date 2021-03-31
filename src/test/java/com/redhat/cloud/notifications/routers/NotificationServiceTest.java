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
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.routers.models.Facet;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NotificationServiceTest {

    /*
     * In the tests below, most JSON responses are verified using JsonObject/JsonArray instead of deserializing these
     * responses into model instances and checking their attributes values. That's because the model classes contain
     * attributes annotated with @JsonProperty(access = READ_ONLY) which can't be deserialized and therefore verified
     * here. The deserialization is still performed only to verify that the JSON responses data structure is correct.
     */

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
    @ActivateRequestContext
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

        JsonArray eventTypes = new JsonArray(response.getBody().asString());
        assertTrue(eventTypes.size() >= 200); // Depending on the test order, we might have existing application types also

        JsonObject policiesAll = eventTypes.getJsonObject(0);
        policiesAll.mapTo(EventType.class);
        assertNotNull(policiesAll.getString("id"));
        assertNotNull(policiesAll.getJsonObject("application"));
        assertNotNull(policiesAll.getJsonObject("application").getString("id"));
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

        JsonArray eventTypes = new JsonArray(response.getBody().asString());
        for (int i = 0; i < eventTypes.size(); i++) {
            JsonObject ev = eventTypes.getJsonObject(i);
            ev.mapTo(EventType.class);
            assertEquals(myOtherTesterApplicationId.toString(), ev.getJsonObject("application").getString("id"));
        }

        assertTrue(eventTypes.size() >= 100); // Depending on the test order, we might have existing application types also
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

        JsonArray eventTypes = new JsonArray(response.getBody().asString());
        for (int i = 0; i < eventTypes.size(); i++) {
            JsonObject ev = eventTypes.getJsonObject(i);
            ev.mapTo(EventType.class);
            assertEquals(myBundleId.toString(), ev.getJsonObject("application").getString("bundle_id"));
        }

        assertTrue(eventTypes.size() >= 100); // Depending on the test order, we might have existing application types also
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

        JsonArray eventTypes = new JsonArray(response.getBody().asString());
        for (int i = 0; i < eventTypes.size(); i++) {
            JsonObject ev = eventTypes.getJsonObject(i);
            ev.mapTo(EventType.class);
            assertEquals(myBundleId.toString(), ev.getJsonObject("application").getString("bundle_id"));
            assertEquals(myOtherTesterApplicationId.toString(), ev.getJsonObject("application").getString("id"));
        }

        assertTrue(eventTypes.size() >= 100); // Depending on the test order, we might have existing application types also
    }

    @Test
    void testAddToDefaultsWithInsufficientPrivileges() {
        // We need to clear out full access first
        mockServerConfig.clearRbac();

        String tenant = "testAddToDefaultsWithInsufficientPrivileges";
        String userName = "user";
        String localIdentityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header localIdentityHeader = TestHelpers.createIdentityHeader(localIdentityHeaderValue);
        mockServerConfig.addMockRbacAccess(localIdentityHeaderValue, MockServerClientConfig.RbacAccess.READ_ACCESS);

        try {
            /*
             * Add an integration to the list of configured default actions without write access.
             * It should fail because of insufficient privileges.
             */
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
    void testDeleteFromDefaultsWithInsufficientPrivileges() {
        // We need to clear out full access first
        mockServerConfig.clearRbac();

        String tenant = "testDeleteFromDefaultsWithInsufficientPrivileges";
        String userName = "user";
        String localIdentityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header localIdentityHeader = TestHelpers.createIdentityHeader(localIdentityHeaderValue);
        mockServerConfig.addMockRbacAccess(localIdentityHeaderValue, MockServerClientConfig.RbacAccess.READ_ACCESS);

        try {
            /*
             * Delete an integration from the list of configured default actions without write access.
             * It should fail because of insufficient privileges.
             */
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
        ep.setType(EndpointType.DEFAULT);
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
    void testPutGetAndDeleteDefaults() {
        String tenant = "testPutGetAndDeleteDefaults";
        String userName = "user";
        String localIdentityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header localIdentityHeader = TestHelpers.createIdentityHeader(localIdentityHeaderValue);
        mockServerConfig.addMockRbacAccess(localIdentityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        // Create default endpoint.
        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.DEFAULT);
        ep.setName("Default endpoint");
        ep.setDescription("The ultimate fallback");
        ep.setEnabled(true);

        Response response = given()
                .basePath(TestConstants.API_INTEGRATIONS_V_1_0)
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .extract().response();
        JsonObject persistedEndpoint = new JsonObject(response.body().asString());
        assertNotNull(persistedEndpoint.getString("id"));

        // Add the persisted endpoint to defaults, it should work.
        given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .pathParam("endpointId", persistedEndpoint.getString("id"))
                .put("/notifications/defaults/{endpointId}")
                .then()
                .statusCode(200);

        // Check if the endpoint appears in the list of defaults.
        response = given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/defaults")
                .then()
                .statusCode(200)
                .extract().response();
        JsonArray defaultEndpoints = new JsonArray(response.body().asString());
        assertEquals(1, defaultEndpoints.size());
        assertEquals(persistedEndpoint.getString("id"), defaultEndpoints.getJsonObject(0).getString("id"));

        // Add the same endpoint in defaults again, it should fail because it's already in defaults.
        given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .pathParam("endpointId", persistedEndpoint.getString("id"))
                .put("/notifications/defaults/{endpointId}")
                .then()
                .statusCode(400);

        // Delete the endpoint from defaults, it should work.
        given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .pathParam("endpointId", persistedEndpoint.getString("id"))
                .delete("/notifications/defaults/{endpointId}")
                .then()
                .statusCode(204);

        // Check if the endpoint was removed from the list of defaults.
        response = given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/defaults")
                .then()
                .statusCode(200)
                .extract().response();
        defaultEndpoints = new JsonArray(response.body().asString());
        assertEquals(0, defaultEndpoints.size());
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

        JsonArray eventTypes = new JsonArray(response.getBody().asString());
        eventTypes.getJsonObject(0).mapTo(EventType.class);
        assertEquals(1, eventTypes.size());
        assertEquals(ev0.getId().toString(), eventTypes.getJsonObject(0).getString("id"));

        response = given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep2.toString())
                .then()
                .statusCode(200)
                .extract().response();

        eventTypes = new JsonArray(response.getBody().asString());
        assertEquals(0, eventTypes.size());

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

        eventTypes = new JsonArray(response.getBody().asString());
        eventTypes.getJsonObject(0).mapTo(EventType.class);
        assertEquals(1, eventTypes.size());
        assertEquals(ev0.getId().toString(), eventTypes.getJsonObject(0).getString("id"));

        response = given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep2.toString())
                .then()
                .statusCode(200)
                .extract().response();

        eventTypes = new JsonArray(response.getBody().asString());
        assertEquals(0, eventTypes.size());

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

        eventTypes = new JsonArray(response.getBody().asString());
        eventTypes.getJsonObject(0).mapTo(EventType.class);
        assertEquals(1, eventTypes.size());
        assertEquals(ev0.getId().toString(), eventTypes.getJsonObject(0).getString("id"));

        response = given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep2.toString())
                .then()
                .statusCode(200)
                .extract().response();

        eventTypes = new JsonArray(response.getBody().asString());
        eventTypes.getJsonObject(0).mapTo(EventType.class);
        assertEquals(1, eventTypes.size());
        assertEquals(ev1.getId().toString(), eventTypes.getJsonObject(0).getString("id"));

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

        eventTypes = new JsonArray(response.getBody().asString());
        eventTypes.getJsonObject(0).mapTo(EventType.class);
        assertEquals(1, eventTypes.size());
        assertEquals(ev0.getId().toString(), eventTypes.getJsonObject(0).getString("id"));

        response = given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep2.toString())
                .then()
                .statusCode(200)
                .extract().response();

        eventTypes = new JsonArray(response.getBody().asString());
        eventTypes.getJsonObject(0).mapTo(EventType.class);
        assertEquals(2, eventTypes.size());
        assertEquals(ev0.getId().toString(), eventTypes.getJsonObject(1).getString("id"));
        assertEquals(ev1.getId().toString(), eventTypes.getJsonObject(0).getString("id"));
    }

    @Test
    void testGetApplicationFacets() {
        String tenant = "test";
        String userName = "user";
        String localIdentityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header localIdentityHeader = TestHelpers.createIdentityHeader(localIdentityHeaderValue);
        mockServerConfig.addMockRbacAccess(localIdentityHeaderValue, RbacAccess.READ_ACCESS);
        List<Facet> applications = given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/facets/applications?bundleName=insights")
                .then()
                .statusCode(200).extract().response().jsonPath().getList(".", Facet.class);

        assertTrue(applications.size() > 0);
        Optional<Facet> policies = applications.stream().filter(facet -> facet.getName().equals("policies")).findFirst();
        assertTrue(policies.isPresent());
        assertEquals("Policies", policies.get().getDisplayName());
    }

    @Test
    void testGetBundlesFacets() {
        String tenant = "test";
        String userName = "user";
        String localIdentityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        Header localIdentityHeader = TestHelpers.createIdentityHeader(localIdentityHeaderValue);
        mockServerConfig.addMockRbacAccess(localIdentityHeaderValue, RbacAccess.READ_ACCESS);
        List<Facet> bundles = given()
                .header(localIdentityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/facets/bundles")
                .then()
                .statusCode(200).extract().response().jsonPath().getList(".", Facet.class);

        assertTrue(bundles.size() > 0);
        Optional<Facet> insights = bundles.stream().filter(facet -> facet.getName().equals("insights")).findFirst();
        assertTrue(insights.isPresent());
        assertEquals("Insights", insights.get().getDisplayName());
    }

}
