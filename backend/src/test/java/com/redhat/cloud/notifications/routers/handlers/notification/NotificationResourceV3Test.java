package com.redhat.cloud.notifications.routers.handlers.notification;

import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.FULL_ACCESS;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.NO_ACCESS;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.READ_ACCESS;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static com.redhat.cloud.notifications.db.ResourceHelpers.TEST_APP_NAME;
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
public class NotificationResourceV3Test extends DbIsolatedTest {

    @InjectSpy
    BackendConfig backendConfig;

    @Inject
    ResourceHelpers helpers;

    @Inject
    ApplicationRepository applicationRepository;

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_3_0;
        MockServerConfig.clearRbac();
        Mockito.when(this.backendConfig.isRBACEnabled()).thenReturn(true);
    }

    private Header initRbacMock(String accountId, String orgId, String username, MockServerConfig.RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, username);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createRHIdentityHeader(identityHeaderValue);
    }

    @Test
    void testGetEventTypesReturnsDTOs() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        String response = given()
            .header(identityHeader)
            .when()
            .get("/notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonObject page = new JsonObject(response);
        JsonArray eventTypes = page.getJsonArray("data");
        assertTrue(eventTypes.size() > 0);

        JsonObject firstEventType = eventTypes.getJsonObject(0);
        assertNotNull(firstEventType.getString("id"));
        assertNotNull(firstEventType.getString("name"));
        assertNotNull(firstEventType.getString("display_name"));

        assertNull(firstEventType.getValue("visible"));
        assertNull(firstEventType.getValue("subscribed_by_default"));
        assertNull(firstEventType.getValue("subscription_locked"));
    }

    @Test
    void testGetBundleByNameReturnsDTO() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        String response = given()
            .header(identityHeader)
            .pathParam("bundleName", TEST_BUNDLE_NAME)
            .when()
            .get("/notifications/bundles/{bundleName}")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonObject bundle = new JsonObject(response);
        assertNotNull(bundle.getString("id"));
        assertEquals(TEST_BUNDLE_NAME, bundle.getString("name"));
        assertNotNull(bundle.getString("display_name"));

        assertNull(bundle.getValue("created"));
        assertNull(bundle.getValue("updated"));
    }

    @Test
    void testGetBundleByNameNotFound() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        given()
            .header(identityHeader)
            .pathParam("bundleName", "nonexistent-bundle")
            .when()
            .get("/notifications/bundles/{bundleName}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void testGetApplicationByNameReturnsDTO() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        String response = given()
            .header(identityHeader)
            .pathParam("bundleName", TEST_BUNDLE_NAME)
            .pathParam("applicationName", TEST_APP_NAME)
            .when()
            .get("/notifications/bundles/{bundleName}/applications/{applicationName}")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonObject app = new JsonObject(response);
        assertNotNull(app.getString("id"));
        assertEquals(TEST_APP_NAME, app.getString("name"));
        assertNotNull(app.getString("display_name"));
        assertNotNull(app.getString("bundle_id"));

        assertNull(app.getValue("created"));
        assertNull(app.getValue("updated"));
    }

    @Test
    void testGetEventTypeByNameReturnsDTO() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        String eventTypeName = String.format(TEST_EVENT_TYPE_FORMAT, 1);

        String response = given()
            .header(identityHeader)
            .pathParam("bundleName", TEST_BUNDLE_NAME)
            .pathParam("applicationName", TEST_APP_NAME)
            .pathParam("eventTypeName", eventTypeName)
            .when()
            .get("/notifications/bundles/{bundleName}/applications/{applicationName}/eventTypes/{eventTypeName}")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonObject eventType = new JsonObject(response);
        assertNotNull(eventType.getString("id"));
        assertEquals(eventTypeName, eventType.getString("name"));
        assertNotNull(eventType.getString("display_name"));

        assertNull(eventType.getValue("visible"));
        assertNull(eventType.getValue("subscribed_by_default"));
        assertNull(eventType.getValue("subscription_locked"));
    }

    @Test
    void testGetApplicationsReturnsDTO() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, READ_ACCESS);

        String response = given()
            .header(identityHeader)
            .queryParam("bundleName", TEST_BUNDLE_NAME)
            .when()
            .get("/notifications/applications")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonArray applications = new JsonArray(response);
        assertTrue(applications.size() > 0);

        JsonObject firstApp = applications.getJsonObject(0);
        assertNotNull(firstApp.getString("id"));
        assertNotNull(firstApp.getString("name"));
        assertNotNull(firstApp.getString("display_name"));
        assertNotNull(firstApp.getString("bundle_id"));
    }

    @Test
    void testGetBundlesReturnsDTOWithoutApplications() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, READ_ACCESS);

        String response = given()
            .header(identityHeader)
            .when()
            .get("/notifications/bundles")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonArray bundles = new JsonArray(response);
        assertTrue(bundles.size() > 0);

        JsonObject firstBundle = bundles.getJsonObject(0);
        assertNotNull(firstBundle.getString("id"));
        assertNotNull(firstBundle.getString("name"));
        assertNotNull(firstBundle.getString("display_name"));
        assertNull(firstBundle.getValue("applications"));
    }

    @Test
    void testGetBundlesReturnsDTOWithApplications() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, READ_ACCESS);

        String response = given()
            .header(identityHeader)
            .queryParam("includeApplications", "true")
            .when()
            .get("/notifications/bundles")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonArray bundles = new JsonArray(response);
        assertTrue(bundles.size() > 0);

        boolean foundBundleWithApps = false;
        for (int i = 0; i < bundles.size(); i++) {
            JsonObject bundle = bundles.getJsonObject(i);
            assertNotNull(bundle.getString("id"));
            assertNotNull(bundle.getString("name"));
            assertNotNull(bundle.getString("display_name"));

            JsonArray apps = bundle.getJsonArray("applications");
            assertNotNull(apps);
            if (apps.size() > 0) {
                foundBundleWithApps = true;
                JsonObject app = apps.getJsonObject(0);
                assertNotNull(app.getString("id"));
                assertNotNull(app.getString("name"));
                assertNotNull(app.getString("display_name"));
                assertNotNull(app.getString("bundle_id"));
            }
        }
        assertTrue(foundBundleWithApps);
    }

    @Test
    void testGetSeverities() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, READ_ACCESS);

        String response = given()
            .header(identityHeader)
            .when()
            .get("/notifications/severities")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonArray severities = new JsonArray(response);
        assertTrue(severities.size() > 0);
    }

    @Test
    void testGetLinkedEndpointsNotFound() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        given()
            .header(identityHeader)
            .pathParam("eventTypeId", UUID.randomUUID())
            .when()
            .get("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void testGetLinkedEndpointsEmpty() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        String eventTypeName = String.format(TEST_EVENT_TYPE_FORMAT, 1);
        UUID eventTypeId = findEventTypeId(identityHeader, eventTypeName);

        String response = given()
            .header(identityHeader)
            .pathParam("eventTypeId", eventTypeId)
            .when()
            .get("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonObject page = new JsonObject(response);
        JsonArray data = page.getJsonArray("data");
        assertEquals(0, data.size());
        assertEquals(0, page.getJsonObject("meta").getInteger("count"));
    }

    @Test
    void testUpdateEventTypeEndpointsNullBody() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        given()
            .header(identityHeader)
            .pathParam("eventTypeId", UUID.randomUUID())
            .contentType(JSON)
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testInsufficientPrivilegesGetEventTypes() {
        Header noAccessHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER + "no-access", NO_ACCESS);

        given()
            .header(noAccessHeader)
            .when()
            .get("/notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);
    }

    @Test
    void testReadOnlyCannotUpdateEndpoints() {
        helpers.createTestAppAndEventTypes();
        Header readOnlyHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER + "read-only", READ_ACCESS);

        given()
            .header(readOnlyHeader)
            .pathParam("eventTypeId", UUID.randomUUID())
            .contentType(JSON)
            .body(Set.of())
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);
    }

    @Test
    void testGetEventTypesWithBundleFilter() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Bundle bundle = helpers.createBundle("v3-filter-bundle", "V3 Filter Bundle");
        Application app = helpers.createApplication(bundle.getId(), "v3-filter-app", "V3 Filter App");
        helpers.createEventType(app.getId(), "v3-filter-et", "V3 Filter ET", "V3 Filter ET");

        String response = given()
            .header(identityHeader)
            .queryParam("bundleId", bundle.getId())
            .when()
            .get("/notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonObject page = new JsonObject(response);
        JsonArray eventTypes = page.getJsonArray("data");
        assertEquals(1, eventTypes.size());
        assertEquals("v3-filter-et", eventTypes.getJsonObject(0).getString("name"));
    }

    @Test
    void testGetEventTypesWithNameFilter() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        String eventTypeName = String.format(TEST_EVENT_TYPE_FORMAT, 1);

        String response = given()
            .header(identityHeader)
            .queryParam("eventTypeName", eventTypeName)
            .when()
            .get("/notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonObject page = new JsonObject(response);
        JsonArray eventTypes = page.getJsonArray("data");
        assertTrue(eventTypes.size() > 0);
        for (int i = 0; i < eventTypes.size(); i++) {
            String name = eventTypes.getJsonObject(i).getString("name").toLowerCase();
            assertTrue(name.contains(eventTypeName.toLowerCase()));
        }
    }

    @Test
    void testGetEventTypesWithPagination() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        String response = given()
            .header(identityHeader)
            .queryParam("limit", 1)
            .queryParam("offset", 0)
            .when()
            .get("/notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonObject page = new JsonObject(response);
        assertEquals(1, page.getJsonArray("data").size());
        assertTrue(page.getJsonObject("meta").getInteger("count") > 1);
    }

    @Test
    void testGetApplicationsWithoutBundleFilter() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, READ_ACCESS);

        String response = given()
            .header(identityHeader)
            .when()
            .get("/notifications/applications")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonArray applications = new JsonArray(response);
        assertTrue(applications.size() > 0);
    }

    @Test
    void testGetEventTypesWithApplicationIdFilter() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Application app = applicationRepository.getApplications(TEST_BUNDLE_NAME).stream()
            .filter(a -> a.getName().equals(TEST_APP_NAME))
            .findFirst().orElseThrow();

        String response = given()
            .header(identityHeader)
            .queryParam("applicationIds", app.getId())
            .when()
            .get("/notifications/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonObject page = new JsonObject(response);
        JsonArray eventTypes = page.getJsonArray("data");
        assertTrue(eventTypes.size() > 0);
        for (int i = 0; i < eventTypes.size(); i++) {
            assertEquals(app.getId().toString(), eventTypes.getJsonObject(i).getJsonObject("application").getString("id"));
        }
    }

    @Test
    void testUpdateEventTypeEndpointsSuccess() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Bundle bundle = helpers.createBundle("v3-update-bundle", "V3 Update Bundle");
        Application app = helpers.createApplication(bundle.getId(), "v3-update-app", "V3 Update App");
        EventType eventType = helpers.createEventType(app.getId(), "v3-update-et", "V3 Update ET", "V3 Update ET");

        UUID endpointId1 = helpers.createWebhookEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "v3-ep-1");
        UUID endpointId2 = helpers.createWebhookEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "v3-ep-2");

        given()
            .header(identityHeader)
            .pathParam("eventTypeId", eventType.getId())
            .contentType(JSON)
            .body(Set.of(endpointId1, endpointId2))
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK);

        String response = given()
            .header(identityHeader)
            .pathParam("eventTypeId", eventType.getId())
            .when()
            .get("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonObject page = new JsonObject(response);
        assertEquals(2, page.getJsonObject("meta").getInteger("count"));
    }

    @Test
    void testUpdateEventTypeEndpointsClearAll() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Bundle bundle = helpers.createBundle("v3-clear-bundle", "V3 Clear Bundle");
        Application app = helpers.createApplication(bundle.getId(), "v3-clear-app", "V3 Clear App");
        EventType eventType = helpers.createEventType(app.getId(), "v3-clear-et", "V3 Clear ET", "V3 Clear ET");

        UUID endpointId = helpers.createWebhookEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "v3-clear-ep");

        given()
            .header(identityHeader)
            .pathParam("eventTypeId", eventType.getId())
            .contentType(JSON)
            .body(Set.of(endpointId))
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK);

        given()
            .header(identityHeader)
            .pathParam("eventTypeId", eventType.getId())
            .contentType(JSON)
            .body(Set.of())
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK);

        String response = given()
            .header(identityHeader)
            .pathParam("eventTypeId", eventType.getId())
            .when()
            .get("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonObject page = new JsonObject(response);
        assertEquals(0, page.getJsonObject("meta").getInteger("count"));
    }

    @Test
    void testGetLinkedEndpointsWithData() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Bundle bundle = helpers.createBundle("v3-linked-bundle", "V3 Linked Bundle");
        Application app = helpers.createApplication(bundle.getId(), "v3-linked-app", "V3 Linked App");
        EventType eventType = helpers.createEventType(app.getId(), "v3-linked-et", "V3 Linked ET", "V3 Linked ET");

        UUID endpointId = helpers.createWebhookEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "v3-linked-ep");

        given()
            .header(identityHeader)
            .pathParam("eventTypeId", eventType.getId())
            .contentType(JSON)
            .body(Set.of(endpointId))
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK);

        String response = given()
            .header(identityHeader)
            .pathParam("eventTypeId", eventType.getId())
            .when()
            .get("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonObject page = new JsonObject(response);
        JsonArray data = page.getJsonArray("data");
        assertEquals(1, data.size());
        assertEquals(1, page.getJsonObject("meta").getInteger("count"));

        JsonObject endpointDTO = data.getJsonObject(0);
        assertNotNull(endpointDTO.getString("id"));
        assertNotNull(endpointDTO.getString("name"));
        assertNotNull(endpointDTO.getString("type"));
    }

    @Test
    void testGetApplicationNotFound() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        given()
            .header(identityHeader)
            .pathParam("bundleName", "nonexistent-bundle")
            .pathParam("applicationName", "nonexistent-app")
            .when()
            .get("/notifications/bundles/{bundleName}/applications/{applicationName}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void testGetEventTypeByNameNotFound() {
        helpers.createTestAppAndEventTypes();
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        given()
            .header(identityHeader)
            .pathParam("bundleName", TEST_BUNDLE_NAME)
            .pathParam("applicationName", TEST_APP_NAME)
            .pathParam("eventTypeName", "nonexistent-event-type")
            .when()
            .get("/notifications/bundles/{bundleName}/applications/{applicationName}/eventTypes/{eventTypeName}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void testUpdateEventTypeEndpointsNotFoundEventType() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        given()
            .header(identityHeader)
            .pathParam("eventTypeId", UUID.randomUUID())
            .contentType(JSON)
            .body(Set.of())
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void testUpdateEventTypeEndpointsWithNonexistentEndpoint() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Bundle bundle = helpers.createBundle("v3-nep-bundle", "V3 NEP Bundle");
        Application app = helpers.createApplication(bundle.getId(), "v3-nep-app", "V3 NEP App");
        EventType eventType = helpers.createEventType(app.getId(), "v3-nep-et", "V3 NEP ET", "V3 NEP ET");

        given()
            .header(identityHeader)
            .pathParam("eventTypeId", eventType.getId())
            .contentType(JSON)
            .body(Set.of(UUID.randomUUID()))
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void testGetLinkedEndpointsPagination() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Bundle bundle = helpers.createBundle("v3-page-ep-bundle", "V3 Page EP Bundle");
        Application app = helpers.createApplication(bundle.getId(), "v3-page-ep-app", "V3 Page EP App");
        EventType eventType = helpers.createEventType(app.getId(), "v3-page-ep-et", "V3 Page EP ET", "V3 Page EP ET");

        UUID ep1 = helpers.createWebhookEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "v3-page-ep-1");
        UUID ep2 = helpers.createWebhookEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "v3-page-ep-2");
        UUID ep3 = helpers.createWebhookEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "v3-page-ep-3");

        given()
            .header(identityHeader)
            .pathParam("eventTypeId", eventType.getId())
            .contentType(JSON)
            .body(Set.of(ep1, ep2, ep3))
            .when()
            .put("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK);

        String response = given()
            .header(identityHeader)
            .pathParam("eventTypeId", eventType.getId())
            .queryParam("limit", 2)
            .queryParam("offset", 0)
            .when()
            .get("/notifications/eventTypes/{eventTypeId}/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().asString();

        JsonObject page = new JsonObject(response);
        assertEquals(3, page.getJsonObject("meta").getInteger("count"));
        assertEquals(2, page.getJsonArray("data").size());
    }

    private UUID findEventTypeId(Header identityHeader, String eventTypeName) {
        String response = given()
            .header(identityHeader)
            .pathParam("bundleName", TEST_BUNDLE_NAME)
            .pathParam("applicationName", TEST_APP_NAME)
            .pathParam("eventTypeName", eventTypeName)
            .when()
            .get("/notifications/bundles/{bundleName}/applications/{applicationName}/eventTypes/{eventTypeName}")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract().asString();
        return UUID.fromString(new JsonObject(response).getString("id"));
    }
}
