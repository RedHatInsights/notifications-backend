package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.routers.internal.models.AddApplicationRequest;
import com.redhat.cloud.notifications.routers.internal.models.RequestDefaultBehaviorGroupPropertyList;
import com.redhat.cloud.notifications.routers.models.RequestSystemSubscriptionProperties;
import com.redhat.cloud.notifications.routers.models.SettingsValuesByEventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_2_0;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.TestConstants.API_INTEGRATIONS_V_1_0;
import static com.redhat.cloud.notifications.TestConstants.API_INTEGRATIONS_V_2_0;
import static com.redhat.cloud.notifications.TestConstants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.TestHelpers.createTurnpikeIdentityHeader;
import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.TEXT;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class LifecycleITest extends DbIsolatedTest {

    /*
     * In the tests below, most JSON responses are verified using JsonObject/JsonArray instead of deserializing these
     * responses into model instances and checking their attributes values. That's because the model classes contain
     * attributes annotated with @JsonProperty(access = READ_ONLY) which can't be deserialized and therefore verified
     * here. The deserialization is still performed only to verify that the JSON responses data structure is correct.
     */

    private static final String APP_NAME = "policies-lifecycle-test";
    private static final String BUNDLE_NAME = "my-bundle";
    private static final String EVENT_TYPE_NAME = "all";
    private static final String WEBHOOK_MOCK_PATH = "/test/lifecycle";
    private static final String SECRET_TOKEN = "super-secret-token";

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    FeatureFlipper featureFlipper;

    @BeforeEach
    void beforeEach() {
        featureFlipper.setInstantEmailsEnabled(true);
    }

    @AfterEach
    void afterEach() {
        featureFlipper.setInstantEmailsEnabled(false);
    }

    private Header initRbacMock(String accountId, String orgId, String username, RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, username);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createRHIdentityHeader(identityHeaderValue);
    }

    @Test
    void shouldReturn400AndBadRequestExceptionWhenDisplayNameIsAlreadyPresent() {
        if (!featureFlipper.isEnforceBehaviorGroupNameUnicity()) {
            // The check is disabled from configuration.
            return;
        }

        final String accountId = "tenant";
        final String orgId = "someOrgId";
        final String username = "user";

        // Identity header used for all public APIs calls. Internal APIs calls don't need that.
        Header identityHeader = initRbacMock(accountId, orgId, username, RbacAccess.FULL_ACCESS);
        Header turnpikeIdentityHeader = createTurnpikeIdentityHeader("admin", adminRole);

        // First, we need a bundle, an app and an event type. Let's create them!
        String bundleId = createBundle(turnpikeIdentityHeader);

        // Create first behavior group with name BG1
        createBehaviorGroup(identityHeader, bundleId, "BG1");

        // create a Behavior Group with the same name again
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setDisplayName("BG1");
        behaviorGroup.setBundleId(UUID.fromString(bundleId));

        String responseBody = given()
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(behaviorGroup))
                .when()
                .post(API_NOTIFICATIONS_V_1_0 + "/notifications/behaviorGroups")
                .then()
                .statusCode(400)
                .contentType(TEXT)
                .extract().asString();

        assertEquals("A behavior group with display name [" + behaviorGroup.getDisplayName() + "] already exists", responseBody);
    }

    @Test
    void test() {
        /*
         * TODO
         * The following code was written before the notifications backend/engine split. It was modified to allow
         * splitting the projects, but some parts of it are no longer relevant now. It should probably be rewritten
         * entirely.
         */

        final String accountId = "tenant";
        final String orgId = "someOrgId";
        final String username = "user";

        RequestDefaultBehaviorGroupPropertyList defaultBehaviorGroupProperties = new RequestDefaultBehaviorGroupPropertyList();
        defaultBehaviorGroupProperties.setOnlyAdmins(true);

        // All events are stored in the canonical email endpoint
        RequestSystemSubscriptionProperties userEmailEndpointRequest = new RequestSystemSubscriptionProperties();

        // Identity header used for all public APIs calls. Internal APIs calls need a different token.
        Header identityHeader = initRbacMock(accountId, orgId, username, RbacAccess.FULL_ACCESS);
        Header turnpikeIdentityHeader = createTurnpikeIdentityHeader("admin", adminRole);

        // First, we need a bundle, an app and an event type. Let's create them!
        String bundleId = createBundle(turnpikeIdentityHeader);
        String appId = createApp(turnpikeIdentityHeader, bundleId);
        String eventTypeId = createEventType(turnpikeIdentityHeader, appId);
        checkAllEventTypes(identityHeader);

        // We also need behavior groups.
        String behaviorGroupId1 = createBehaviorGroup(identityHeader, bundleId, "Behavior group 1");
        String behaviorGroupId2 = createBehaviorGroup(identityHeader, bundleId, "Behavior group 2");
        String defaultBehaviorGroupId = createDefaultBehaviorGroup(turnpikeIdentityHeader, bundleId);

        // We need actions for our behavior groups.
        String endpointId1 = createWebhookEndpoint(identityHeader, SECRET_TOKEN);
        String endpointId2 = createWebhookEndpoint(identityHeader, SECRET_TOKEN);
        String endpointId3 = createWebhookEndpoint(identityHeader, "wrong-secret-token");
        checkEndpoints(identityHeader, endpointId1, endpointId2, endpointId3);

        // We'll start with a first behavior group actions configuration. This will slightly change later in the test.
        addBehaviorGroupActions(identityHeader, behaviorGroupId1, 200, endpointId1, endpointId2);
        addBehaviorGroupActions(identityHeader, behaviorGroupId2, 200, endpointId3);

        // Can't add actions to default behavior group using public API
        addBehaviorGroupActions(identityHeader, defaultBehaviorGroupId, 404, endpointId1, endpointId2, endpointId3);

        // Can't add default actions to behavior group using internal API
        addDefaultBehaviorGroupActions(turnpikeIdentityHeader, behaviorGroupId1, 404, defaultBehaviorGroupProperties);

        // Adding the same config multiple times yields an error
        addDefaultBehaviorGroupActions(turnpikeIdentityHeader, defaultBehaviorGroupId, 400, defaultBehaviorGroupProperties, defaultBehaviorGroupProperties, defaultBehaviorGroupProperties);

        // Adding an email endpoint to the default behavior group
        addDefaultBehaviorGroupActions(turnpikeIdentityHeader, defaultBehaviorGroupId, 200, defaultBehaviorGroupProperties);

        // Before the notifications split, a Kafka message was sent here.

        // Now we'll link the event type with one behavior group.
        updateEventTypeBehaviors(identityHeader, eventTypeId, behaviorGroupId1);
        checkEventTypeBehaviorGroups(identityHeader, eventTypeId, behaviorGroupId1);

        // Get user email endpoint
        String emailEndpoint = getEmailEndpoint(identityHeader, userEmailEndpointRequest);

        // Before the notifications split, a Kafka message was sent here.
        Event event = createEvent(accountId, orgId, eventTypeId);
        createNotificationHistory(event, endpointId1, NotificationStatus.SUCCESS);
        createNotificationHistory(event, endpointId2, NotificationStatus.SUCCESS);

        // Let's check the notifications history.
        retry(() -> checkEndpointHistory(identityHeader, endpointId1, 1, true, 200));
        retry(() -> checkEndpointHistory(identityHeader, endpointId2, 1, true, 200));
        retry(() -> checkEndpointHistory(identityHeader, emailEndpoint, 0, true, 200));

        // We'll link the event type with the default behavior group
        linkDefaultBehaviorGroup(turnpikeIdentityHeader, eventTypeId, defaultBehaviorGroupId);
        checkEventTypeBehaviorGroups(identityHeader, eventTypeId, behaviorGroupId1, defaultBehaviorGroupId);

        // We'll link an additional behavior group to the event type.
        updateEventTypeBehaviors(identityHeader, eventTypeId, behaviorGroupId1, behaviorGroupId2);
        checkEventTypeBehaviorGroups(identityHeader, eventTypeId, behaviorGroupId1, behaviorGroupId2, defaultBehaviorGroupId);

        // Before the notifications split, a Kafka message was sent here.
        event = createEvent(accountId, orgId, eventTypeId);
        createNotificationHistory(event, endpointId1, NotificationStatus.SUCCESS);
        createNotificationHistory(event, endpointId2, NotificationStatus.SUCCESS);
        createNotificationHistory(event, endpointId3, NotificationStatus.FAILED_INTERNAL);

        // Let's check the notifications history again.
        retry(() -> checkEndpointHistory(identityHeader, endpointId1, 2, true, 200));
        retry(() -> checkEndpointHistory(identityHeader, endpointId2, 2, true, 200));
        retry(() -> checkEndpointHistory(identityHeader, endpointId3, 1, false, 400));
        retry(() -> checkEndpointHistory(identityHeader, emailEndpoint, 0, true, 200));

        // Lets subscribe the user to the email preferences
        subscribeUserPreferences(identityHeader, BUNDLE_NAME, APP_NAME);

        // Before the notifications split, a Kafka message was sent here.
        event = createEvent(accountId, orgId, eventTypeId);
        createNotificationHistory(event, endpointId1, NotificationStatus.SUCCESS);
        createNotificationHistory(event, endpointId2, NotificationStatus.SUCCESS);
        createNotificationHistory(event, endpointId3, NotificationStatus.FAILED_INTERNAL);
        createNotificationHistory(event, emailEndpoint, NotificationStatus.SUCCESS);

        // Let's check the notifications history again.
        retry(() -> checkEndpointHistory(identityHeader, endpointId1, 3, true, 200));
        retry(() -> checkEndpointHistory(identityHeader, endpointId2, 3, true, 200));
        retry(() -> checkEndpointHistory(identityHeader, endpointId3, 2, false, 400));
        retry(() -> checkEndpointHistory(identityHeader, emailEndpoint, 1, true, 200));

        /*
         * Let's change the behavior group actions configuration by adding an action to the second behavior group.
         * Endpoint 2 is now an action for both behavior groups, but it should not be notified twice on each message because we don't want duplicate notifications.
         */
        addBehaviorGroupActions(identityHeader, behaviorGroupId2, 200, endpointId3, endpointId2);

        // Before the notifications split, a Kafka message was sent here.
        createEvent(accountId, orgId, eventTypeId);
        createNotificationHistory(event, endpointId1, NotificationStatus.SUCCESS);
        createNotificationHistory(event, endpointId2, NotificationStatus.SUCCESS);
        createNotificationHistory(event, endpointId3, NotificationStatus.FAILED_INTERNAL);
        createNotificationHistory(event, emailEndpoint, NotificationStatus.SUCCESS);

        // Let's check the notifications history again.
        retry(() -> checkEndpointHistory(identityHeader, endpointId1, 4, true, 200));
        retry(() -> checkEndpointHistory(identityHeader, endpointId2, 4, true, 200));
        retry(() -> checkEndpointHistory(identityHeader, endpointId3, 3, false, 400));
        retry(() -> checkEndpointHistory(identityHeader, emailEndpoint, 2, true, 200));

        /*
         * What happens if we unlink the event type from the behavior groups?
         * Pushing a new message should not trigger any webhook call.
         */
        // Unlinking user behavior group
        updateEventTypeBehaviors(identityHeader, eventTypeId);
        checkEventTypeBehaviorGroups(identityHeader, eventTypeId, defaultBehaviorGroupId);

        // Unlinking default behavior groups
        unlinkDefaultBehaviorGroup(turnpikeIdentityHeader, eventTypeId, defaultBehaviorGroupId);
        checkEventTypeBehaviorGroups(identityHeader, eventTypeId);
        // Before the notifications split, a Kafka message was sent here.

        // The notifications history should be exactly the same than last time.
        retry(() -> checkEndpointHistory(identityHeader, endpointId1, 4, true, 200));
        retry(() -> checkEndpointHistory(identityHeader, endpointId2, 4, true, 200));
        retry(() -> checkEndpointHistory(identityHeader, endpointId3, 3, false, 400));
        retry(() -> checkEndpointHistory(identityHeader, emailEndpoint, 2, true, 200));

        // Linking the default behavior group again
        linkDefaultBehaviorGroup(turnpikeIdentityHeader, eventTypeId, defaultBehaviorGroupId);
        checkEventTypeBehaviorGroups(identityHeader, eventTypeId, defaultBehaviorGroupId);
        // Before the notifications split, a Kafka message was sent here.

        // Deleting the default behavior group should unlink it
        deleteDefaultBehaviorGroup(turnpikeIdentityHeader, defaultBehaviorGroupId);
        checkEventTypeBehaviorGroups(identityHeader, eventTypeId);
        // Before the notifications split, a Kafka message was sent here.

        /*
         * We'll finish with a bundle removal.
         * Why would we do that here? I don't really know, it was there before the big test refactor... :)
         */
        deleteBundle(turnpikeIdentityHeader, bundleId);
    }

    private String createBundle(Header header) {
        Bundle bundle = new Bundle(BUNDLE_NAME, "A bundle");

        String responseBody = given()
                .basePath(API_INTERNAL)
                .header(header)
                .contentType(JSON)
                .body(Json.encode(bundle))
                .when()
                .post("/bundles")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().asString();

        JsonObject jsonBundle = new JsonObject(responseBody);
        jsonBundle.mapTo(Bundle.class);
        assertNotNull(jsonBundle.getString("id"));
        assertEquals(bundle.getName(), jsonBundle.getString("name"));
        assertEquals(bundle.getDisplayName(), jsonBundle.getString("display_name"));
        assertNotNull(jsonBundle.getString("created"));

        return jsonBundle.getString("id");
    }

    private String createApp(Header header, String bundleId) {
        AddApplicationRequest request = new AddApplicationRequest();
        request.bundleId = UUID.fromString(bundleId);
        request.name = APP_NAME;
        request.displayName = "The best app in the life";

        String responseBody = given()
                .when()
                .basePath(API_INTERNAL)
                .header(header)
                .contentType(JSON)
                .body(Json.encode(request))
                .post("/applications")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().asString();

        JsonObject jsonApp = new JsonObject(responseBody);
        jsonApp.mapTo(Application.class);
        assertNotNull(jsonApp.getString("id"));
        assertEquals(request.name, jsonApp.getString("name"));
        assertEquals(request.displayName, jsonApp.getString("display_name"));
        assertEquals(request.bundleId.toString(), jsonApp.getString("bundle_id"));
        assertNotNull(jsonApp.getString("created"));

        return jsonApp.getString("id");
    }

    private String createEventType(Header header, String appId) {
        EventType eventType = new EventType();
        eventType.setApplicationId(UUID.fromString(appId));
        eventType.setName(EVENT_TYPE_NAME);
        eventType.setDisplayName("Policies will take care of the rules");
        eventType.setDescription("Policies is super cool, you should use it");

        String responseBody = given()
                .when()
                .basePath(API_INTERNAL)
                .header(header)
                .contentType(JSON)
                .body(Json.encode(eventType))
                .post("/eventTypes")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().asString();

        JsonObject jsonEventType = new JsonObject(responseBody);
        jsonEventType.mapTo(EventType.class);
        assertNotNull(jsonEventType.getString("id"));
        assertEquals(eventType.getName(), jsonEventType.getString("name"));
        assertEquals(eventType.getDisplayName(), jsonEventType.getString("display_name"));
        assertEquals(eventType.getDescription(), jsonEventType.getString("description"));

        return jsonEventType.getString("id");
    }

    private void checkAllEventTypes(Header identityHeader) {
        String responseBody = given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .when()
                .get("/notifications/eventTypes")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().asString();

        JsonObject jsonPageEventTypes = new JsonObject(responseBody);
        JsonArray jsonEventTypes = jsonPageEventTypes.getJsonArray("data");
        assertEquals(2, jsonEventTypes.size()); // One from the current test, one from the default DB records.
    }

    private String createBehaviorGroupInternal(String path, Header identityHeader, String bundleId, String displayName) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(UUID.fromString(bundleId));

        var request = given();
        if (identityHeader != null) {
            request.header(identityHeader);
        }

        String responseBody = request
                .contentType(JSON)
                .body(Json.encode(behaviorGroup))
                .when()
                .post(path)
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().asString();

        JsonObject jsonBehaviorGroup = new JsonObject(responseBody);
        jsonBehaviorGroup.mapTo(BehaviorGroup.class);
        assertNotNull(jsonBehaviorGroup.getString("id"));
        assertNull(jsonBehaviorGroup.getString("accountId"));
        assertNull(jsonBehaviorGroup.getString("orgId"));
        assertEquals(behaviorGroup.getDisplayName(), jsonBehaviorGroup.getString("display_name"));
        assertEquals(behaviorGroup.getBundleId().toString(), jsonBehaviorGroup.getString("bundle_id"));
        assertNotNull(jsonBehaviorGroup.getString("created"));

        return jsonBehaviorGroup.getString("id");
    }

    private String createBehaviorGroup(Header identityHeader, String bundleId, String displayName) {
        return createBehaviorGroupInternal(API_NOTIFICATIONS_V_1_0 + "/notifications/behaviorGroups", identityHeader, bundleId, displayName);
    }

    private String createDefaultBehaviorGroup(Header turnpikeIdentityHeader, String bundleId) {
        return createBehaviorGroupInternal(API_INTERNAL + "/behaviorGroups/default", turnpikeIdentityHeader, bundleId, "Behavior group");
    }

    @Transactional
    Event createEvent(String accountId, String orgId, String eventTypeId) {
        EventType eventType = entityManager.createQuery("FROM EventType e JOIN FETCH e.application a JOIN FETCH a.bundle WHERE e.id = :id", EventType.class)
                .setParameter("id", UUID.fromString(eventTypeId))
                .getSingleResult();

        Event event = new Event(accountId, orgId, eventType, UUID.randomUUID());
        entityManager.persist(event);
        return event;
    }

    @Transactional
    void createNotificationHistory(Event event, String endpointId, NotificationStatus status) {
        Endpoint endpoint = entityManager.createQuery("FROM Endpoint WHERE id = :id", Endpoint.class)
                .setParameter("id", UUID.fromString(endpointId))
                .getSingleResult();

        NotificationHistory history = new NotificationHistory();
        history.setId(UUID.randomUUID());
        history.setEvent(event);
        history.setEndpoint(endpoint);
        history.setEndpointType(endpoint.getType());
        history.setInvocationTime(123L);
        history.setStatus(status);
        if (status == NotificationStatus.FAILED_INTERNAL || status == NotificationStatus.FAILED_EXTERNAL) {
            history.setDetails(Map.of(
                    "code", 400,
                    "url", "https://www.foo.com",
                    "method", "GET"
            ));
        }
        history.prePersist();
        entityManager.persist(history);
    }

    private void deleteDefaultBehaviorGroup(Header turnpikeIdentityHeader, String defaultBehaviorGroupId) {
        given()
                .basePath(API_INTERNAL)
                .header(turnpikeIdentityHeader)
                .pathParam("behaviorGroupId", defaultBehaviorGroupId)
                .when()
                .delete("/behaviorGroups/default/{behaviorGroupId}")
                .then()
                .statusCode(200)
                .contentType(JSON);
    }

    private String getEmailEndpoint(Header identityHeader, RequestSystemSubscriptionProperties properties) {
        return given()
                .basePath(API_INTEGRATIONS_V_1_0)
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(properties))
                .when()
                .post("/endpoints/system/email_subscription")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().jsonPath().getString("id");
    }

    private String createWebhookEndpoint(Header identityHeader, String secretToken) {
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setDisableSslVerification(true);
        properties.setSecretToken(secretToken);
        properties.setUrl(getMockServerUrl() + WEBHOOK_MOCK_PATH);

        Endpoint endpoint = new Endpoint();
        endpoint.setType(EndpointType.WEBHOOK);
        endpoint.setName("endpoint");
        endpoint.setDescription("Endpoint");
        endpoint.setEnabled(true);
        endpoint.setProperties(properties);

        String responseBody = given()
                .basePath(API_INTEGRATIONS_V_1_0)
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(endpoint))
                .when()
                .post("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().asString();

        JsonObject jsonEndpoint = new JsonObject(responseBody);
        jsonEndpoint.mapTo(Endpoint.class);
        assertNotNull(jsonEndpoint.getString("id"));
        assertNull(jsonEndpoint.getString("accountId"));
        assertNull(jsonEndpoint.getString("orgId"));
        assertEquals(endpoint.getName(), jsonEndpoint.getString("name"));
        assertEquals(endpoint.getDescription(), jsonEndpoint.getString("description"));
        assertTrue(jsonEndpoint.getBoolean("enabled"));
        assertEquals(EndpointType.WEBHOOK.name().toLowerCase(), jsonEndpoint.getString("type"));

        JsonObject jsonWebhookProperties = jsonEndpoint.getJsonObject("properties");
        jsonWebhookProperties.mapTo(WebhookProperties.class);
        assertEquals(properties.getMethod().name(), jsonWebhookProperties.getString("method"));
        assertEquals(properties.getDisableSslVerification(), jsonWebhookProperties.getBoolean("disable_ssl_verification"));
        if (properties.getSecretToken() != null) {
            assertEquals(properties.getSecretToken(), jsonWebhookProperties.getString("secret_token"));
        }
        assertEquals(properties.getUrl(), jsonWebhookProperties.getString("url"));

        return jsonEndpoint.getString("id");
    }

    private void checkEndpoints(Header identityHeader, String... expectedEndpointIds) {
        String responseBody = given()
                .basePath(API_INTEGRATIONS_V_1_0)
                .header(identityHeader)
                .when()
                .get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().asString();

        JsonArray jsonEndpoints = new JsonObject(responseBody).getJsonArray("data");
        assertEquals(expectedEndpointIds.length, jsonEndpoints.size());
        jsonEndpoints.getJsonObject(0).mapTo(Endpoint.class);
        jsonEndpoints.getJsonObject(0).getJsonObject("properties").mapTo(WebhookProperties.class);

        for (String endpointId : expectedEndpointIds) {
            if (!responseBody.contains(endpointId)) {
                fail("One of the expected endpoint could not be found in the database");
            }
        }
    }

    private void addBehaviorGroupActions(Header identityHeader, String behaviorGroupId, int expectedHttpStatusCode, String... endpointIds) {
        given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .contentType(JSON)
                .pathParam("behaviorGroupId", behaviorGroupId)
                .body(Json.encode(Arrays.asList(endpointIds)))
                .when()
                .put("/notifications/behaviorGroups/{behaviorGroupId}/actions")
                .then()
                .statusCode(expectedHttpStatusCode);
    }

    private void addDefaultBehaviorGroupActions(Header turnpikeIdentityHeader, String defaultBehaviorGroupId, int expectedHttpStatusCode, RequestDefaultBehaviorGroupPropertyList... properties) {
        given()
                .basePath(API_INTERNAL)
                .header(turnpikeIdentityHeader)
                .contentType(JSON)
                .pathParam("behaviorGroupId", defaultBehaviorGroupId)
                .body(Json.encode(List.of(properties)))
                .when()
                .put("/behaviorGroups/default/{behaviorGroupId}/actions")
                .then()
                .statusCode(expectedHttpStatusCode);
    }

    private void updateEventTypeBehaviors(Header identityHeader, String eventTypeId, String... behaviorGroupIds) {
        given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .contentType(JSON)
                .pathParam("eventTypeId", eventTypeId)
                .body(Json.encode(Arrays.asList(behaviorGroupIds)))
                .when()
                .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
                .then()
                .statusCode(200);
    }

    private void linkDefaultBehaviorGroup(Header turnpikeIdentityHeader, String eventTypeId, String behaviorGroupId) {
        given()
                .basePath(API_INTERNAL)
                .header(turnpikeIdentityHeader)
                .pathParam("behaviorGroupId", behaviorGroupId)
                .pathParam("eventTypeId", eventTypeId)
                .when()
                .put("/behaviorGroups/default/{behaviorGroupId}/eventType/{eventTypeId}")
                .then()
                .statusCode(200);
    }

    private void unlinkDefaultBehaviorGroup(Header turnpikeIdentityHeader, String eventTypeId, String behaviorGroupId) {
        given()
                .basePath(API_INTERNAL)
                .header(turnpikeIdentityHeader)
                .pathParam("behaviorGroupId", behaviorGroupId)
                .pathParam("eventTypeId", eventTypeId)
                .when()
                .delete("/behaviorGroups/default/{behaviorGroupId}/eventType/{eventTypeId}")
                .then()
                .statusCode(200);
    }

    private void checkEventTypeBehaviorGroups(Header identityHeader, String eventTypeId, String... expectedBehaviorGroupIds) {
        checkEventTypeBehaviorGroupsV1(identityHeader, eventTypeId, expectedBehaviorGroupIds);
        checkEventTypeBehaviorGroupsV2(identityHeader, eventTypeId, expectedBehaviorGroupIds);
    }

    private void checkEventTypeBehaviorGroupsV1(Header identityHeader, String eventTypeId, String... expectedBehaviorGroupIds) {
        String responseBody = given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("eventTypeId", eventTypeId)
                .when()
                .get("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().asString();

        JsonArray jsonBehaviorGroups = new JsonArray(responseBody);
        assertEquals(expectedBehaviorGroupIds.length, jsonBehaviorGroups.size());

        for (String behaviorGroupId : expectedBehaviorGroupIds) {
            if (!responseBody.contains(behaviorGroupId)) {
                fail("One of the expected behavior groups is not linked to the event type");
            }
        }
    }

    private void checkEventTypeBehaviorGroupsV2(Header identityHeader, String eventTypeId, String... expectedBehaviorGroupIds) {
        String responseBody = given()
                .basePath(API_NOTIFICATIONS_V_2_0)
                .header(identityHeader)
                .pathParam("eventTypeId", eventTypeId)
                .when()
                .get("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().asString();

        // The response body contains the meta information about the collection
        // of behavior groups. We need to access the "data" array to access the
        // elements.
        final JsonObject responseBodyJson = new JsonObject(responseBody);
        final JsonArray jsonBehaviorGroups = responseBodyJson.getJsonArray("data");
        assertEquals(expectedBehaviorGroupIds.length, jsonBehaviorGroups.size());

        for (String behaviorGroupId : expectedBehaviorGroupIds) {
            if (!responseBody.contains(behaviorGroupId)) {
                fail("One of the expected behavior groups is not linked to the event type");
            }
        }

        // Check the "meta" and "links" elements.
        final JsonObject links = responseBodyJson.getJsonObject("links");
        assertNotNull(links, "the \"links\" element is missing from the paged response");

        // Check the "first" link.
        final String expectedFirstValue = String.format("/api/notifications/v2.0/notifications/eventTypes/%s/behaviorGroups?limit=20&offset=0", eventTypeId);
        assertEquals(expectedFirstValue, links.getString("first"), "the \"first\" link element contains an unexpected value");

        // Check the "last" link.
        final String expectedLastValue = String.format("/api/notifications/v2.0/notifications/eventTypes/%s/behaviorGroups?limit=20&offset=0", eventTypeId);
        assertEquals(expectedLastValue, links.getString("last"), "the \"last\" link element contains an unexpected value");

        // Check the "meta" object.
        final JsonObject meta = responseBodyJson.getJsonObject("meta");
        assertEquals(expectedBehaviorGroupIds.length, meta.getNumber("count"), "the \"meta\" \"count\" element contains an unexpected number");
    }

    private void retry(Supplier<Boolean> checkEndpointHistoryResult) {
        await()
                .pollInterval(Duration.ofSeconds(1L))
                .atMost(Duration.ofSeconds(5L))
                .until(checkEndpointHistoryResult::get);
    }

    private boolean checkEndpointHistory(Header identityHeader, String endpointId, int expectedHistoryEntries, boolean expectedInvocationResult, int expectedHttpStatus) {
        return checkEndpointHistoryV1(identityHeader, endpointId, expectedHistoryEntries, expectedInvocationResult, expectedHttpStatus) &&
                checkEndpointHistoryV2(identityHeader, endpointId, expectedHistoryEntries, expectedInvocationResult, expectedHttpStatus);
    }


    private boolean checkEndpointHistoryV1(Header identityHeader, String endpointId, int expectedHistoryEntries, boolean expectedInvocationResult, int expectedHttpStatus) {
        try {
            String responseBody = given()
                    .basePath(API_INTEGRATIONS_V_1_0)
                    .header(identityHeader)
                    .pathParam("endpointId", endpointId)
                    .when()
                    .get("/endpoints/{endpointId}/history")
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .extract().body().asString();

            // The response body contains the meta information about the history collection. We need to access the
            // "data" array to access the elements.
            final JsonArray jsonEndpointHistory = new JsonArray(responseBody);
            assertEquals(expectedHistoryEntries, jsonEndpointHistory.size());

            checkEndpointHistoryDetails(identityHeader, endpointId, expectedInvocationResult, expectedHttpStatus, jsonEndpointHistory);
            return true;
        } catch (AssertionError e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean checkEndpointHistoryV2(Header identityHeader, String endpointId, int expectedHistoryEntries, boolean expectedInvocationResult, int expectedHttpStatus) {
        try {
            String rawResponseBody = given()
                    .basePath(API_INTEGRATIONS_V_2_0)
                    .header(identityHeader)
                    .pathParam("endpointId", endpointId)
                    .when()
                    .get("/endpoints/{endpointId}/history")
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .extract().body().asString();

            // The response body contains the meta information about the history collection. We need to access the
            // "data" array to access the elements.
            final JsonObject responseBodyJson = new JsonObject(rawResponseBody);
            final JsonArray jsonEndpointHistory = responseBodyJson.getJsonArray("data");
            assertEquals(expectedHistoryEntries, jsonEndpointHistory.size());

            checkEndpointHistoryDetails(identityHeader, endpointId, expectedInvocationResult, expectedHttpStatus, jsonEndpointHistory);
            return true;
        } catch (AssertionError e) {
            e.printStackTrace();
            return false;
        }
    }

    private void checkEndpointHistoryDetails(Header identityHeader, String endpointId, boolean expectedInvocationResult, int expectedHttpStatus, JsonArray jsonEndpointHistory) {
        for (int i = 0; i < jsonEndpointHistory.size(); i++) {
            JsonObject jsonNotificationHistory = jsonEndpointHistory.getJsonObject(i);
            jsonNotificationHistory.mapTo(NotificationHistory.class);
            assertEquals(expectedInvocationResult, jsonNotificationHistory.getBoolean("invocationResult"));

            if (!expectedInvocationResult) {
                String responseBody = given()
                        .basePath(API_INTEGRATIONS_V_2_0)
                        .header(identityHeader)
                        .pathParam("endpointId", endpointId)
                        .pathParam("historyId", jsonNotificationHistory.getString("id"))
                        .when()
                        .get("/endpoints/{endpointId}/history/{historyId}/details")
                        .then()
                        .statusCode(200)
                        .contentType(JSON)
                        .extract().body().asString();

                JsonObject jsonDetails = new JsonObject(responseBody);
                assertFalse(jsonDetails.isEmpty());
                assertEquals(expectedHttpStatus, jsonDetails.getInteger("code"));
                assertNotNull(jsonDetails.getString("url"));
                assertNotNull(jsonDetails.getString("method"));
            }
        }
    }

    private void deleteBundle(Header turnpikeIdentityHeader, String bundleId) {
        given()
                .basePath(API_INTERNAL)
                .header(turnpikeIdentityHeader)
                .when()
                .pathParam("bundleId", bundleId)
                .delete("/bundles/{bundleId}")
                .then()
                .statusCode(200)
                .contentType(JSON);
    }

    private void subscribeUserPreferences(Header identityHeader, String bundleName, String appName) {

        SettingsValuesByEventType.EventTypeSettingsValue eventTypeSettingsValue = new SettingsValuesByEventType.EventTypeSettingsValue();
        eventTypeSettingsValue.emailSubscriptionTypes.put(DAILY, true);

        SettingsValuesByEventType.ApplicationSettingsValue applicationSettingsValue = new SettingsValuesByEventType.ApplicationSettingsValue();
        applicationSettingsValue.eventTypes.put(EVENT_TYPE_NAME, eventTypeSettingsValue);

        SettingsValuesByEventType.BundleSettingsValue bundleSettingsValue = new SettingsValuesByEventType.BundleSettingsValue();
        bundleSettingsValue.applications.put(appName, applicationSettingsValue);

        SettingsValuesByEventType settingsValues = new SettingsValuesByEventType();
        settingsValues.bundles.put(bundleName, bundleSettingsValue);

        given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(settingsValues))
                .when()
                .post("/user-config/notification-event-type-preference")
                .then()
                .statusCode(200);
    }
}
