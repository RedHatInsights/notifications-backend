package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
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
import static io.restassured.http.ContentType.TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class InternalResourceTest extends DbIsolatedTest {

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

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    FeatureFlipper featureFlipper;

    @Test
    void testCreateNullBundle_AccountId() {
        featureFlipper.setUseOrgId(false);
        testCreateNullBundle();
    }

    @Test
    void testCreateNullBundle_OrgId() {
        featureFlipper.setUseOrgId(true);
        testCreateNullBundle();
        featureFlipper.setUseOrgId(false);
    }

    void testCreateNullBundle() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        createBundle(identity, null, BAD_REQUEST);
    }

    @Test
    void testCreateNullApp_AccountId() {
        featureFlipper.setUseOrgId(false);
        testCreateNullApp();
    }

    @Test
    void testCreateNullApp_OrgId() {
        featureFlipper.setUseOrgId(true);
        testCreateNullApp();
        featureFlipper.setUseOrgId(false);
    }

    void testCreateNullApp() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        createApp(identity, null, null, BAD_REQUEST);
    }

    @Test
    void testCreateNullEventType_AccountId() {
        featureFlipper.setUseOrgId(false);
        testCreateNullEventType();
    }

    @Test
    void testCreateNullEventType_OrgId() {
        featureFlipper.setUseOrgId(true);
        testCreateNullEventType();
        featureFlipper.setUseOrgId(false);
    }

    void testCreateNullEventType() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        createEventType(identity, null, BAD_REQUEST);
    }

    @Test
    void testCreateInvalidBundle_AccountId() {
        featureFlipper.setUseOrgId(false);
        testCreateInvalidBundle();
    }

    @Test
    void testCreateInvalidBundle_OrgId() {
        featureFlipper.setUseOrgId(true);
        testCreateInvalidBundle();
        featureFlipper.setUseOrgId(false);
    }

    void testCreateInvalidBundle() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        createBundle(identity, buildBundle(null, "I am valid"), BAD_REQUEST);
        createBundle(identity, buildBundle("i-am-valid", null), BAD_REQUEST);
        createBundle(identity, buildBundle("I violate the @Pattern constraint", "I am valid"), BAD_REQUEST);
    }

    @Test
    void testCreateInvalidApp_AccountId() {
        featureFlipper.setUseOrgId(false);
        testCreateInvalidApp();
    }

    @Test
    void testCreateInvalidApp_OrgId() {
        featureFlipper.setUseOrgId(true);
        testCreateInvalidApp();
        featureFlipper.setUseOrgId(false);
    }

    void testCreateInvalidApp() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(identity, "bundle-name", "Bundle", OK).get();
        createApp(identity, buildApp(null, "i-am-valid", "I am valid"), null, BAD_REQUEST);
        createApp(identity, buildApp(bundleId, null, "I am valid"), null, BAD_REQUEST);
        createApp(identity, buildApp(bundleId, "i-am-valid", null), null, BAD_REQUEST);
        createApp(identity, buildApp(bundleId, "I violate the @Pattern constraint", "I am valid"), null, BAD_REQUEST);
    }

    @Test
    void testCreateInvalidEventType_AccountId() {
        featureFlipper.setUseOrgId(false);
        testCreateInvalidEventType();
    }

    @Test
    void testCreateInvalidEventType_OrgId() {
        featureFlipper.setUseOrgId(true);
        testCreateInvalidEventType();
        featureFlipper.setUseOrgId(false);
    }

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
    void testBundleNameUniqueSqlConstraint_AccountId() {
        featureFlipper.setUseOrgId(false);
        testBundleNameUniqueSqlConstraint();
    }

    @Test
    void testBundleNameUniqueSqlConstraint_OrgId() {
        featureFlipper.setUseOrgId(true);
        testBundleNameUniqueSqlConstraint();
        featureFlipper.setUseOrgId(false);
    }

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
    void testAppNameUniqueSqlConstraint_AccountId() {
        featureFlipper.setUseOrgId(false);
        testAppNameUniqueSqlConstraint();
    }

    @Test
    void testAppNameUniqueSqlConstraint_OrgId() {
        featureFlipper.setUseOrgId(true);
        testAppNameUniqueSqlConstraint();
        featureFlipper.setUseOrgId(false);
    }

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
    void testEventTypeNameUniqueSqlConstraint_AccountId() {
        featureFlipper.setUseOrgId(false);
        testEventTypeNameUniqueSqlConstraint();
    }

    @Test
    void testEventTypeNameUniqueSqlConstraint_OrgId() {
        featureFlipper.setUseOrgId(true);
        testEventTypeNameUniqueSqlConstraint();
        featureFlipper.setUseOrgId(false);
    }

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
    void testGetUnknownBundle_AccountId() {
        featureFlipper.setUseOrgId(false);
        testGetUnknownBundle();
    }

    @Test
    void testGetUnknownBundle_OrgId() {
        featureFlipper.setUseOrgId(true);
        testGetUnknownBundle();
        featureFlipper.setUseOrgId(false);
    }

    void testGetUnknownBundle() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownBundleId = UUID.randomUUID().toString();
        getBundle(identity, unknownBundleId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testGetUnknownApp_AccountId() {
        featureFlipper.setUseOrgId(false);
        testGetUnknownApp();
    }

    @Test
    void testGetUnknownApp_OrgId() {
        featureFlipper.setUseOrgId(true);
        testGetUnknownApp();
        featureFlipper.setUseOrgId(false);
    }

    void testGetUnknownApp() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownAppId = UUID.randomUUID().toString();
        getApp(identity, unknownAppId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testUpdateNullBundle_AccountId() {
        featureFlipper.setUseOrgId(false);
        testUpdateNullBundle();
    }

    @Test
    void testUpdateNullBundle_OrgId() {
        featureFlipper.setUseOrgId(true);
        testUpdateNullBundle();
        featureFlipper.setUseOrgId(false);
    }

    void testUpdateNullBundle() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(identity, "bundle-name", "Bundle", OK).get();
        updateBundle(identity, bundleId, null, BAD_REQUEST);
    }

    @Test
    void testUpdateNullApp_AccountId() {
        featureFlipper.setUseOrgId(false);
        testUpdateNullApp();
    }

    @Test
    void testUpdateNullApp_OrgId() {
        featureFlipper.setUseOrgId(true);
        testUpdateNullApp();
        featureFlipper.setUseOrgId(false);
    }

    void testUpdateNullApp() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(identity, "bundle-name", "Bundle", OK).get();
        String appId = createApp(identity, bundleId, "app-name", "App", null, OK).get();
        updateApp(identity, appId, null, BAD_REQUEST);
    }

    @Test
    void testUpdateUnknownBundle_AccountId() {
        featureFlipper.setUseOrgId(false);
        testUpdateUnknownBundle();
    }

    @Test
    void testUpdateUnknownBundle_OrgId() {
        featureFlipper.setUseOrgId(true);
        testUpdateUnknownBundle();
        featureFlipper.setUseOrgId(false);
    }

    void testUpdateUnknownBundle() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownBundleId = UUID.randomUUID().toString();
        updateBundle(identity, unknownBundleId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testUpdateUnknownApp_AccountId() {
        featureFlipper.setUseOrgId(false);
        testUpdateUnknownApp();
    }

    @Test
    void testUpdateUnknownApp_OrgId() {
        featureFlipper.setUseOrgId(true);
        testUpdateUnknownApp();
        featureFlipper.setUseOrgId(false);
    }

    void testUpdateUnknownApp() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(identity, "bundle-name", "Bundle", OK).get();
        String unknownAppId = UUID.randomUUID().toString();
        updateApp(identity, bundleId, unknownAppId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testAddAppToUnknownBundle_AccountId() {
        featureFlipper.setUseOrgId(false);
        testAddAppToUnknownBundle();
    }

    @Test
    void testAddAppToUnknownBundle_OrgId() {
        featureFlipper.setUseOrgId(true);
        testAddAppToUnknownBundle();
        featureFlipper.setUseOrgId(false);
    }

    void testAddAppToUnknownBundle() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownBundleId = UUID.randomUUID().toString();
        createApp(identity, unknownBundleId, NOT_USED, NOT_USED, null, NOT_FOUND);
    }

    @Test
    void testAddEventTypeToUnknownApp_AccountId() {
        featureFlipper.setUseOrgId(false);
        testAddEventTypeToUnknownApp();
    }

    @Test
    void testAddEventTypeToUnknownApp_OrgId() {
        featureFlipper.setUseOrgId(true);
        testAddEventTypeToUnknownApp();
        featureFlipper.setUseOrgId(false);
    }

    void testAddEventTypeToUnknownApp() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownAppId = UUID.randomUUID().toString();
        createEventType(identity, unknownAppId, NOT_USED, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @Test
    void testGetAppsFromUnknownBundle_AccountId() {
        featureFlipper.setUseOrgId(false);
        testGetAppsFromUnknownBundle();
    }

    @Test
    void testGetAppsFromUnknownBundle_OrgId() {
        featureFlipper.setUseOrgId(true);
        testGetAppsFromUnknownBundle();
        featureFlipper.setUseOrgId(false);
    }

    void testGetAppsFromUnknownBundle() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownBundleId = UUID.randomUUID().toString();
        getApps(identity, unknownBundleId, NOT_FOUND, 0);
    }

    @Test
    void testGetEventTypesFromUnknownApp_AccountId() {
        featureFlipper.setUseOrgId(false);
        testGetEventTypesFromUnknownApp();
    }

    @Test
    void testGetEventTypesFromUnknownApp_OrgId() {
        featureFlipper.setUseOrgId(true);
        testGetEventTypesFromUnknownApp();
        featureFlipper.setUseOrgId(false);
    }

    void testGetEventTypesFromUnknownApp() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownAppId = UUID.randomUUID().toString();
        getEventTypes(identity, unknownAppId, NOT_FOUND, 0);
    }

    @Test
    void testCreateAndGetAndUpdateAndDeleteBundle_AccountId() {
        featureFlipper.setUseOrgId(false);
        testCreateAndGetAndUpdateAndDeleteBundle();
    }

    @Test
    void testCreateAndGetAndUpdateAndDeleteBundle_OrgId() {
        featureFlipper.setUseOrgId(true);
        testCreateAndGetAndUpdateAndDeleteBundle();
        featureFlipper.setUseOrgId(false);
    }

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
    void testCreateAndGetAndUpdateAndDeleteApp_AccountId() {
        featureFlipper.setUseOrgId(false);
        testCreateAndGetAndUpdateAndDeleteApp();
    }

    @Test
    void testCreateAndGetAndUpdateAndDeleteApp_OrgId() {
        featureFlipper.setUseOrgId(true);
        testCreateAndGetAndUpdateAndDeleteApp();
        featureFlipper.setUseOrgId(false);
    }

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
    void testCreateAndGetAndUpdateAndDeleteEventType_AccountId() {
        featureFlipper.setUseOrgId(false);
        testCreateAndGetAndUpdateAndDeleteEventType();
    }

    @Test
    void testCreateAndGetAndUpdateAndDeleteEventType_OrgId() {
        featureFlipper.setUseOrgId(true);
        testCreateAndGetAndUpdateAndDeleteEventType();
        featureFlipper.setUseOrgId(false);
    }

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
    void testDefaultBehaviorGroupCRUD_AccountId() {
        featureFlipper.setUseOrgId(false);
        testDefaultBehaviorGroupCRUD();
    }

    @Test
    void testDefaultBehaviorGroupCRUD_OrgId() {
        featureFlipper.setUseOrgId(true);
        testDefaultBehaviorGroupCRUD();
        featureFlipper.setUseOrgId(false);
    }

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
        updateDefaultBehaviorGroup(identity, dbgId1, "Group1", bundleId, true, OK);
        assertEquals(
                Set.of(
                        Pair.of(dbgId1, "Group1"),
                        Pair.of(dbgId2, "DefaultBehaviorGroup2")
                ),
                getDefaultBehaviorGroups(identity).stream().map(bg -> Pair.of(bg.getId().toString(), bg.getDisplayName())).collect(Collectors.toSet())
        );

        // Delete behaviors
        deleteDefaultBehaviorGroup(identity, dbgId1, true, OK);
        assertEquals(
                Set.of(dbgId2),
                getDefaultBehaviorGroups(identity).stream().map(BehaviorGroup::getId).map(UUID::toString).collect(Collectors.toSet())
        );
        deleteDefaultBehaviorGroup(identity, dbgId2, true, OK);
        assertEquals(
                Set.of(),
                getDefaultBehaviorGroups(identity).stream().map(BehaviorGroup::getId).map(UUID::toString).collect(Collectors.toSet())
        );

        // The default behavior group no longer exists, deleting it again will cause a 404 status.
        deleteDefaultBehaviorGroup(identity, dbgId1, false, NOT_FOUND);

        // Updating an unknown default behavior group will cause a 404 status.
        updateDefaultBehaviorGroup(identity, UUID.randomUUID().toString(), "Behavior group", bundleId, false, NOT_FOUND);
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
    void appUserAccessTest_AccountId() {
        featureFlipper.setUseOrgId(false);
        appUserAccessTest();
    }

    @Test
    void appUserAccessTest_OrgId() {
        featureFlipper.setUseOrgId(true);
        appUserAccessTest();
        featureFlipper.setUseOrgId(false);
    }

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
        updateDefaultBehaviorGroup(appIdentity, defaultBGId, NOT_USED, bundleId, false, FORBIDDEN);
        deleteDefaultBehaviorGroup(appIdentity, defaultBGId, false, FORBIDDEN);

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
