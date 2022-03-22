package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateRequest;
import com.redhat.cloud.notifications.templates.TemplateEngineClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.ws.rs.BadRequestException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.CrudTestHelpers.buildApp;
import static com.redhat.cloud.notifications.CrudTestHelpers.buildBundle;
import static com.redhat.cloud.notifications.CrudTestHelpers.buildEventType;
import static com.redhat.cloud.notifications.CrudTestHelpers.createApp;
import static com.redhat.cloud.notifications.CrudTestHelpers.createBundle;
import static com.redhat.cloud.notifications.CrudTestHelpers.createDefaultBehaviorGroup;
import static com.redhat.cloud.notifications.CrudTestHelpers.createEventType;
import static com.redhat.cloud.notifications.CrudTestHelpers.createInternalRoleAccess;
import static com.redhat.cloud.notifications.CrudTestHelpers.deleteApp;
import static com.redhat.cloud.notifications.CrudTestHelpers.deleteBundle;
import static com.redhat.cloud.notifications.CrudTestHelpers.deleteDefaultBehaviorGroup;
import static com.redhat.cloud.notifications.CrudTestHelpers.deleteEventType;
import static com.redhat.cloud.notifications.CrudTestHelpers.getApp;
import static com.redhat.cloud.notifications.CrudTestHelpers.getApps;
import static com.redhat.cloud.notifications.CrudTestHelpers.getBundle;
import static com.redhat.cloud.notifications.CrudTestHelpers.getDefaultBehaviorGroups;
import static com.redhat.cloud.notifications.CrudTestHelpers.getEventTypes;
import static com.redhat.cloud.notifications.CrudTestHelpers.updateApp;
import static com.redhat.cloud.notifications.CrudTestHelpers.updateBundle;
import static com.redhat.cloud.notifications.CrudTestHelpers.updateDefaultBehaviorGroup;
import static com.redhat.cloud.notifications.CrudTestHelpers.updateEventType;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class InternalServiceTest extends DbIsolatedTest {

    /*
     * In the tests below, most JSON responses are verified using JsonObject/JsonArray instead of deserializing these
     * responses into model instances and checking their attributes values. That's because the model classes contain
     * attributes annotated with @JsonProperty(access = READ_ONLY) which can't be deserialized and therefore verified
     * here. The deserialization is still performed only to verify that the JSON responses data structure is correct.
     */

    private static final int OK = 200;
    private static final int BAD_REQUEST = 400;
    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;
    private static final int INTERNAL_SERVER_ERROR = 500;
    private static final String NOT_USED = "not-used-in-assertions";

    @InjectMock
    TemplateEngineClient templateEngineClient;

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Test
    void testCreateNullBundle() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        createBundle(identity, null, BAD_REQUEST);
    }

    @Test
    void testCreateNullApp() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        createApp(identity, null, null, BAD_REQUEST);
    }

    @Test
    void testCreateNullEventType() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        createEventType(identity, null, BAD_REQUEST);
    }

    @Test
    void testCreateInvalidBundle() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        createBundle(identity, buildBundle(null, "I am valid"), BAD_REQUEST);
        createBundle(identity, buildBundle("i-am-valid", null), BAD_REQUEST);
        createBundle(identity, buildBundle("I violate the @Pattern constraint", "I am valid"), BAD_REQUEST);
    }

    @Test
    void testCreateInvalidApp() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(identity, "bundle-name", "Bundle", OK).get();
        createApp(identity, buildApp(null, "i-am-valid", "I am valid"), null, BAD_REQUEST);
        createApp(identity, buildApp(bundleId, null, "I am valid"), null, BAD_REQUEST);
        createApp(identity, buildApp(bundleId, "i-am-valid", null), null, BAD_REQUEST);
        createApp(identity, buildApp(bundleId, "I violate the @Pattern constraint", "I am valid"), null, BAD_REQUEST);
    }

    @Test
    void testCreateInvalidEventType() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(identity, "bundle-name", "Bundle", OK).get();
        String appId = createApp(identity, bundleId, "app-name", "App", null, OK).get();
        createEventType(identity, buildEventType(null, "i-am-valid", "I am valid", NOT_USED), BAD_REQUEST);
        createEventType(identity, buildEventType(appId, null, "I am valid", NOT_USED), BAD_REQUEST);
        createEventType(identity, buildEventType(appId, "i-am-valid", null, NOT_USED), BAD_REQUEST);
        createEventType(identity, buildEventType(appId, "I violate the @Pattern constraint", "I am valid", NOT_USED), BAD_REQUEST);
    }

    @Test
    void testBundleNameUniqueSqlConstraint() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        // Double bundle creation with the same name.
        String nonUniqueBundleName = "bundle-1-name";
        createBundle(identity, nonUniqueBundleName, NOT_USED, OK);
        createBundle(identity, nonUniqueBundleName, NOT_USED, INTERNAL_SERVER_ERROR);
        // We create a bundle with an available name and then rename it to an unavailable name.
        String bundleId = createBundle(identity, "bundle-2-name", NOT_USED, OK).get();
        updateBundle(identity, bundleId, nonUniqueBundleName, NOT_USED, INTERNAL_SERVER_ERROR);
    }

    @Test
    void testAppNameUniqueSqlConstraint() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(identity, "bundle-name", "Bundle", OK).get();
        // Double app creation with the same name.
        String nonUniqueAppName = "app-1-name";
        createApp(identity, bundleId, nonUniqueAppName, NOT_USED, null, OK);
        createApp(identity, bundleId, nonUniqueAppName, NOT_USED, null, INTERNAL_SERVER_ERROR);
        // We create an app with an available name and then rename it to an unavailable name.
        String appId = createApp(identity, bundleId, "app-2-name", NOT_USED, null, OK).get();
        updateApp(identity, bundleId, appId, nonUniqueAppName, NOT_USED, INTERNAL_SERVER_ERROR);
    }

    @Test
    void testEventTypeNameUniqueSqlConstraint() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(identity, "bundle-name", "Bundle", OK).get();
        String appId = createApp(identity, bundleId, "app-name", "App", null, OK).get();
        // Double event type creation with the same name.
        String nonUniqueEventTypeName = "event-type-name";
        createEventType(identity, appId, nonUniqueEventTypeName, NOT_USED, NOT_USED, OK);
        createEventType(identity, appId, nonUniqueEventTypeName, NOT_USED, NOT_USED, INTERNAL_SERVER_ERROR);
    }

    @Test
    void testGetUnknownBundle() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownBundleId = UUID.randomUUID().toString();
        getBundle(identity, unknownBundleId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testGetUnknownApp() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownAppId = UUID.randomUUID().toString();
        getApp(identity, unknownAppId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testUpdateNullBundle() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(identity, "bundle-name", "Bundle", OK).get();
        updateBundle(identity, bundleId, null, BAD_REQUEST);
    }

    @Test
    void testUpdateNullApp() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(identity, "bundle-name", "Bundle", OK).get();
        String appId = createApp(identity, bundleId, "app-name", "App", null, OK).get();
        updateApp(identity, appId, null, BAD_REQUEST);
    }

    @Test
    void testUpdateUnknownBundle() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownBundleId = UUID.randomUUID().toString();
        updateBundle(identity, unknownBundleId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testUpdateUnknownApp() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(identity, "bundle-name", "Bundle", OK).get();
        String unknownAppId = UUID.randomUUID().toString();
        updateApp(identity, bundleId, unknownAppId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testAddAppToUnknownBundle() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownBundleId = UUID.randomUUID().toString();
        createApp(identity, unknownBundleId, NOT_USED, NOT_USED, null, NOT_FOUND);
    }

    @Test
    void testAddEventTypeToUnknownApp() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownAppId = UUID.randomUUID().toString();
        createEventType(identity, unknownAppId, NOT_USED, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testGetAppsFromUnknownBundle() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownBundleId = UUID.randomUUID().toString();
        getApps(identity, unknownBundleId, NOT_FOUND, 0);
    }

    @Test
    void testGetEventTypesFromUnknownApp() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownAppId = UUID.randomUUID().toString();
        getEventTypes(identity, unknownAppId, NOT_FOUND, 0);
    }

    @Test
    void testCreateAndGetAndUpdateAndDeleteBundle() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        // First, we create two bundles with different names. Only the second one will be used after that.
        createBundle(identity, "bundle-1-name", "Bundle 1", OK).get();
        String bundleId = createBundle(identity, "bundle-2-name", "Bundle 2", OK).get();

        // Then the bundle update API is tested.
        updateBundle(identity, bundleId, "bundle-2-new-name", "Bundle 2 new display name", OK);

        // Same for the bundle delete API.
        deleteBundle(identity, bundleId, true);

        // Now that we deleted the bundle, all the following APIs calls should "fail".
        deleteBundle(identity, bundleId, false);
        getBundle(identity, bundleId, NOT_USED, NOT_USED, NOT_FOUND);
        updateBundle(identity, bundleId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testCreateAndGetAndUpdateAndDeleteApp() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        // We need to persist a bundle for this test.
        String bundleId = createBundle(identity, "bundle-name", "Bundle", OK).get();

        // First, we create two apps with different names. Only the second one will be used after that.
        createApp(identity, bundleId, "app-1-name", "App 1", null, OK);
        String appId = createApp(identity, bundleId, "app-2-name", "App 2", null, OK).get();

        // The bundle should contain two apps.
        getApps(identity, bundleId, OK, 2);

        // Let's test the app update API.
        updateApp(identity, bundleId, appId, "app-2-new-name", "App 2 new display name", OK);

        // Same for the app delete API.
        deleteApp(identity, appId, true);

        // Now that we deleted the app, all the following APIs calls should "fail".
        deleteApp(identity, appId, false);
        getApp(identity, appId, NOT_USED, NOT_USED, NOT_FOUND);
        updateApp(identity, bundleId, appId, NOT_USED, NOT_USED, NOT_FOUND);

        // The bundle should also contain one app now.
        getApps(identity, bundleId, OK, 1);
    }

    @Test
    void testCreateAndGetAndUpdateAndDeleteEventType() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        // We need to persist a bundle and an app for this test.
        String bundleId = createBundle(identity, "bundle-name", "Bundle", OK).get();
        String appId = createApp(identity, bundleId, "app-name", "App", null, OK).get();

        // First, we create two event types with different names. Only the second one will be used after that.
        createEventType(identity, appId, "event-type-1-name", "Event type 1", "Description 1", OK);
        String eventTypeId = createEventType(identity, appId, "event-type-2-name", "Event type 2", "Description 2", OK).get();

        // The app should contain two event types.
        getEventTypes(identity, appId, OK, 2);

        // Let's test the event type update API.
        updateEventType(identity, appId, eventTypeId, "event-type-2-new-name", "Event type 2 new display name", "Event type 2 new description", OK);

        // Let's test the event type delete API.
        deleteEventType(identity, eventTypeId, true);

        // Deleting the event type again should not work.
        deleteEventType(identity, eventTypeId, false);

        // The app should also contain one event type now.
        getEventTypes(identity, appId, OK, 1);
    }

    @Test
    void testDefaultBehaviorGroupCRUD() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        // We need to persist a bundle.
        String bundleId = createBundle(identity, "dbg-bundle-name", "Bundle", OK).get();

        // Creates 2 behavior groups
        String dbgId1 = createDefaultBehaviorGroup(identity, "DefaultBehaviorGroup1", bundleId, OK).get();
        String dbgId2 = createDefaultBehaviorGroup(identity, "DefaultBehaviorGroup2", bundleId, OK).get();

        assertEquals(
                Set.of(
                        Pair.of(dbgId1, "DefaultBehaviorGroup1"),
                        Pair.of(dbgId2, "DefaultBehaviorGroup2")
                ),
                getDefaultBehaviorGroups(identity).stream().map(bg -> Pair.of(bg.getId().toString(), bg.getDisplayName())).collect(Collectors.toSet())
        );

        // Update displayName of behavior group 1
        updateDefaultBehaviorGroup(identity, dbgId1, "Group1", bundleId, true);
        assertEquals(
                Set.of(
                        Pair.of(dbgId1, "Group1"),
                        Pair.of(dbgId2, "DefaultBehaviorGroup2")
                ),
                getDefaultBehaviorGroups(identity).stream().map(bg -> Pair.of(bg.getId().toString(), bg.getDisplayName())).collect(Collectors.toSet())
        );

        // Delete behaviors
        deleteDefaultBehaviorGroup(identity, dbgId1, true);
        assertEquals(
                Set.of(dbgId2),
                getDefaultBehaviorGroups(identity).stream().map(BehaviorGroup::getId).map(UUID::toString).collect(Collectors.toSet())
        );
        deleteDefaultBehaviorGroup(identity, dbgId2, true);
        assertEquals(
                Set.of(),
                getDefaultBehaviorGroups(identity).stream().map(BehaviorGroup::getId).map(UUID::toString).collect(Collectors.toSet())
        );

        // Deleting again yields false
        deleteDefaultBehaviorGroup(identity, dbgId1, false);
    }

    @Test
    void testInvalidEmailTemplateRendering() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        RenderEmailTemplateRequest request = new RenderEmailTemplateRequest();
        request.setPayload("I am invalid!");
        request.setSubjectTemplate(""); // Not important, won't be used.
        request.setBodyTemplate(""); // Not important, won't be used.

        JsonObject exceptionMessage = new JsonObject();
        exceptionMessage.put("message", "Action parsing failed for payload: I am invalid!");
        BadRequestException badRequest = new BadRequestException(exceptionMessage.toString());
        when(templateEngineClient.render(Mockito.any(RenderEmailTemplateRequest.class))).thenThrow(badRequest);

        String responseBody = given()
                .basePath(API_INTERNAL)
                .header(identity)
                .contentType(JSON)
                .body(Json.encode(request))
                .when()
                .post("/templates/email/render")
                .then()
                .contentType(JSON)
                .statusCode(400)
                .extract().asString();

        assertEquals("Action parsing failed for payload: I am invalid!", new JsonObject(responseBody).getString("message"));
    }

    @Test
    void testVersion() {
        String responseBody = given()
                .basePath(API_INTERNAL)
                .when()
                .get("/version")
                .then()
                .contentType(TEXT)
                .statusCode(200)
                .extract().asString();
        assertTrue(responseBody.matches("^[0-9a-f]{7}$"));
    }

    @Test
    void appUserAccessTest() {
        String appRole = "app-admin";
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("admin", adminRole);
        Header appIdentity = TestHelpers.createTurnpikeIdentityHeader("user", appRole);
        Header otherAppIdentity = TestHelpers.createTurnpikeIdentityHeader("user", "other-app-admin");

        String bundleName = "admin-bundle";
        String app1Name = "admin-app-1";
        String app2Name = "admin-app-2";
        String event1Name = "admin-event-1";
        String event2Name = "admin-event-1";

        String bundleId = createBundle(adminIdentity, bundleName, NOT_USED, OK).get();
        String defaultBGId = createDefaultBehaviorGroup(adminIdentity, NOT_USED, bundleId, OK).get();
        String app1Id = createApp(adminIdentity, bundleId, app1Name, NOT_USED, null, OK).get();
        String app2Id = createApp(adminIdentity, bundleId, app2Name, NOT_USED, null, OK).get();

        createEventType(adminIdentity, app1Id, event1Name, NOT_USED, NOT_USED, OK);
        String eventType2Id = createEventType(adminIdentity, app2Id, event2Name, NOT_USED, NOT_USED, OK).get();

        // Gives access to `appIdentity` to `app1`
        createInternalRoleAccess(adminIdentity, appRole, app1Id, OK);

        // free for all internal users
        getBundle(appIdentity, bundleId, bundleName, NOT_USED, OK);
        getApp(appIdentity, app1Id, app1Name, NOT_USED, OK);
        getEventTypes(appIdentity, app1Id, OK, 1);
        getDefaultBehaviorGroups(appIdentity);

        // Only allowed to admins
        createBundle(appIdentity, "app-user-bundle", NOT_USED, FORBIDDEN);
        updateBundle(appIdentity, bundleId, "new-name", NOT_USED, FORBIDDEN);
        deleteBundle(appIdentity, bundleId, null, FORBIDDEN);

        createApp(appIdentity, bundleId, "app-user-app", NOT_USED, null, FORBIDDEN);
        deleteApp(appIdentity, app1Id, null, FORBIDDEN);

        createDefaultBehaviorGroup(appIdentity, NOT_USED, bundleId, FORBIDDEN);
        updateDefaultBehaviorGroup(appIdentity, defaultBGId, NOT_USED, bundleId, null, FORBIDDEN);
        deleteDefaultBehaviorGroup(appIdentity, defaultBGId, null, FORBIDDEN);

        // Allowed depending the permissions
        updateApp(appIdentity, bundleId, app1Id, "new-name", NOT_USED, OK);
        updateApp(appIdentity, bundleId, app2Id, "new-name", NOT_USED, FORBIDDEN);
        updateApp(otherAppIdentity, bundleId, app2Id, "new-name", NOT_USED, FORBIDDEN);
        updateApp(otherAppIdentity, bundleId, app2Id, "new-name", NOT_USED, FORBIDDEN);

        String newEventTypeId = createEventType(appIdentity, app1Id, "new-name-1", NOT_USED, NOT_USED, OK).get();
        createEventType(appIdentity, app2Id, "new-name-2", NOT_USED, NOT_USED, FORBIDDEN);
        createEventType(otherAppIdentity, app1Id, "new-name-3", NOT_USED, NOT_USED, FORBIDDEN);
        createEventType(otherAppIdentity, app2Id, "new-name-4", NOT_USED, NOT_USED, FORBIDDEN);

        updateEventType(otherAppIdentity, app1Id, newEventTypeId, NOT_USED, NOT_USED, NOT_USED, FORBIDDEN);
        updateEventType(appIdentity, app1Id, newEventTypeId, NOT_USED, NOT_USED, NOT_USED, OK);

        deleteEventType(otherAppIdentity, newEventTypeId, null, FORBIDDEN);
        deleteEventType(otherAppIdentity, eventType2Id, null, FORBIDDEN);
        deleteEventType(appIdentity, newEventTypeId, true, OK);
        deleteEventType(appIdentity, eventType2Id, null, FORBIDDEN);
    }
}
