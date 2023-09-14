package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.MockServerConfig.RbacAccess;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BehaviorGroupRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.routers.models.Facet;
import com.redhat.cloud.notifications.routers.models.behaviorgroup.CreateBehaviorGroupRequest;
import com.redhat.cloud.notifications.routers.models.behaviorgroup.CreateBehaviorGroupResponse;
import com.redhat.cloud.notifications.routers.models.behaviorgroup.UpdateBehaviorGroupRequest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.FULL_ACCESS;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.NO_ACCESS;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.READ_ACCESS;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.db.ResourceHelpers.TEST_APP_NAME;
import static com.redhat.cloud.notifications.db.ResourceHelpers.TEST_APP_NAME_2;
import static com.redhat.cloud.notifications.db.ResourceHelpers.TEST_BUNDLE_2_NAME;
import static com.redhat.cloud.notifications.db.ResourceHelpers.TEST_BUNDLE_NAME;
import static com.redhat.cloud.notifications.db.ResourceHelpers.TEST_EVENT_TYPE_FORMAT;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class NotificationResourceTest extends DbIsolatedTest {

    /*
     * In the tests below, most JSON responses are verified using JsonObject/JsonArray instead of deserializing these
     * responses into model instances and checking their attributes values. That's because the model classes contain
     * attributes annotated with @JsonProperty(access = READ_ONLY) which can't be deserialized and therefore verified
     * here. The deserialization is still performed only to verify that the JSON responses data structure is correct.
     */

    private static final String ACCOUNT_ID = "NotificationServiceTest";
    private static final String ORG_ID = "NotificationServiceTestOrgId";
    private static final String USERNAME = "user";

    @Inject
    ResourceHelpers helpers;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

    @Inject
    FeatureFlipper featureFlipper;

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_1_0;
        MockServerConfig.clearRbac();
        featureFlipper.setEnforceBehaviorGroupNameUnicity(true);
    }

    @AfterEach
    void afterEach() {
        featureFlipper.setEnforceBehaviorGroupNameUnicity(false);
    }

    private Header initRbacMock(String accountId, String orgId, String username, RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, username);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createRHIdentityHeader(identityHeaderValue);
    }

    @Test
    void testEventTypeFetching() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(ACCOUNT_ID, ORG_ID, USERNAME, RbacAccess.FULL_ACCESS);

        // no offset
        Response response = given()
                .when()
                .header(identityHeader)
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject page = new JsonObject(response.getBody().asString());
        JsonArray eventTypes = page.getJsonArray("data");
        assertEquals(20, eventTypes.size());
        assertEquals(201, page.getJsonObject("meta").getNumber("count")); // One of the event types is part of the default DB records.

        JsonObject policiesAll = eventTypes.getJsonObject(0);
        policiesAll.mapTo(EventType.class);
        assertNotNull(policiesAll.getString("id"));
        assertNotNull(policiesAll.getJsonObject("application"));
        assertNotNull(policiesAll.getJsonObject("application").getString("id"));

        // offset = 200
        response = given()
                .when()
                .header(identityHeader)
                .queryParam("offset", "200")
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        page = new JsonObject(response.getBody().asString());
        eventTypes = page.getJsonArray("data");
        assertEquals(1, eventTypes.size()); // only one element past 200
        assertEquals(201, page.getJsonObject("meta").getNumber("count")); // One of the event types is part of the default DB records.

        // different limit
        response = given()
                .when()
                .header(identityHeader)
                .get("/notifications/eventTypes?limit=100")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        page = new JsonObject(response.getBody().asString());
        eventTypes = page.getJsonArray("data");
        assertEquals(100, eventTypes.size());
        assertEquals(201, page.getJsonObject("meta").getNumber("count")); // One of the event types is part of the default DB records.

        // different limit and offset
        response = given()
                .when()
                .header(identityHeader)
                .get("/notifications/eventTypes?limit=100&offset=150")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        page = new JsonObject(response.getBody().asString());
        eventTypes = page.getJsonArray("data");
        assertEquals(51, eventTypes.size()); // 51 elements past 150
        assertEquals(201, page.getJsonObject("meta").getNumber("count")); // One of the event types is part of the default DB records.
    }

    @Test
    void testEventTypeFetchingByApplication() {
        helpers.createTestAppAndEventTypes();
        List<Application> apps = applicationRepository.getApplications(TEST_BUNDLE_NAME);
        UUID myOtherTesterApplicationId = apps.stream().filter(a -> a.getName().equals(TEST_APP_NAME_2)).findFirst().get().getId();
        Header identityHeader = initRbacMock(ACCOUNT_ID, ORG_ID, USERNAME, RbacAccess.FULL_ACCESS);

        Response response = given()
                .when()
                .header(identityHeader)
                .queryParam("applicationIds", myOtherTesterApplicationId)
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject page = new JsonObject(response.getBody().asString());
        JsonArray eventTypes = page.getJsonArray("data");
        for (int i = 0; i < eventTypes.size(); i++) {
            JsonObject ev = eventTypes.getJsonObject(i);
            ev.mapTo(EventType.class);
            assertEquals(myOtherTesterApplicationId.toString(), ev.getJsonObject("application").getString("id"));
        }

        assertEquals(100, page.getJsonObject("meta").getInteger("count"));
        assertEquals(20, eventTypes.size());
    }

    @Test
    void testEventTypeFetchingByBundle() {
        helpers.createTestAppAndEventTypes();
        List<Application> apps = applicationRepository.getApplications(TEST_BUNDLE_NAME);
        UUID myBundleId = apps.stream().filter(a -> a.getName().equals(TEST_APP_NAME_2)).findFirst().get().getBundleId();

        Header identityHeader = initRbacMock(ACCOUNT_ID, ORG_ID, USERNAME, RbacAccess.FULL_ACCESS);

        Response response = given()
                .when()
                .header(identityHeader)
                .queryParam("bundleId", myBundleId)
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject page = new JsonObject(response.getBody().asString());
        JsonArray eventTypes = page.getJsonArray("data");
        for (int i = 0; i < eventTypes.size(); i++) {
            JsonObject ev = eventTypes.getJsonObject(i);
            ev.mapTo(EventType.class);
            assertEquals(myBundleId.toString(), ev.getJsonObject("application").getString("bundle_id"));
        }

        assertEquals(200, page.getJsonObject("meta").getInteger("count"));
        assertEquals(20, eventTypes.size());
    }

    @Test
    void testEventTypeFetchingByBundleAndApplicationId() {
        helpers.createTestAppAndEventTypes();
        List<Application> apps = applicationRepository.getApplications(TEST_BUNDLE_NAME);
        Application app = apps.stream().filter(a -> a.getName().equals(TEST_APP_NAME_2)).findFirst().get();
        UUID myOtherTesterApplicationId = app.getId();
        UUID myBundleId = app.getBundleId();
        Header identityHeader = initRbacMock(ACCOUNT_ID, ORG_ID, USERNAME, RbacAccess.FULL_ACCESS);

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

        JsonObject page = new JsonObject(response.getBody().asString());
        JsonArray eventTypes = page.getJsonArray("data");
        for (int i = 0; i < eventTypes.size(); i++) {
            JsonObject ev = eventTypes.getJsonObject(i);
            ev.mapTo(EventType.class);
            assertEquals(myBundleId.toString(), ev.getJsonObject("application").getString("bundle_id"));
            assertEquals(myOtherTesterApplicationId.toString(), ev.getJsonObject("application").getString("id"));
        }

        assertEquals(100, page.getJsonObject("meta").getInteger("count"));
        assertEquals(20, eventTypes.size());
    }

    @Test
    void testEventTypeFetchingByEventTypeName() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(ACCOUNT_ID, ORG_ID, USERNAME, RbacAccess.FULL_ACCESS);

        Response response = given()
                .when()
                .header(identityHeader)
                .queryParam("eventTypeName", "50")
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject page = new JsonObject(response.getBody().asString());
        JsonArray eventTypes = page.getJsonArray("data");
        for (int i = 0; i < eventTypes.size(); i++) {
            JsonObject ev = eventTypes.getJsonObject(i);
            ev.mapTo(EventType.class);
            assertTrue(ev.getString("display_name").contains("50") || ev.getString("name").contains("50"));
        }

        assertEquals(2, page.getJsonObject("meta").getInteger("count"));
        assertEquals(2, eventTypes.size());
    }

    @Test
    void testEventTypeFetchingByBundleApplicationAndEventTypeName() {
        helpers.createTestAppAndEventTypes();
        List<Application> apps = applicationRepository.getApplications(TEST_BUNDLE_NAME);
        Application app = apps.stream().filter(a -> a.getName().equals(TEST_APP_NAME_2)).findFirst().get();
        UUID myOtherTesterApplicationId = app.getId();
        UUID myBundleId = app.getBundleId();
        Header identityHeader = initRbacMock(ACCOUNT_ID, ORG_ID, USERNAME, RbacAccess.FULL_ACCESS);

        Response response = given()
                .when()
                .header(identityHeader)
                .queryParam("bundleId", myBundleId)
                .queryParam("applicationIds", myOtherTesterApplicationId)
                .queryParam("eventTypeName", "50")
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject page = new JsonObject(response.getBody().asString());
        JsonArray eventTypes = page.getJsonArray("data");
        for (int i = 0; i < eventTypes.size(); i++) {
            JsonObject ev = eventTypes.getJsonObject(i);
            ev.mapTo(EventType.class);
            assertEquals(myBundleId.toString(), ev.getJsonObject("application").getString("bundle_id"));
            assertEquals(myOtherTesterApplicationId.toString(), ev.getJsonObject("application").getString("id"));
            assertTrue(ev.getString("display_name").contains("50") || ev.getString("name").contains("50"));
        }

        assertEquals(1, page.getJsonObject("meta").getInteger("count"));
        assertEquals(1, eventTypes.size());
    }

    @Test
    void testGetEventTypesAffectedByEndpoint() {
        String accountId = "testGetEventTypesAffectedByEndpoint";
        String orgId = "testGetEventTypesAffectedByEndpointOrgId";
        Header identityHeader = initRbacMock(accountId, orgId, "user", FULL_ACCESS);
        UUID bundleId = helpers.createTestAppAndEventTypes();
        UUID behaviorGroupId1 = helpers.createBehaviorGroup(accountId, orgId, "behavior-group-1", bundleId).getId();
        UUID behaviorGroupId2 = helpers.createBehaviorGroup(accountId, orgId, "behavior-group-2", bundleId).getId();
        UUID appId = applicationRepository.getApplications(TEST_BUNDLE_NAME).stream()
                .filter(a -> a.getName().equals(TEST_APP_NAME_2))
                .findFirst().get().getId();
        UUID endpointId1 = helpers.createWebhookEndpoint(accountId, orgId);
        UUID endpointId2 = helpers.createWebhookEndpoint(accountId, orgId);
        List<EventType> eventTypes = applicationRepository.getEventTypes(appId);
        // ep1 assigned to ev0; ep2 not assigned.
        behaviorGroupRepository.updateEventTypeBehaviors(orgId, eventTypes.get(0).getId(), Set.of(behaviorGroupId1));
        behaviorGroupRepository.updateBehaviorGroupActions(orgId, behaviorGroupId1, List.of(endpointId1));

        String responseBody = given()
                .header(identityHeader)
                .pathParam("endpointId", endpointId1.toString())
                .when()
                .get("/notifications/behaviorGroups/affectedByRemovalOfEndpoint/{endpointId}")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().asString();

        JsonArray behaviorGroups = new JsonArray(responseBody);
        assertEquals(1, behaviorGroups.size());
        behaviorGroups.getJsonObject(0).mapTo(BehaviorGroup.class);
        assertEquals(behaviorGroupId1.toString(), behaviorGroups.getJsonObject(0).getString("id"));

        responseBody = given()
                .header(identityHeader)
                .pathParam("endpointId", endpointId2.toString())
                .when()
                .get("/notifications/behaviorGroups/affectedByRemovalOfEndpoint/{endpointId}")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().asString();

        behaviorGroups = new JsonArray(responseBody);
        assertEquals(0, behaviorGroups.size());

        // ep1 assigned to event ev0; ep2 assigned to event ev1
        behaviorGroupRepository.updateEventTypeBehaviors(orgId, eventTypes.get(0).getId(), Set.of(behaviorGroupId2));
        behaviorGroupRepository.updateBehaviorGroupActions(orgId, behaviorGroupId2, List.of(endpointId2));

        responseBody = given()
                .header(identityHeader)
                .pathParam("endpointId", endpointId1.toString())
                .when()
                .get("/notifications/behaviorGroups/affectedByRemovalOfEndpoint/{endpointId}")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().asString();

        behaviorGroups = new JsonArray(responseBody);
        assertEquals(1, behaviorGroups.size());
        behaviorGroups.getJsonObject(0).mapTo(BehaviorGroup.class);
        assertEquals(behaviorGroupId1.toString(), behaviorGroups.getJsonObject(0).getString("id"));

        responseBody = given()
                .header(identityHeader)
                .pathParam("endpointId", endpointId2.toString())
                .when()
                .get("/notifications/behaviorGroups/affectedByRemovalOfEndpoint/{endpointId}")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().asString();

        behaviorGroups = new JsonArray(responseBody);
        assertEquals(1, behaviorGroups.size());
        behaviorGroups.getJsonObject(0).mapTo(BehaviorGroup.class);
        assertEquals(behaviorGroupId2.toString(), behaviorGroups.getJsonObject(0).getString("id"));
    }

    @Test
    void testGetApplicationFacets() {
        Header identityHeader = initRbacMock("test", "test2", "user", READ_ACCESS);
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

        // Without bundle returns all applications across bundles
        applications = given()
                .header(identityHeader)
                .when()
                .get("/notifications/facets/applications")
                .then()
                .statusCode(200).contentType(JSON).extract().response().jsonPath().getList(".", Facet.class);

        assertTrue(applications.size() > 0);
        policies = applications.stream().filter(facet -> facet.getName().equals("policies")).findFirst();
        assertTrue(policies.isPresent());
        assertEquals("Policies", policies.get().getDisplayName());
    }

    @Test
    void testGetBundlesFacets() {
        // no children by default
        Header identityHeader = initRbacMock("test", "test2", "user", READ_ACCESS);
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
        assertNull(rhel.get().getChildren());

        // with children
        bundles = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .queryParam("includeApplications", "true")
                .get("/notifications/facets/bundles")
                .then()
                .statusCode(200).contentType(JSON).extract().response().jsonPath().getList(".", Facet.class);

        assertTrue(bundles.size() > 0);
        rhel = bundles.stream().filter(facet -> facet.getName().equals("rhel")).findFirst();
        assertTrue(rhel.isPresent());
        assertEquals("Red Hat Enterprise Linux", rhel.get().getDisplayName());
        assertNotNull(rhel.get().getChildren());

        Optional<Facet> policies = rhel.get().getChildren().stream().filter(facet -> facet.getName().equals("policies")).findFirst();
        assertTrue(policies.isPresent());
        assertEquals("Policies", policies.get().getDisplayName());
    }

    @Test
    void testCreateFullBehaviorGroup() {
        String accountId = "tenant-bg-create-full";
        String orgId = "org-bg-create-full";

        Header identityHeader = initRbacMock(accountId, orgId, "user", FULL_ACCESS);

        helpers.createTestAppAndEventTypes();
        List<Application> apps = applicationRepository.getApplications(TEST_BUNDLE_NAME);
        UUID myBundleId = apps.stream().findFirst().get().getBundleId();

        List<UUID> endpoints = Stream.of(
                helpers.createEndpoint(accountId, orgId, EndpointType.EMAIL_SUBSCRIPTION),
                helpers.createEndpoint(accountId, orgId, EndpointType.EMAIL_SUBSCRIPTION),
                helpers.createEndpoint(accountId, orgId, EndpointType.DRAWER),
                helpers.createEndpoint(accountId, orgId, EndpointType.DRAWER),
                helpers.createEndpoint(accountId, orgId, EndpointType.CAMEL),
                helpers.createEndpoint(accountId, orgId, EndpointType.CAMEL)
        ).map(Endpoint::getId).collect(Collectors.toList());
        Set<UUID> eventTypes = apps.stream().findFirst().get().getEventTypes().stream().map(EventType::getId).collect(Collectors.toSet());

        CreateBehaviorGroupRequest behaviorGroupRequest = new CreateBehaviorGroupRequest();
        behaviorGroupRequest.bundleId = myBundleId;
        behaviorGroupRequest.displayName = "My behavior group 1";

        CreateBehaviorGroupResponse response = createBehaviorGroup(identityHeader, behaviorGroupRequest);

        assertEquals("My behavior group 1", response.displayName);
        assertEquals(myBundleId, response.bundleId);
        assertEquals(List.of(), response.endpoints);
        assertEquals(Set.of(), response.eventTypes);

        // Create with only endpoints
        behaviorGroupRequest.displayName = "My behavior group 2";
        behaviorGroupRequest.endpointIds = endpoints;

        response = createBehaviorGroup(identityHeader, behaviorGroupRequest);

        assertEquals("My behavior group 2", response.displayName);
        assertEquals(myBundleId, response.bundleId);
        assertEquals(endpoints, response.endpoints);
        assertEquals(Set.of(), response.eventTypes);

        // Create with only event types
        behaviorGroupRequest.displayName = "My behavior group 3";
        behaviorGroupRequest.eventTypeIds = eventTypes;
        behaviorGroupRequest.endpointIds = null;

        response = createBehaviorGroup(identityHeader, behaviorGroupRequest);

        assertEquals("My behavior group 3", response.displayName);
        assertEquals(myBundleId, response.bundleId);
        assertEquals(List.of(), response.endpoints);
        assertEquals(eventTypes, response.eventTypes);

        // Create with both
        behaviorGroupRequest.displayName = "My behavior group 4";
        behaviorGroupRequest.endpointIds = endpoints;
        behaviorGroupRequest.eventTypeIds = eventTypes;

        response = createBehaviorGroup(identityHeader, behaviorGroupRequest);

        assertEquals("My behavior group 4", response.displayName);
        assertEquals(myBundleId, response.bundleId);
        assertEquals(endpoints, response.endpoints);
        assertEquals(eventTypes, response.eventTypes);
    }

    @Test
    void testUpdateFullBehaviorGroup() {
        String accountId = "tenant-bg-update-full";
        String orgId = "orgId-bg-update-full";
        Header identityHeader = initRbacMock(accountId, orgId, "user", FULL_ACCESS);

        helpers.createTestAppAndEventTypes();
        List<Application> apps = applicationRepository.getApplications(TEST_BUNDLE_NAME);
        UUID myBundleId = apps.stream().findFirst().get().getBundleId();

        UUID behaviorGroupId = helpers.createBehaviorGroup(accountId, orgId, "My behavior group 1", myBundleId).getId();
        UUID behaviorGroupIdOtherTenant = helpers.createBehaviorGroup(accountId + "-other", orgId + "-other", "My behavior", myBundleId).getId();

        List<UUID> endpoints = Stream.of(
                helpers.createEndpoint(accountId, orgId, EndpointType.EMAIL_SUBSCRIPTION),
                helpers.createEndpoint(accountId, orgId, EndpointType.EMAIL_SUBSCRIPTION),
                helpers.createEndpoint(accountId, orgId, EndpointType.DRAWER),
                helpers.createEndpoint(accountId, orgId, EndpointType.DRAWER),
                helpers.createEndpoint(accountId, orgId, EndpointType.CAMEL),
                helpers.createEndpoint(accountId, orgId, EndpointType.CAMEL)
        ).map(Endpoint::getId).collect(Collectors.toList());

        Set<UUID> eventTypes = apps.stream().findFirst().get().getEventTypes().stream().map(EventType::getId).collect(Collectors.toSet());

        // Updating a behavior of other tenant yields 404 - i.e. can't find the behavior group
        UpdateBehaviorGroupRequest behaviorGroupRequest = new UpdateBehaviorGroupRequest();
        behaviorGroupRequest.displayName = "My behavior group 1.0";
        updateBehaviorGroup(identityHeader, behaviorGroupIdOtherTenant, behaviorGroupRequest, 404);
        BehaviorGroup behaviorGroup = helpers.getBehaviorGroup(behaviorGroupIdOtherTenant);
        assertEquals("My behavior", behaviorGroup.getDisplayName()); // No change

        // Updating the behavior group displayName only
        updateBehaviorGroup(identityHeader, behaviorGroupId, behaviorGroupRequest, 200);
        behaviorGroup = helpers.getBehaviorGroup(behaviorGroupId);
        assertEquals("My behavior group 1.0", behaviorGroup.getDisplayName());

        // Updating with all null is effectively a no-op
        behaviorGroupRequest.displayName = null;
        updateBehaviorGroup(identityHeader, behaviorGroupId, behaviorGroupRequest, 200);
        behaviorGroup = helpers.getBehaviorGroup(behaviorGroupId);
        assertEquals("My behavior group 1.0", behaviorGroup.getDisplayName());

        // Updating only endpoints
        behaviorGroupRequest.displayName = "My behavior group 2.0";
        behaviorGroupRequest.endpointIds = endpoints;
        behaviorGroupRequest.eventTypeIds = null;
        updateBehaviorGroup(identityHeader, behaviorGroupId, behaviorGroupRequest, 200);
        behaviorGroup = helpers.getBehaviorGroup(behaviorGroupId);
        assertEquals("My behavior group 2.0", behaviorGroup.getDisplayName());
        assertEquals(endpoints, getEndpointsIds(orgId, behaviorGroupId));
        assertEquals(Set.of(), getEventTypeIds(orgId, behaviorGroupId));

        // Updating only event types (endpoints remain the same)
        behaviorGroupRequest.displayName = "My behavior group 3.0";
        behaviorGroupRequest.endpointIds = null;
        behaviorGroupRequest.eventTypeIds = eventTypes;
        updateBehaviorGroup(identityHeader, behaviorGroupId, behaviorGroupRequest, 200);
        behaviorGroup = helpers.getBehaviorGroup(behaviorGroupId);
        assertEquals("My behavior group 3.0", behaviorGroup.getDisplayName());
        assertEquals(endpoints, getEndpointsIds(orgId, behaviorGroupId));
        assertEquals(eventTypes, getEventTypeIds(orgId, behaviorGroupId));

        // Updating both to empty
        behaviorGroupRequest.displayName = "My behavior group 4.0";
        behaviorGroupRequest.endpointIds = List.of();
        behaviorGroupRequest.eventTypeIds = Set.of();
        updateBehaviorGroup(identityHeader, behaviorGroupId, behaviorGroupRequest, 200);
        behaviorGroup = helpers.getBehaviorGroup(behaviorGroupId);
        assertEquals("My behavior group 4.0", behaviorGroup.getDisplayName());
        assertEquals(List.of(), getEndpointsIds(orgId, behaviorGroupId));
        assertEquals(Set.of(), getEventTypeIds(orgId, behaviorGroupId));
    }

    @Test
    void testBundleApplicationEventTypeByName() {
        String accountId = "tenant-bundle-app-eventtype";
        String orgId = "orgId-bundle-app-eventtype";
        Header identityHeader = initRbacMock(accountId, orgId, "user", FULL_ACCESS);
        helpers.createTestAppAndEventTypes();

        String eventTypeName = String.format(TEST_EVENT_TYPE_FORMAT, 1);

        Bundle bundle = given()
                .header(identityHeader)
                .when()
                .given()
                .pathParam("bundleName", TEST_BUNDLE_NAME)
                .get("/notifications/bundles/{bundleName}")
                .then()
                .statusCode(200)
                .extract()
                .as(Bundle.class);
        assertEquals(bundle.getName(), TEST_BUNDLE_NAME);

        Application application = given()
                .header(identityHeader)
                .pathParam("bundleName", TEST_BUNDLE_NAME)
                .pathParam("applicationName", TEST_APP_NAME)
                .when()
                .given()
                .get("/notifications/bundles/{bundleName}/applications/{applicationName}")
                .then()
                .statusCode(200)
                .extract()
                .as(Application.class);
        assertEquals(application.getName(), TEST_APP_NAME);

        EventType eventType = given()
                .header(identityHeader)
                .pathParam("bundleName", TEST_BUNDLE_NAME)
                .pathParam("applicationName", TEST_APP_NAME)
                .pathParam("eventTypeName", eventTypeName)
                .when()
                .given()
                .get("/notifications/bundles/{bundleName}/applications/{applicationName}/eventTypes/{eventTypeName}")
                .then()
                .statusCode(200)
                .extract()
                .as(EventType.class);
        assertEquals(eventType.getName(), eventTypeName);

        // Not found cases
        given()
                .header(identityHeader)
                .pathParam("bundleName", "bla bla")
                .when()
                .given()
                .get("/notifications/bundles/{bundleName}")
                .then()
                .statusCode(404);

        given()
                .header(identityHeader)
                .pathParam("bundleName", TEST_BUNDLE_NAME)
                .pathParam("applicationName", "bla bla")
                .when()
                .given()
                .get("/notifications/bundles/{bundleName}/applications/{applicationName}")
                .then()
                .statusCode(404);

        given()
                .header(identityHeader)
                .pathParam("bundleName", "bla bla")
                .pathParam("applicationName", TEST_APP_NAME)
                .when()
                .given()
                .get("/notifications/bundles/{bundleName}/applications/{applicationName}")
                .then()
                .statusCode(404);

        given()
                .header(identityHeader)
                .pathParam("bundleName", "bla bla")
                .pathParam("applicationName", TEST_APP_NAME)
                .pathParam("eventTypeName", eventTypeName)
                .when()
                .given()
                .get("/notifications/bundles/{bundleName}/applications/{applicationName}/eventTypes/{eventTypeName}")
                .then()
                .statusCode(404);

        given()
                .header(identityHeader)
                .pathParam("bundleName", TEST_BUNDLE_NAME)
                .pathParam("applicationName", "bla bla")
                .pathParam("eventTypeName", eventTypeName)
                .when()
                .given()
                .get("/notifications/bundles/{bundleName}/applications/{applicationName}/eventTypes/{eventTypeName}")
                .then()
                .statusCode(404);

        given()
                .header(identityHeader)
                .pathParam("bundleName", TEST_BUNDLE_NAME)
                .pathParam("applicationName", TEST_APP_NAME)
                .pathParam("eventTypeName", "blabla")
                .when()
                .given()
                .get("/notifications/bundles/{bundleName}/applications/{applicationName}/eventTypes/{eventTypeName}")
                .then()
                .statusCode(404);
    }

    @Test
    void testInsufficientPrivileges() {
        Header noAccessIdentityHeader = initRbacMock("tenant", "orgId", "noAccess", NO_ACCESS);
        Header readAccessIdentityHeader = initRbacMock("tenant", "orgId", "readAccess", READ_ACCESS);

        given()
                .header(noAccessIdentityHeader)
                .when()
                .get("/notifications/eventTypes")
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
                .header(noAccessIdentityHeader)
                .pathParam("endpointId", UUID.randomUUID())
                .when()
                .get("/notifications/behaviorGroups/affectedByRemovalOfEndpoint/{endpointId}")
                .then()
                .statusCode(403);

        given()
                .header(readAccessIdentityHeader)
                .contentType(JSON)
                .pathParam("eventTypeId", UUID.randomUUID())
                .body(Json.encode(List.of(UUID.randomUUID())))
                .when()
                .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
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
                .contentType(JSON)
                .when()
                .post("/notifications/behaviorGroups")
                .then()
                .statusCode(403);

        given()
                .header(readAccessIdentityHeader)
                .contentType(JSON)
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
                .contentType(JSON)
                .pathParam("behaviorGroupId", UUID.randomUUID())
                .body(Json.encode(List.of(UUID.randomUUID())))
                .when()
                .put("/notifications/behaviorGroups/{behaviorGroupId}/actions")
                .then()
                .statusCode(403);

        given()
                .header(noAccessIdentityHeader)
                .pathParam("bundleId", UUID.randomUUID())
                .when()
                .get("/notifications/bundles/{bundleId}/behaviorGroups")
                .then()
                .statusCode(403);

        given()
                .header(noAccessIdentityHeader)
                .pathParam("bundleName", "myBundle")
                .when()
                .get("/notifications/bundles/{bundleName}")
                .then()
                .statusCode(403);

        given()
                .header(noAccessIdentityHeader)
                .pathParam("bundleName", "myBundle")
                .pathParam("applicationName", "myApp")
                .when()
                .get("/notifications/bundles/{bundleName}/applications/{applicationName}")
                .then()
                .statusCode(403);

        given()
                .header(noAccessIdentityHeader)
                .pathParam("bundleName", "myBundle")
                .pathParam("applicationName", "myApp")
                .pathParam("eventTypeName", "myEventType")
                .when()
                .get("/notifications/bundles/{bundleName}/applications/{applicationName}/eventTypes/{eventTypeName}")
                .then()
                .statusCode(403);
    }

    @Test
    void testUpdateUnknownBehaviorGroupId() {
        Header identityHeader = initRbacMock("tenant", "orgId", "user", FULL_ACCESS);

        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setDisplayName("Behavior group");
        behaviorGroup.setBundleId(UUID.randomUUID()); // Only used for @NotNull validation.

        given()
                .header(identityHeader)
                .contentType(JSON)
                .pathParam("id", UUID.randomUUID())
                .body(Json.encode(behaviorGroup))
                .when()
                .put("/notifications/behaviorGroups/{id}")
                .then()
                .statusCode(404);
    }

    @Test
    void testDeleteUnknownBehaviorGroupId() {
        Header identityHeader = initRbacMock("tenant", "orgId", "user", FULL_ACCESS);
        given()
                .header(identityHeader)
                .pathParam("id", UUID.randomUUID())
                .when()
                .delete("/notifications/behaviorGroups/{id}")
                .then()
                .statusCode(404);
    }

    @Test
    void testFindBehaviorGroupsByUnknownBundleId() {
        Header identityHeader = initRbacMock("tenant", "orgId", "user", FULL_ACCESS);
        given()
                .header(identityHeader)
                .pathParam("bundleId", UUID.randomUUID())
                .when()
                .get("/notifications/bundles/{bundleId}/behaviorGroups")
                .then()
                .statusCode(404);
    }

    @Test
    void testFindEventTypesAffectedByRemovalOfUnknownBehaviorGroupId() {
        Header identityHeader = initRbacMock("tenant", "orgId", "user", FULL_ACCESS);
        given()
                .header(identityHeader)
                .pathParam("behaviorGroupId", UUID.randomUUID())
                .when()
                .get("/notifications/eventTypes/affectedByRemovalOfBehaviorGroup/{behaviorGroupId}")
                .then()
                .statusCode(404);
    }

    @Test
    void testFindBehaviorGroupsByUnknownEventTypeId() {
        Header identityHeader = initRbacMock("tenant", "orgId", "user", FULL_ACCESS);
        given()
                .header(identityHeader)
                .pathParam("eventTypeId", UUID.randomUUID())
                .when()
                .get("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
                .then()
                .statusCode(404);
    }

    @Test
    void testBehaviorGroupsAffectedByRemovalOfUnknownEndpointId() {
        Header identityHeader = initRbacMock("tenant", "someOrgId", "user", FULL_ACCESS);
        given()
                .header(identityHeader)
                .pathParam("endpointId", UUID.randomUUID())
                .when()
                .get("/notifications/behaviorGroups/affectedByRemovalOfEndpoint/{endpointId}")
                .then()
                .statusCode(404);
    }

    @Test
    void testBehaviorGroupSameName() {
        try {
            final String BEHAVIOR_GROUP_1_NAME = "BehaviorGroup1";
            final String BEHAVIOR_GROUP_2_NAME = "BehaviorGroup2";

            featureFlipper.setEnforceBehaviorGroupNameUnicity(true);
            Header identityHeader = initRbacMock("tenant", "sameBehaviorGroupName", "user", FULL_ACCESS);

            Bundle bundle1 = helpers.createBundle(TEST_BUNDLE_NAME, "Bundle1");
            Bundle bundle2 = helpers.createBundle(TEST_BUNDLE_2_NAME, "Bundle2");

            CreateBehaviorGroupRequest createBehaviorGroupRequest = new CreateBehaviorGroupRequest();
            createBehaviorGroupRequest.displayName = BEHAVIOR_GROUP_1_NAME;
            createBehaviorGroupRequest.bundleId = bundle1.getId();

            UUID behaviorGroup1Id = createBehaviorGroup(identityHeader, createBehaviorGroupRequest, 200).get().id;
            assertNotNull(behaviorGroup1Id);

            // same display name in same bundle is not possible
            createBehaviorGroup(identityHeader, createBehaviorGroupRequest, 400);

            // same display name in a different bundle is OK
            createBehaviorGroupRequest.bundleId = bundle2.getId();
            createBehaviorGroup(identityHeader, createBehaviorGroupRequest, 200);

            // Different display name in bundle1
            createBehaviorGroupRequest.bundleId = bundle1.getId();
            createBehaviorGroupRequest.displayName = BEHAVIOR_GROUP_2_NAME;
            createBehaviorGroup(identityHeader, createBehaviorGroupRequest, 200);

            // Cannot update Behavior Group 1 name to "BehaviorGroup2"  as it already exists
            UpdateBehaviorGroupRequest updateBehaviorGroupRequest = new UpdateBehaviorGroupRequest();
            updateBehaviorGroupRequest.displayName = BEHAVIOR_GROUP_2_NAME;
            updateBehaviorGroup(identityHeader, behaviorGroup1Id, updateBehaviorGroupRequest, 400);

            // Can update other properties without changing the name
            updateBehaviorGroupRequest.displayName = BEHAVIOR_GROUP_1_NAME;
            updateBehaviorGroupRequest.eventTypeIds = Set.of();
            updateBehaviorGroup(identityHeader, behaviorGroup1Id, updateBehaviorGroupRequest, 200);

            // Can use other name
            updateBehaviorGroupRequest.displayName = "OtherName";
            updateBehaviorGroup(identityHeader, behaviorGroup1Id, updateBehaviorGroupRequest, 200);

        } finally {
            featureFlipper.setEnforceBehaviorGroupNameUnicity(false);
        }
    }

    /**
     * Tests that a successful status code is returned when a behavior group is
     * created by using the bundle's name instead of its UUID.
     */
    @Test
    void testBehaviorGroupUsingBundleName() {
        final Bundle bundle = helpers.createBundle(TEST_BUNDLE_NAME, "Bundle-display-name");

        final CreateBehaviorGroupRequest createBehaviorGroupRequest = new CreateBehaviorGroupRequest();
        createBehaviorGroupRequest.displayName = "behavior-group-display-name";
        createBehaviorGroupRequest.bundleName =  bundle.getName();

        final Header identityHeader = initRbacMock("tenant", "sameBehaviorGroupName", "user", FULL_ACCESS);

        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(createBehaviorGroupRequest))
            .post("/notifications/behaviorGroups")
            .then()
            .statusCode(200);
    }

    /**
     * Tests that a bad request response is returned when attempting to create
     * a behavior group without specifying a bundle ID or its name.
     */
    @Test
    void testBadRequestBehaviorGroupInvalidBundle() {
        final CreateBehaviorGroupRequest createBehaviorGroupRequest = new CreateBehaviorGroupRequest();
        createBehaviorGroupRequest.displayName = "behavior-group-display-name";

        final Header identityHeader = initRbacMock("tenant", "sameBehaviorGroupName", "user", FULL_ACCESS);

        final String response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(createBehaviorGroupRequest))
            .post("/notifications/behaviorGroups")
            .then()
            .statusCode(400)
            .extract()
            .body()
            .asString();

        final JsonObject responseJson = new JsonObject(response);
        final JsonArray constraintViolations = responseJson.getJsonArray("violations");

        Assertions.assertNotNull(constraintViolations, "the constraint violations key is not present");
        Assertions.assertEquals(1, constraintViolations.size(), "only one error message was expected, but more were found");

        final JsonObject error = constraintViolations.getJsonObject(0);
        final String errorMessage = error.getString("message");

        Assertions.assertNotNull(errorMessage, "the error message is null");
        Assertions.assertEquals("either the bundle name or the bundle UUID are required", errorMessage, "unexpected error message received");
    }

    /**
     * Tests that a "not found" response is returned from the handler when the
     * bundle ID or its name don't correspond to any existing bundle in the
     * database.
     */
    @Test
    void testNotFoundBehaviorGroupNotExists() {
        final Header identityHeader = initRbacMock("tenant", "sameBehaviorGroupName", "user", FULL_ACCESS);

        final var bgNoBundleId = new CreateBehaviorGroupRequest();
        bgNoBundleId.bundleId = UUID.randomUUID();
        bgNoBundleId.displayName = "test not found behavior group not exists";

        final var bgNoBundleName = new CreateBehaviorGroupRequest();
        bgNoBundleName.bundleName = "test not found bundle name";
        bgNoBundleName.displayName = "test not found behavior group not exists";

        final CreateBehaviorGroupRequest[] bgs = {bgNoBundleId, bgNoBundleName};
        for (final var bg : bgs) {
            final String response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(bg))
                .post("/notifications/behaviorGroups")
                .then()
                .statusCode(404)
                .extract()
                .body()
                .asString();

            // The handler returns an error when fetching the bundle by its
            // name...
            final String handlerError = "the specified bundle was not found in the database";
            // ... but when the user provides the bundle ID, then the
            // persistence layer returns another error.
            final String persistenceLayerError = "bundle_id not found";
            final boolean responseIsWhatWeExpected = response.equals(handlerError) || response.equals(persistenceLayerError);

            Assertions.assertTrue(responseIsWhatWeExpected, String.format(
                "unexpected response. Expecting \"%s\" or \"%s\", got \"%s\"",
                handlerError,
                persistenceLayerError,
                response
            ));
        }
    }

    /**
     * Tests that when creating a behavior group, if the display name exceeds
     * the limit set, then a corresponding error message is returned.
     * @throws NoSuchFieldException if the field in the request class to grab
     * the maximum value for the display name does not exist.
     */
    @Test
    void testBehaviorGroupDisplayNameTooLong() throws NoSuchFieldException {
        final Bundle bundle = this.helpers.createBundle(TEST_BUNDLE_NAME, "Bundle-display-name");

        // Get the value of the "max" property for the "Size" annotation.
        final Field classField = CreateBehaviorGroupRequest.class.getDeclaredField("displayName");
        final Size sizeClassAnnotation = classField.getAnnotation(Size.class);

        final CreateBehaviorGroupRequest createBehaviorGroupRequest = new CreateBehaviorGroupRequest();
        createBehaviorGroupRequest.bundleId = bundle.getId();
        createBehaviorGroupRequest.displayName = "a".repeat(sizeClassAnnotation.max() + 1);

        final Header identityHeader = initRbacMock("behavior-group-display-name-too-long-account-number", "behavior-group-display-name-too-long-org-id", "user", FULL_ACCESS);

        final String response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(createBehaviorGroupRequest))
                .post("/notifications/behaviorGroups")
                .then()
                .statusCode(400)
                .extract()
                .body()
                .asString();

        final JsonObject responseJson = new JsonObject(response);
        final JsonArray constraintViolations = responseJson.getJsonArray("violations");

        Assertions.assertNotNull(constraintViolations, "the constraint violations key is not present");
        Assertions.assertEquals(1, constraintViolations.size(), "only one error message was expected, but more were found");

        final JsonObject error = constraintViolations.getJsonObject(0);
        final String errorMessage = error.getString("message");

        Assertions.assertNotNull(errorMessage, "the error message is null");

        final String expectedError = String.format("the display name cannot exceed %s characters", sizeClassAnnotation.max());
        Assertions.assertEquals(expectedError, errorMessage, "unexpected error message received");
    }

    /**
     * Tests that when updating a behavior group, if the display name exceeds
     * the limit set, then a corresponding error message is returned.
     * @throws NoSuchFieldException if the field in the request class to grab
     * the maximum value for the display name does not exist.
     */
    @Test
    void testUpdateBehaviorGroupDisplayNameTooLong() throws NoSuchFieldException {
        // The tenant's identification elements will be reused below.
        final String accountId = "update-bg-display-name-too-long-account-id";
        final String orgId = "update-bg-display-name-too-long-org-id";

        // Create the fixtures in the database.
        final Bundle bundle = this.helpers.createBundle(TEST_BUNDLE_NAME, "Bundle-display-name");
        final BehaviorGroup behaviorGroup = this.helpers.createBehaviorGroup(accountId, orgId, "valid display name", bundle.getId());

        // Get the value of the "max" property for the "Size" annotation.
        final Field classField = UpdateBehaviorGroupRequest.class.getDeclaredField("displayName");
        final Size sizeClassAnnotation = classField.getAnnotation(Size.class);

        final UpdateBehaviorGroupRequest updateBehaviorGroupRequest = new UpdateBehaviorGroupRequest();
        updateBehaviorGroupRequest.displayName = "a".repeat(sizeClassAnnotation.max() + 1);

        final Header identityHeader = initRbacMock(accountId, orgId, "user", FULL_ACCESS);
        final String url = String.format("/notifications/behaviorGroups/%s", behaviorGroup.getId());

        final String response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(updateBehaviorGroupRequest))
                .put(url)
                .then()
                .statusCode(400)
                .extract()
                .asString();

        final JsonObject responseJson = new JsonObject(response);
        final JsonArray constraintViolations = responseJson.getJsonArray("violations");

        Assertions.assertNotNull(constraintViolations, "the constraint violations key is not present");
        Assertions.assertEquals(1, constraintViolations.size(), "only one error message was expected, but more were found");

        final JsonObject error = constraintViolations.getJsonObject(0);
        final String errorMessage = error.getString("message");

        Assertions.assertNotNull(errorMessage, "the error message is null");

        final String expectedError = String.format("the display name cannot exceed %s characters", sizeClassAnnotation.max());
        Assertions.assertEquals(expectedError, errorMessage, "unexpected error message received");
    }

    /**
     * Tests that when updating a behavior group, if the specified display name
     * is blank, a bad request response is returned.
     */
    @Test
    void testUpdateBehaviorGroupDisplayNameBlank() {
        // The tenant's identification elements will be reused below.
        final String accountId = "update-bg-blank-display-name-account-id";
        final String orgId = "update-bg-blank-display-name-org-id";

        // Create the fixtures in the database.
        final Bundle bundle = this.helpers.createBundle(TEST_BUNDLE_NAME, "Bundle-display-name");
        final BehaviorGroup behaviorGroup = this.helpers.createBehaviorGroup(accountId, orgId, "valid display name", bundle.getId());

        final String[] blankDisplayNames = {"", "     "};
        for (final String blankDisplayName : blankDisplayNames) {

            final UpdateBehaviorGroupRequest updateBehaviorGroupRequest = new UpdateBehaviorGroupRequest();
            updateBehaviorGroupRequest.displayName = blankDisplayName;

            final Header identityHeader = initRbacMock(accountId, orgId, "user", FULL_ACCESS);
            final String url = String.format("/notifications/behaviorGroups/%s", behaviorGroup.getId());

            final String response = given()
                    .header(identityHeader)
                    .when()
                    .contentType(JSON)
                    .body(Json.encode(updateBehaviorGroupRequest))
                    .put(url)
                    .then()
                    .statusCode(400)
                    .extract()
                    .asString();

            final JsonObject responseJson = new JsonObject(response);
            final JsonArray constraintViolations = responseJson.getJsonArray("violations");

            Assertions.assertNotNull(constraintViolations, "the constraint violations key is not present");
            Assertions.assertEquals(1, constraintViolations.size(), "only one error message was expected, but more were found");

            final JsonObject error = constraintViolations.getJsonObject(0);
            final String errorMessage = error.getString("message");

            Assertions.assertNotNull(errorMessage, "the error message is null");
            Assertions.assertEquals("the display name cannot be empty", errorMessage, "unexpected error message received");
        }
    }

    /**
     * Tests that a behavior group can be successfully appended to an event type.
     */
    @Test
    void testAppendBehaviorEventType() {
        final Bundle bundle = this.helpers.createBundle();
        final BehaviorGroup behaviorGroup = this.helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "display-name", bundle.getId());

        final Application application = this.helpers.createApplication(bundle.getId());
        final EventType eventType = this.helpers.createEventType(application.getId(), "name", "display-name", "description");

        final Header identityHeader = initRbacMock("tenant", DEFAULT_ORG_ID, "user", FULL_ACCESS);
        RestAssured.given()
            .header(identityHeader)
            .pathParam("eventTypeUuid", eventType.getId())
            .pathParam("behaviorGroupUuid", behaviorGroup.getId())
            .when()
            .put("/notifications/eventTypes/{eventTypeUuid}/behaviorGroups/{behaviorGroupUuid}")
            .then()
            .statusCode(204);
    }

    /**
     * Tests that a not found response is returned when the behavior group does
     * not exist.
     */
    @Test
    void testAppendBehaviorEventBehaviorGroupNotFound() {
        final Header identityHeader = initRbacMock("tenant", "orgId", "user", FULL_ACCESS);

        final String url = String.format("/notifications/eventTypes/%s/behaviorGroups/%s", UUID.randomUUID(), UUID.randomUUID());

        final String response = RestAssured.given()
            .header(identityHeader)
            .when()
            .put(url)
            .then()
            .statusCode(404)
            .extract()
            .asString();

        Assertions.assertEquals("the specified behavior group doesn't exist or the specified event type doesn't belong to the same bundle as the behavior group", response, "unexpected error message received when specifying a non-existent behavior group");
    }

    /**
     * Tests that a not found response is returned when the behavior group
     * exists, but the tenant that is performing the operation is a different
     * one.
     */
    @Test
    void testAppendBehaviorEventBehaviorGroupWrongTenantNotFound() {
        final Bundle bundle = this.helpers.createBundle();
        final BehaviorGroup behaviorGroup = this.helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "display-name", bundle.getId());

        final Application application = this.helpers.createApplication(bundle.getId());
        final EventType eventType = this.helpers.createEventType(application.getId(), "name", "display-name", "description");

        final Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, "different-tenant-org-id", "user", FULL_ACCESS);

        final String url = String.format("/notifications/eventTypes/%s/behaviorGroups/%s", eventType.getId(), behaviorGroup.getId());

        final String response = RestAssured.given()
            .header(identityHeader)
            .when()
            .put(url)
            .then()
            .statusCode(404)
            .extract()
            .asString();

        Assertions.assertEquals("the specified behavior group doesn't exist or the specified event type doesn't belong to the same bundle as the behavior group", response, "unexpected error message received when specifying a valid behavior group but from a different tenant");
    }

    /**
     * Tests that a "not found" response is returned when the event type and
     * the behavior group are of incompatible types.
     */
    @Test
    void testAppendBehaviorEventBehaviorGroupIncompatibleEventTypeBehaviorGroup() {
        // Create a bundle and a set of fixtures...
        final Bundle bundle = this.helpers.createBundle();
        final Application application = this.helpers.createApplication(bundle.getId());
        final EventType eventType = this.helpers.createEventType(application.getId(), "name", "display-name", "description");

        // ... and create a second bundle for the behavior group.
        final Bundle differentBundle = this.helpers.createBundle("bundle-name-different", "bundle-display-name-different");
        final BehaviorGroup behaviorGroup = this.helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "display-name", differentBundle.getId());

        final Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "user", FULL_ACCESS);

        final String url = String.format("/notifications/eventTypes/%s/behaviorGroups/%s", eventType.getId(), behaviorGroup.getId());

        final String response = RestAssured.given()
            .header(identityHeader)
            .when()
            .put(url)
            .then()
            .statusCode(404)
            .extract()
            .asString();

        Assertions.assertEquals("the specified behavior group doesn't exist or the specified event type doesn't belong to the same bundle as the behavior group", response, "unexpected error message received when specifying an incompatible event type with a behavior group");
    }

    /**
     * Test that deleting an existing behavior group from an event type works as expected.
     */
    @Test
    void testDeleteBehaviorEventType() {
        // Create the fixtures.
        final Bundle bundle = this.helpers.createBundle();
        final BehaviorGroup behaviorGroup = this.helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "display-name", bundle.getId());

        final Application application = this.helpers.createApplication(bundle.getId());
        final EventType eventType = this.helpers.createEventType(application.getId(), "name", "display-name", "description");

        // Generate the identity header.
        final Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "user", FULL_ACCESS);

        // Construct the URL we are going to send the request to.
        final String createUrl = String.format("/notifications/eventTypes/%s/behaviorGroups/%s", eventType.getId(), behaviorGroup.getId());

        // First add the behavior group to the event type.
        RestAssured.given()
            .header(identityHeader)
            .when()
            .put(createUrl)
            .then()
            .statusCode(204);

        final String deleteUrl = String.format("/notifications/eventTypes/%s/behaviorGroups/%s", eventType.getId(), behaviorGroup.getId());

        // Call the "delete" endpoint and expect a proper deletion.
        RestAssured.given()
            .header(identityHeader)
            .when()
            .delete(deleteUrl)
            .then()
            .statusCode(204);
    }

    /**
     * Test that when a non-existent event type or a non-existent behavior group is specified, a bad request is
     * returned. The same thing when the user tries to delete the behavior group - event type relation that doesn't
     * exist.
     */
    @Test
    void testDeleteBehaviorEventTypeError() {
        // Create the fixtures.
        final Bundle bundle = this.helpers.createBundle();
        final BehaviorGroup behaviorGroup = this.helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "display-name", bundle.getId());

        final Application application = this.helpers.createApplication(bundle.getId());
        final EventType eventType = this.helpers.createEventType(application.getId(), "name", "display-name", "description");

        // Generate the identity header.
        final Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "user", FULL_ACCESS);

        // Create a small test class to help structure the inputs and outputs.
        class TestCase {
            public final String expectedErrorMessage;
            public final int expectedStatusCode;
            public final String url;

            TestCase(final String expectedErrorMessage, final int expectedStatusCode, final String url) {
                this.expectedErrorMessage = expectedErrorMessage;
                this.expectedStatusCode = expectedStatusCode;
                this.url = url;
            }
        }

        // Use a base URL to construct the final URLs we will be sending requests to.
        final String baseUrlFormat = "/notifications/eventTypes/%s/behaviorGroups/%s";

        final var testCases = new ArrayList<TestCase>(3);

        // Test a bad request response when the event type does not exist.
        testCases.add(
            new TestCase(
                "the specified behavior group was not found for the given event type",
                404,
                String.format(baseUrlFormat, UUID.randomUUID(), behaviorGroup.getId())
            )
        );

        // Test a bad request response when the behavior group does not exist.
        testCases.add(
            new TestCase(
                "the specified behavior group was not found for the given event type",
                404,
                String.format(baseUrlFormat, eventType.getId(), UUID.randomUUID())
            )
        );

        //  Test a not found response when the behavior group - event type relation does not exist.
        testCases.add(
            new TestCase(
                "the specified behavior group was not found for the given event type",
                404,
                String.format(baseUrlFormat, eventType.getId(), behaviorGroup.getId())
            )
        );

        for (final var testCase : testCases) {
            final String response = RestAssured.given()
                .header(identityHeader)
                .when()
                .delete(testCase.url)
                .then()
                .statusCode(testCase.expectedStatusCode)
                .extract()
                .body()
                .asString();

            Assertions.assertEquals(testCase.expectedErrorMessage, response, "unexpected error message received");
        }
    }

    private Optional<CreateBehaviorGroupResponse> createBehaviorGroup(Header identityHeader, CreateBehaviorGroupRequest request, int expectedStatusCode) {
        ValidatableResponse response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(request))
                .post("/notifications/behaviorGroups")
                .then()
                .statusCode(expectedStatusCode);

        if (expectedStatusCode == 200) {
            return Optional.of(response.extract().as(CreateBehaviorGroupResponse.class));
        }

        return Optional.empty();
    }

    private CreateBehaviorGroupResponse createBehaviorGroup(Header identityHeader, CreateBehaviorGroupRequest request) {
        return createBehaviorGroup(identityHeader, request, 200).get();
    }

    private void updateBehaviorGroup(Header identityHeader, UUID behaviorGroupId, UpdateBehaviorGroupRequest request, int expectedStatus) {
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(request))
                .pathParam("behaviorGroupId", behaviorGroupId)
                .put("/notifications/behaviorGroups/{behaviorGroupId}")
                .then()
                .statusCode(expectedStatus);
    }

    private List<UUID> getEndpointsIds(String orgId, UUID behaviorGroupId) {
        return helpers
                .findEndpointsByBehaviorGroupId(orgId, behaviorGroupId)
                .stream()
                .map(Endpoint::getId)
                .collect(Collectors.toList());
    }

    private Set<UUID> getEventTypeIds(String orgId, UUID behaviorGroupId) {
        return helpers
                .findEventTypesByBehaviorGroupId(orgId, behaviorGroupId)
                .stream()
                .map(EventType::getId)
                .collect(Collectors.toSet());
    }
}
