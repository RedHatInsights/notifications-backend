package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response.Status.Family;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.TEXT;
import static javax.ws.rs.core.Response.Status.Family.familyOf;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

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
    private static final int NOT_FOUND = 404;
    private static final int INTERNAL_SERVER_ERROR = 500;
    private static final String NOT_USED = "not-used-in-assertions";

    @Test
    void testCreateNullBundle() {
        createBundle(null, BAD_REQUEST);
    }

    @Test
    void testCreateNullApp() {
        createApp(null, BAD_REQUEST);
    }

    @Test
    void testCreateNullEventType() {
        createEventType(null, BAD_REQUEST);
    }

    @Test
    void testCreateInvalidBundle() {
        createBundle(buildBundle(null, "I am valid"), BAD_REQUEST);
        createBundle(buildBundle("i-am-valid", null), BAD_REQUEST);
        createBundle(buildBundle("I violate the @Pattern constraint", "I am valid"), BAD_REQUEST);
    }

    @Test
    void testCreateInvalidApp() {
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();
        createApp(buildApp(null, "i-am-valid", "I am valid"), BAD_REQUEST);
        createApp(buildApp(bundleId, null, "I am valid"), BAD_REQUEST);
        createApp(buildApp(bundleId, "i-am-valid", null), BAD_REQUEST);
        createApp(buildApp(bundleId, "I violate the @Pattern constraint", "I am valid"), BAD_REQUEST);
    }

    @Test
    void testCreateInvalidEventType() {
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();
        String appId = createApp(bundleId, "app-name", "App", OK).get();
        createEventType(buildEventType(null, "i-am-valid", "I am valid", NOT_USED), BAD_REQUEST);
        createEventType(buildEventType(appId, null, "I am valid", NOT_USED), BAD_REQUEST);
        createEventType(buildEventType(appId, "i-am-valid", null, NOT_USED), BAD_REQUEST);
        createEventType(buildEventType(appId, "I violate the @Pattern constraint", "I am valid", NOT_USED), BAD_REQUEST);
    }

    @Test
    void testBundleNameUniqueSqlConstraint() {
        // Double bundle creation with the same name.
        String nonUniqueBundleName = "bundle-1-name";
        createBundle(nonUniqueBundleName, NOT_USED, OK);
        createBundle(nonUniqueBundleName, NOT_USED, INTERNAL_SERVER_ERROR);
        // We create a bundle with an available name and then rename it to an unavailable name.
        String bundleId = createBundle("bundle-2-name", NOT_USED, OK).get();
        updateBundle(bundleId, nonUniqueBundleName, NOT_USED, INTERNAL_SERVER_ERROR);
    }

    @Test
    void testAppNameUniqueSqlConstraint() {
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();
        // Double app creation with the same name.
        String nonUniqueAppName = "app-1-name";
        createApp(bundleId, nonUniqueAppName, NOT_USED, OK);
        createApp(bundleId, nonUniqueAppName, NOT_USED, INTERNAL_SERVER_ERROR);
        // We create an app with an available name and then rename it to an unavailable name.
        String appId = createApp(bundleId, "app-2-name", NOT_USED, OK).get();
        updateApp(bundleId, appId, nonUniqueAppName, NOT_USED, INTERNAL_SERVER_ERROR);
    }

    @Test
    void testEventTypeNameUniqueSqlConstraint() {
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();
        String appId = createApp(bundleId, "app-name", "App", OK).get();
        // Double event type creation with the same name.
        String nonUniqueEventTypeName = "event-type-name";
        createEventType(appId, nonUniqueEventTypeName, NOT_USED, NOT_USED, OK);
        createEventType(appId, nonUniqueEventTypeName, NOT_USED, NOT_USED, INTERNAL_SERVER_ERROR);
    }

    @Test
    void testGetUnknownBundle() {
        String unknownBundleId = UUID.randomUUID().toString();
        getBundle(unknownBundleId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testGetUnknownApp() {
        String unknownAppId = UUID.randomUUID().toString();
        getApp(unknownAppId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testUpdateNullBundle() {
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();
        updateBundle(bundleId, null, BAD_REQUEST);
    }

    @Test
    void testUpdateNullApp() {
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();
        String appId = createApp(bundleId, "app-name", "App", OK).get();
        updateApp(appId, null, BAD_REQUEST);
    }

    @Test
    void testUpdateUnknownBundle() {
        String unknownBundleId = UUID.randomUUID().toString();
        updateBundle(unknownBundleId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testUpdateUnknownApp() {
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();
        String unknownAppId = UUID.randomUUID().toString();
        updateApp(bundleId, unknownAppId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testAddAppToUnknownBundle() {
        String unknownBundleId = UUID.randomUUID().toString();
        createApp(unknownBundleId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testAddEventTypeToUnknownApp() {
        String unknownAppId = UUID.randomUUID().toString();
        createEventType(unknownAppId, NOT_USED, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testGetAppsFromUnknownBundle() {
        String unknownBundleId = UUID.randomUUID().toString();
        getApps(unknownBundleId, NOT_FOUND, 0);
    }

    @Test
    void testGetEventTypesFromUnknownApp() {
        String unknownAppId = UUID.randomUUID().toString();
        getEventTypes(unknownAppId, NOT_FOUND, 0);
    }

    @Test
    void testCreateAndGetAndUpdateAndDeleteBundle() {
        // First, we create two bundles with different names. Only the second one will be used after that.
        createBundle("bundle-1-name", "Bundle 1", OK).get();
        String bundleId = createBundle("bundle-2-name", "Bundle 2", OK).get();

        // Then the bundle update API is tested.
        updateBundle(bundleId, "bundle-2-new-name", "Bundle 2 new display name", OK);

        // Same for the bundle delete API.
        deleteBundle(bundleId, true);

        // Now that we deleted the bundle, all the following APIs calls should "fail".
        deleteBundle(bundleId, false);
        getBundle(bundleId, NOT_USED, NOT_USED, NOT_FOUND);
        updateBundle(bundleId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testCreateAndGetAndUpdateAndDeleteApp() {
        // We need to persist a bundle for this test.
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();

        // First, we create two apps with different names. Only the second one will be used after that.
        createApp(bundleId, "app-1-name", "App 1", OK);
        String appId = createApp(bundleId, "app-2-name", "App 2", OK).get();

        // The bundle should contain two apps.
        getApps(bundleId, OK, 2);

        // Let's test the app update API.
        updateApp(bundleId, appId, "app-2-new-name", "App 2 new display name", OK);

        // Same for the app delete API.
        deleteApp(appId, true);

        // Now that we deleted the app, all the following APIs calls should "fail".
        deleteApp(appId, false);
        getApp(appId, NOT_USED, NOT_USED, NOT_FOUND);
        updateApp(bundleId, appId, NOT_USED, NOT_USED, NOT_FOUND);

        // The bundle should also contain one app now.
        getApps(bundleId, OK, 1);
    }

    @Test
    void testCreateAndGetAndUpdateAndDeleteEventType() {
        // We need to persist a bundle and an app for this test.
        String bundleId = createBundle("bundle-name", "Bundle", OK).get();
        String appId = createApp(bundleId, "app-name", "App", OK).get();

        // First, we create two event types with different names. Only the second one will be used after that.
        createEventType(appId, "event-type-1-name", "Event type 1", "Description 1", OK);
        String eventTypeId = createEventType(appId, "event-type-2-name", "Event type 2", "Description 2", OK).get();

        // The app should contain two event types.
        getEventTypes(appId, OK, 2);

        // Let's test the event type update API.
        updateEventType(appId, eventTypeId, "event-type-2-new-name", "Event type 2 new display name", "Event type 2 new description", OK);

        // Let's test the event type delete API.
        deleteEventType(eventTypeId, true);

        // Deleting the event type again should not work.
        deleteEventType(eventTypeId, false);

        // The app should also contain one event type now.
        getEventTypes(appId, OK, 1);
    }

    @Test
    void testDefaultBehaviorGroupCRUD() {
        // We need to persist a bundle, an app and two event types for this test.
        String bundleId = createBundle("dbg-bundle-name", "Bundle", OK).get();

        // Creates 2 behavior groups
        String dbgId1 = createDefaultBehaviorGroup("DefaultBehaviorGroup1", bundleId);
        String dbgId2 = createDefaultBehaviorGroup("DefaultBehaviorGroup2", bundleId);

        assertEquals(
                Set.of(
                        Pair.of(dbgId1, "DefaultBehaviorGroup1"),
                        Pair.of(dbgId2, "DefaultBehaviorGroup2")
                ),
                getDefaultBehaviorGroups().stream().map(bg -> Pair.of(bg.getId().toString(), bg.getDisplayName())).collect(Collectors.toSet())
        );

        // Update displayName of behavior group 1
        updateDefaultBehaviorGroup(dbgId1, "Group1", bundleId, true);
        assertEquals(
                Set.of(
                        Pair.of(dbgId1, "Group1"),
                        Pair.of(dbgId2, "DefaultBehaviorGroup2")
                ),
                getDefaultBehaviorGroups().stream().map(bg -> Pair.of(bg.getId().toString(), bg.getDisplayName())).collect(Collectors.toSet())
        );

        // Delete behaviors
        deleteDefaultBehaviorGroup(dbgId1, true);
        assertEquals(
                Set.of(dbgId2),
                getDefaultBehaviorGroups().stream().map(BehaviorGroup::getId).map(UUID::toString).collect(Collectors.toSet())
        );
        deleteDefaultBehaviorGroup(dbgId2, true);
        assertEquals(
                Set.of(),
                getDefaultBehaviorGroups().stream().map(BehaviorGroup::getId).map(UUID::toString).collect(Collectors.toSet())
        );

        // Deleting again yields false
        deleteDefaultBehaviorGroup(dbgId1, false);
    }

    private static Bundle buildBundle(String name, String displayName) {
        Bundle bundle = new Bundle();
        bundle.setName(name);
        bundle.setDisplayName(displayName);
        return bundle;
    }

    private static Optional<String> createBundle(String name, String displayName, int expectedStatusCode) {
        Bundle bundle = buildBundle(name, displayName);
        return createBundle(bundle, expectedStatusCode);
    }

    private static Optional<String> createBundle(Bundle bundle, int expectedStatusCode) {
        String responseBody = given()
                .contentType(JSON)
                .body(Json.encode(bundle))
                .when()
                .post("/internal/bundles")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(familyOf(expectedStatusCode) == Family.SUCCESSFUL ? is(JSON.toString()) : any(String.class))
                .extract().asString();

        if (familyOf(expectedStatusCode) == Family.SUCCESSFUL) {
            JsonObject jsonBundle = new JsonObject(responseBody);
            jsonBundle.mapTo(Bundle.class);
            assertNotNull(jsonBundle.getString("id"));
            assertNotNull(jsonBundle.getString("created"));
            assertEquals(bundle.getName(), jsonBundle.getString("name"));
            assertEquals(bundle.getDisplayName(), jsonBundle.getString("display_name"));

            getBundle(jsonBundle.getString("id"), bundle.getName(), bundle.getDisplayName(), OK);

            return Optional.of(jsonBundle.getString("id"));
        } else {
            return Optional.empty();
        }
    }

    private static void getBundle(String bundleId, String expectedName, String expectedDisplayName, int expectedStatusCode) {
        String responseBody = given()
                .pathParam("bundleId", bundleId)
                .get("/internal/bundles/{bundleId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(JSON)
                .extract().asString();

        if (familyOf(expectedStatusCode) == Family.SUCCESSFUL) {
            JsonObject jsonBundle = new JsonObject(responseBody);
            jsonBundle.mapTo(Bundle.class);
            assertEquals(bundleId, jsonBundle.getString("id"));
            assertEquals(expectedName, jsonBundle.getString("name"));
            assertEquals(expectedDisplayName, jsonBundle.getString("display_name"));
        }
    }

    private static void updateBundle(String bundleId, String name, String displayName, int expectedStatusCode) {
        Bundle bundle = buildBundle(name, displayName);
        updateBundle(bundleId, bundle, expectedStatusCode);
    }

    private static void updateBundle(String bundleId, Bundle bundle, int expectedStatusCode) {
        given()
                .contentType(JSON)
                .pathParam("bundleId", bundleId)
                .body(Json.encode(bundle))
                .put("/internal/bundles/{bundleId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(familyOf(expectedStatusCode) == Family.SUCCESSFUL ? containsString(TEXT.toString()) : any(String.class));

        if (familyOf(expectedStatusCode) == Family.SUCCESSFUL) {
            getBundle(bundleId, bundle.getName(), bundle.getDisplayName(), OK);
        }
    }

    private static void deleteBundle(String bundleId, boolean expectedResult) {
        Boolean result = given()
                .pathParam("bundleId", bundleId)
                .when()
                .delete("/internal/bundles/{bundleId}")
                .then()
                .statusCode(OK)
                .contentType(JSON)
                .extract().body().as(Boolean.class);

        assertEquals(expectedResult, result);
    }

    private static Application buildApp(String bundleId, String name, String displayName) {
        Application app = new Application();
        if (bundleId != null) {
            app.setBundleId(UUID.fromString(bundleId));
        }
        app.setName(name);
        app.setDisplayName(displayName);
        return app;
    }

    private static Optional<String> createApp(String bundleId, String name, String displayName, int expectedStatusCode) {
        Application app = buildApp(bundleId, name, displayName);
        return createApp(app, expectedStatusCode);
    }

    private static Optional<String> createApp(Application app, int expectedStatusCode) {
        String responseBody = given()
                .contentType(JSON)
                .body(Json.encode(app))
                .when()
                .post("/internal/applications")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(familyOf(expectedStatusCode) == Family.SUCCESSFUL ? is(JSON.toString()) : any(String.class))
                .extract().asString();

        if (familyOf(expectedStatusCode) == Family.SUCCESSFUL) {
            JsonObject jsonApp = new JsonObject(responseBody);
            jsonApp.mapTo(Application.class);
            assertNotNull(jsonApp.getString("id"));
            assertNotNull(jsonApp.getString("created"));
            assertEquals(app.getBundleId().toString(), jsonApp.getString("bundle_id"));
            assertEquals(app.getName(), jsonApp.getString("name"));
            assertEquals(app.getDisplayName(), jsonApp.getString("display_name"));

            getApp(jsonApp.getString("id"), app.getName(), app.getDisplayName(), OK);

            return Optional.of(jsonApp.getString("id"));
        } else {
            return Optional.empty();
        }
    }

    private static void getApps(String bundleId, int expectedStatusCode, int expectedAppsCount) {
        String responseBody = given()
                .pathParam("bundleId", bundleId)
                .when()
                .get("/internal/bundles/{bundleId}/applications")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(JSON)
                .extract().asString();

        if (familyOf(expectedStatusCode) == Family.SUCCESSFUL) {
            JsonArray jsonApps = new JsonArray(responseBody);
            assertEquals(expectedAppsCount, jsonApps.size());
            if (expectedAppsCount > 0) {
                for (int i = 0; i < expectedAppsCount; i++) {
                    jsonApps.getJsonObject(i).mapTo(Application.class);
                }
            }
        }
    }

    private static void getApp(String appId, String expectedName, String expectedDisplayName, int expectedStatusCode) {
        String responseBody = given()
                .pathParam("appId", appId)
                .get("/internal/applications/{appId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(JSON)
                .extract().asString();

        if (familyOf(expectedStatusCode) == Family.SUCCESSFUL) {
            JsonObject jsonApp = new JsonObject(responseBody);
            jsonApp.mapTo(Application.class);
            assertEquals(appId, jsonApp.getString("id"));
            assertEquals(expectedName, jsonApp.getString("name"));
            assertEquals(expectedDisplayName, jsonApp.getString("display_name"));
        }
    }

    private static void updateApp(String bundleId, String appId, String name, String displayName, int expectedStatusCode) {
        Application app = buildApp(bundleId, name, displayName);
        updateApp(appId, app, expectedStatusCode);
    }

    private static void updateApp(String appId, Application app, int expectedStatusCode) {
        given()
                .contentType(JSON)
                .pathParam("appId", appId)
                .body(Json.encode(app))
                .put("/internal/applications/{appId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(familyOf(expectedStatusCode) == Family.SUCCESSFUL ? containsString(TEXT.toString()) : any(String.class));

        if (familyOf(expectedStatusCode) == Family.SUCCESSFUL) {
            getApp(appId, app.getName(), app.getDisplayName(), OK);
        }
    }

    private static void deleteApp(String appId, boolean expectedResult) {
        Boolean result = given()
                .pathParam("appId", appId)
                .when()
                .delete("/internal/applications/{appId}")
                .then()
                .statusCode(OK)
                .contentType(JSON)
                .extract().body().as(Boolean.class);

        assertEquals(expectedResult, result);
    }

    private static EventType buildEventType(String appId, String name, String displayName, String description) {
        EventType eventType = new EventType();
        if (appId != null) {
            eventType.setApplicationId(UUID.fromString(appId));
        }
        eventType.setName(name);
        eventType.setDisplayName(displayName);
        eventType.setDescription(description);
        return eventType;
    }

    private static Optional<String> createEventType(String appId, String name, String displayName, String description, int expectedStatusCode) {
        EventType eventType = buildEventType(appId, name, displayName, description);
        return createEventType(eventType, expectedStatusCode);
    }

    private static Optional<String> createEventType(EventType eventType, int expectedStatusCode) {
        String responseBody = given()
                .contentType(JSON)
                .body(Json.encode(eventType))
                .when()
                .post("/internal/eventTypes")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(familyOf(expectedStatusCode) == Family.SUCCESSFUL ? is(JSON.toString()) : any(String.class))
                .extract().asString();

        if (familyOf(expectedStatusCode) == Family.SUCCESSFUL) {
            JsonObject jsonEventType = new JsonObject(responseBody);
            jsonEventType.mapTo(EventType.class);
            assertNotNull(jsonEventType.getString("id"));
            assertEquals(eventType.getApplicationId().toString(), jsonEventType.getString("application_id"));
            assertEquals(eventType.getName(), jsonEventType.getString("name"));
            assertEquals(eventType.getDisplayName(), jsonEventType.getString("display_name"));
            assertEquals(eventType.getDescription(), jsonEventType.getString("description"));

            return Optional.of(jsonEventType.getString("id"));
        } else {
            return Optional.empty();
        }
    }

    private static void getEventTypes(String appId, int expectedStatusCode, int expectedEventTypesCount) {
        String responseBody = given()
                .pathParam("appId", appId)
                .when()
                .get("/internal/applications/{appId}/eventTypes")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(JSON)
                .extract().asString();

        if (familyOf(expectedStatusCode) == Family.SUCCESSFUL) {
            JsonArray jsonEventTypes = new JsonArray(responseBody);
            assertEquals(expectedEventTypesCount, jsonEventTypes.size());
            if (expectedEventTypesCount > 0) {
                for (int i = 0; i < expectedEventTypesCount; i++) {
                    jsonEventTypes.getJsonObject(0).mapTo(EventType.class);
                }
            }
        }
    }

    private static void updateEventType(String appId, String eventTypeId, String name, String displayName, String description, int expectedStatusCode) {
        EventType eventType = buildEventType(appId, name, displayName, description);

        given()
                .contentType(JSON)
                .pathParam("eventTypeId", eventTypeId)
                .body(Json.encode(eventType))
                .put("/internal/eventTypes/{eventTypeId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(familyOf(expectedStatusCode) == Family.SUCCESSFUL ? containsString(TEXT.toString()) : any(String.class));

        if (familyOf(expectedStatusCode) == Family.SUCCESSFUL) {
            String responseBody = given()
                    .pathParam("appId", eventType.getApplicationId())
                    .when()
                    .get("/internal/applications/{appId}/eventTypes")
                    .then()
                    .statusCode(expectedStatusCode)
                    .contentType(JSON)
                    .extract().asString();

            JsonArray jsonEventTypes = new JsonArray(responseBody);
            for (int i = 0; i < jsonEventTypes.size(); i++) {
                JsonObject jsonEventType = jsonEventTypes.getJsonObject(i);
                jsonEventType.mapTo(EventType.class);
                if (jsonEventType.getString("id").equals(eventTypeId)) {
                    assertEquals(eventType.getName(), jsonEventType.getString("name"));
                    assertEquals(eventType.getDisplayName(), jsonEventType.getString("display_name"));
                    assertEquals(eventType.getDescription(), jsonEventType.getString("description"));
                    break;
                }
                if (i == jsonEventTypes.size() - 1) {
                    fail("Event type not found");
                }
            }
        }
    }

    private static void deleteEventType(String eventTypeId, boolean expectedResult) {
        Boolean result = given()
                .pathParam("eventTypeId", eventTypeId)
                .when()
                .delete("/internal/eventTypes/{eventTypeId}")
                .then()
                .statusCode(OK)
                .contentType(JSON)
                .extract().body().as(Boolean.class);

        assertEquals(expectedResult, result);
    }

    private static BehaviorGroup buildDefaultBehaviorGroup(String displayName, String bundleId) {
        BehaviorGroup bg = new BehaviorGroup();
        bg.setDisplayName(displayName);
        bg.setBundleId(UUID.fromString(bundleId));
        return bg;
    }

    private static String createDefaultBehaviorGroup(String displayName, String bundleId) {
        BehaviorGroup behaviorGroup = buildDefaultBehaviorGroup(displayName, bundleId);

        return given()
                .basePath(API_INTERNAL)
                .contentType(JSON)
                .body(Json.encode(behaviorGroup))
                .post("/behaviorGroups/default")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getString("id");
    }

    private static void updateDefaultBehaviorGroup(String behaviorGroupId, String displayName, String bundleId, boolean expectedResult) {
        BehaviorGroup behaviorGroup = buildDefaultBehaviorGroup(displayName, bundleId);

        Boolean result = given()
                .basePath(API_INTERNAL)
                .contentType(JSON)
                .body(Json.encode(behaviorGroup))
                .pathParam("behavior_group_id", behaviorGroupId)
                .put("/behaviorGroups/default/{behavior_group_id}")
                .then()
                .statusCode(200)
                .extract().as(Boolean.class);

        assertEquals(expectedResult, result);
    }

    private static void deleteDefaultBehaviorGroup(String behaviorGroupId, boolean expectedResult) {
        Boolean result = given()
                .basePath(API_INTERNAL)
                .pathParam("behavior_group_id", behaviorGroupId)
                .delete("/behaviorGroups/default/{behavior_group_id}")
                .then()
                .statusCode(200)
                .extract().as(Boolean.class);

        assertEquals(expectedResult, result);
    }

    private static List<BehaviorGroup> getDefaultBehaviorGroups() {
        List<?> behaviorGroups = given()
                .basePath(API_INTERNAL)
                .get("/behaviorGroups/default")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract()
                .body().as(List.class);

        return behaviorGroups.stream().map(rawBg -> {
            Map<String, Object> mbg = (Map<String, Object>) rawBg;
            BehaviorGroup bg = new BehaviorGroup();
            bg.setId(UUID.fromString(mbg.get("id").toString()));
            bg.setDisplayName(mbg.get("display_name").toString());
            bg.setBundleId(UUID.fromString(mbg.get("bundle_id").toString()));

            return bg;
        }).collect(Collectors.toList());
    }
}
