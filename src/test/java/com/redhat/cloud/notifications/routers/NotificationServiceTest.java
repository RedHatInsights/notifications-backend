package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.routers.models.Facet;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class NotificationServiceTest extends DbIsolatedTest {

    /*
     * In the tests below, most JSON responses are verified using JsonObject/JsonArray instead of deserializing these
     * responses into model instances and checking their attributes values. That's because the model classes contain
     * attributes annotated with @JsonProperty(access = READ_ONLY) which can't be deserialized and therefore verified
     * here. The deserialization is still performed only to verify that the JSON responses data structure is correct.
     */

    private static final String TENANT = "NotificationServiceTest";
    private static final String USERNAME = "user";

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Inject
    ResourceHelpers helpers;

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_1_0;
        mockServerConfig.clearRbac();
    }

    private Header initRbacMock(String tenant, String username, RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, username);
        mockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createIdentityHeader(identityHeaderValue);
    }

    @Test
    void testEventTypeFetching() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(TENANT, USERNAME, RbacAccess.FULL_ACCESS);

        Response response = given()
                .when()
                .header(identityHeader)
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonArray eventTypes = new JsonArray(response.getBody().asString());
        assertEquals(201, eventTypes.size()); // One of the event types is part of the default DB records.

        JsonObject policiesAll = eventTypes.getJsonObject(0);
        policiesAll.mapTo(EventType.class);
        assertNotNull(policiesAll.getString("id"));
        assertNotNull(policiesAll.getJsonObject("application"));
        assertNotNull(policiesAll.getJsonObject("application").getString("id"));
    }

    @Test
    void testEventTypeFetchingByApplication() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(TENANT, USERNAME, RbacAccess.FULL_ACCESS);

        List<Application> applications = this.helpers.getApplications(ResourceHelpers.TEST_BUNDLE_NAME);
        UUID myOtherTesterApplicationId = applications.stream().filter(a -> a.getName().equals(ResourceHelpers.TEST_APP_NAME_2)).findFirst().get().getId();

        Response response = given()
                .when()
                .header(identityHeader)
                .queryParam("applicationIds", myOtherTesterApplicationId)
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonArray eventTypes = new JsonArray(response.getBody().asString());
        for (int i = 0; i < eventTypes.size(); i++) {
            JsonObject ev = eventTypes.getJsonObject(i);
            ev.mapTo(EventType.class);
            assertEquals(myOtherTesterApplicationId.toString(), ev.getJsonObject("application").getString("id"));
        }

        assertEquals(100, eventTypes.size());
    }

    @Test
    void testEventTypeFetchingByBundle() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(TENANT, USERNAME, RbacAccess.FULL_ACCESS);

        List<Application> applications = this.helpers.getApplications(ResourceHelpers.TEST_BUNDLE_NAME);
        UUID myBundleId = applications.stream().filter(a -> a.getName().equals(this.helpers.TEST_APP_NAME_2)).findFirst().get().getBundleId();

        Response response = given()
                .when()
                .header(identityHeader)
                .queryParam("bundleId", myBundleId)
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonArray eventTypes = new JsonArray(response.getBody().asString());
        for (int i = 0; i < eventTypes.size(); i++) {
            JsonObject ev = eventTypes.getJsonObject(i);
            ev.mapTo(EventType.class);
            assertEquals(myBundleId.toString(), ev.getJsonObject("application").getString("bundle_id"));
        }

        assertEquals(200, eventTypes.size());
    }

    @Test
    void testEventTypeFetchingByBundleAndApplicationId() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(TENANT, USERNAME, RbacAccess.FULL_ACCESS);

        List<Application> applications = this.helpers.getApplications(ResourceHelpers.TEST_BUNDLE_NAME);
        UUID myOtherTesterApplicationId = applications.stream().filter(a -> a.getName().equals(this.helpers.TEST_APP_NAME_2)).findFirst().get().getId();
        UUID myBundleId = applications.stream().filter(a -> a.getName().equals(this.helpers.TEST_APP_NAME_2)).findFirst().get().getBundleId();

        Response response = given()
                .when()
                .header(identityHeader)
                .queryParam("bundleId", myBundleId)
                .queryParam("applicationIds", myOtherTesterApplicationId)
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonArray eventTypes = new JsonArray(response.getBody().asString());
        for (int i = 0; i < eventTypes.size(); i++) {
            JsonObject ev = eventTypes.getJsonObject(i);
            ev.mapTo(EventType.class);
            assertEquals(myBundleId.toString(), ev.getJsonObject("application").getString("bundle_id"));
            assertEquals(myOtherTesterApplicationId.toString(), ev.getJsonObject("application").getString("id"));
        }

        assertEquals(100, eventTypes.size());
    }

    // TODO [BG Phase 2] Delete this test
    @Test
    void testAddToDefaultsWithInsufficientPrivileges() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock("testAddToDefaultsWithInsufficientPrivileges", "user", RbacAccess.READ_ACCESS);

        /*
         * Add an integration to the list of configured default actions without write access.
         * It should fail because of insufficient privileges.
         */
        given()
                .header(identityHeader)
                .when()
                .put(String.format("/notifications/defaults/%s", UUID.randomUUID()))
                .then()
                .statusCode(403)
                .contentType(TEXT);
    }

    // TODO [BG Phase 2] Delete this test
    @Test
    void testDeleteFromDefaultsWithInsufficientPrivileges() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock("testDeleteFromDefaultsWithInsufficientPrivileges", "user", RbacAccess.READ_ACCESS);

        /*
         * Delete an integration from the list of configured default actions without write access.
         * It should fail because of insufficient privileges.
         */
        given()
                .header(identityHeader)
                .when()
                .delete(String.format("/notifications/defaults/%s", UUID.randomUUID()))
                .then()
                .statusCode(403);
    }

    // TODO [BG Phase 2] Delete this test
    @Test
    void testNonExistantDefaults() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock("testNonExistantDefaults", "user", RbacAccess.FULL_ACCESS);

        // Add non-existant endpointId to an account without default created
        given()
                .header(identityHeader)
                .when()
                .put(String.format("/notifications/defaults/%s", UUID.randomUUID()))
                .then()
                .statusCode(400)
                .contentType(TEXT);

        // Create default endpoint
        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.DEFAULT);
        ep.setName("Default endpoint");
        ep.setDescription("The ultimate fallback");
        ep.setEnabled(true);

        given()
                .basePath(TestConstants.API_INTEGRATIONS_V_1_0)
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON);

        // Send non-existant UUID again
        given()
                .header(identityHeader)
                .when()
                .put(String.format("/notifications/defaults/%s", UUID.randomUUID()))
                .then()
                .statusCode(400)
                .contentType(TEXT);
    }

    // TODO [BG Phase 2] Delete this test
    @Test
    void testPutGetAndDeleteDefaults() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock("testPutGetAndDeleteDefaults", "user", RbacAccess.FULL_ACCESS);

        // Create default endpoint.
        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.DEFAULT);
        ep.setName("Default endpoint");
        ep.setDescription("The ultimate fallback");
        ep.setEnabled(true);

        Response response = given()
                .basePath(TestConstants.API_INTEGRATIONS_V_1_0)
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();
        JsonObject persistedEndpoint = new JsonObject(response.body().asString());
        assertNotNull(persistedEndpoint.getString("id"));

        // Add the persisted endpoint to defaults, it should work.
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("endpointId", persistedEndpoint.getString("id"))
                .put("/notifications/defaults/{endpointId}")
                .then()
                .statusCode(200)
                .contentType(TEXT);

        // Check if the endpoint appears in the list of defaults.
        response = given()
                .header(identityHeader)
                .when()
                .get("/notifications/defaults")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();
        JsonArray defaultEndpoints = new JsonArray(response.body().asString());
        assertEquals(1, defaultEndpoints.size());
        assertEquals(persistedEndpoint.getString("id"), defaultEndpoints.getJsonObject(0).getString("id"));

        // Add the same endpoint in defaults again, it should fail because it's already in defaults.
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("endpointId", persistedEndpoint.getString("id"))
                .put("/notifications/defaults/{endpointId}")
                .then()
                .statusCode(400)
                .contentType(TEXT);

        // Delete the endpoint from defaults, it should work.
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("endpointId", persistedEndpoint.getString("id"))
                .delete("/notifications/defaults/{endpointId}")
                .then()
                .statusCode(204);

        // Check if the endpoint was removed from the list of defaults.
        response = given()
                .header(identityHeader)
                .when()
                .get("/notifications/defaults")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();
        defaultEndpoints = new JsonArray(response.body().asString());
        assertEquals(0, defaultEndpoints.size());
    }

    @Test
    void testGetEventTypesAffectedByEndpoint() {
        helpers.createTestAppAndEventTypes();
        String tenant = "testGetEventTypesAffectedByEndpoint";
        Header identityHeader = initRbacMock(tenant, "user", RbacAccess.FULL_ACCESS);

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
                .header(identityHeader)
                .when()
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep1.toString())
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonArray eventTypes = new JsonArray(response.getBody().asString());
        eventTypes.getJsonObject(0).mapTo(EventType.class);
        assertEquals(1, eventTypes.size());
        assertEquals(ev0.getId().toString(), eventTypes.getJsonObject(0).getString("id"));

        response = given()
                .header(identityHeader)
                .when()
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep2.toString())
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        eventTypes = new JsonArray(response.getBody().asString());
        assertEquals(0, eventTypes.size());

        // ep1 assigned to event ev0; ep2 assigned to default; default not assigned
        this.helpers.assignEndpointToDefault(tenant, ep2);
        response = given()
                .header(identityHeader)
                .when()
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep1.toString())
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        eventTypes = new JsonArray(response.getBody().asString());
        eventTypes.getJsonObject(0).mapTo(EventType.class);
        assertEquals(1, eventTypes.size());
        assertEquals(ev0.getId().toString(), eventTypes.getJsonObject(0).getString("id"));

        response = given()
                .header(identityHeader)
                .when()
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep2.toString())
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        eventTypes = new JsonArray(response.getBody().asString());
        assertEquals(0, eventTypes.size());

        // ep1 assigned to ev0; ep2 assigned to default; default assigned to ev1
        this.helpers.assignEndpointToEventType(tenant, defaultEp, ev1.getId());
        response = given()
                .header(identityHeader)
                .when()
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep1.toString())
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        eventTypes = new JsonArray(response.getBody().asString());
        eventTypes.getJsonObject(0).mapTo(EventType.class);
        assertEquals(1, eventTypes.size());
        assertEquals(ev0.getId().toString(), eventTypes.getJsonObject(0).getString("id"));

        response = given()
                .header(identityHeader)
                .when()
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep2.toString())
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        eventTypes = new JsonArray(response.getBody().asString());
        eventTypes.getJsonObject(0).mapTo(EventType.class);
        assertEquals(1, eventTypes.size());
        assertEquals(ev1.getId().toString(), eventTypes.getJsonObject(0).getString("id"));

        // ep1 assigned to event app[0][0]; ep2 assigned to default; default assigned to app[0][1] & app[0][0]
        this.helpers.assignEndpointToEventType(tenant, defaultEp, ev0.getId());
        response = given()
                .header(identityHeader)
                .when()
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep1.toString())
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        eventTypes = new JsonArray(response.getBody().asString());
        eventTypes.getJsonObject(0).mapTo(EventType.class);
        assertEquals(1, eventTypes.size());
        assertEquals(ev0.getId().toString(), eventTypes.getJsonObject(0).getString("id"));

        response = given()
                .header(identityHeader)
                .when()
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep2.toString())
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        eventTypes = new JsonArray(response.getBody().asString());
        eventTypes.getJsonObject(0).mapTo(EventType.class);
        assertEquals(2, eventTypes.size());
        assertEquals(ev0.getId().toString(), eventTypes.getJsonObject(1).getString("id"));
        assertEquals(ev1.getId().toString(), eventTypes.getJsonObject(0).getString("id"));
    }

    @Test
    void testGetApplicationFacets() {
        Header identityHeader = initRbacMock("test", "user", RbacAccess.READ_ACCESS);
        List<Facet> applications = given()
                .header(identityHeader)
                .when()
                .get("/notifications/facets/applications?bundleName=rhel")
                .then()
                .statusCode(200).contentType(JSON).extract().response().jsonPath().getList(".", Facet.class);

        assertTrue(applications.size() > 0);
        Optional<Facet> policies = applications.stream().filter(facet -> facet.getName().equals("policies")).findFirst();
        assertTrue(policies.isPresent());
        assertEquals("Policies", policies.get().getDisplayName());
    }

    @Test
    void testGetBundlesFacets() {
        Header identityHeader = initRbacMock("test", "user", RbacAccess.READ_ACCESS);
        List<Facet> bundles = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .get("/notifications/facets/bundles")
                .then()
                .statusCode(200).contentType(JSON).extract().response().jsonPath().getList(".", Facet.class);

        assertTrue(bundles.size() > 0);
        Optional<Facet> rhel = bundles.stream().filter(facet -> facet.getName().equals("rhel")).findFirst();
        assertTrue(rhel.isPresent());
        assertEquals("Red Hat Enterprise Linux", rhel.get().getDisplayName());
    }

    @Test
    void testInsufficientPrivileges() {
        Header noAccessIdentityHeader = initRbacMock("tenant", "noAccess", RbacAccess.NO_ACCESS);
        Header readAccessIdentityHeader = initRbacMock("tenant", "readAccess", RbacAccess.READ_ACCESS);

        given()
                .header(noAccessIdentityHeader)
                .when()
                .get("/notifications/eventTypes")
                .then()
                .statusCode(403)
                .contentType(JSON);

        given()
                .header(noAccessIdentityHeader)
                .pathParam("endpointId", UUID.randomUUID())
                .when()
                // TODO [BG Phase 2] Remove '/bg' from path
                .get("/notifications/bg/eventTypes/affectedByRemovalOfEndpoint/{endpointId}")
                .then()
                .statusCode(403)
                .contentType(JSON);

        given()
                .header(noAccessIdentityHeader)
                .pathParam("behaviorGroupId", UUID.randomUUID())
                .when()
                .get("/notifications/eventTypes/affectedByRemovalOfBehaviorGroup/{behaviorGroupId}")
                .then()
                .statusCode(403)
                .contentType(JSON);

        given()
                .header(readAccessIdentityHeader)
                .contentType(JSON)
                .pathParam("eventTypeId", UUID.randomUUID())
                .body(Json.encode(List.of(UUID.randomUUID())))
                .when()
                .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
                .then()
                .statusCode(403)
                .contentType(TEXT);

        given()
                .header(noAccessIdentityHeader)
                .pathParam("eventTypeId", UUID.randomUUID())
                .when()
                .get("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
                .then()
                .statusCode(403)
                .contentType(JSON);

        given()
                .header(readAccessIdentityHeader)
                .contentType(JSON)
                // TODO Remove the body when https://github.com/quarkusio/quarkus/issues/16897 is fixed
                .body(Json.encode(new BehaviorGroup()))
                .when()
                .post("/notifications/behaviorGroups")
                .then()
                .statusCode(403)
                .contentType(JSON);

        given()
                .header(readAccessIdentityHeader)
                .contentType(JSON)
                .pathParam("id", UUID.randomUUID())
                // TODO Remove the body when https://github.com/quarkusio/quarkus/issues/16897 is fixed
                .body(Json.encode(new BehaviorGroup()))
                .when()
                .put("/notifications/behaviorGroups/{id}")
                .then()
                .statusCode(403)
                .contentType(JSON);

        given()
                .header(readAccessIdentityHeader)
                .pathParam("id", UUID.randomUUID())
                .when()
                .delete("/notifications/behaviorGroups/{id}")
                .then()
                .statusCode(403)
                .contentType(JSON);

        given()
                .header(readAccessIdentityHeader)
                .contentType(JSON)
                .pathParam("behaviorGroupId", UUID.randomUUID())
                .body(Json.encode(List.of(UUID.randomUUID())))
                .when()
                .put("/notifications/behaviorGroups/{behaviorGroupId}/actions")
                .then()
                .statusCode(403)
                .contentType(TEXT);

        given()
                .header(noAccessIdentityHeader)
                .pathParam("bundleId", UUID.randomUUID())
                .when()
                .get("/notifications/bundles/{bundleId}/behaviorGroups")
                .then()
                .statusCode(403)
                .contentType(JSON);
    }
}
