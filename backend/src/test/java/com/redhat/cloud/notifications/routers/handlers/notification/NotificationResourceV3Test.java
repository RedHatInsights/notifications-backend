package com.redhat.cloud.notifications.routers.handlers.notification;

import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
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

import java.util.UUID;

import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.FULL_ACCESS;
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
