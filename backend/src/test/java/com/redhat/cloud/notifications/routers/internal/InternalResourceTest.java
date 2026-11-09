package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.BehaviorGroupRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointEventTypeRepository;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.routers.dailydigest.TriggerDailyDigestRequest;
import com.redhat.cloud.notifications.routers.engine.DailyDigestService;
import com.redhat.cloud.notifications.routers.engine.GeneralCommunicationsService;
import com.redhat.cloud.notifications.routers.general.communication.SendGeneralCommunicationResponse;
import com.redhat.cloud.notifications.routers.internal.models.BulkEventTypeBehaviorRequest;
import com.redhat.cloud.notifications.routers.internal.models.RequestDefaultBehaviorGroupPropertyList;
import com.redhat.cloud.notifications.routers.internal.models.dto.SendGeneralCommunicationRequest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.restassured.http.Header;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.Constants.API_INTERNAL_NOTIFICATIONS_BACKEND;
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
import static com.redhat.cloud.notifications.CrudTestHelpers.updateEventTypeVisibility;
import static com.redhat.cloud.notifications.TestHelpers.createTurnpikeIdentityHeader;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
    ResourceHelpers resourceHelpers;

    /**
     * Mock the REST Service to avoid receiving errors from it.
     */
    @InjectMock
    @RestClient
    DailyDigestService dailyDigestService;

    @InjectMock
    Environment environment;

    @Inject
    EntityManager entityManager;

    @InjectMock
    @RestClient
    GeneralCommunicationsService generalCommunicationsService;

    @InjectSpy
    SubscriptionRepository subscriptionRepository;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

    @Inject
    EndpointEventTypeRepository endpointEventTypeRepository;

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testCreateNullBundle(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        createBundle(basePath, identity, null, BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testCreateNullApp(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        createApp(basePath, identity, null, null, BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testCreateNullEventType(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        createEventType(basePath, identity, null, BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testCreateInvalidBundle(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        createBundle(basePath, identity, buildBundle(null, "I am valid"), BAD_REQUEST);
        createBundle(basePath, identity, buildBundle("i-am-valid", null), BAD_REQUEST);
        createBundle(basePath, identity, buildBundle("I violate the @Pattern constraint", "I am valid"), BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testCreateInvalidApp(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(basePath, identity, "bundle-name", "Bundle", OK).get();
        createApp(basePath, identity, buildApp(null, "i-am-valid", "I am valid"), null, BAD_REQUEST);
        createApp(basePath, identity, buildApp(bundleId, null, "I am valid"), null, BAD_REQUEST);
        createApp(basePath, identity, buildApp(bundleId, "i-am-valid", null), null, BAD_REQUEST);
        createApp(basePath, identity, buildApp(bundleId, "I violate the @Pattern constraint", "I am valid"), null, BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testCreateInvalidEventType(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(basePath, identity, "bundle-name", "Bundle", OK).get();
        String appId = createApp(basePath, identity, bundleId, "app-name", "App", null, OK).get();
        createEventType(basePath, identity, buildEventType(null, "i-am-valid", "I am valid", NOT_USED, false, false), BAD_REQUEST);
        createEventType(basePath, identity, buildEventType(appId, null, "I am valid", NOT_USED, false, false), BAD_REQUEST);
        createEventType(basePath, identity, buildEventType(appId, "i-am-valid", null, NOT_USED, false, false), BAD_REQUEST);
        createEventType(basePath, identity, buildEventType(appId, "I violate the @Pattern constraint", "I am valid", NOT_USED, false, false), BAD_REQUEST);
        // When EventType#subscriptionLocked is true, EventType#subscribedByDefault has to be true as well.
        createEventType(basePath, identity, buildEventType(appId, "i-am-valid", "I am valid", NOT_USED, false, true), BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testBundleNameUniqueSqlConstraint(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        // Double bundle creation with the same name.
        String nonUniqueBundleName = "bundle-1-name";
        createBundle(basePath, identity, nonUniqueBundleName, NOT_USED, OK);
        createBundle(basePath, identity, nonUniqueBundleName, NOT_USED, INTERNAL_SERVER_ERROR);
        // We create a bundle with an available name and then rename it to an unavailable name.
        String bundleId = createBundle(basePath, identity, "bundle-2-name", NOT_USED, OK).get();
        updateBundle(basePath, identity, bundleId, nonUniqueBundleName, NOT_USED, INTERNAL_SERVER_ERROR);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testAppNameUniqueSqlConstraint(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(basePath, identity, "bundle-name", "Bundle", OK).get();
        // Double app creation with the same name.
        String nonUniqueAppName = "app-1-name";
        createApp(basePath, identity, bundleId, nonUniqueAppName, NOT_USED, null, OK);
        createApp(basePath, identity, bundleId, nonUniqueAppName, NOT_USED, null, INTERNAL_SERVER_ERROR);
        // We create an app with an available name and then rename it to an unavailable name.
        String appId = createApp(basePath, identity, bundleId, "app-2-name", NOT_USED, null, OK).get();
        updateApp(basePath, identity, bundleId, appId, nonUniqueAppName, NOT_USED, INTERNAL_SERVER_ERROR);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testEventTypeNameUniqueSqlConstraint(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(basePath, identity, "bundle-name", "Bundle", OK).get();
        String appId = createApp(basePath, identity, bundleId, "app-name", "App", null, OK).get();
        // Double event type creation with the same name.
        String nonUniqueEventTypeName = "event-type-name";
        createEventType(basePath, identity, appId, nonUniqueEventTypeName, NOT_USED, NOT_USED, false, false, OK);
        createEventType(basePath, identity, appId, nonUniqueEventTypeName, NOT_USED, NOT_USED, false, false, INTERNAL_SERVER_ERROR);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testGetUnknownBundle(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownBundleId = UUID.randomUUID().toString();
        getBundle(basePath, identity, unknownBundleId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testGetUnknownApp(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownAppId = UUID.randomUUID().toString();
        getApp(basePath, identity, unknownAppId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testUpdateNullBundle(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(basePath, identity, "bundle-name", "Bundle", OK).get();
        updateBundle(basePath, identity, bundleId, null, BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testUpdateNullApp(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(basePath, identity, "bundle-name", "Bundle", OK).get();
        String appId = createApp(basePath, identity, bundleId, "app-name", "App", null, OK).get();
        updateApp(basePath, identity, appId, null, BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testUpdateUnknownBundle(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownBundleId = UUID.randomUUID().toString();
        updateBundle(basePath, identity, unknownBundleId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testUpdateUnknownApp(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String bundleId = createBundle(basePath, identity, "bundle-name", "Bundle", OK).get();
        String unknownAppId = UUID.randomUUID().toString();
        updateApp(basePath, identity, bundleId, unknownAppId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testAddAppToUnknownBundle(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownBundleId = UUID.randomUUID().toString();
        createApp(basePath, identity, unknownBundleId, NOT_USED, NOT_USED, null, NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testAddEventTypeToUnknownApp(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownAppId = UUID.randomUUID().toString();
        createEventType(basePath, identity, unknownAppId, NOT_USED, NOT_USED, NOT_USED, false, false, NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testGetAppsFromUnknownBundle(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownBundleId = UUID.randomUUID().toString();
        getApps(basePath, identity, unknownBundleId, NOT_FOUND, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testGetEventTypesFromUnknownApp(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String unknownAppId = UUID.randomUUID().toString();
        getEventTypes(basePath, identity, unknownAppId, NOT_FOUND, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testCreateAndGetAndUpdateAndDeleteBundle(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        // First, we create two bundles with different names. Only the second one will be used after that.
        createBundle(basePath, identity, "bundle-1-name", "Bundle 1", OK).get();
        String bundleId = createBundle(basePath, identity, "bundle-2-name", "Bundle 2", OK).get();

        // Then the bundle update API is tested.
        updateBundle(basePath, identity, bundleId, "bundle-2-new-name", "Bundle 2 new display name", OK);

        // Same for the bundle delete API.
        deleteBundle(basePath, identity, bundleId, true);

        // Now that we deleted the bundle, all the following APIs calls should "fail".
        deleteBundle(basePath, identity, bundleId, false);
        getBundle(basePath, identity, bundleId, NOT_USED, NOT_USED, NOT_FOUND);
        updateBundle(basePath, identity, bundleId, NOT_USED, NOT_USED, NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testCreateAndGetAndUpdateAndDeleteApp(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        // We need to persist a bundle for this test.
        String bundleId = createBundle(basePath, identity, "bundle-name", "Bundle", OK).get();

        // First, we create two apps with different names. Only the second one will be used after that.
        createApp(basePath, identity, bundleId, "app-1-name", "App 1", null, OK);
        String appId = createApp(basePath, identity, bundleId, "app-2-name", "App 2", null, OK).get();

        // The bundle should contain two apps.
        getApps(basePath, identity, bundleId, OK, 2);

        // Let's test the app update API.
        updateApp(basePath, identity, bundleId, appId, "app-2-new-name", "App 2 new display name", OK);

        // Same for the app delete API.
        deleteApp(basePath, identity, appId, true);

        // Now that we deleted the app, all the following APIs calls should "fail".
        deleteApp(basePath, identity, appId, false);
        getApp(basePath, identity, appId, NOT_USED, NOT_USED, NOT_FOUND);
        updateApp(basePath, identity, bundleId, appId, NOT_USED, NOT_USED, NOT_FOUND);

        // The bundle should also contain one app now.
        getApps(basePath, identity, bundleId, OK, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testCreateAndGetAndUpdateAndDeleteEventType(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        // We need to persist a bundle and an app for this test.
        String bundleId = createBundle(basePath, identity, "bundle-name", "Bundle", OK).get();
        String appId = createApp(basePath, identity, bundleId, "app-name", "App", null, OK).get();

        // First, we create two event types with different names. Only the second one will be used after that.
        createEventType(basePath, identity, appId, "event-type-1-name", "Event type 1", "Description 1", false, false, OK);
        String eventTypeId = createEventType(basePath, identity, appId, "event-type-2-name", "Event type 2", "Description 2", false, false, OK).get();

        checkEventTypeRestrictToRecipientsIntegrations(eventTypeId, false);

        // The app should contain two event types.
        getEventTypes(basePath, identity, appId, OK, 2);

        // Let's test the event type update API.
        updateEventType(basePath, identity, appId, eventTypeId, "event-type-2-new-name", "Event type 2 new display name", "Event type 2 new description", true, true, true, OK);
        verify(subscriptionRepository, times(1)).resubscribeAllUsersIfNeeded(any(UUID.class));
        checkEventTypeRestrictToRecipientsIntegrations(eventTypeId, true);

        checkEventTypeVisibility(eventTypeId, true);
        updateEventTypeVisibility(basePath, identity, eventTypeId, false, OK);
        checkEventTypeVisibility(eventTypeId, false);
        updateEventTypeVisibility(basePath, identity, eventTypeId, true, OK);
        checkEventTypeVisibility(eventTypeId, true);
        updateEventTypeVisibility(basePath, identity, "unknown", true, NOT_FOUND);

        // Let's test the event type delete API.
        deleteEventType(basePath, identity, eventTypeId, true);

        // Deleting the event type again should not work.
        deleteEventType(basePath, identity, eventTypeId, false);

        // The app should also contain one event type now.
        getEventTypes(basePath, identity, appId, OK, 1);
    }

    void checkEventTypeVisibility(String eventTypeId, boolean isVisible) {
        entityManager.clear();
        EventType eventType = entityManager.find(EventType.class, UUID.fromString(eventTypeId));
        assertEquals(isVisible, eventType.isVisible());
    }

    void checkEventTypeRestrictToRecipientsIntegrations(String eventTypeId, boolean isResticted) {
        entityManager.clear();
        EventType eventType = entityManager.find(EventType.class, UUID.fromString(eventTypeId));
        assertEquals(isResticted, eventType.isRestrictToRecipientsIntegrations());
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testDefaultBehaviorGroupCRUD(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        // We need to persist a bundle.
        String bundleId = createBundle(basePath, identity, "dbg-bundle-name", "Bundle", OK).get();

        // Creates 2 behavior groups
        String dbgId1 = createDefaultBehaviorGroup(basePath, identity, "DefaultBehaviorGroup1", bundleId, OK).get();
        String dbgId2 = createDefaultBehaviorGroup(basePath, identity, "DefaultBehaviorGroup2", bundleId, OK).get();

        assertEquals(
                Set.of(
                        Pair.of(dbgId1, "DefaultBehaviorGroup1"),
                        Pair.of(dbgId2, "DefaultBehaviorGroup2")
                ),
                getDefaultBehaviorGroups(basePath, identity).stream().map(bg -> Pair.of(bg.getId().toString(), bg.getDisplayName())).collect(Collectors.toSet())
        );

        // Update displayName of behavior group 1
        updateDefaultBehaviorGroup(basePath, identity, dbgId1, "Group1", bundleId, true, OK);
        assertEquals(
                Set.of(
                        Pair.of(dbgId1, "Group1"),
                        Pair.of(dbgId2, "DefaultBehaviorGroup2")
                ),
                getDefaultBehaviorGroups(basePath, identity).stream().map(bg -> Pair.of(bg.getId().toString(), bg.getDisplayName())).collect(Collectors.toSet())
        );

        // Delete behaviors
        deleteDefaultBehaviorGroup(basePath, identity, dbgId1, true, OK);
        assertEquals(
                Set.of(dbgId2),
                getDefaultBehaviorGroups(basePath, identity).stream().map(BehaviorGroup::getId).map(UUID::toString).collect(Collectors.toSet())
        );
        deleteDefaultBehaviorGroup(basePath, identity, dbgId2, true, OK);
        assertEquals(
                Set.of(),
                getDefaultBehaviorGroups(basePath, identity).stream().map(BehaviorGroup::getId).map(UUID::toString).collect(Collectors.toSet())
        );

        // The default behavior group no longer exists, deleting it again will cause a 404 status.
        deleteDefaultBehaviorGroup(basePath, identity, dbgId1, false, NOT_FOUND);

        // Updating an unknown default behavior group will cause a 404 status.
        updateDefaultBehaviorGroup(basePath, identity, UUID.randomUUID().toString(), "Behavior group", bundleId, false, NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testDefaultBehaviorGroupCRUDWithDepencies(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        // We need to persist a bundle.
        String bundleId = createBundle(basePath, identity, "dbg-bundle-name", "Bundle", OK).get();
        String appId = createApp(basePath, identity, bundleId, "app-name", "App", null, OK).get();
        EventType eventType1 = new EventType();
        eventType1.setName(RandomStringUtils.randomAlphabetic(10).toLowerCase());
        eventType1.setDisplayName(RandomStringUtils.randomAlphabetic(10));
        eventType1.setDescription(RandomStringUtils.randomAlphabetic(10));
        eventType1.setApplicationId(UUID.fromString(appId));
        UUID uuidEventType1 = UUID.fromString(createEventType(basePath, identity, eventType1, OK).get());

        // Creates 1 behavior groups
        UUID dbgId1 = UUID.fromString(createDefaultBehaviorGroup(basePath, identity, "DefaultBehaviorGroup1", bundleId, OK).get());

        RequestDefaultBehaviorGroupPropertyList defaultBehaviorGroupProperties = new RequestDefaultBehaviorGroupPropertyList();
        defaultBehaviorGroupProperties.setOnlyAdmins(true);

        given()
            .basePath(basePath)
            .header(identity)
            .contentType(JSON)
            .pathParam("behaviorGroupId", dbgId1)
            .body(Json.encode(List.of(defaultBehaviorGroupProperties)))
            .when()
            .put("/behaviorGroups/default/{behaviorGroupId}/actions")
            .then()
            .statusCode(200);

        List<BehaviorGroup> listBg = getDefaultBehaviorGroups(basePath, identity);
        Optional<BehaviorGroup> createdBg1 = listBg.stream().filter(bg -> bg.getId().equals(dbgId1)).findFirst();
        assertTrue(createdBg1.isPresent());
        assertEquals(1, createdBg1.get().getActions().size());
        assertEquals(0, createdBg1.get().getBehaviors().size());

        UUID endpointId1 = createdBg1.get().getActions().getFirst().getId().endpointId;

        assertEquals(0, resourceHelpers.getEndpoint(endpointId1).getEventTypes().size());

        // link it to something
        given()
            .basePath(basePath)
            .header(identity)
            .pathParam("behaviorGroupId", dbgId1)
            .pathParam("eventTypeId", uuidEventType1)
            .when()
            .put("/behaviorGroups/default/{behaviorGroupId}/eventType/{eventTypeId}")
            .then()
            .statusCode(200);

        assertEquals(1, resourceHelpers.getEndpoint(endpointId1).getEventTypes().size());

        // unlink it
        given()
            .basePath(basePath)
            .header(identity)
            .pathParam("behaviorGroupId", dbgId1)
            .pathParam("eventTypeId", uuidEventType1)
            .when()
            .delete("/behaviorGroups/default/{behaviorGroupId}/eventType/{eventTypeId}")
            .then()
            .statusCode(200);

        assertEquals(0, resourceHelpers.getEndpoint(endpointId1).getEventTypes().size());

    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testBulkUpdateDefaultBehaviorEventTypes(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        // Setup: bundle, app, three event types, default behavior group.
        String bundleId = createBundle(basePath, identity, "bulk-bundle", "Bundle", OK).get();
        String appId = createApp(basePath, identity, bundleId, "bulk-app", "App", null, OK).get();

        EventType eventType1 = new EventType();
        eventType1.setName(RandomStringUtils.randomAlphabetic(10).toLowerCase());
        eventType1.setDisplayName(RandomStringUtils.randomAlphabetic(10));
        eventType1.setDescription(RandomStringUtils.randomAlphabetic(10));
        eventType1.setApplicationId(UUID.fromString(appId));
        UUID et1Id = UUID.fromString(createEventType(basePath, identity, eventType1, OK).get());

        EventType eventType2 = new EventType();
        eventType2.setName(RandomStringUtils.randomAlphabetic(10).toLowerCase());
        eventType2.setDisplayName(RandomStringUtils.randomAlphabetic(10));
        eventType2.setDescription(RandomStringUtils.randomAlphabetic(10));
        eventType2.setApplicationId(UUID.fromString(appId));
        UUID et2Id = UUID.fromString(createEventType(basePath, identity, eventType2, OK).get());

        EventType eventType3 = new EventType();
        eventType3.setName(RandomStringUtils.randomAlphabetic(10).toLowerCase());
        eventType3.setDisplayName(RandomStringUtils.randomAlphabetic(10));
        eventType3.setDescription(RandomStringUtils.randomAlphabetic(10));
        eventType3.setApplicationId(UUID.fromString(appId));
        UUID et3Id = UUID.fromString(createEventType(basePath, identity, eventType3, OK).get());

        UUID dbgId = UUID.fromString(createDefaultBehaviorGroup(basePath, identity, "BulkTestBG", bundleId, OK).get());

        // AC1: Bulk link two event types.
        BulkEventTypeBehaviorRequest linkRequest = new BulkEventTypeBehaviorRequest();
        linkRequest.eventTypeIdsToLink = Set.of(et1Id, et2Id);

        given()
            .basePath(basePath)
            .header(identity)
            .contentType(JSON)
            .pathParam("behaviorGroupId", dbgId)
            .body(Json.encode(linkRequest))
            .when()
            .put("/behaviorGroups/default/{behaviorGroupId}/eventTypes")
            .then()
            .statusCode(200);

        List<BehaviorGroup> bgs = getDefaultBehaviorGroups(basePath, identity);
        BehaviorGroup bg = bgs.stream().filter(b -> b.getId().equals(dbgId)).findFirst().get();
        assertEquals(2, bg.getBehaviors().size());

        // AC2: Bulk link and unlink in the same call — link et3, unlink et1.
        BulkEventTypeBehaviorRequest linkUnlinkRequest = new BulkEventTypeBehaviorRequest();
        linkUnlinkRequest.eventTypeIdsToLink = Set.of(et3Id);
        linkUnlinkRequest.eventTypeIdsToUnlink = Set.of(et1Id);

        given()
            .basePath(basePath)
            .header(identity)
            .contentType(JSON)
            .pathParam("behaviorGroupId", dbgId)
            .body(Json.encode(linkUnlinkRequest))
            .when()
            .put("/behaviorGroups/default/{behaviorGroupId}/eventTypes")
            .then()
            .statusCode(200);

        bgs = getDefaultBehaviorGroups(basePath, identity);
        bg = bgs.stream().filter(b -> b.getId().equals(dbgId)).findFirst().get();
        assertEquals(2, bg.getBehaviors().size());
        Set<UUID> linkedIds = bg.getBehaviors().stream()
                .map(b -> b.getId().eventTypeId)
                .collect(Collectors.toSet());
        assertTrue(linkedIds.contains(et2Id));
        assertTrue(linkedIds.contains(et3Id));
        Assertions.assertFalse(linkedIds.contains(et1Id));

        // AC3: Overlap validation — same ID in both link and unlink sets returns 400.
        BulkEventTypeBehaviorRequest overlapRequest = new BulkEventTypeBehaviorRequest();
        overlapRequest.eventTypeIdsToLink = Set.of(et1Id);
        overlapRequest.eventTypeIdsToUnlink = Set.of(et1Id);

        given()
            .basePath(basePath)
            .header(identity)
            .contentType(JSON)
            .pathParam("behaviorGroupId", dbgId)
            .body(Json.encode(overlapRequest))
            .when()
            .put("/behaviorGroups/default/{behaviorGroupId}/eventTypes")
            .then()
            .statusCode(BAD_REQUEST);

        // AC4: Cross-bundle validation — event type from a different bundle returns 400.
        String otherBundleId = createBundle(basePath, identity, "other-bundle", "Other", OK).get();
        String otherAppId = createApp(basePath, identity, otherBundleId, "other-app", "Other App", null, OK).get();
        EventType crossBundleEt = new EventType();
        crossBundleEt.setName(RandomStringUtils.randomAlphabetic(10).toLowerCase());
        crossBundleEt.setDisplayName(RandomStringUtils.randomAlphabetic(10));
        crossBundleEt.setDescription(RandomStringUtils.randomAlphabetic(10));
        crossBundleEt.setApplicationId(UUID.fromString(otherAppId));
        UUID crossBundleEtId = UUID.fromString(createEventType(basePath, identity, crossBundleEt, OK).get());

        BulkEventTypeBehaviorRequest crossBundleRequest = new BulkEventTypeBehaviorRequest();
        crossBundleRequest.eventTypeIdsToLink = Set.of(crossBundleEtId);

        given()
            .basePath(basePath)
            .header(identity)
            .contentType(JSON)
            .pathParam("behaviorGroupId", dbgId)
            .body(Json.encode(crossBundleRequest))
            .when()
            .put("/behaviorGroups/default/{behaviorGroupId}/eventTypes")
            .then()
            .statusCode(BAD_REQUEST);

        // AC5: Nonexistent eventTypeId returns 400 instead of 500.
        BulkEventTypeBehaviorRequest nonexistentRequest = new BulkEventTypeBehaviorRequest();
        nonexistentRequest.eventTypeIdsToLink = Set.of(UUID.randomUUID());

        given()
            .basePath(basePath)
            .header(identity)
            .contentType(JSON)
            .pathParam("behaviorGroupId", dbgId)
            .body(Json.encode(nonexistentRequest))
            .when()
            .put("/behaviorGroups/default/{behaviorGroupId}/eventTypes")
            .then()
            .statusCode(BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testVersion(String basePath) {
        String responseBody = given()
                .basePath(basePath)
                .when()
                .get("/version")
                .then()
                .contentType(TEXT)
                .statusCode(200)
                .extract().asString();
        assertTrue(responseBody.matches("^[0-9a-f]{7}$"));
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void appUserAccessTest(String basePath) {
        String appRole = "app-admin";
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("admin", adminRole);
        Header appIdentity = TestHelpers.createTurnpikeIdentityHeader("user", appRole);
        Header otherAppIdentity = TestHelpers.createTurnpikeIdentityHeader("user", "other-app-admin");

        String bundleName = "admin-bundle";
        String app1Name = "admin-app-1";
        String app2Name = "admin-app-2";
        String event1Name = "admin-event-1";
        String event2Name = "admin-event-1";

        String bundleId = createBundle(basePath, adminIdentity, bundleName, NOT_USED, OK).get();
        String defaultBGId = createDefaultBehaviorGroup(basePath, adminIdentity, NOT_USED, bundleId, OK).get();
        String app1Id = createApp(basePath, adminIdentity, bundleId, app1Name, NOT_USED, null, OK).get();
        String app2Id = createApp(basePath, adminIdentity, bundleId, app2Name, NOT_USED, null, OK).get();

        createEventType(basePath, adminIdentity, app1Id, event1Name, NOT_USED, NOT_USED, false, false, OK);
        String eventType2Id = createEventType(basePath, adminIdentity, app2Id, event2Name, NOT_USED, NOT_USED, false, false, OK).get();

        // Gives access to `appIdentity` to `app1`
        createInternalRoleAccess(basePath, adminIdentity, appRole, app1Id, OK);

        // free for all internal users
        getBundle(basePath, appIdentity, bundleId, bundleName, NOT_USED, OK);
        getApp(basePath, appIdentity, app1Id, app1Name, NOT_USED, OK);
        getEventTypes(basePath, appIdentity, app1Id, OK, 1);
        getDefaultBehaviorGroups(basePath, appIdentity);

        // Only allowed to admins
        createBundle(basePath, appIdentity, "app-user-bundle", NOT_USED, FORBIDDEN);
        updateBundle(basePath, appIdentity, bundleId, "new-name", NOT_USED, FORBIDDEN);
        deleteBundle(basePath, appIdentity, bundleId, null, FORBIDDEN);

        createApp(basePath, appIdentity, bundleId, "app-user-app", NOT_USED, null, FORBIDDEN);
        deleteApp(basePath, appIdentity, app1Id, null, FORBIDDEN);

        createDefaultBehaviorGroup(basePath, appIdentity, NOT_USED, bundleId, FORBIDDEN);
        updateDefaultBehaviorGroup(basePath, appIdentity, defaultBGId, NOT_USED, bundleId, false, FORBIDDEN);
        deleteDefaultBehaviorGroup(basePath, appIdentity, defaultBGId, false, FORBIDDEN);

        // Allowed depending the permissions
        updateApp(basePath, appIdentity, bundleId, app1Id, "new-name", NOT_USED, OK);
        updateApp(basePath, appIdentity, bundleId, app2Id, "new-name", NOT_USED, FORBIDDEN);
        updateApp(basePath, otherAppIdentity, bundleId, app2Id, "new-name", NOT_USED, FORBIDDEN);
        updateApp(basePath, otherAppIdentity, bundleId, app2Id, "new-name", NOT_USED, FORBIDDEN);

        String newEventTypeId = createEventType(basePath, appIdentity, app1Id, "new-name-1", NOT_USED, NOT_USED, false, false, OK).get();
        createEventType(basePath, appIdentity, app2Id, "new-name-2", NOT_USED, NOT_USED, false, false, FORBIDDEN);
        createEventType(basePath, otherAppIdentity, app1Id, "new-name-3", NOT_USED, NOT_USED, false, false, FORBIDDEN);
        createEventType(basePath, otherAppIdentity, app2Id, "new-name-4", NOT_USED, NOT_USED, false, false, FORBIDDEN);

        updateEventType(basePath, otherAppIdentity, app1Id, newEventTypeId, NOT_USED, NOT_USED, NOT_USED, false, false, false, FORBIDDEN);
        verify(subscriptionRepository, times(0)).resubscribeAllUsersIfNeeded(any(UUID.class));
        updateEventType(basePath, appIdentity, app1Id, newEventTypeId, NOT_USED, NOT_USED, NOT_USED, false, false, false, OK);
        verify(subscriptionRepository, times(1)).resubscribeAllUsersIfNeeded(any(UUID.class));

        updateEventTypeVisibility(basePath, otherAppIdentity, newEventTypeId, true, FORBIDDEN);
        updateEventTypeVisibility(basePath, adminIdentity, newEventTypeId, true, OK);

        deleteEventType(basePath, otherAppIdentity, newEventTypeId, null, FORBIDDEN);
        deleteEventType(basePath, otherAppIdentity, eventType2Id, null, FORBIDDEN);
        deleteEventType(basePath, appIdentity, newEventTypeId, true, OK);
        deleteEventType(basePath, appIdentity, eventType2Id, null, FORBIDDEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    public void testDailyDigestTimePreference(String basePath) {
        // The test runs once per internal base path and the stored preference is not cleaned up, so each run needs its own org ID.
        String orgId = UUID.randomUUID().toString();

        // try to get time preference from an unknown org ID
        given()
            .basePath(basePath)
            .header(createTurnpikeIdentityHeader("admin", adminRole))
            .when()
            .get("/daily-digest/time-preference/" + orgId)
            .then()
            .statusCode(404);

        // Set time preference to the org ID
        LocalTime localTime = LocalTime.now(ZoneOffset.UTC).withNano(0);
        given()
            .basePath(basePath)
            .header(createTurnpikeIdentityHeader("admin", adminRole))
            .when()
            .contentType(JSON)
            .body(localTime)
            .put("/daily-digest/time-preference/" + orgId)
            .then()
            .statusCode(200);

        // Get time preference from the org ID
        LocalTime storedLocalTime = given()
            .basePath(basePath)
            .header(createTurnpikeIdentityHeader("admin", adminRole))
            .when()
            .get("/daily-digest/time-preference/" + orgId)
            .then()
            .statusCode(200)
            .contentType(JSON).extract().as(LocalTime.class);
        assertEquals(localTime, storedLocalTime);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    public void testDailyDigestTimePreferenceInsufficentPrivileges(String basePath) {
        given()
            .basePath(basePath)
            .header(createTurnpikeIdentityHeader("admin", "none"))
            .when()
            .contentType(JSON)
            .body(LocalTime.now(ZoneOffset.UTC).withNano(0))
            .put("/daily-digest/time-preference/1234")
            .then()
            .statusCode(403);
    }

    /**
     * Tests that the daily digest cannot be triggered in an environment that
     * isn't "local" or "stage".
     */
    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    public void testTriggerDailyDigestNonStage(String basePath) {
        // Simulate that we are in the prod environment.
        Mockito.when(this.environment.isLocal()).thenReturn(false);
        Mockito.when(this.environment.isStage()).thenReturn(false);

        final TriggerDailyDigestRequest triggerDailyDigestRequest = new TriggerDailyDigestRequest(
            "application-name",
            "bundle-name",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "organization-id",
            null,
            null
        );

        final String response = given()
            .basePath(basePath)
            .header(createTurnpikeIdentityHeader("admin", adminRole))
            .when()
            .contentType(JSON)
            .body(Json.encode(triggerDailyDigestRequest))
            .post("/daily-digest/trigger")
            .then()
            .statusCode(400)
            .extract()
            .asString();

        Assertions.assertEquals("the daily digests can only be triggered in the stage environment", response, "unexpected error message received");
    }

    /**
     * Tests that a bad request is returned when a daily digest is triggered
     * with a blank application and bundle names.
     */
    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    public void testTriggerDailyDigest(String basePath) {
        final Bundle bundle = this.resourceHelpers.createBundle();
        final Application application = this.resourceHelpers.createApplication(bundle.getId());

        final LocalDateTime end = LocalDateTime.now();
        final LocalDateTime start = end.minusDays(5);

        final TriggerDailyDigestRequest triggerDailyDigestRequest = new TriggerDailyDigestRequest(
            application.getName(),
            bundle.getName(),
            bundle.getId(),
            application.getId(),
            "org-id",
            start,
            end
        );

        // Simulate that we are in the stage environment.
        Mockito.when(this.environment.isStage()).thenReturn(true);

        given()
            .basePath(basePath)
            .header(createTurnpikeIdentityHeader("admin", adminRole))
            .when()
            .contentType(JSON)
            .body(Json.encode(triggerDailyDigestRequest))
            .post("/daily-digest/trigger")
            .then()
            .statusCode(204);

        // Ensure that the sent payload to the engine matches the one that was
        // sent to the handler under test.
        final ArgumentCaptor<TriggerDailyDigestRequest> capturedPayload = ArgumentCaptor.forClass(TriggerDailyDigestRequest.class);
        verify(this.dailyDigestService).triggerDailyDigest(capturedPayload.capture());

        final TriggerDailyDigestRequest capturedDto = capturedPayload.getValue();
        Assertions.assertEquals(triggerDailyDigestRequest.getApplicationName(), capturedDto.getApplicationName());
        Assertions.assertEquals(triggerDailyDigestRequest.getBundleName(), capturedDto.getBundleName());
        Assertions.assertEquals(triggerDailyDigestRequest.getOrgId(), capturedDto.getOrgId());
        Assertions.assertEquals(triggerDailyDigestRequest.getEnd(), capturedDto.getEnd());
        Assertions.assertEquals(triggerDailyDigestRequest.getStart(), capturedDto.getStart());
    }

    /**
     * Tests that a bad request is returned when a daily digest is triggered
     * with an invalid application name.
     */
    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    public void testTriggerDailyDigestInvalidApplication(String basePath) {
        final String bundleName = "test-trigger-daily-digest-invalid-application";
        Bundle bundle = this.resourceHelpers.createBundle(bundleName, bundleName + "-display-name");

        final TriggerDailyDigestRequest triggerDailyDigestRequest = new TriggerDailyDigestRequest(
            UUID.randomUUID().toString(),
            bundleName,
            bundle.getId(),
            UUID.randomUUID(),
            "trigger-daily-digest-invalid-application-name-org-id",
            null,
            null
        );

        // Simulate that we are in the stage environment.
        Mockito.when(this.environment.isStage()).thenReturn(true);

        final String response = given()
            .basePath(basePath)
            .header(createTurnpikeIdentityHeader("admin", adminRole))
            .when()
            .contentType(JSON)
            .body(Json.encode(triggerDailyDigestRequest))
            .post("/daily-digest/trigger")
            .then()
            .statusCode(400)
            .extract()
            .asString();

        Assertions.assertNotNull(response, "unexpected null response received from the handler");
        Assertions.assertFalse(response.isBlank(), "unexpected blank response received from the handler");

        Assertions.assertEquals("unable to find the specified application — bundle combination", response, "unexpected error message received");
    }

    /**
     * Tests that a bad request is returned when a daily digest is triggered
     * with an invalid bundle name.
     */
    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    public void testTriggerDailyDigestInvalidBundle(String basePath) {
        final String applicationName = "test-trigger-daily-digest-invalid-bundle";

        final Bundle bundle = this.resourceHelpers.createBundle();
        final Application application = this.resourceHelpers.createApplication(bundle.getId(), applicationName, applicationName + "-display");

        final TriggerDailyDigestRequest triggerDailyDigestRequest = new TriggerDailyDigestRequest(
                application.getName(),
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                application.getId(),
                "trigger-daily-digest-invalid-bundle-name-org-id",
                null,
                null
        );

        // Simulate that we are in the stage environment.
        Mockito.when(this.environment.isStage()).thenReturn(true);

        final String response = given()
                .basePath(basePath)
                .header(createTurnpikeIdentityHeader("admin", adminRole))
                .when()
                .contentType(JSON)
                .body(Json.encode(triggerDailyDigestRequest))
                .post("/daily-digest/trigger")
                .then()
                .statusCode(400)
                .extract()
                .asString();

        Assertions.assertNotNull(response, "unexpected null response received from the handler");
        Assertions.assertFalse(response.isBlank(), "unexpected blank response received from the handler");

        Assertions.assertEquals("unable to find the specified application — bundle combination", response, "unexpected error message received");
    }

    /**
     * Tests that a bad request is returned when a daily digest is triggered
     * with an invalid payload.
     */
    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    public void testTriggerDailyDigestInvalidPayload(String basePath) {
        /*
         * Create a simple class to store test cases and
         */
        class TestCase {
            /**
             * The DTO under test.
             */
            public final TriggerDailyDigestRequest testDto;
            /**
             * The expected field that should be reported as having an error.
             */
            public final String expectedErrorField;

            TestCase(final TriggerDailyDigestRequest testDto, final String expectedErrorField) {
                this.testDto = testDto;
                this.expectedErrorField = expectedErrorField;
            }
        }

        final List<TestCase> testCaseList = new ArrayList<>();

        testCaseList.add(
            new TestCase(
                new TriggerDailyDigestRequest(
                    "     ",
                    "bundle-name-blank-application-name",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "org-id-blank-application-name",
                    null,
                    null
                ),
                "applicationName"
            )
        );

        testCaseList.add(
            new TestCase(
                new TriggerDailyDigestRequest(
                    "application-name-blank-bundle-name",
                    "     ",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "org-id-blank-bundle-name",
                    null,
                    null
                ),
                "bundleName"
            )
        );

        testCaseList.add(
            new TestCase(
                new TriggerDailyDigestRequest(
                    "application-name-blank-org-id",
                    "bundle-name-blank-org-id",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "     ",
                    null,
                    null
                ),
                "orgId"
            )
        );

        // Simulate that we are in the stage environment.
        Mockito.when(this.environment.isStage()).thenReturn(true);

        for (final TestCase testCase : testCaseList) {

            final String response = given()
                .basePath(basePath)
                .header(createTurnpikeIdentityHeader("admin", adminRole))
                .when()
                .contentType(JSON)
                .body(Json.encode(testCase.testDto))
                .post("/daily-digest/trigger")
                .then()
                .statusCode(400)
                .extract()
                .asString();

            final JsonObject responseJson = new JsonObject(response);
            final JsonArray constraintViolations = responseJson.getJsonArray("violations");

            Assertions.assertNotNull(constraintViolations, "the constraint violations key is not present");
            Assertions.assertEquals(1, constraintViolations.size(), "only one error message was expected, but more were found");

            final JsonObject error = constraintViolations.getJsonObject(0);

            // Test that the "errored field" is the correct one.
            final String errorField = error.getString("field");
            Assertions.assertNotNull(errorField, "the error field is null");
            Assertions.assertTrue(errorField.contains(testCase.expectedErrorField));

            // Test that the error message is "must not be blank".
            final String errorMessage = error.getString("message");
            Assertions.assertNotNull(errorMessage, "the error message is null");
            Assertions.assertEquals("must not be blank", errorMessage, "unexpected error message received");
        }
    }

    /**
     * Tests that when attempting to send a general communication, if the
     * required header or body are not present, or contain an unexpected value,
     * a bad request response is returned.
     */
    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testSendGeneralCommunicationInvalidPrerequisites(String basePath) {
        final String responseMissingEverything = given()
            .basePath(basePath)
            .header(createTurnpikeIdentityHeader("admin", adminRole))
            .when()
            .contentType(JSON)
            .post("/general-communications")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
            .extract()
            .asString();

        // Assert that we received the expected errors.
        final JsonObject responseMissingEverythingJson = new JsonObject(responseMissingEverything);
        final JsonArray constraintViolations = responseMissingEverythingJson.getJsonArray("violations");
        Assertions.assertEquals(2, constraintViolations.size(), "two constraint violations expected: one for the missing header, and another one for the missing body");

        final Map<String, String> expectedErrors = Map.of(
            "sendGeneralCommunication.request", "must not be null",
            "sendGeneralCommunication.safetyHeader", "must not be blank"
        );
        for (final Object jsonObject : constraintViolations) {
            final JsonObject json = (JsonObject) jsonObject;

            Assertions.assertEquals(expectedErrors.get(json.getString("field")), json.getString("message"), "unexpected error message received");
        }

        // Send a request with a header that contains an unexpected value.
        final SendGeneralCommunicationRequest request = new SendGeneralCommunicationRequest(true);

        final String responseIncorrectHeader = given()
            .basePath(basePath)
            .header(createTurnpikeIdentityHeader("admin", adminRole))
            .header("x-rh-send-general-communication", "unexpected-value")
            .when()
            .contentType(JSON)
            .body(Json.encode(request))
            .post("/general-communications")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
            .extract()
            .asString();

        final JsonObject responseIncorrectHeaderJson = new JsonObject(responseIncorrectHeader);
        Assertions.assertEquals("The safety header does not have the expected value", responseIncorrectHeaderJson.getString("error"));

        // Send a request with a body that contains an unexpected value.
        final SendGeneralCommunicationRequest invalidBody = new SendGeneralCommunicationRequest(false);

        final String invalidBodyResponse = given()
            .basePath(basePath)
            .header(createTurnpikeIdentityHeader("admin", adminRole))
            .header("x-rh-send-general-communication", "send-communication")
            .when()
            .contentType(JSON)
            .body(Json.encode(invalidBody))
            .post("/general-communications")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
            .extract()
            .asString();

        final JsonObject invalidBodyResponseJson = new JsonObject(invalidBodyResponse);
        Assertions.assertEquals("The request body does not contain the expected safety payload", invalidBodyResponseJson.getString("error"));
    }

    /**
     * Tests that when the correct header and bodies are specified for the
     * "general communication" endpoint, a request is sent to the engine. It
     * also verifies that the incoming response body that the back end receives
     * is simply forwareded to the client.
     */
    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testInternalSendGeneralCommunication(String basePath) {
        final SendGeneralCommunicationRequest request = new SendGeneralCommunicationRequest(true);

        // Mock an incoming response from the engine.
        final SendGeneralCommunicationResponse response = new SendGeneralCommunicationResponse("Everything went great!");
        Mockito.when(this.generalCommunicationsService.sendGeneralCommunication()).thenReturn(response);

        final SendGeneralCommunicationResponse receivedResponse = given()
            .basePath(basePath)
            .header(createTurnpikeIdentityHeader("admin", adminRole))
            .header("x-rh-send-general-communication", "send-communication")
            .when()
            .contentType(JSON)
            .body(Json.encode(request))
            .post("/general-communications")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract()
            .as(SendGeneralCommunicationResponse.class);

        // Assert that the REST service was called.
        Mockito.verify(this.generalCommunicationsService, Mockito.times(1)).sendGeneralCommunication();

        // Assert that the received response from the back end is the same as
        // the one received from the engine.
        Assertions.assertEquals(response, receivedResponse, "the received response from the back end is not the same as the one received from the engine");
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testDeleteOrphanEmailIntegrations(String basePath) {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        String orgId = "orphan-email-test-org";
        String accountId = "orphan-email-test-account";

        LocalDateTime oldTimestamp = LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(2);
        Endpoint orphan1 = resourceHelpers.createEndpoint(accountId, orgId, EndpointType.EMAIL_SUBSCRIPTION, null, UUID.randomUUID().toString(), "orphan1", null, false, oldTimestamp);
        Endpoint orphan2 = resourceHelpers.createEndpoint(accountId, orgId, EndpointType.EMAIL_SUBSCRIPTION, null, UUID.randomUUID().toString(), "orphan2", null, false, oldTimestamp);

        Bundle bundle = resourceHelpers.createBundle("orphan-test-bundle", "Orphan Test Bundle");
        Application app = resourceHelpers.createApplication(bundle.getId(), "orphan-test-app", "Orphan Test App");
        EventType eventType = resourceHelpers.createEventType(app.getId(), "orphan-test-event", "Orphan Test Event", "desc");

        Endpoint linkedToEventType = resourceHelpers.createEndpoint(accountId, orgId, EndpointType.EMAIL_SUBSCRIPTION);
        endpointEventTypeRepository.addEventTypeToEndpoint(eventType.getId(), linkedToEventType.getId(), orgId);

        BehaviorGroup bg = resourceHelpers.createBehaviorGroup(accountId, orgId, "orphan-test-bg", bundle.getId());
        Endpoint linkedToBehaviorGroup = resourceHelpers.createEndpoint(accountId, orgId, EndpointType.EMAIL_SUBSCRIPTION);
        behaviorGroupRepository.updateBehaviorGroupActions(orgId, bg.getId(), List.of(linkedToBehaviorGroup.getId()));

        Endpoint linkedToHistory = resourceHelpers.createEndpoint(accountId, orgId, EndpointType.EMAIL_SUBSCRIPTION);
        Event event = resourceHelpers.createEvent(accountId, orgId, bundle, app, eventType);
        resourceHelpers.createNotificationHistory(event, linkedToHistory, NotificationStatus.SUCCESS);

        Endpoint webhook = resourceHelpers.createEndpoint(accountId, orgId, EndpointType.WEBHOOK);

        String response = given()
            .header(identity)
            .basePath(basePath)
            .when()
            .delete("/orphanEmailIntegrations")
            .then()
            .statusCode(OK)
            .extract()
            .asString();

        JsonObject json = new JsonObject(response);
        int deleted = json.getInteger("deleted");
        assertEquals(2, deleted);

        Long remainingEmail = entityManager.createQuery(
            "SELECT COUNT(e) FROM Endpoint e WHERE e.orgId = :orgId AND e.compositeType.type = :type", Long.class)
            .setParameter("orgId", orgId)
            .setParameter("type", EndpointType.EMAIL_SUBSCRIPTION)
            .getSingleResult();
        assertEquals(3L, remainingEmail);

        Assertions.assertNotNull(entityManager.find(Endpoint.class, linkedToEventType.getId()));
        Assertions.assertNotNull(entityManager.find(Endpoint.class, linkedToBehaviorGroup.getId()));
        Assertions.assertNotNull(entityManager.find(Endpoint.class, linkedToHistory.getId()));

        Assertions.assertNull(entityManager.find(Endpoint.class, orphan1.getId()));
        Assertions.assertNull(entityManager.find(Endpoint.class, orphan2.getId()));

        Assertions.assertNotNull(entityManager.find(Endpoint.class, webhook.getId()));
    }
}
