package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.CounterAssertionHelper;
import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.WebhookProperties;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess;
import static com.redhat.cloud.notifications.TestConstants.API_INTEGRATIONS_V_1_0;
import static com.redhat.cloud.notifications.TestConstants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
import static com.redhat.cloud.notifications.events.EndpointProcessor.PROCESSED_ENDPOINTS_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EndpointProcessor.PROCESSED_MESSAGES_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.REJECTED_COUNTER_NAME;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockserver.model.HttpResponse.response;

// TODO [BG Phase 2] Remove '_BG_' from the class name
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class Lifecycle_BG_ITest extends DbIsolatedTest {

    /*
     * In the tests below, most JSON responses are verified using JsonObject/JsonArray instead of deserializing these
     * responses into model instances and checking their attributes values. That's because the model classes contain
     * attributes annotated with @JsonProperty(access = READ_ONLY) which can't be deserialized and therefore verified
     * here. The deserialization is still performed only to verify that the JSON responses data structure is correct.
     */

    private static final String APP_NAME = "policies-lifecycle-test";
    private static final String BUNDLE_NAME = "my-bundle";
    private static final String EVENT_TYPE_NAME = "all";
    private static final String INTERNAL_BASE_PATH = "/";
    private static final String WEBHOOK_MOCK_PATH = "/test/lifecycle";
    private static final String SECRET_TOKEN = "super-secret-token";

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @Inject
    CounterAssertionHelper counterAssertionHelper;

    private Header initRbacMock(String tenant, String username, MockServerClientConfig.RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, username);
        mockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createIdentityHeader(identityHeaderValue);
    }

    @Test
    void test() throws IOException, InterruptedException {
        // Identity header used for all public APIs calls. Internal APIs calls don't need that.
        Header identityHeader = initRbacMock("tenant", "user", RbacAccess.FULL_ACCESS);

        // First, we need a bundle, an app and an event type. Let's create them!
        String bundleId = createBundle();
        String appId = createApp(bundleId);
        String eventTypeId = createEventType(appId);
        checkAllEventTypes(identityHeader);

        /*
         * Then, we'll create two behavior groups which will be part of the bundle created above. One will be the
         * default behavior group, the other one will be a custom behavior group.
         */
        String defaultBehaviorGroupId = createBehaviorGroup(identityHeader, "Default behavior group", bundleId);
        String customBehaviorGroupId = createBehaviorGroup(identityHeader, "Custom behavior group", bundleId);
        setDefaultBehaviorGroup(identityHeader, bundleId, defaultBehaviorGroupId);

        // We need actions for our behavior groups.
        String endpointId1 = createWebhookEndpoint(identityHeader, "positive feeling", "needle in the haystack", SECRET_TOKEN);
        String endpointId2 = createWebhookEndpoint(identityHeader, "negative feeling", "I feel like dying", "wrong-secret-token");
        checkEndpoints(identityHeader, endpointId1, endpointId2);

        // The actions must be added to the behavior groups.
        addBehaviorGroupActions(identityHeader, defaultBehaviorGroupId, endpointId1);
        // Adding the same action twice must not raise an exception.
        addBehaviorGroupActions(identityHeader, defaultBehaviorGroupId, endpointId1, endpointId2);

        /*
         * Let's push a first message! It should not trigger any webhook call since we didn't link the event type with
         * any behavior group.
         */
        pushMessage(0);

        /*
         * Now we'll link the event type with the default behavior group.
         * Pushing a new message should trigger one webhook call.
         */
        addEventTypeBehaviorGroup(identityHeader, eventTypeId, defaultBehaviorGroupId, 1);
        // Adding the same behavior group twice must not raise an exception.
        addEventTypeBehaviorGroup(identityHeader, eventTypeId, defaultBehaviorGroupId, 1);
        checkEventTypeBehaviorGroups(identityHeader, eventTypeId, defaultBehaviorGroupId);
        // pushMessage(1); TODO [BG Phase 2] Uncomment (it does not work here because EndpointProcessor does not use behavior groups)

        /*
         * Let's also link the same event type with the custom behavior group.
         * Pushing a new message should trigger two webhook calls.
         */
        addEventTypeBehaviorGroup(identityHeader, eventTypeId, customBehaviorGroupId, 2);
        checkEventTypeBehaviorGroups(identityHeader, eventTypeId, defaultBehaviorGroupId, customBehaviorGroupId);
        // pushMessage(2); TODO [BG Phase 2] Uncomment (it does not work here because EndpointProcessor does not use behavior groups)

        /*
         * What happens if we mute the event type?
         * Pushing a new message should not trigger any webhook call.
         */
        muteEventType(identityHeader, eventTypeId);
        checkEventTypeBehaviorGroups(identityHeader, eventTypeId);
        // pushMessage(0); TODO [BG Phase 2] Uncomment (it does not work here because EndpointProcessor does not use behavior groups)

        // Enough messages! Now let's check the history of both endpoints.
        /*
        TODO [BG Phase 2] Uncomment (it does not work here because EndpointProcessor does not use behavior groups)
        checkEndpointHistory(identityHeader, endpointId1, 2, true, 200);
        checkEndpointHistory(identityHeader, endpointId2, 1, false, 400);
         */

        /*
         * We'll finish with a bundle removal.
         * Why would we do that here? I don't really know, it was there before the big test refactor... :)
         */
        deleteBundle(bundleId);
    }

    private String createBundle() {
        Bundle bundle = new Bundle(BUNDLE_NAME, "A bundle");

        String responseBody = given()
                .basePath(INTERNAL_BASE_PATH)
                .contentType(ContentType.JSON)
                .body(Json.encode(bundle))
                .when()
                .post("/internal/bundles")
                .then()
                .statusCode(200)
                .extract().body().asString();

        JsonObject jsonBundle = new JsonObject(responseBody);
        jsonBundle.mapTo(Bundle.class);
        assertNotNull(jsonBundle.getString("id"));
        assertEquals(bundle.getName(), jsonBundle.getString("name"));
        assertEquals(bundle.getDisplayName(), jsonBundle.getString("display_name"));
        assertNotNull(jsonBundle.getString("created"));

        return jsonBundle.getString("id");
    }

    private String createApp(String bundleId) {
        Application app = new Application();
        app.setBundleId(UUID.fromString(bundleId));
        app.setName(APP_NAME);
        app.setDisplayName("The best app in the life");
        app.setBundleId(UUID.fromString(bundleId));

        String responseBody = given()
                .when()
                .basePath(INTERNAL_BASE_PATH)
                .contentType(ContentType.JSON)
                .body(Json.encode(app))
                .post("/internal/applications")
                .then()
                .statusCode(200)
                .extract().body().asString();

        JsonObject jsonApp = new JsonObject(responseBody);
        jsonApp.mapTo(Application.class);
        assertNotNull(jsonApp.getString("id"));
        assertEquals(app.getName(), jsonApp.getString("name"));
        assertEquals(app.getDisplayName(), jsonApp.getString("display_name"));
        assertEquals(app.getBundleId().toString(), jsonApp.getString("bundle_id"));
        assertNotNull(jsonApp.getString("created"));

        return jsonApp.getString("id");
    }

    private String createEventType(String appId) {
        EventType eventType = new EventType();
        eventType.setApplicationId(UUID.fromString(appId));
        eventType.setName(EVENT_TYPE_NAME);
        eventType.setDisplayName("Policies will take care of the rules");
        eventType.setDescription("Policies is super cool, you should use it");

        String responseBody = given()
                .when()
                .basePath(INTERNAL_BASE_PATH)
                .contentType(ContentType.JSON)
                .body(Json.encode(eventType))
                .post("/internal/eventTypes")
                .then()
                .statusCode(200)
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
                .extract().body().asString();

        JsonArray jsonEventTypes = new JsonArray(responseBody);
        assertEquals(2, jsonEventTypes.size()); // One from the current test, one from the default DB records.
    }

    private String createBehaviorGroup(Header identityHeader, String displayName, String bundleId) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(UUID.fromString(bundleId));

        String responseBody = given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .contentType(ContentType.JSON)
                .body(Json.encode(behaviorGroup))
                .when()
                .post("/notifications/behaviorGroups")
                .then()
                .statusCode(200)
                .extract().body().asString();

        JsonObject jsonBehaviorGroup = new JsonObject(responseBody);
        jsonBehaviorGroup.mapTo(BehaviorGroup.class);
        assertNotNull(jsonBehaviorGroup.getString("id"));
        assertNull(jsonBehaviorGroup.getString("accountId"));
        assertEquals(behaviorGroup.getDisplayName(), jsonBehaviorGroup.getString("display_name"));
        assertEquals(behaviorGroup.getBundleId().toString(), jsonBehaviorGroup.getString("bundle_id"));
        assertNotNull(jsonBehaviorGroup.getString("created"));

        return jsonBehaviorGroup.getString("id");
    }

    private void setDefaultBehaviorGroup(Header identityHeader, String bundleId, String behaviorGroupId) {
        // Sets the default behavior group.
        given()
                .basePath(INTERNAL_BASE_PATH)
                .pathParam("bundleId", bundleId)
                .pathParam("behaviorGroupId", behaviorGroupId)
                .when()
                .put("/internal/bundles/{bundleId}/behaviorGroups/{behaviorGroupId}/default")
                .then()
                .statusCode(200)
                .contentType(ContentType.TEXT)
                .extract().body().asString();

        // Now let's verify which behavior group is marked as default in the database.
        String responseBody = given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("bundleId", bundleId)
                .when()
                .get("/notifications/bundles/{bundleId}/behaviorGroups")
                .then()
                .statusCode(200)
                .extract().body().asString();

        JsonArray jsonBehaviorGroups = new JsonArray(responseBody);
        assertEquals(2, jsonBehaviorGroups.size());
        for (int i = 0; i < jsonBehaviorGroups.size(); i++) {
            JsonObject jsonBehaviorGroup = jsonBehaviorGroups.getJsonObject(i);
            jsonBehaviorGroup.mapTo(BehaviorGroup.class);
            if (jsonBehaviorGroup.getString("id").equals(behaviorGroupId)) {
                assertTrue(jsonBehaviorGroup.getBoolean("default_behavior"));
            } else {
                assertFalse(jsonBehaviorGroup.getBoolean("default_behavior"));
            }
        }
    }

    private String createWebhookEndpoint(Header identityHeader, String name, String description, String secretToken) {
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setDisableSslVerification(true);
        properties.setSecretToken(secretToken);
        properties.setUrl("http://" + mockServerConfig.getRunningAddress() + WEBHOOK_MOCK_PATH);

        Endpoint endpoint = new Endpoint();
        endpoint.setType(EndpointType.WEBHOOK);
        endpoint.setName(name);
        endpoint.setDescription(description);
        endpoint.setEnabled(true);
        endpoint.setProperties(properties);

        String responseBody = given()
                .basePath(API_INTEGRATIONS_V_1_0)
                .header(identityHeader)
                .contentType(ContentType.JSON)
                .body(Json.encode(endpoint))
                .when()
                .post("/endpoints")
                .then()
                .statusCode(200)
                .extract().body().asString();

        JsonObject jsonEndpoint = new JsonObject(responseBody);
        jsonEndpoint.mapTo(Endpoint.class);
        assertNotNull(jsonEndpoint.getString("id"));
        assertNull(jsonEndpoint.getString("accountId"));
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

    private void addBehaviorGroupActions(Header identityHeader, String behaviorGroupId, String... endpointIds) {
        given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .contentType(ContentType.JSON)
                .pathParam("behaviorGroupId", behaviorGroupId)
                .body(Json.encode(Arrays.asList(endpointIds)))
                .when()
                .put("/notifications/behaviorGroups/{behaviorGroupId}/actions")
                .then()
                .statusCode(200)
                .contentType(ContentType.TEXT);
    }

    /*
     * Pushes a single message to the 'ingress' channel.
     * Depending on the event type, behavior groups and endpoints configuration, it will trigger zero or more webhook calls.
     */
    private void pushMessage(int expectedWebhookCalls) throws IOException, InterruptedException {
        counterAssertionHelper.saveCounterValuesBeforeTest(REJECTED_COUNTER_NAME, PROCESSED_MESSAGES_COUNTER_NAME, PROCESSED_ENDPOINTS_COUNTER_NAME);

        CountDownLatch requestsCounter = new CountDownLatch(expectedWebhookCalls);
        HttpRequest expectedRequestPattern = null;

        if (expectedWebhookCalls > 0) {
            expectedRequestPattern = setupWebhookMock(requestsCounter);
        }

        emitMockedIngressAction();

        if (expectedWebhookCalls > 0) {
            if (!requestsCounter.await(5, TimeUnit.SECONDS)) {
                fail("HttpServer never received the requests");
                HttpRequest[] httpRequests = mockServerConfig.getMockServerClient().retrieveRecordedRequests(expectedRequestPattern);
                assertEquals(2, httpRequests.length);
                // Verify calls were correct, sort first?
            }
            mockServerConfig.getMockServerClient().clear(expectedRequestPattern);
        }

        counterAssertionHelper.assertIncrement(REJECTED_COUNTER_NAME, 0);
        counterAssertionHelper.assertIncrement(PROCESSED_MESSAGES_COUNTER_NAME, 1);
        counterAssertionHelper.assertIncrement(PROCESSED_ENDPOINTS_COUNTER_NAME, expectedWebhookCalls);
        counterAssertionHelper.clear();
    }

    private void emitMockedIngressAction() throws IOException {
        Action action = new Action();
        action.setAccountId("tenant");
        action.setBundle(BUNDLE_NAME);
        action.setApplication(APP_NAME);
        action.setEventType(EVENT_TYPE_NAME);
        action.setTimestamp(LocalDateTime.now());
        action.setContext(Map.of());
        action.setEvents(List.of(
                Event.newBuilder()
                        .setMetadataBuilder(Metadata.newBuilder())
                        .setPayload(Map.of())
                        .build()
        ));

        String serializedAction = serializeAction(action);
        inMemoryConnector.source("ingress").send(serializedAction);
    }

    private HttpRequest setupWebhookMock(CountDownLatch requestsCounter) {
        HttpRequest expectedRequestPattern = new HttpRequest()
                .withPath(WEBHOOK_MOCK_PATH)
                .withMethod("POST");

        mockServerConfig.getMockServerClient()
                .withSecure(false)
                .when(expectedRequestPattern)
                .respond(request -> {
                    requestsCounter.countDown();
                    List<String> header = request.getHeader("X-Insight-Token");
                    if (header != null && header.size() == 1 && SECRET_TOKEN.equals(header.get(0))) {
                        return response().withStatusCode(200)
                                .withBody("Success");
                    } else {
                        return response().withStatusCode(400)
                                .withBody("{ \"message\": \"Time is running out\" }");
                    }
                });

        return expectedRequestPattern;
    }

    private void addEventTypeBehaviorGroup(Header identityHeader, String eventTypeId, String behaviorGroupId, int expectedEventTypeBehaviorGroups) {
        given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("eventTypeId", eventTypeId)
                .pathParam("behaviorGroupId", behaviorGroupId)
                .when()
                .put("/notifications/eventTypes/{eventTypeId}/behaviorGroups/{behaviorGroupId}")
                .then()
                .statusCode(200)
                .contentType(ContentType.TEXT);
    }

    private void checkEventTypeBehaviorGroups(Header identityHeader, String eventTypeId, String... expectedBehaviorGroupIds) {
        String responseBody = given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("eventTypeId", eventTypeId)
                .when()
                .get("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
                .then()
                .statusCode(200)
                .extract().body().asString();

        JsonArray jsonBehaviorGroups = new JsonArray(responseBody);
        assertEquals(expectedBehaviorGroupIds.length, jsonBehaviorGroups.size());

        for (String behaviorGroupId : expectedBehaviorGroupIds) {
            if (!responseBody.contains(behaviorGroupId)) {
                fail("One of the expected behavior groups is not linked to the event type");
            }
        }
    }

    private void muteEventType(Header identityHeader, String eventTypeId) {
        given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("eventTypeId", eventTypeId)
                .when()
                .delete("/notifications/eventTypes/{eventTypeId}/mute")
                .then()
                .statusCode(200);

        String responseBody = given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("eventTypeId", eventTypeId)
                .when()
                .get("/notifications/eventTypes/{eventTypeId}/behaviorGroups")
                .then()
                .statusCode(200)
                .extract().body().asString();

        JsonArray jsonBehaviorGroups = new JsonArray(responseBody);
        assertEquals(0, jsonBehaviorGroups.size());
    }

    private void checkEndpointHistory(Header identityHeader, String endpointId, int expectedHistoryEntries, boolean expectedInvocationResult, int expectedHttpStatus) {
        String responseBody = given()
                .basePath(API_INTEGRATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("endpointId", endpointId)
                .when()
                .get("/endpoints/{endpointId}/history")
                .then()
                .statusCode(200)
                .extract().body().asString();

        JsonArray jsonEndpointHistory = new JsonArray(responseBody);
        assertEquals(expectedHistoryEntries, jsonEndpointHistory.size());

        for (int i = 0; i < jsonEndpointHistory.size(); i++) {
            JsonObject jsonNotificationHistory = jsonEndpointHistory.getJsonObject(i);
            jsonNotificationHistory.mapTo(NotificationHistory.class);
            assertEquals(expectedInvocationResult, jsonNotificationHistory.getBoolean("invocationResult"));

            if (!expectedInvocationResult) {
                responseBody = given()
                        .basePath(API_INTEGRATIONS_V_1_0)
                        .header(identityHeader)
                        .pathParam("endpointId", endpointId)
                        .pathParam("historyId", jsonNotificationHistory.getString("id"))
                        .when()
                        .get("/endpoints/{endpointId}/history/{historyId}/details")
                        .then()
                        .statusCode(200)
                        .extract().body().asString();

                JsonObject jsonDetails = new JsonObject(responseBody);
                assertFalse(jsonDetails.isEmpty());
                assertEquals(expectedHttpStatus, jsonDetails.getInteger("code"));
                assertNotNull(jsonDetails.getString("url"));
                assertNotNull(jsonDetails.getString("method"));
            }
        }
    }

    private void deleteBundle(String bundleId) {
        given()
                .basePath("/")
                .when()
                .pathParam("bundleId", bundleId)
                .delete("/internal/bundles/{bundleId}")
                .then()
                .statusCode(200);
    }
}
