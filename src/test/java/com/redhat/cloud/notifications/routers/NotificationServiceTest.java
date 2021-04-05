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
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.routers.models.Facet;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
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

        assertEquals(100, eventTypes.size());
    }

    @Test
    void testGetEventTypesAffectedByEndpoint() {
        UUID bundleId = helpers.createTestAppAndEventTypes();
        String tenant = "testGetEventTypesAffectedByEndpoint";
        Header identityHeader = initRbacMock(tenant, "user", RbacAccess.FULL_ACCESS);

        UUID behaviorGroupId1 = helpers.createBehaviorGroup(tenant, "behavior-group-1", bundleId);
        UUID behaviorGroupId2 = helpers.createBehaviorGroup(tenant, "behavior-group-2", bundleId);
        UUID applicationId = this.helpers.getApplications(ResourceHelpers.TEST_BUNDLE_NAME).stream().filter(a -> a.getName().equals(ResourceHelpers.TEST_APP_NAME_2)).findFirst().get().getId();
        UUID ep1 = this.helpers.createWebhookEndpoint(tenant);
        UUID ep2 = this.helpers.createWebhookEndpoint(tenant);
        List<EventType> eventTypesFromApp1 = this.helpers.getEventTypesForApplication(applicationId);
        EventType ev0 = eventTypesFromApp1.get(0);
        EventType ev1 = eventTypesFromApp1.get(1);

        // ep1 assigned to ev0; ep2 not assigned.
        helpers.linkBehaviorGroupToEventType(tenant, ev0.getId(), behaviorGroupId1);
        helpers.addBehaviorGroupAction(tenant, behaviorGroupId1, ep1);
        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep1.toString())
                .then()
                .statusCode(200)
                .extract().response();

        JsonArray eventTypes = new JsonArray(response.getBody().asString());
        assertEquals(1, eventTypes.size());
        eventTypes.getJsonObject(0).mapTo(EventType.class);
        assertEquals(ev0.getId().toString(), eventTypes.getJsonObject(0).getString("id"));

        response = given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep2.toString())
                .then()
                .statusCode(200)
                .extract().response();

        eventTypes = new JsonArray(response.getBody().asString());
        assertEquals(0, eventTypes.size());

        // ep1 assigned to event ev0; ep2 assigned to event ev1
        helpers.linkBehaviorGroupToEventType(tenant, ev1.getId(), behaviorGroupId2);
        helpers.addBehaviorGroupAction(tenant, behaviorGroupId2, ep2);
        response = given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep1.toString())
                .then()
                .statusCode(200)
                .extract().response();

        eventTypes = new JsonArray(response.getBody().asString());
        assertEquals(1, eventTypes.size());
        eventTypes.getJsonObject(0).mapTo(EventType.class);
        assertEquals(ev0.getId().toString(), eventTypes.getJsonObject(0).getString("id"));

        response = given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/" + ep2.toString())
                .then()
                .statusCode(200)
                .extract().response();

        eventTypes = new JsonArray(response.getBody().asString());
        assertEquals(1, eventTypes.size());
        eventTypes.getJsonObject(0).mapTo(EventType.class);
        assertEquals(ev1.getId().toString(), eventTypes.getJsonObject(0).getString("id"));
    }

    @Test
    void testGetApplicationFacets() {
        Header identityHeader = initRbacMock("test", "user", RbacAccess.READ_ACCESS);
        List<Facet> applications = given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .get("/notifications/facets/applications?bundleName=rhel")
                .then()
                .statusCode(200).extract().response().jsonPath().getList(".", Facet.class);

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
                .contentType(ContentType.JSON)
                .get("/notifications/facets/bundles")
                .then()
                .statusCode(200).extract().response().jsonPath().getList(".", Facet.class);

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
                .statusCode(403);

        given()
                .header(noAccessIdentityHeader)
                .pathParam("endpointId", UUID.randomUUID())
                .when()
                .get("/notifications/eventTypes/affectedByRemovalOfEndpoint/{endpointId}")
                .then()
                .statusCode(403);

        given()
                .header(noAccessIdentityHeader)
                .pathParam("behaviorGroupId", UUID.randomUUID())
                .when()
                .get("/notifications/eventTypes/affectedByRemovalOfBehaviorGroup/{behaviorGroupId}")
                .then()
                .statusCode(403);

        given()
                .header(readAccessIdentityHeader)
                .pathParam("eventTypeId", UUID.randomUUID())
                .pathParam("behaviorGroupId", UUID.randomUUID())
                .when()
                .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups/{behaviorGroupId}")
                .then()
                .statusCode(403);

        given()
                .header(readAccessIdentityHeader)
                .pathParam("eventTypeId", UUID.randomUUID())
                .pathParam("behaviorGroupId", UUID.randomUUID())
                .when()
                .delete("/notifications/eventTypes/{eventTypeId}/behaviorGroups/{behaviorGroupId}")
                .then()
                .statusCode(403);

        given()
                .header(noAccessIdentityHeader)
                .pathParam("eventTypeId", UUID.randomUUID())
                .when()
                .get("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
                .then()
                .statusCode(403);

        given()
                .header(readAccessIdentityHeader)
                .pathParam("eventTypeId", UUID.randomUUID())
                .when()
                .delete("/notifications/eventTypes/{eventTypeId}/mute")
                .then()
                .statusCode(403);

        given()
                .header(readAccessIdentityHeader)
                .contentType(ContentType.JSON)
                .when()
                .post("/notifications/behaviorGroups")
                .then()
                .statusCode(403);

        given()
                .header(readAccessIdentityHeader)
                .contentType(ContentType.JSON)
                .pathParam("id", UUID.randomUUID())
                .when()
                .put("/notifications/behaviorGroups/{id}")
                .then()
                .statusCode(403);

        given()
                .header(readAccessIdentityHeader)
                .pathParam("id", UUID.randomUUID())
                .when()
                .delete("/notifications/behaviorGroups/{id}")
                .then()
                .statusCode(403);

        given()
                .header(readAccessIdentityHeader)
                .contentType(ContentType.JSON)
                .pathParam("behaviorGroupId", UUID.randomUUID())
                .pathParam("endpointId", UUID.randomUUID())
                .when()
                .put("/notifications/behaviorGroups/{behaviorGroupId}/actions/{endpointId}")
                .then()
                .statusCode(403);

        given()
                .header(readAccessIdentityHeader)
                .pathParam("behaviorGroupId", UUID.randomUUID())
                .pathParam("endpointId", UUID.randomUUID())
                .when()
                .delete("/notifications/behaviorGroups/{behaviorGroupId}/actions/{endpointId}")
                .then()
                .statusCode(403);

        given()
                .header(noAccessIdentityHeader)
                .pathParam("bundleId", UUID.randomUUID())
                .when()
                .get("/notifications/bundles/{bundleId}/behaviorGroups")
                .then()
                .statusCode(403);
    }
}
