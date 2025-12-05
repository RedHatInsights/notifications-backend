package com.redhat.cloud.notifications.routers.handlers.notification;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.MockServerConfig.RbacAccess;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.auth.kessel.KesselTestHelper;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.kessel.permission.IntegrationPermission;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BehaviorGroupRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointEventTypeRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.routers.models.Facet;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.routers.models.behaviorgroup.CreateBehaviorGroupRequest;
import com.redhat.cloud.notifications.routers.models.behaviorgroup.CreateBehaviorGroupResponse;
import com.redhat.cloud.notifications.routers.models.behaviorgroup.UpdateBehaviorGroupRequest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.project_kessel.inventory.client.KesselCheckClient;
import org.project_kessel.inventory.client.NotificationsIntegrationClient;
import org.project_kessel.relations.client.CheckClient;
import org.project_kessel.relations.client.LookupClient;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.FULL_ACCESS;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.NO_ACCESS;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.READ_ACCESS;
import static com.redhat.cloud.notifications.TestConstants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static com.redhat.cloud.notifications.db.ResourceHelpers.TEST_APP_NAME;
import static com.redhat.cloud.notifications.db.ResourceHelpers.TEST_APP_NAME_2;
import static com.redhat.cloud.notifications.db.ResourceHelpers.TEST_BUNDLE_2_NAME;
import static com.redhat.cloud.notifications.db.ResourceHelpers.TEST_BUNDLE_NAME;
import static com.redhat.cloud.notifications.db.ResourceHelpers.TEST_EVENT_TYPE_FORMAT;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class NotificationResourceTest extends DbIsolatedTest {

    /*
     * In the tests below, most JSON responses are verified using JsonObject/JsonArray instead of deserializing these
     * responses into model instances and checking their attributes values. That's because the model classes contain
     * attributes annotated with @JsonProperty(access = READ_ONLY) which can't be deserialized and therefore verified
     * here. The deserialization is still performed only to verify that the JSON responses data structure is correct.
     */

    private static final String ACCOUNT_ID = DEFAULT_ACCOUNT_ID;
    private static final String ORG_ID = DEFAULT_ORG_ID;
    private static final String USERNAME = DEFAULT_ACCOUNT_ID;

    /**
     * Mocked the backend configuration so that the {@link KesselTestHelper}
     * can be used.
     */
    @InjectMock
    BackendConfig backendConfig;

    /**
     * Mocked Kessel's check client so that the {@link KesselTestHelper} can
     * be used.
     */
    @InjectMock
    CheckClient checkClient;

    /**
     * Mocked Kessel's check client so that the {@link KesselTestHelper} can
     * be used.
     */
    @InjectMock
    KesselCheckClient KesselCheckClient;

    /**
     * Mocked Kessel's lookup client so that the {@link KesselTestHelper} can
     * be used.
     */
    @InjectMock
    LookupClient lookupClient;

    /**
     * Mocked Kessel's lookup client so that the {@link KesselTestHelper} can
     * be used.
     */
    @InjectMock
    NotificationsIntegrationClient notificationsIntegrationClient;

    /**
     * Mocked RBAC's workspace utilities so that the {@link KesselTestHelper}
     * can be used.
     */
    @InjectMock
    WorkspaceUtils workspaceUtils;

    @Inject
    ResourceHelpers helpers;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

    @Inject
    EndpointEventTypeRepository endpointEventTypeRepository;

    @Inject
    KesselTestHelper kesselTestHelper;

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_1_0;
        MockServerConfig.clearRbac();

        // Since the backend configuration is mocked, the "isRBACEnabled()"
        // method returns "false" by default. We need to have it enabled so
        // that the "ConsoleIdentityProvider" doesn't build a principal with
        // all the privileges because it thinks that both Kessel and RBAC are
        // disabled.
        Mockito.when(this.backendConfig.isRBACEnabled()).thenReturn(true);
    }

    private Header initRbacMock(String accountId, String orgId, String username, RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, username);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createRHIdentityHeader(identityHeaderValue);
    }

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testEventTypeFetching(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, RbacAccess.FULL_ACCESS);

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        // no offset
        Response response = given()
            .when()
            .header(identityHeader)
            .get("/notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
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
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        page = new JsonObject(response.getBody().asString());
        eventTypes = page.getJsonArray("data");
        assertEquals(1, eventTypes.size()); // only one element past HttpStatus.SC_OK
        assertEquals(201, page.getJsonObject("meta").getNumber("count")); // One of the event types is part of the default DB records.

        // different limit
        response = given()
            .when()
            .header(identityHeader)
            .get("/notifications/eventTypes?limit=100")
            .then()
            .statusCode(HttpStatus.SC_OK)
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
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        page = new JsonObject(response.getBody().asString());
        eventTypes = page.getJsonArray("data");
        assertEquals(51, eventTypes.size()); // 51 elements past 150
        assertEquals(201, page.getJsonObject("meta").getNumber("count")); // One of the event types is part of the default DB records.
    }

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testEventTypeFetchingByApplication(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        helpers.createTestAppAndEventTypes();
        List<Application> apps = applicationRepository.getApplications(TEST_BUNDLE_NAME);
        UUID myOtherTesterApplicationId = apps.stream().filter(a -> a.getName().equals(TEST_APP_NAME_2)).findFirst().get().getId();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, RbacAccess.FULL_ACCESS);

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        Response response = given()
            .when()
            .header(identityHeader)
            .queryParam("applicationIds", myOtherTesterApplicationId)
            .get("/notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
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

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testEventTypeFetchingByBundle(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        helpers.createTestAppAndEventTypes();
        List<Application> apps = applicationRepository.getApplications(TEST_BUNDLE_NAME);
        UUID myBundleId = apps.stream().filter(a -> a.getName().equals(TEST_APP_NAME_2)).findFirst().get().getBundleId();

        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, RbacAccess.FULL_ACCESS);
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        Response response = given()
            .when()
            .header(identityHeader)
            .queryParam("bundleId", myBundleId)
            .get("/notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        JsonObject page = new JsonObject(response.getBody().asString());
        JsonArray eventTypes = page.getJsonArray("data");
        for (int i = 0; i < eventTypes.size(); i++) {
            JsonObject ev = eventTypes.getJsonObject(i);
            ev.mapTo(EventType.class);
            assertEquals(myBundleId.toString(), ev.getJsonObject("application").getString("bundle_id"));
        }

        assertEquals(HttpStatus.SC_OK, page.getJsonObject("meta").getInteger("count"));
        assertEquals(20, eventTypes.size());
    }

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testEventTypeFetchingByBundleAndApplicationId(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        helpers.createTestAppAndEventTypes();
        List<Application> apps = applicationRepository.getApplications(TEST_BUNDLE_NAME);
        Application app = apps.stream().filter(a -> a.getName().equals(TEST_APP_NAME_2)).findFirst().get();
        UUID myOtherTesterApplicationId = app.getId();
        UUID myBundleId = app.getBundleId();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, RbacAccess.FULL_ACCESS);

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        Response response = given()
            .when()
            .header(identityHeader)
            .queryParam("bundleId", myBundleId)
            .queryParam("applicationIds", myOtherTesterApplicationId)
            .get("/notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
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

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testEventTypeFetchingByEventTypeName(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, RbacAccess.FULL_ACCESS);

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        Response response = given()
            .when()
            .header(identityHeader)
            .queryParam("eventTypeName", "50")
            .get("/notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
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

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testEventTypeFetchingByBundleApplicationAndEventTypeName(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        helpers.createTestAppAndEventTypes();
        List<Application> apps = applicationRepository.getApplications(TEST_BUNDLE_NAME);
        Application app = apps.stream().filter(a -> a.getName().equals(TEST_APP_NAME_2)).findFirst().get();
        UUID myOtherTesterApplicationId = app.getId();
        UUID myBundleId = app.getBundleId();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, RbacAccess.FULL_ACCESS);

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        Response response = given()
            .when()
            .header(identityHeader)
            .queryParam("bundleId", myBundleId)
            .queryParam("applicationIds", myOtherTesterApplicationId)
            .queryParam("eventTypeName", "50")
            .get("/notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
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

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testEventTypeFetchingAndExcludeMutedTypes(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        UUID bundleId = helpers.createTestAppAndEventTypes();
        List<Application> apps = applicationRepository.getApplications(TEST_BUNDLE_NAME);
        Application app = apps.stream().filter(a -> a.getName().equals(TEST_APP_NAME_2)).findFirst().get();
        UUID appId = app.getId();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);
        UUID behaviorGroupId1 = helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "behavior-group-1", bundleId).getId();
        UUID behaviorGroupId2 = helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "behavior-group-2", bundleId).getId();

        List<EventType> eventTypes = applicationRepository.getEventTypes(appId);
        // bgroup1 assigned to ev0 and ev1, bgroup2 assigned to ev14, all other event types unassigned
        ArrayList<String> unmutedEventTypeNames = new ArrayList<>(List.of(String.format(TEST_EVENT_TYPE_FORMAT, 0), String.format(TEST_EVENT_TYPE_FORMAT, 1), String.format(TEST_EVENT_TYPE_FORMAT, 14)));
        behaviorGroupRepository.updateEventTypeBehaviors(DEFAULT_ORG_ID, eventTypes.stream().filter(ev -> ev.getName().equals(unmutedEventTypeNames.getFirst())).findFirst().get().getId(), Set.of(behaviorGroupId1));
        behaviorGroupRepository.updateEventTypeBehaviors(DEFAULT_ORG_ID, eventTypes.stream().filter(ev -> ev.getName().equals(unmutedEventTypeNames.get(1))).findFirst().get().getId(), Set.of(behaviorGroupId1));
        behaviorGroupRepository.updateEventTypeBehaviors(DEFAULT_ORG_ID, eventTypes.stream().filter(ev -> ev.getName().equals(unmutedEventTypeNames.get(2))).findFirst().get().getId(), Set.of(behaviorGroupId2));

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        String response = given()
            .when()
            .header(identityHeader)
            .queryParam("excludeMutedTypes", "true")
            .get("/notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonObject page = new JsonObject(response);
        JsonArray respEventTypes = page.getJsonArray("data");
        ArrayList<String> respEventTypeNames = new ArrayList<>();
        for (int i = 0; i < respEventTypes.size(); i++) {
            JsonObject ev = respEventTypes.getJsonObject(i);
            respEventTypeNames.add(ev.getString("name"));
        }

        assertTrue(unmutedEventTypeNames.containsAll(respEventTypeNames) && respEventTypeNames.containsAll(unmutedEventTypeNames));
        assertFalse(respEventTypeNames.contains(String.format(TEST_EVENT_TYPE_FORMAT, 2)));
        assertEquals(3, page.getJsonObject("meta").getInteger("count"));

        // Should not match: default bgroup assigned to ev20, bgroup from different org assigned to ev30
        String otherOrganization = "otherOrganizationId";
        UUID defaultBehaviorGroup = helpers.createDefaultBehaviorGroup("default-behavior-group", bundleId).getId();
        UUID otherOrgBehaviorGroup = helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, otherOrganization, "other-org-bgroup", bundleId).getId();

        String defaultEventTypeName = String.format(TEST_EVENT_TYPE_FORMAT, 20);
        String otherOrgEventTypeName = String.format(TEST_EVENT_TYPE_FORMAT, 30);
        behaviorGroupRepository.linkEventTypeDefaultBehavior(eventTypes.stream().filter(ev -> ev.getName().equals(defaultEventTypeName)).findFirst().get().getId(), defaultBehaviorGroup);
        behaviorGroupRepository.updateEventTypeBehaviors(otherOrganization, eventTypes.stream().filter(ev -> ev.getName().equals(otherOrgEventTypeName)).findFirst().get().getId(), Set.of(otherOrgBehaviorGroup));

        String multiOrgResponse = given()
            .when()
            .header(identityHeader)
            .queryParam("excludeMutedTypes", "true")
            .get("/notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonObject multiOrgPage = new JsonObject(multiOrgResponse);
        JsonArray multiOrgEventTypes = multiOrgPage.getJsonArray("data");
        ArrayList<String> multiOrgEventTypeNames = new ArrayList<>();
        for (int i = 0; i < multiOrgEventTypes.size(); i++) {
            JsonObject ev = multiOrgEventTypes.getJsonObject(i);
            multiOrgEventTypeNames.add(ev.getString("name"));
        }

        assertTrue(unmutedEventTypeNames.containsAll(multiOrgEventTypeNames) && multiOrgEventTypeNames.containsAll(unmutedEventTypeNames));
        assertFalse(multiOrgEventTypeNames.contains(otherOrgEventTypeName));
        assertEquals(3, multiOrgPage.getJsonObject("meta").getInteger("count"));
    }

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testEventTypeFetchingByBundleApplicationEventTypeNameAndExcludeMutedTypes(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        UUID bundleId = helpers.createTestAppAndEventTypes();
        List<Application> apps = applicationRepository.getApplications(TEST_BUNDLE_NAME);
        Application app1 = apps.stream().filter(a -> a.getName().equals(TEST_APP_NAME)).findFirst().get();
        UUID appId1 = app1.getId();
        Application app2 = apps.stream().filter(a -> a.getName().equals(TEST_APP_NAME_2)).findFirst().get();
        UUID appId2 = app2.getId();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);
        UUID behaviorGroupId1 = helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "behavior-group-1", bundleId).getId();
        UUID behaviorGroupId2 = helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "behavior-group-2", bundleId).getId();
        UUID defaultBehaviorGroup = helpers.createDefaultBehaviorGroup(DEFAULT_ACCOUNT_ID, bundleId).getId();

        // bgroup1 assigned to ev0 and ev1 on TEST_APP_NAME, bgroup2 assigned to ev1 on TEST_APP_NAME_2,
        // default bgroup assigned to ev20 on TEST_APP_NAME_2 (should not match), all other event types unassigned
        String defaultEventTypeName = String.format(TEST_EVENT_TYPE_FORMAT, 20);
        List<EventType> eventTypesApp1 = applicationRepository.getEventTypes(appId1);
        behaviorGroupRepository.updateEventTypeBehaviors(DEFAULT_ORG_ID, eventTypesApp1.getFirst().getId(), Set.of(behaviorGroupId1));
        behaviorGroupRepository.updateEventTypeBehaviors(DEFAULT_ORG_ID, eventTypesApp1.get(1).getId(), Set.of(behaviorGroupId1));
        List<EventType> eventTypesApp2 = applicationRepository.getEventTypes(appId2);
        behaviorGroupRepository.updateEventTypeBehaviors(DEFAULT_ORG_ID, eventTypesApp2.get(1).getId(), Set.of(behaviorGroupId2));
        behaviorGroupRepository.linkEventTypeDefaultBehavior(eventTypesApp2.stream().filter(ev -> ev.getName().equals(defaultEventTypeName)).findFirst().get().getId(), defaultBehaviorGroup);

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        Response unmutedResponse = given()
            .when()
            .header(identityHeader)
            .queryParam("bundleId", bundleId)
            .queryParam("applicationIds", appId1)
            .queryParam("eventTypeName", "1")
            .queryParam("excludeMutedTypes", "true")
            .get("/notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        JsonObject unmutedPage = new JsonObject(unmutedResponse.getBody().asString());
        JsonArray unmutedEventTypes = unmutedPage.getJsonArray("data");
        for (int i = 0; i < unmutedEventTypes.size(); i++) {
            JsonObject unmutedEv = unmutedEventTypes.getJsonObject(i);
            unmutedEv.mapTo(EventType.class);
            assertEquals(bundleId.toString(), unmutedEv.getJsonObject("application").getString("bundle_id"));
            assertEquals(appId1.toString(), unmutedEv.getJsonObject("application").getString("id"));
            assertTrue(unmutedEv.getString("display_name").contains("1") || unmutedEv.getString("name").contains("1"));
        }
        assertEquals(1, unmutedEventTypes.size());

        Response unmutedDefaultGroupResponse = given()
            .when()
            .header(identityHeader)
            .queryParam("bundleId", bundleId)
            .queryParam("applicationIds", appId2)
            .queryParam("eventTypeName", "20")
            .queryParam("excludeMutedTypes", "true")
            .get("/notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        JsonObject unmutedDefaultGroupPage = new JsonObject(unmutedDefaultGroupResponse.getBody().asString());
        JsonArray unmutedDefaultGroupEventTypes = unmutedDefaultGroupPage.getJsonArray("data");
        assertTrue(unmutedDefaultGroupEventTypes.isEmpty());

        Response mutedResponse = given()
            .when()
            .header(identityHeader)
            .queryParam("bundleId", bundleId)
            .queryParam("applicationIds", appId2)
            .queryParam("eventTypeName", "50")
            .queryParam("excludeMutedTypes", "true")
            .get("notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        JsonObject mutedPage = new JsonObject(mutedResponse.getBody().asString());
        JsonArray mutedEventTypes = mutedPage.getJsonArray("data");
        assertTrue(mutedEventTypes.isEmpty());

        // bgroup in different org assigned to ev30 on TEST_APP_NAME_2. Although unmuted, this should not match since the org is different.
        String otherOrganization = "otherOrganizationId";
        UUID otherOrgBehaviorGroup = helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, otherOrganization, "other-org-bgroup", bundleId).getId();

        String otherOrgEventTypeName = String.format(TEST_EVENT_TYPE_FORMAT, 30);
        behaviorGroupRepository.updateEventTypeBehaviors(otherOrganization, eventTypesApp2.stream().filter(ev -> ev.getName().equals(otherOrgEventTypeName)).findFirst().get().getId(), Set.of(otherOrgBehaviorGroup));

        final UUID otherOrganizationWorkspaceId = UUID.randomUUID();
        this.kesselTestHelper.mockDefaultWorkspaceId(otherOrganization, otherOrganizationWorkspaceId);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, otherOrganizationWorkspaceId.toString());

        Response otherOrgUnmatchedResponse = given()
            .when()
            .header(identityHeader)
            .queryParam("bundleId", bundleId)
            .queryParam("applicationIds", appId2)
            .queryParam("eventTypeName", "30")
            .queryParam("excludeMutedTypes", "true")
            .get("notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        JsonObject otherOrgUnmatchedPage = new JsonObject(otherOrgUnmatchedResponse.getBody().asString());
        JsonArray otherOrgUnmatchedEventTypes = otherOrgUnmatchedPage.getJsonArray("data");
        assertTrue(otherOrgUnmatchedEventTypes.isEmpty());

        // Query as other org, should match
        Response otherOrgMatchedResponse = given()
            .when()
            .header(initRbacMock(DEFAULT_ACCOUNT_ID, otherOrganization, DEFAULT_USER, FULL_ACCESS))
            .queryParam("bundleId", bundleId)
            .queryParam("applicationIds", appId2)
            .queryParam("eventTypeName", "30")
            .queryParam("excludeMutedTypes", "true")
            .get("notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        JsonObject otherOrgMatchedPage = new JsonObject(otherOrgMatchedResponse.getBody().asString());
        JsonArray otherOrgMatchedEventTypes = otherOrgMatchedPage.getJsonArray("data");
        for (int i = 0; i < otherOrgMatchedEventTypes.size(); i++) {
            JsonObject otherOrgMatchedEv = otherOrgMatchedEventTypes.getJsonObject(i);
            otherOrgMatchedEv.mapTo(EventType.class);
            assertEquals(bundleId.toString(), otherOrgMatchedEv.getJsonObject("application").getString("bundle_id"));
            assertEquals(appId2.toString(), otherOrgMatchedEv.getJsonObject("application").getString("id"));
            assertTrue(otherOrgMatchedEv.getString("display_name").contains("30") || otherOrgMatchedEv.getString("name").contains("30"));
        }
        assertEquals(1, otherOrgMatchedEventTypes.size());
    }

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testGetEventTypesAffectedByEndpoint(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);
        UUID bundleId = helpers.createTestAppAndEventTypes();
        UUID behaviorGroupId1 = helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "behavior-group-1", bundleId).getId();
        UUID behaviorGroupId2 = helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "behavior-group-2", bundleId).getId();
        UUID appId = applicationRepository.getApplications(TEST_BUNDLE_NAME).stream()
            .filter(a -> a.getName().equals(TEST_APP_NAME_2))
            .findFirst().get().getId();
        UUID endpointId1 = helpers.createWebhookEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID);
        UUID endpointId2 = helpers.createWebhookEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID);
        List<EventType> eventTypes = applicationRepository.getEventTypes(appId);
        // ep1 assigned to ev0; ep2 not assigned.
        behaviorGroupRepository.updateEventTypeBehaviors(DEFAULT_ORG_ID, eventTypes.get(0).getId(), Set.of(behaviorGroupId1));
        behaviorGroupRepository.updateBehaviorGroupActions(DEFAULT_ORG_ID, behaviorGroupId1, List.of(endpointId1));

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.VIEW, ResourceType.INTEGRATION, endpointId1.toString());

        String responseBody = given()
            .header(identityHeader)
            .pathParam("endpointId", endpointId1.toString())
            .when()
            .get("/notifications/behaviorGroups/affectedByRemovalOfEndpoint/{endpointId}")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonArray behaviorGroups = new JsonArray(responseBody);
        assertEquals(1, behaviorGroups.size());
        behaviorGroups.getJsonObject(0).mapTo(BehaviorGroup.class);
        assertEquals(behaviorGroupId1.toString(), behaviorGroups.getJsonObject(0).getString("id"));

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.VIEW, ResourceType.INTEGRATION, endpointId2.toString());

        responseBody = given()
            .header(identityHeader)
            .pathParam("endpointId", endpointId2.toString())
            .when()
            .get("/notifications/behaviorGroups/affectedByRemovalOfEndpoint/{endpointId}")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        behaviorGroups = new JsonArray(responseBody);
        assertEquals(0, behaviorGroups.size());

        // ep1 assigned to event ev0; ep2 assigned to event ev1
        behaviorGroupRepository.updateEventTypeBehaviors(DEFAULT_ORG_ID, eventTypes.get(0).getId(), Set.of(behaviorGroupId2));
        behaviorGroupRepository.updateBehaviorGroupActions(DEFAULT_ORG_ID, behaviorGroupId2, List.of(endpointId2));

        responseBody = given()
            .header(identityHeader)
            .pathParam("endpointId", endpointId1.toString())
            .when()
            .get("/notifications/behaviorGroups/affectedByRemovalOfEndpoint/{endpointId}")
            .then()
            .statusCode(HttpStatus.SC_OK)
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
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        behaviorGroups = new JsonArray(responseBody);
        assertEquals(1, behaviorGroups.size());
        behaviorGroups.getJsonObject(0).mapTo(BehaviorGroup.class);
        assertEquals(behaviorGroupId2.toString(), behaviorGroups.getJsonObject(0).getString("id"));
    }

    @Test
    void testGetApplicationFacets() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, READ_ACCESS);
        List<Facet> applications = given()
                .header(identityHeader)
                .when()
                .get("/notifications/facets/applications?bundleName=rhel")
                .then()
                .statusCode(HttpStatus.SC_OK).contentType(JSON).extract().response().jsonPath().getList(".", Facet.class);

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
                .statusCode(HttpStatus.SC_OK).contentType(JSON).extract().response().jsonPath().getList(".", Facet.class);

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
                .statusCode(HttpStatus.SC_OK).contentType(JSON).extract().response().jsonPath().getList(".", Facet.class);

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
                .statusCode(HttpStatus.SC_OK).contentType(JSON).extract().response().jsonPath().getList(".", Facet.class);

        assertTrue(bundles.size() > 0);
        rhel = bundles.stream().filter(facet -> facet.getName().equals("rhel")).findFirst();
        assertTrue(rhel.isPresent());
        assertEquals("Red Hat Enterprise Linux", rhel.get().getDisplayName());
        assertNotNull(rhel.get().getChildren());

        Optional<Facet> policies = rhel.get().getChildren().stream().filter(facet -> facet.getName().equals("policies")).findFirst();
        assertTrue(policies.isPresent());
        assertEquals("Policies", policies.get().getDisplayName());
    }

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testCreateFullBehaviorGroup(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        helpers.createTestAppAndEventTypes();
        List<Application> apps = applicationRepository.getApplications(TEST_BUNDLE_NAME);
        UUID myBundleId = apps.stream().findFirst().get().getBundleId();

        List<UUID> endpoints = Stream.of(
            helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.EMAIL_SUBSCRIPTION),
            helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.EMAIL_SUBSCRIPTION),
            helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.DRAWER),
            helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.DRAWER),
            helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.CAMEL),
            helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.CAMEL),
            helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.PAGERDUTY),
            helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.PAGERDUTY)
        ).map(Endpoint::getId).collect(Collectors.toList());
        Set<UUID> eventTypes = apps.stream().findFirst().get().getEventTypes().stream().map(EventType::getId).collect(Collectors.toSet());

        CreateBehaviorGroupRequest behaviorGroupRequest = new CreateBehaviorGroupRequest();
        behaviorGroupRequest.bundleId = myBundleId;
        behaviorGroupRequest.displayName = "My behavior group 1";

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

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

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testUpdateFullBehaviorGroup(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        helpers.createTestAppAndEventTypes();
        List<Application> apps = applicationRepository.getApplications(TEST_BUNDLE_NAME);
        UUID myBundleId = apps.stream().findFirst().get().getBundleId();

        UUID behaviorGroupId = helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "My behavior group 1", myBundleId).getId();
        UUID behaviorGroupIdOtherTenant = helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID + "-other", DEFAULT_ORG_ID + "-other", "My behavior", myBundleId).getId();

        List<UUID> endpoints = Stream.of(
            helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.EMAIL_SUBSCRIPTION),
            helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.EMAIL_SUBSCRIPTION),
            helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.DRAWER),
            helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.DRAWER),
            helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.CAMEL),
            helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.CAMEL),
            helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.PAGERDUTY),
            helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.PAGERDUTY)
        ).map(Endpoint::getId).collect(Collectors.toList());

        Set<UUID> eventTypes = apps.stream().findFirst().get().getEventTypes().stream().map(EventType::getId).collect(Collectors.toSet());

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);

        // Mock the workspace identifier for the different tenant so that the
        // function which grabs the default workspace identifier does not cause
        // an exception.
        final UUID otherWorkspaceId = UUID.randomUUID();
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID + "-other", otherWorkspaceId);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        // Updating a behavior of other tenant yields 404 - i.e. can't find the behavior group
        UpdateBehaviorGroupRequest behaviorGroupRequest = new UpdateBehaviorGroupRequest();
        behaviorGroupRequest.displayName = "My behavior group 1.0";
        updateBehaviorGroup(identityHeader, behaviorGroupIdOtherTenant, behaviorGroupRequest, HttpStatus.SC_NOT_FOUND);
        BehaviorGroup behaviorGroup = helpers.getBehaviorGroup(behaviorGroupIdOtherTenant);
        assertEquals("My behavior", behaviorGroup.getDisplayName()); // No change

        // Updating the behavior group displayName only
        updateBehaviorGroup(identityHeader, behaviorGroupId, behaviorGroupRequest, HttpStatus.SC_OK);
        behaviorGroup = helpers.getBehaviorGroup(behaviorGroupId);
        assertEquals("My behavior group 1.0", behaviorGroup.getDisplayName());

        // Updating with all null is effectively a no-op
        behaviorGroupRequest.displayName = null;
        updateBehaviorGroup(identityHeader, behaviorGroupId, behaviorGroupRequest, HttpStatus.SC_OK);
        behaviorGroup = helpers.getBehaviorGroup(behaviorGroupId);
        assertEquals("My behavior group 1.0", behaviorGroup.getDisplayName());

        // Updating only endpoints
        behaviorGroupRequest.displayName = "My behavior group 2.0";
        behaviorGroupRequest.endpointIds = endpoints;
        behaviorGroupRequest.eventTypeIds = null;
        updateBehaviorGroup(identityHeader, behaviorGroupId, behaviorGroupRequest, HttpStatus.SC_OK);
        behaviorGroup = helpers.getBehaviorGroup(behaviorGroupId);
        assertEquals("My behavior group 2.0", behaviorGroup.getDisplayName());
        assertEquals(endpoints, getEndpointsIds(DEFAULT_ORG_ID, behaviorGroupId));
        assertEquals(Set.of(), getEventTypeIds(DEFAULT_ORG_ID, behaviorGroupId));

        // Updating only event types (endpoints remain the same)
        behaviorGroupRequest.displayName = "My behavior group 3.0";
        behaviorGroupRequest.endpointIds = null;
        behaviorGroupRequest.eventTypeIds = eventTypes;
        updateBehaviorGroup(identityHeader, behaviorGroupId, behaviorGroupRequest, HttpStatus.SC_OK);
        behaviorGroup = helpers.getBehaviorGroup(behaviorGroupId);
        assertEquals("My behavior group 3.0", behaviorGroup.getDisplayName());
        assertEquals(endpoints, getEndpointsIds(DEFAULT_ORG_ID, behaviorGroupId));
        assertEquals(eventTypes, getEventTypeIds(DEFAULT_ORG_ID, behaviorGroupId));

        // Updating both to empty
        behaviorGroupRequest.displayName = "My behavior group 4.0";
        behaviorGroupRequest.endpointIds = List.of();
        behaviorGroupRequest.eventTypeIds = Set.of();
        updateBehaviorGroup(identityHeader, behaviorGroupId, behaviorGroupRequest, HttpStatus.SC_OK);
        behaviorGroup = helpers.getBehaviorGroup(behaviorGroupId);
        assertEquals("My behavior group 4.0", behaviorGroup.getDisplayName());
        assertEquals(List.of(), getEndpointsIds(DEFAULT_ORG_ID, behaviorGroupId));
        assertEquals(Set.of(), getEventTypeIds(DEFAULT_ORG_ID, behaviorGroupId));
    }

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testBundleApplicationEventTypeByName(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);
        helpers.createTestAppAndEventTypes();

        String eventTypeName = String.format(TEST_EVENT_TYPE_FORMAT, 1);

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BUNDLES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        Bundle bundle = given()
            .header(identityHeader)
            .when()
            .given()
            .pathParam("bundleName", TEST_BUNDLE_NAME)
            .get("/notifications/bundles/{bundleName}")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract()
            .as(Bundle.class);
        assertEquals(bundle.getName(), TEST_BUNDLE_NAME);

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.APPLICATIONS_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        Application application = given()
            .header(identityHeader)
            .pathParam("bundleName", TEST_BUNDLE_NAME)
            .pathParam("applicationName", TEST_APP_NAME)
            .when()
            .given()
            .get("/notifications/bundles/{bundleName}/applications/{applicationName}")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract()
            .as(Application.class);
        assertEquals(application.getName(), TEST_APP_NAME);

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        EventType eventType = given()
            .header(identityHeader)
            .pathParam("bundleName", TEST_BUNDLE_NAME)
            .pathParam("applicationName", TEST_APP_NAME)
            .pathParam("eventTypeName", eventTypeName)
            .when()
            .given()
            .get("/notifications/bundles/{bundleName}/applications/{applicationName}/eventTypes/{eventTypeName}")
            .then()
            .statusCode(HttpStatus.SC_OK)
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
            .statusCode(HttpStatus.SC_NOT_FOUND);

        given()
            .header(identityHeader)
            .pathParam("bundleName", TEST_BUNDLE_NAME)
            .pathParam("applicationName", "bla bla")
            .when()
            .given()
            .get("/notifications/bundles/{bundleName}/applications/{applicationName}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);

        given()
            .header(identityHeader)
            .pathParam("bundleName", "bla bla")
            .pathParam("applicationName", TEST_APP_NAME)
            .when()
            .given()
            .get("/notifications/bundles/{bundleName}/applications/{applicationName}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);

        given()
            .header(identityHeader)
            .pathParam("bundleName", "bla bla")
            .pathParam("applicationName", TEST_APP_NAME)
            .pathParam("eventTypeName", eventTypeName)
            .when()
            .given()
            .get("/notifications/bundles/{bundleName}/applications/{applicationName}/eventTypes/{eventTypeName}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);

        given()
            .header(identityHeader)
            .pathParam("bundleName", TEST_BUNDLE_NAME)
            .pathParam("applicationName", "bla bla")
            .pathParam("eventTypeName", eventTypeName)
            .when()
            .given()
            .get("/notifications/bundles/{bundleName}/applications/{applicationName}/eventTypes/{eventTypeName}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);

        given()
            .header(identityHeader)
            .pathParam("bundleName", TEST_BUNDLE_NAME)
            .pathParam("applicationName", TEST_APP_NAME)
            .pathParam("eventTypeName", "blabla")
            .when()
            .given()
            .get("/notifications/bundles/{bundleName}/applications/{applicationName}/eventTypes/{eventTypeName}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testInsufficientPrivileges(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        Header noAccessIdentityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER + "no-access", NO_ACCESS);
        Header readAccessIdentityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER + "read-access", READ_ACCESS);

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);

        try {
            RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_2_0;

            given()
                .header(noAccessIdentityHeader)
                .pathParam("eventTypeId", UUID.randomUUID())
                .when()
                .get("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
                .then()
                .statusCode(HttpStatus.SC_FORBIDDEN);
        } finally {
            RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_1_0;

            given()
                .header(noAccessIdentityHeader)
                .pathParam("eventTypeId", UUID.randomUUID())
                .when()
                .get("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
                .then()
                .statusCode(HttpStatus.SC_FORBIDDEN);
        }

        given()
            .header(noAccessIdentityHeader)
            .when()
            .get("/notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(noAccessIdentityHeader)
            .pathParam("bundleName", "myBundle")
            .when()
            .get("/notifications/bundles/{bundleName}")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(noAccessIdentityHeader)
            .pathParam("bundleName", "myBundle")
            .pathParam("applicationName", "myApp")
            .when()
            .get("/notifications/bundles/{bundleName}/applications/{applicationName}")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(noAccessIdentityHeader)
            .pathParam("bundleName", "myBundle")
            .pathParam("applicationName", "myApp")
            .pathParam("eventTypeName", "myEventType")
            .when()
            .get("/notifications/bundles/{bundleName}/applications/{applicationName}/eventTypes/{eventTypeName}")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(noAccessIdentityHeader)
            .pathParam("behaviorGroupId", UUID.randomUUID())
            .when()
            .get("/notifications/eventTypes/affectedByRemovalOfBehaviorGroup/{behaviorGroupId}")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(noAccessIdentityHeader)
            .pathParam("endpointId", UUID.randomUUID())
            .when()
            .get("/notifications/behaviorGroups/affectedByRemovalOfEndpoint/{endpointId}")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(readAccessIdentityHeader)
            .contentType(JSON)
            .pathParam("eventTypeId", UUID.randomUUID())
            .body(Json.encode(List.of(UUID.randomUUID())))
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(readAccessIdentityHeader)
            .contentType(JSON)
            .when()
            .post("/notifications/behaviorGroups")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(readAccessIdentityHeader)
            .contentType(JSON)
            .pathParam("id", UUID.randomUUID())
            .when()
            .put("/notifications/behaviorGroups/{id}")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(readAccessIdentityHeader)
            .pathParam("id", UUID.randomUUID())
            .when()
            .delete("/notifications/behaviorGroups/{id}")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(readAccessIdentityHeader)
            .contentType(JSON)
            .pathParam("behaviorGroupId", UUID.randomUUID())
            .when()
            .put("/notifications/behaviorGroups/{behaviorGroupId}/actions")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(readAccessIdentityHeader)
            .contentType(JSON)
            .pathParam("eventTypeId", UUID.randomUUID())
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(readAccessIdentityHeader)
            .contentType(JSON)
            .pathParam("eventTypeId", UUID.randomUUID())
            .pathParam("behaviorGroupId", UUID.randomUUID())
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups/{behaviorGroupId}")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(readAccessIdentityHeader)
            .contentType(JSON)
            .pathParam("eventTypeId", UUID.randomUUID())
            .pathParam("behaviorGroupId", UUID.randomUUID())
            .when()
            .delete("/notifications/eventTypes/{eventTypeId}/behaviorGroups/{behaviorGroupId}")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(noAccessIdentityHeader)
            .pathParam("bundleId", UUID.randomUUID())
            .when()
            .get("/notifications/bundles/{bundleId}/behaviorGroups")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(noAccessIdentityHeader)
            .pathParam("eventTypeId", UUID.randomUUID())
            .when()
            .get("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);
    }

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testUpdateUnknownBehaviorGroupId(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setDisplayName("Behavior group");
        behaviorGroup.setBundleId(UUID.randomUUID()); // Only used for @NotNull validation.

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        given()
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("id", UUID.randomUUID())
            .body(Json.encode(behaviorGroup))
            .when()
            .put("/notifications/behaviorGroups/{id}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testDeleteUnknownBehaviorGroupId(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        given()
            .header(identityHeader)
            .pathParam("id", UUID.randomUUID())
            .when()
            .delete("/notifications/behaviorGroups/{id}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testFindBehaviorGroupsByUnknownBundleId(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BUNDLES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        given()
            .header(identityHeader)
            .pathParam("bundleId", UUID.randomUUID())
            .when()
            .get("/notifications/bundles/{bundleId}/behaviorGroups")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testFindEventTypesAffectedByRemovalOfUnknownBehaviorGroupId(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        given()
            .header(identityHeader)
            .pathParam("behaviorGroupId", UUID.randomUUID())
            .when()
            .get("/notifications/eventTypes/affectedByRemovalOfBehaviorGroup/{behaviorGroupId}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testFindBehaviorGroupsByUnknownEventTypeId(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        given()
            .header(identityHeader)
            .pathParam("eventTypeId", UUID.randomUUID())
            .when()
            .get("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testBehaviorGroupsAffectedByRemovalOfUnknownEndpointId(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        final UUID integrationId = UUID.randomUUID();

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.VIEW, ResourceType.INTEGRATION, integrationId.toString());

        given()
            .header(identityHeader)
            .pathParam("endpointId", integrationId)
            .when()
            .get("/notifications/behaviorGroups/affectedByRemovalOfEndpoint/{endpointId}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testBehaviorGroupSameName(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        final String BEHAVIOR_GROUP_1_NAME = "BehaviorGroup1";
        final String BEHAVIOR_GROUP_2_NAME = "BehaviorGroup2";

        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        Bundle bundle1 = helpers.createBundle(TEST_BUNDLE_NAME, "Bundle1");
        Bundle bundle2 = helpers.createBundle(TEST_BUNDLE_2_NAME, "Bundle2");

        CreateBehaviorGroupRequest createBehaviorGroupRequest = new CreateBehaviorGroupRequest();
        createBehaviorGroupRequest.displayName = BEHAVIOR_GROUP_1_NAME;
        createBehaviorGroupRequest.bundleId = bundle1.getId();

        UUID behaviorGroup1Id = createBehaviorGroup(identityHeader, createBehaviorGroupRequest, HttpStatus.SC_OK).get().id;
        assertNotNull(behaviorGroup1Id);

        // same display name in same bundle is not possible
        createBehaviorGroup(identityHeader, createBehaviorGroupRequest, HttpStatus.SC_BAD_REQUEST);

        // same display name in a different bundle is OK
        createBehaviorGroupRequest.bundleId = bundle2.getId();
        createBehaviorGroup(identityHeader, createBehaviorGroupRequest, HttpStatus.SC_OK);

        // Different display name in bundle1
        createBehaviorGroupRequest.bundleId = bundle1.getId();
        createBehaviorGroupRequest.displayName = BEHAVIOR_GROUP_2_NAME;
        createBehaviorGroup(identityHeader, createBehaviorGroupRequest, HttpStatus.SC_OK);

        // Cannot update Behavior Group 1 name to "BehaviorGroup2"  as it already exists
        UpdateBehaviorGroupRequest updateBehaviorGroupRequest = new UpdateBehaviorGroupRequest();
        updateBehaviorGroupRequest.displayName = BEHAVIOR_GROUP_2_NAME;
        updateBehaviorGroup(identityHeader, behaviorGroup1Id, updateBehaviorGroupRequest, HttpStatus.SC_BAD_REQUEST);

        // Can update other properties without changing the name
        updateBehaviorGroupRequest.displayName = BEHAVIOR_GROUP_1_NAME;
        updateBehaviorGroupRequest.eventTypeIds = Set.of();
        updateBehaviorGroup(identityHeader, behaviorGroup1Id, updateBehaviorGroupRequest, HttpStatus.SC_OK);

        // Can use other name
        updateBehaviorGroupRequest.displayName = "OtherName";
        updateBehaviorGroup(identityHeader, behaviorGroup1Id, updateBehaviorGroupRequest, HttpStatus.SC_OK);
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
    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testBadRequestBehaviorGroupInvalidBundle(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        final CreateBehaviorGroupRequest createBehaviorGroupRequest = new CreateBehaviorGroupRequest();
        createBehaviorGroupRequest.displayName = "behavior-group-display-name";

        final Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        final String response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(createBehaviorGroupRequest))
            .post("/notifications/behaviorGroups")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
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
    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testNotFoundBehaviorGroupNotExists(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        final Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

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
                .statusCode(HttpStatus.SC_NOT_FOUND)
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
    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testBehaviorGroupDisplayNameTooLong(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) throws NoSuchFieldException {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        final Bundle bundle = this.helpers.createBundle(TEST_BUNDLE_NAME, "Bundle-display-name");

        // Get the value of the "max" property for the "Size" annotation.
        final Field classField = CreateBehaviorGroupRequest.class.getDeclaredField("displayName");
        final Size sizeClassAnnotation = classField.getAnnotation(Size.class);

        final CreateBehaviorGroupRequest createBehaviorGroupRequest = new CreateBehaviorGroupRequest();
        createBehaviorGroupRequest.bundleId = bundle.getId();
        createBehaviorGroupRequest.displayName = "a".repeat(sizeClassAnnotation.max() + 1);

        final Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        final String response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(createBehaviorGroupRequest))
            .post("/notifications/behaviorGroups")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
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
    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testUpdateBehaviorGroupDisplayNameTooLong(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) throws NoSuchFieldException {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        // Create the fixtures in the database.
        final Bundle bundle = this.helpers.createBundle(TEST_BUNDLE_NAME, "Bundle-display-name");
        final BehaviorGroup behaviorGroup = this.helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "valid display name", bundle.getId());

        // Get the value of the "max" property for the "Size" annotation.
        final Field classField = UpdateBehaviorGroupRequest.class.getDeclaredField("displayName");
        final Size sizeClassAnnotation = classField.getAnnotation(Size.class);

        final UpdateBehaviorGroupRequest updateBehaviorGroupRequest = new UpdateBehaviorGroupRequest();
        updateBehaviorGroupRequest.displayName = "a".repeat(sizeClassAnnotation.max() + 1);

        final Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        final String response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("behaviorGroupId", behaviorGroup.getId())
            .body(Json.encode(updateBehaviorGroupRequest))
            .put("/notifications/behaviorGroups/{behaviorGroupId}")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
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
    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testUpdateBehaviorGroupDisplayNameBlank(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        // Create the fixtures in the database.
        final Bundle bundle = this.helpers.createBundle(TEST_BUNDLE_NAME, "Bundle-display-name");
        final BehaviorGroup behaviorGroup = this.helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "valid display name", bundle.getId());

        final String[] blankDisplayNames = {"", "     "};
        for (final String blankDisplayName : blankDisplayNames) {

            final UpdateBehaviorGroupRequest updateBehaviorGroupRequest = new UpdateBehaviorGroupRequest();
            updateBehaviorGroupRequest.displayName = blankDisplayName;

            final Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);
            this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
            this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

            final String response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("behaviorGroupId", behaviorGroup.getId())
                .body(Json.encode(updateBehaviorGroupRequest))
                .put("/notifications/behaviorGroups/{behaviorGroupId}")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
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
    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testAppendBehaviorEventType(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        final Bundle bundle = this.helpers.createBundle();
        final BehaviorGroup behaviorGroup = this.helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "display-name", bundle.getId());

        final Application application = this.helpers.createApplication(bundle.getId());
        final EventType eventType = this.helpers.createEventType(application.getId(), "name", "display-name", "description");

        final Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        RestAssured.given()
            .header(identityHeader)
            .pathParam("eventTypeUuid", eventType.getId())
            .pathParam("behaviorGroupUuid", behaviorGroup.getId())
            .when()
            .put("/notifications/eventTypes/{eventTypeUuid}/behaviorGroups/{behaviorGroupUuid}")
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);
    }

    /**
     * Tests that a not found response is returned when the behavior group does
     * not exist.
     */
    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testAppendBehaviorEventBehaviorGroupNotFound(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        final Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        final String response = RestAssured.given()
            .header(identityHeader)
            .when()
            .pathParam("eventTypeId", UUID.randomUUID())
            .pathParam("behaviorGroupId", UUID.randomUUID())
            .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups/{behaviorGroupId}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND)
            .extract()
            .asString();

        Assertions.assertEquals("the specified behavior group doesn't exist or the specified event type doesn't belong to the same bundle as the behavior group", response, "unexpected error message received when specifying a non-existent behavior group");
    }

    /**
     * Tests that a not found response is returned when the behavior group
     * exists, but the tenant that is performing the operation is a different
     * one.
     */
    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testAppendBehaviorEventBehaviorGroupWrongTenantNotFound(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        final Bundle bundle = this.helpers.createBundle();
        final BehaviorGroup behaviorGroup = this.helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "display-name", bundle.getId());

        final Application application = this.helpers.createApplication(bundle.getId());
        final EventType eventType = this.helpers.createEventType(application.getId(), "name", "display-name", "description");

        final Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID + "different-tenant", DEFAULT_USER, FULL_ACCESS);
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID + "different-tenant");
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        final String response = RestAssured.given()
            .header(identityHeader)
            .when()
            .pathParam("eventTypeId", eventType.getId())
            .pathParam("behaviorGroupId", behaviorGroup.getId())
            .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups/{behaviorGroupId}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND)
            .extract()
            .asString();

        Assertions.assertEquals("the specified behavior group doesn't exist or the specified event type doesn't belong to the same bundle as the behavior group", response, "unexpected error message received when specifying a valid behavior group but from a different tenant");
    }

    /**
     * Tests that a "not found" response is returned when the event type and
     * the behavior group are of incompatible types.
     */
    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testAppendBehaviorEventBehaviorGroupIncompatibleEventTypeBehaviorGroup(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        // Create a bundle and a set of fixtures...
        final Bundle bundle = this.helpers.createBundle();
        final Application application = this.helpers.createApplication(bundle.getId());
        final EventType eventType = this.helpers.createEventType(application.getId(), "name", "display-name", "description");

        // ... and create a second bundle for the behavior group.
        final Bundle differentBundle = this.helpers.createBundle("bundle-name-different", "bundle-display-name-different");
        final BehaviorGroup behaviorGroup = this.helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "display-name", differentBundle.getId());

        final Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        final String response = RestAssured.given()
            .header(identityHeader)
            .when()
            .pathParam("eventTypeId", eventType.getId())
            .pathParam("behaviorGroupId", behaviorGroup.getId())
            .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups/{behaviorGroupId}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND)
            .extract()
            .asString();

        Assertions.assertEquals("the specified behavior group doesn't exist or the specified event type doesn't belong to the same bundle as the behavior group", response, "unexpected error message received when specifying an incompatible event type with a behavior group");
    }

    /**
     * Test that deleting an existing behavior group from an event type works as expected.
     */
    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testDeleteBehaviorEventType(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        // Create the fixtures.
        final Bundle bundle = this.helpers.createBundle();
        final BehaviorGroup behaviorGroup = this.helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "display-name", bundle.getId());

        final Application application = this.helpers.createApplication(bundle.getId());
        final EventType eventType = this.helpers.createEventType(application.getId(), "name", "display-name", "description");

        // Generate the identity header.
        final Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        // First add the behavior group to the event type.
        RestAssured.given()
            .header(identityHeader)
            .when()
            .pathParam("eventTypeId", eventType.getId())
            .pathParam("behaviorGroupId", behaviorGroup.getId())
            .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups/{behaviorGroupId}")
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);

        // Call the "delete" endpoint and expect a proper deletion.
        RestAssured.given()
            .header(identityHeader)
            .when()
            .pathParam("eventTypeId", eventType.getId())
            .pathParam("behaviorGroupId", behaviorGroup.getId())
            .delete("/notifications/eventTypes/{eventTypeId}/behaviorGroups/{behaviorGroupId}")
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);
    }

    /**
     * Test that when a non-existent event type or a non-existent behavior group is specified, a bad request is
     * returned. The same thing when the user tries to delete the behavior group - event type relation that doesn't
     * exist.
     */
    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testDeleteBehaviorEventTypeError(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        // Create the fixtures.
        final Bundle bundle = this.helpers.createBundle();
        final BehaviorGroup behaviorGroup = this.helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "display-name", bundle.getId());

        final Application application = this.helpers.createApplication(bundle.getId());
        final EventType eventType = this.helpers.createEventType(application.getId(), "name", "display-name", "description");

        // Generate the identity header.
        final Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        // Create a small test class to help structure the inputs and outputs.
        record TestCase(UUID behaviorGroupId, UUID eventTypeId, String expectedErrorMessage, int expectedStatusCode) { }

        final var testCases = new ArrayList<TestCase>(3);

        // Test a bad request response when the event type does not exist.
        testCases.add(
            new TestCase(
                behaviorGroup.getId(),
                UUID.randomUUID(),
                "the specified behavior group was not found for the given event type",
                HttpStatus.SC_NOT_FOUND
            )
        );

        // Test a bad request response when the behavior group does not exist.
        testCases.add(
            new TestCase(
                UUID.randomUUID(),
                eventType.getId(),
                "the specified behavior group was not found for the given event type",
                HttpStatus.SC_NOT_FOUND
            )
        );

        //  Test a not found response when the behavior group - event type relation does not exist.
        testCases.add(
            new TestCase(
                behaviorGroup.getId(),
                eventType.getId(),
                "the specified behavior group was not found for the given event type",
                HttpStatus.SC_NOT_FOUND
            )
        );

        for (final var testCase : testCases) {
            final String response = RestAssured.given()
                .header(identityHeader)
                .when()
                .pathParam("eventTypeId", testCase.eventTypeId())
                .pathParam("behaviorGroupId", testCase.behaviorGroupId())
                .delete("/notifications/eventTypes/{eventTypeId}/behaviorGroups/{behaviorGroupId}")
                .then()
                .statusCode(testCase.expectedStatusCode())
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

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testGetEndpointsLinkedToAnEventType(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        final Bundle bundle = helpers.createBundle();

        // Create event type and endpoint
        final Application application = helpers.createApplication(bundle.getId());
        final EventType eventType = helpers.createEventType(application.getId(), "name", "display-name", "description");
        final Endpoint endpoint = helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.WEBHOOK);
        final Endpoint endpoint1 = helpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.ANSIBLE);

        final Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(Set.of(endpoint.getId(), endpoint1.getId()));

        // Check that created endpoint don't ave any event type associated
        Page<Endpoint> endpointPage = given()
            .header(identityHeader)
            .pathParam("eventTypeUuid", eventType.getId())
            .when()
            .given()
            .get("/notifications/eventTypes/{eventTypeUuid}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract().as(Page.class);
        assertEquals(0, endpointPage.getData().size());

        endpointEventTypeRepository.addEventTypeToEndpoint(eventType.getId(), endpoint.getId(), DEFAULT_ORG_ID);
        endpointEventTypeRepository.addEventTypeToEndpoint(eventType.getId(), endpoint1.getId(), DEFAULT_ORG_ID);
        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(Set.of(endpoint.getId(), endpoint1.getId()));

        // Check that endpoint is linked to the event type
        endpointPage = given()
            .header(identityHeader)
            .pathParam("eventTypeUuid", eventType.getId())
            .when()
            .given()
            .get("/notifications/eventTypes/{eventTypeUuid}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract().as(new TypeRef<>() { });
        assertEquals(2, endpointPage.getData().size());
        assertEquals(Set.of(endpoint.getId(), endpoint1.getId()), endpointPage.getData().stream().map(ep -> ep.getId()).collect(Collectors.toSet()));
    }

    @ParameterizedTest
    @MethodSource("kesselFlags")
    void testEndpointEventTypeLinksUpdatesFromBehaviorGroupActions(final boolean isKesselRelationsApiEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled, isKesselInventoryUseForPermissionsChecksEnabled);

        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.BEHAVIOR_GROUPS_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_TYPES_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        UUID bundleId = helpers.createTestAppAndEventTypes();
        UUID behaviorGroupId1 = helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "behavior-group-ep-1", bundleId).getId();
        UUID behaviorGroupId2 = helpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "behavior-group-ep-2", bundleId).getId();
        UUID appId = applicationRepository.getApplications(TEST_BUNDLE_NAME).stream()
            .filter(a -> a.getName().equals(TEST_APP_NAME_2))
            .findFirst().get().getId();
        UUID endpointId1 = helpers.createWebhookEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID);
        UUID endpointId2 = helpers.createWebhookEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID);
        List<EventType> eventTypes = applicationRepository.getEventTypes(appId);

        // assign event type 1 to behavior group 1
        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("eventTypeId", eventTypes.get(0).getId())
            .body(Json.encode(Set.of(behaviorGroupId1)))
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
            .then()
            .statusCode(HttpStatus.SC_OK);

        assertEquals(0, helpers.getEndpoint(endpointId1).getEventTypes().size());

        // assign endpoint 1 to behavior group 1
        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("behaviorGroupId", behaviorGroupId1)
            .body(Json.encode(Arrays.asList(endpointId1)))
            .when()
            .put("/notifications/behaviorGroups/{behaviorGroupId}/actions")
            .then()
            .statusCode(HttpStatus.SC_OK);
        assertEquals(1, helpers.getEndpoint(endpointId1).getEventTypes().size());

        // add event type 2 to behavior group 1
        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("eventTypeId", eventTypes.get(1).getId())
            .body(Json.encode(Set.of(behaviorGroupId1)))
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
            .then()
            .statusCode(HttpStatus.SC_OK);
        assertEquals(2, helpers.getEndpoint(endpointId1).getEventTypes().size());

        // assign event type 1 to behavior group 2
        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("eventTypeId", eventTypes.get(0).getId())
            .body(Json.encode(Set.of(behaviorGroupId2)))
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
            .then()
            .statusCode(HttpStatus.SC_OK);
        assertEquals(1, helpers.getEndpoint(endpointId1).getEventTypes().size());

        // assign endpoint 2 to behavior group 2
        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("behaviorGroupId", behaviorGroupId2)
            .body(Json.encode(Arrays.asList(endpointId2)))
            .when()
            .put("/notifications/behaviorGroups/{behaviorGroupId}/actions")
            .then()
            .statusCode(HttpStatus.SC_OK);
        assertEquals(1, helpers.getEndpoint(endpointId2).getEventTypes().size());

        // assign endpoint 1 to behavior group 2
        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("behaviorGroupId", behaviorGroupId2)
            .body(Json.encode(Arrays.asList(endpointId1, endpointId2)))
            .when()
            .put("/notifications/behaviorGroups/{behaviorGroupId}/actions")
            .then()
            .statusCode(HttpStatus.SC_OK);
        assertEquals(1, helpers.getEndpoint(endpointId2).getEventTypes().size());
        assertEquals(2, helpers.getEndpoint(endpointId1).getEventTypes().size());

        // remove event type 2 from behavior group 1
        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("eventTypeId", eventTypes.get(1).getId())
            .pathParam("behaviorGroupId", behaviorGroupId1)
            .when()
            .delete("/notifications/eventTypes/{eventTypeId}/behaviorGroups/{behaviorGroupId}")
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);

        assertEquals(1, helpers.getEndpoint(endpointId1).getEventTypes().size());
        assertEquals(1, helpers.getEndpoint(endpointId2).getEventTypes().size());

        // delete BG1
        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("behaviorGroupId", behaviorGroupId1)
            .when()
            .delete("/notifications/behaviorGroups/{behaviorGroupId}")
            .then()
            .statusCode(HttpStatus.SC_OK);

        assertEquals(1, helpers.getEndpoint(endpointId2).getEventTypes().size());
        assertEquals(1, helpers.getEndpoint(endpointId1).getEventTypes().size());

        // delete BG2
        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("behaviorGroupId", behaviorGroupId2)
            .when()
            .delete("/notifications/behaviorGroups/{behaviorGroupId}")
            .then()
            .statusCode(HttpStatus.SC_OK);

        assertEquals(0, helpers.getEndpoint(endpointId2).getEventTypes().size());
        assertEquals(0, helpers.getEndpoint(endpointId1).getEventTypes().size());

        // re-create a BG using full creation API
        CreateBehaviorGroupRequest behaviorGroup = new CreateBehaviorGroupRequest();
        behaviorGroup.bundleId = bundleId;
        behaviorGroup.displayName = RandomStringUtils.randomAlphabetic(15);
        behaviorGroup.endpointIds = List.of(endpointId1, endpointId2);
        behaviorGroup.eventTypeIds = Set.of(eventTypes.get(2).getId(), eventTypes.get(3).getId(), eventTypes.get(4).getId());

        CreateBehaviorGroupResponse createdBehaviorGroup = given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .body(Json.encode(behaviorGroup))
            .when()
            .post("/notifications/behaviorGroups")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract().body().as(CreateBehaviorGroupResponse.class);

        assertEquals(3, helpers.getEndpoint(endpointId2).getEventTypes().size());
        assertEquals(3, helpers.getEndpoint(endpointId1).getEventTypes().size());

        // Update created behaviour group using update API
        UpdateBehaviorGroupRequest updateBehaviorGroup = new UpdateBehaviorGroupRequest();
        updateBehaviorGroup.displayName = RandomStringUtils.randomAlphabetic(15);
        updateBehaviorGroup.endpointIds = List.of(endpointId2);
        updateBehaviorGroup.eventTypeIds = Set.of(eventTypes.get(1).getId());

        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("behaviorGroupId", createdBehaviorGroup.id)
            .body(Json.encode(updateBehaviorGroup))
            .when()
            .put("/notifications/behaviorGroups/{behaviorGroupId}")
            .then()
            .statusCode(HttpStatus.SC_OK);

        assertEquals(1, helpers.getEndpoint(endpointId2).getEventTypes().size());
        assertEquals(0, helpers.getEndpoint(endpointId1).getEventTypes().size());
    }

    @Test
    void testUpdateEventTypeEndpoints() {
        String accountId = RandomStringUtils.randomAlphanumeric(25);
        String orgId = RandomStringUtils.randomAlphanumeric(25);
        Header identityHeader = initRbacMock(accountId, orgId, "user", FULL_ACCESS);
        UUID bundleId = helpers.createTestAppAndEventTypes();
        UUID behaviorGroupId1 = helpers.createBehaviorGroup(accountId, orgId, "behavior-group-ep-1", bundleId).getId();
        helpers.createBehaviorGroup(accountId, orgId, "behavior-group-ep-2", bundleId).getId();
        UUID appId = applicationRepository.getApplications(TEST_BUNDLE_NAME).stream()
            .filter(a -> a.getName().equals(TEST_APP_NAME_2))
            .findFirst().get().getId();
        UUID endpointId1 = helpers.createWebhookEndpoint(accountId, orgId, "endpoint1");
        UUID endpointId2 = helpers.createWebhookEndpoint(accountId, orgId, "endpoint2");
        Endpoint emailEndpoint = helpers.createSystemEndpoint(accountId, orgId, new SystemSubscriptionProperties(), EndpointType.EMAIL_SUBSCRIPTION);

        List<EventType> eventTypes = applicationRepository.getEventTypes(appId);
        final EventType retictedRecipientsIntegrationEventType = helpers.createEventType(appId, RandomStringUtils.randomAlphabetic(10).toLowerCase(), "restricted event type 2", "description", true);

        EventType eventType1 = eventTypes.get(1);
        EventType eventType2 = eventTypes.get(2);

        // assign event type 1 to behavior group 1
        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("eventTypeId", eventType1.getId())
            .body(Json.encode(Set.of(behaviorGroupId1)))
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
            .then()
            .statusCode(HttpStatus.SC_OK);

        assertEquals(0, helpers.getEndpoint(endpointId1).getEventTypes().size());

        // assign endpoint 1 and email endpoint to behavior group 1
        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("behaviorGroupId", behaviorGroupId1)
            .body(Json.encode(Arrays.asList(endpointId1, emailEndpoint.getId())))
            .when()
            .put("/notifications/behaviorGroups/{behaviorGroupId}/actions")
            .then()
            .statusCode(HttpStatus.SC_OK);

        assertEquals(1, helpers.getEndpoint(endpointId1).getEventTypes().size());

        // add event type restrictedRecipientsIntegrationEventType to behavior group 1
        // must be rejected because restrictedRecipientsIntegrationEventType can't be apply to endpoint 1 (webhook)
        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("eventTypeId", retictedRecipientsIntegrationEventType.getId())
            .body(Json.encode(Set.of(behaviorGroupId1)))
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
        assertEquals(1, helpers.getEndpoint(endpointId1).getEventTypes().size());

        // remove endpoint1 from behavior group 1
        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("behaviorGroupId", behaviorGroupId1)
            .body(Json.encode(Arrays.asList(emailEndpoint.getId())))
            .when()
            .put("/notifications/behaviorGroups/{behaviorGroupId}/actions")
            .then()
            .statusCode(HttpStatus.SC_OK);

        // add event type restrictedRecipientsIntegrationEventType to behavior group 1
        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("eventTypeId", retictedRecipientsIntegrationEventType.getId())
            .body(Json.encode(new HashSet<>()))
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
            .then()
            .statusCode(HttpStatus.SC_OK);

        assertEquals(0, helpers.getEndpoint(endpointId1).getEventTypes().size());

        // assign endpoint 1 to behavior group 1
        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("behaviorGroupId", behaviorGroupId1)
            .body(Json.encode(Arrays.asList(endpointId1)))
            .when()
            .put("/notifications/behaviorGroups/{behaviorGroupId}/actions")
            .then()
            .statusCode(HttpStatus.SC_OK);
        assertEquals(1, helpers.getEndpoint(endpointId1).getEventTypes().size());

        // add event type 2 to behavior group 1
        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("eventTypeId", eventType2.getId())
            .body(Json.encode(Set.of(behaviorGroupId1)))
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
            .then()
            .statusCode(HttpStatus.SC_OK);
        assertEquals(2, helpers.getEndpoint(endpointId1).getEventTypes().size());

        List<BehaviorGroup> behaviorGroupList = helpers.findBehaviorGroupsByOrgId(orgId);
        assertEquals(2, behaviorGroupList.size());

        // assign event type 1 to endpoint2 only
        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("eventTypeId", eventType1.getId())
            .body(Json.encode(Set.of(endpointId2)))
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK);

        // event type 1 was removed from endpoint 1
        assertEquals(1, helpers.getEndpoint(endpointId1).getEventTypes().size());
        // event type 1 was added to endpoint 2
        assertEquals(1, helpers.getEndpoint(endpointId2).getEventTypes().size());

        behaviorGroupList = helpers.findBehaviorGroupsByOrgId(orgId);
        // a new behavior group was created
        // endpoint 1 was removed from behavior group 1
        assertEquals(3, behaviorGroupList.size());
        for (BehaviorGroup bg : behaviorGroupList)  {
            switch (bg.getDisplayName()) {
                case "behavior-group-ep-1":
                    assertEquals(1, bg.getActions().size());
                    assertEquals("endpoint1", bg.getActions().getFirst().getEndpoint().getName());
                    assertEquals(1, bg.getBehaviors().size());
                    assertEquals(eventType2.getName(), bg.getBehaviors().stream().findFirst().get().getEventType().getName());
                    break;
                case "behavior-group-ep-2":
                    assertEquals(0, bg.getActions().size());
                    break;
                default:
                    assertTrue(bg.getDisplayName().startsWith("Event type \""));
                    assertEquals(1, bg.getActions().size());
                    assertEquals("endpoint2", bg.getActions().getFirst().getEndpoint().getName());
                    assertEquals(1, bg.getBehaviors().size());
                    assertEquals(eventType1.getName(), bg.getBehaviors().stream().findFirst().get().getEventType().getName());
                    break;
            }
        }

        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("eventTypeId", eventType1.getId())
            .body(Json.encode(new HashSet<>()))
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK);

        behaviorGroupList = helpers.findBehaviorGroupsByOrgId(orgId);
        // a new behavior group was created
        // endpoint 1 was removed from behavior group 1
        assertEquals(2, behaviorGroupList.size());
        for (BehaviorGroup bg : behaviorGroupList)  {
            switch (bg.getDisplayName()) {
                case "behavior-group-ep-1":
                    assertEquals(1, bg.getActions().size());
                    assertEquals("endpoint1", bg.getActions().getFirst().getEndpoint().getName());
                    assertEquals(1, bg.getBehaviors().size());
                    assertEquals(eventType2.getName(), bg.getBehaviors().stream().findFirst().get().getEventType().getName());
                    break;
                case "behavior-group-ep-2":
                    assertEquals(0, bg.getActions().size());
                    break;
                default:
                    fail();
                    break;
            }
        }

        given()
            .basePath(API_NOTIFICATIONS_V_1_0)
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("eventTypeId", eventType2.getId())
            .body(Json.encode(new HashSet<>()))
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK);

        behaviorGroupList = helpers.findBehaviorGroupsByOrgId(orgId);
        // endpoint 1 was removed from behavior group 1
        assertEquals(1, behaviorGroupList.size());
        assertEquals("behavior-group-ep-2", behaviorGroupList.get(0).getDisplayName());
        assertEquals(0, behaviorGroupList.get(0).getActions().size());
    }

    private static Stream<Arguments> kesselFlags() {
        return Stream.of(
            Arguments.of(false, false), // Should use RBAC
            Arguments.of(true, false), // Should use Kessel relations
            Arguments.of(true, true) // Should use Kessel inventory
        );
    }
}
