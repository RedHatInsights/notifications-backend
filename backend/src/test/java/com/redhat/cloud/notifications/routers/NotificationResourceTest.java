package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.MockServerConfig.RbacAccess;
import com.redhat.cloud.notifications.OrgIdConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.accountid.BehaviorGroupRepository;
import com.redhat.cloud.notifications.db.repositories.orgid.BehaviorGroupRepositoryOrgId;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.routers.models.Facet;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.FULL_ACCESS;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.NO_ACCESS;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.READ_ACCESS;
import static com.redhat.cloud.notifications.db.ResourceHelpers.TEST_APP_NAME_2;
import static com.redhat.cloud.notifications.db.ResourceHelpers.TEST_BUNDLE_NAME;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static final String TENANT = "NotificationServiceTest";
    private static final String ORG_ID = "NotificationServiceTestOrgId";
    private static final String USERNAME = "user";

    @Inject
    ResourceHelpers helpers;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

    @Inject
    BehaviorGroupRepositoryOrgId behaviorGroupRepositoryOrgId;

    @Inject
    OrgIdConfig orgIdConfig;

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_1_0;
        MockServerConfig.clearRbac();

        orgIdConfig.overrideForTest(false);
    }

    private Header initRbacMock(String tenant, String orgId, String username, RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(tenant, orgId, username);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createRHIdentityHeader(identityHeaderValue);
    }

    private Header initRbacMock(String orgId, String username, RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(orgId, username);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createRHIdentityHeader(identityHeaderValue);
    }

    @Test
    void shouldContainDefaultBehaviorFieldInResponseWhenAccountIdIsPartOfHeader() {
        Header identityHeader = initRbacMock(TENANT, ORG_ID, USERNAME, RbacAccess.FULL_ACCESS);

        final UUID bundleId = helpers.createBundle("bundle-1", "Bundle 1").getId();

        BehaviorGroup behaviorGroup = new BehaviorGroup();

        behaviorGroup.setDisplayName("someDisplayName");
        behaviorGroup.setBundleId(bundleId);

        final Response response = given()
                .header(identityHeader)
                .body(Json.encode(behaviorGroup))
                .contentType(JSON)
                .when()
                .post("/notifications/behaviorGroups")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject page = new JsonObject(response.getBody().asString());
        assertTrue(page.containsKey("default_behavior"));
        assertFalse(page.getBoolean("default_behavior"));
    }

    @Test
    void shouldContainDefaultBehaviorFieldInResponseWhenOrgIdIsPartOfHeader() {
        orgIdConfig.overrideForTest(true);
        Header identityHeader = initRbacMock(ORG_ID, USERNAME, RbacAccess.FULL_ACCESS);

        final UUID bundleId = helpers.createBundle("bundle-1", "Bundle 1").getId();

        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setDisplayName("someDisplayName");
        behaviorGroup.setBundleId(bundleId);

        final Response response = given()
                .header(identityHeader)
                .body(Json.encode(behaviorGroup))
                .contentType(JSON)
                .when()
                .post("/notifications/behaviorGroups")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject page = new JsonObject(response.getBody().asString());
        assertTrue(page.containsKey("default_behavior"));
        assertFalse(page.getBoolean("default_behavior"));
    }

    @Test
    void shouldContainDefaultBehaviorFieldInResponseWhenOrgIdAndAccountIdArePartOfHeader() {
        orgIdConfig.overrideForTest(true);
        Header identityHeader = initRbacMock(TENANT, ORG_ID, USERNAME, RbacAccess.FULL_ACCESS);

        final UUID bundleId = helpers.createBundle("bundle-1", "Bundle 1").getId();

        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setDisplayName("someDisplayName");
        behaviorGroup.setBundleId(bundleId);

        final Response response = given()
                .header(identityHeader)
                .body(Json.encode(behaviorGroup))
                .contentType(JSON)
                .when()
                .post("/notifications/behaviorGroups")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject page = new JsonObject(response.getBody().asString());
        assertTrue(page.containsKey("default_behavior"));
        assertFalse(page.getBoolean("default_behavior"));
    }

    @Test
    void shouldNotContainDefaultBehaviorFieldInResponseWhen() {
        orgIdConfig.overrideForTest(true);
        Header identityHeader = initRbacMock(TENANT, ORG_ID, USERNAME, RbacAccess.FULL_ACCESS);

        final UUID bundleId = helpers.createBundle("bundle-1", "Bundle 1").getId();

        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setOrgId(null);
        behaviorGroup.setAccountId(null);
        behaviorGroup.setDisplayName("someDisplayName");
        behaviorGroup.setBundleId(bundleId);

        final Response response = given()
                .header(identityHeader)
                .body(Json.encode(behaviorGroup))
                .contentType(JSON)
                .when()
                .post("/notifications/behaviorGroups")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject page = new JsonObject(response.getBody().asString());

        assertFalse(page.getBoolean("default_behavior"));
    }

    @Test
    void testEventTypeFetching() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(TENANT, ORG_ID, USERNAME, RbacAccess.FULL_ACCESS);

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
        Header identityHeader = initRbacMock(TENANT, ORG_ID, USERNAME, RbacAccess.FULL_ACCESS);

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

        Header identityHeader = initRbacMock(TENANT, ORG_ID, USERNAME, RbacAccess.FULL_ACCESS);

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
        Header identityHeader = initRbacMock(TENANT, ORG_ID, USERNAME, RbacAccess.FULL_ACCESS);

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
        Header identityHeader = initRbacMock(TENANT, ORG_ID, USERNAME, RbacAccess.FULL_ACCESS);

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
        Header identityHeader = initRbacMock(TENANT, ORG_ID, USERNAME, RbacAccess.FULL_ACCESS);

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

    @Nested
    class OrgIdRelated {

        @Test
        void testGetEventTypesAffectedByEndpointOrgId() {
            orgIdConfig.overrideForTest(true);

            String tenant = "testGetEventTypesAffectedByEndpoint";
            String orgId = "testGetEventTypesAffectedByEndpointOrgId";
            Header identityHeader = initRbacMock(tenant, orgId, "user", FULL_ACCESS);
            UUID bundleId = helpers.createTestAppAndEventTypes();

            UUID behaviorGroupId1 = createBehaviorGroup(orgId, "behavior-group-1", bundleId).getId();
            UUID behaviorGroupId2 = createBehaviorGroup(orgId, "behavior-group-2", bundleId).getId();

            UUID appId = applicationRepository.getApplications(TEST_BUNDLE_NAME).stream()
                    .filter(a -> a.getName().equals(TEST_APP_NAME_2))
                    .findFirst().get().getId();
            UUID endpointId1 = helpers.createWebhookEndpointOrgId(orgId);
            UUID endpointId2 = helpers.createWebhookEndpointOrgId(orgId);

            List<EventType> eventTypes = applicationRepository.getEventTypes(appId);
            // ep1 assigned to ev0; ep2 not assigned.
            behaviorGroupRepositoryOrgId.updateEventTypeBehaviors(orgId, eventTypes.get(0).getId(), Set.of(behaviorGroupId1));
            behaviorGroupRepositoryOrgId.updateBehaviorGroupActions(orgId, behaviorGroupId1, List.of(endpointId1));

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
            behaviorGroupRepositoryOrgId.updateEventTypeBehaviors(orgId, eventTypes.get(0).getId(), Set.of(behaviorGroupId2));
            behaviorGroupRepositoryOrgId.updateBehaviorGroupActions(orgId, behaviorGroupId2, List.of(endpointId2));

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

        private BehaviorGroup createBehaviorGroup(String orgId, String displayName, UUID bundleId) {
            BehaviorGroup behaviorGroup = new BehaviorGroup();
            behaviorGroup.setDisplayName(displayName);
            behaviorGroup.setBundleId(bundleId);
            return behaviorGroupRepositoryOrgId.create(orgId, behaviorGroup);
        }
    }

    @Nested
    class AccountIdRelated {

        @Test
        void testGetEventTypesAffectedByEndpoint() {
            String tenant = "testGetEventTypesAffectedByEndpoint";
            String orgId = "testGetEventTypesAffectedByEndpointOrgId";
            Header identityHeader = initRbacMock(tenant, orgId, "user", FULL_ACCESS);
            UUID bundleId = helpers.createTestAppAndEventTypes();
            UUID behaviorGroupId1 = createBehaviorGroup(tenant, "behavior-group-1", bundleId).getId();
            UUID behaviorGroupId2 = createBehaviorGroup(tenant, "behavior-group-2", bundleId).getId();
            UUID appId = applicationRepository.getApplications(TEST_BUNDLE_NAME).stream()
                    .filter(a -> a.getName().equals(TEST_APP_NAME_2))
                    .findFirst().get().getId();
            UUID endpointId1 = helpers.createWebhookEndpoint(tenant);
            UUID endpointId2 = helpers.createWebhookEndpoint(tenant);
            List<EventType> eventTypes = applicationRepository.getEventTypes(appId);
            // ep1 assigned to ev0; ep2 not assigned.
            behaviorGroupRepository.updateEventTypeBehaviors(tenant, eventTypes.get(0).getId(), Set.of(behaviorGroupId1));
            behaviorGroupRepository.updateBehaviorGroupActions(tenant, behaviorGroupId1, List.of(endpointId1));

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
            behaviorGroupRepository.updateEventTypeBehaviors(tenant, eventTypes.get(0).getId(), Set.of(behaviorGroupId2));
            behaviorGroupRepository.updateBehaviorGroupActions(tenant, behaviorGroupId2, List.of(endpointId2));

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

        private BehaviorGroup createBehaviorGroup(String accountId, String displayName, UUID bundleId) {
            BehaviorGroup behaviorGroup = new BehaviorGroup();
            behaviorGroup.setDisplayName(displayName);
            behaviorGroup.setBundleId(bundleId);
            return behaviorGroupRepository.create(accountId, behaviorGroup);
        }
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
}
