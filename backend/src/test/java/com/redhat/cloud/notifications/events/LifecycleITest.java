package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
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
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.processors.email.EmailSender;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import com.redhat.cloud.notifications.routers.models.RequestEmailSubscriptionProperties;
import com.redhat.cloud.notifications.routers.models.SettingsValues;
import com.redhat.cloud.notifications.templates.Blank;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.restassured.http.Header;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockserver.model.HttpRequest;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess;
import static com.redhat.cloud.notifications.TestConstants.API_INTEGRATIONS_V_1_0;
import static com.redhat.cloud.notifications.TestConstants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
import static com.redhat.cloud.notifications.TestHelpers.updateField;
import static com.redhat.cloud.notifications.events.EndpointProcessor.PROCESSED_ENDPOINTS_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EndpointProcessor.PROCESSED_MESSAGES_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.REJECTED_COUNTER_NAME;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockserver.model.HttpResponse.response;

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
    private static final String EMAIL_SENDER_MOCK_PATH = "/test-email-sender/lifecycle";
    private static final String SECRET_TOKEN = "super-secret-token";

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @InjectMock
    EmailTemplateFactory emailTemplateFactory;

    @InjectMock
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    // InjectSpy allows us to update the fields via reflection (Inject does not)
    @InjectSpy
    EmailSender emailSender;

    private Header initRbacMock(String tenant, String username, MockServerClientConfig.RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, username);
        mockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createIdentityHeader(identityHeaderValue);
    }

    @Test
    void test() throws IOException, InterruptedException {
        final String accountId = "tenant";
        final String username = "user";
        setupEmailMock(accountId, username);

        EmailSubscriptionProperties defaultBehaviorGroupProperties = new EmailSubscriptionProperties();
        defaultBehaviorGroupProperties.setOnlyAdmins(true);

        // All events are stored in the canonical email endpoint
        RequestEmailSubscriptionProperties userEmailEndpointRequest = new RequestEmailSubscriptionProperties();

        // Identity header used for all public APIs calls. Internal APIs calls don't need that.
        Header identityHeader = initRbacMock(accountId, username, RbacAccess.FULL_ACCESS);

        // First, we need a bundle, an app and an event type. Let's create them!
        String bundleId = createBundle();
        String appId = createApp(bundleId);
        String eventTypeId = createEventType(appId);
        checkAllEventTypes(identityHeader);

        // We also need behavior groups.
        String behaviorGroupId1 = createBehaviorGroup(identityHeader, bundleId);
        String behaviorGroupId2 = createBehaviorGroup(identityHeader, bundleId);
        String defaultBehaviorGroupId = createDefaultBehaviorGroup(bundleId);

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
        addDefaultBehaviorGroupActions(behaviorGroupId1, 404, defaultBehaviorGroupProperties);

        // Adding the same config multiple times yields an error
        addDefaultBehaviorGroupActions(defaultBehaviorGroupId, 400, defaultBehaviorGroupProperties, defaultBehaviorGroupProperties, defaultBehaviorGroupProperties);

        // Adding an email endpoint to the default behavior group
        addDefaultBehaviorGroupActions(defaultBehaviorGroupId, 200, defaultBehaviorGroupProperties);

        // Let's push a first message! It should not trigger any webhook call since we didn't link the event type with any behavior group.
        pushMessage(0, 0, 0);

        // Now we'll link the event type with one behavior group.
        updateEventTypeBehaviors(identityHeader, eventTypeId, behaviorGroupId1);
        checkEventTypeBehaviorGroups(identityHeader, eventTypeId, behaviorGroupId1);

        // Get user email endpoint
        String emailEndpoint = getEmailEndpoint(identityHeader, userEmailEndpointRequest);

        // Pushing a new message should trigger two webhook calls.
        pushMessage(2, 0, 0);

        // Let's check the notifications history.
        retry(() -> checkEndpointHistory(identityHeader, endpointId1, 1, true, 200));
        retry(() -> checkEndpointHistory(identityHeader, endpointId2, 1, true, 200));
        retry(() -> checkEndpointHistory(identityHeader, emailEndpoint, 0, true, 200));

        // We'll link with the default behavior group
        linkDefaultBehaviorGroup(eventTypeId, defaultBehaviorGroupId);
        checkEventTypeBehaviorGroups(identityHeader, eventTypeId, behaviorGroupId1, defaultBehaviorGroupId);

        // We'll link an additional behavior group to the event type.
        updateEventTypeBehaviors(identityHeader, eventTypeId, behaviorGroupId1, behaviorGroupId2);
        checkEventTypeBehaviorGroups(identityHeader, eventTypeId, behaviorGroupId1, behaviorGroupId2, defaultBehaviorGroupId);

        // Pushing a new message should trigger three webhook calls and 1 emails - email is not sent as the user is not subscribed
        pushMessage(3, 1, 0);

        // Let's check the notifications history again.
        retry(() -> checkEndpointHistory(identityHeader, endpointId1, 2, true, 200));
        retry(() -> checkEndpointHistory(identityHeader, endpointId2, 2, true, 200));
        retry(() -> checkEndpointHistory(identityHeader, endpointId3, 1, false, 400));
        retry(() -> checkEndpointHistory(identityHeader, emailEndpoint, 0, true, 200));

        // Lets subscribe the user to the email preferences
        subscribeUserPreferences(identityHeader, BUNDLE_NAME, APP_NAME);

        // Pushing a new message should trigger three webhook calls and 1 email
        pushMessage(3, 1, 1);

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

        // Pushing a new message should trigger three webhook calls.
        pushMessage(3, 1, 1);

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
        unlinkDefaultBehaviorGroup(eventTypeId, defaultBehaviorGroupId);
        checkEventTypeBehaviorGroups(identityHeader, eventTypeId);
        pushMessage(0, 0, 0);

        // The notifications history should be exactly the same than last time.
        retry(() -> checkEndpointHistory(identityHeader, endpointId1, 4, true, 200));
        retry(() -> checkEndpointHistory(identityHeader, endpointId2, 4, true, 200));
        retry(() -> checkEndpointHistory(identityHeader, endpointId3, 3, false, 400));
        retry(() -> checkEndpointHistory(identityHeader, emailEndpoint, 2, true, 200));

        /*
         * We'll finish with a bundle removal.
         * Why would we do that here? I don't really know, it was there before the big test refactor... :)
         */
        deleteBundle(bundleId);
    }

    private String createBundle() {
        Bundle bundle = new Bundle(BUNDLE_NAME, "A bundle");

        String responseBody = given()
                .basePath(API_INTERNAL)
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

    private String createApp(String bundleId) {
        Application app = new Application();
        app.setBundleId(UUID.fromString(bundleId));
        app.setName(APP_NAME);
        app.setDisplayName("The best app in the life");

        String responseBody = given()
                .when()
                .basePath(API_INTERNAL)
                .contentType(JSON)
                .body(Json.encode(app))
                .post("/applications")
                .then()
                .statusCode(200)
                .contentType(JSON)
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
                .basePath(API_INTERNAL)
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

        JsonArray jsonEventTypes = new JsonArray(responseBody);
        assertEquals(2, jsonEventTypes.size()); // One from the current test, one from the default DB records.
    }

    private String createBehaviorGroupInternal(String path, Header identityHeader, String bundleId) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setDisplayName("Behavior group");
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
        assertEquals(behaviorGroup.getDisplayName(), jsonBehaviorGroup.getString("display_name"));
        assertEquals(behaviorGroup.getBundleId().toString(), jsonBehaviorGroup.getString("bundle_id"));
        assertNotNull(jsonBehaviorGroup.getString("created"));

        return jsonBehaviorGroup.getString("id");
    }

    private String createBehaviorGroup(Header identityHeader, String bundleId) {
        return createBehaviorGroupInternal(API_NOTIFICATIONS_V_1_0 + "/notifications/behaviorGroups", identityHeader, bundleId);
    }

    private String createDefaultBehaviorGroup(String bundleId) {
        return createBehaviorGroupInternal(API_INTERNAL + "/defaultBehaviorGroups", null, bundleId);
    }

    private String getEmailEndpoint(Header identityHeader, RequestEmailSubscriptionProperties properties) {
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
        properties.setUrl("http://" + mockServerConfig.getRunningAddress() + WEBHOOK_MOCK_PATH);

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
                .statusCode(expectedHttpStatusCode)
                .contentType(TEXT);
    }

    private void addDefaultBehaviorGroupActions(String defaultBehaviorGroupId, int expectedHttpStatusCode, EmailSubscriptionProperties... properties) {
        given()
                .basePath(API_INTERNAL)
                .contentType(JSON)
                .pathParam("behaviorGroupId", defaultBehaviorGroupId)
                .body(Json.encode(List.of(properties)))
                .when()
                .put("/defaultBehaviorGroups/{behaviorGroupId}/actions")
                .then()
                .statusCode(expectedHttpStatusCode)
                .contentType(TEXT);
    }

    /*
     * Pushes a single message to the 'ingress' channel.
     * Depending on the event type, behavior groups and endpoints configuration, it will trigger zero or more webhook calls.
     */
    private void pushMessage(int expectedWebhookCalls, int expectedEmailEndpoints, int expectedSentEmails) throws IOException, InterruptedException {
        micrometerAssertionHelper.saveCounterValuesBeforeTest(REJECTED_COUNTER_NAME, PROCESSED_MESSAGES_COUNTER_NAME, PROCESSED_ENDPOINTS_COUNTER_NAME);

        Runnable waitForWebhooks = setupCountdownCalls(
                expectedWebhookCalls,
                "HttpServer never received the requests",
                this::setupWebhookMock
        );

        Runnable waitForEmails = setupCountdownCalls(
                expectedSentEmails,
                "Emails were never sent",
                this::setupEmailMockServer
        );

        emitMockedIngressAction();

        waitForWebhooks.run();
        waitForEmails.run();

        micrometerAssertionHelper.awaitAndAssertCounterIncrement(PROCESSED_MESSAGES_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(REJECTED_COUNTER_NAME, 0);
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_ENDPOINTS_COUNTER_NAME, expectedWebhookCalls + expectedEmailEndpoints);
        micrometerAssertionHelper.clearSavedValues();
    }

    private Runnable setupCountdownCalls(int expected, String failMessage, Function<CountDownLatch, HttpRequest> setup) {
        CountDownLatch requestCounter = new CountDownLatch(expected);
        final HttpRequest request;

        if (expected > 0) {
            request = setup.apply(requestCounter);
        } else {
            request = null;
        }

        return () -> {
            if (expected > 0) {
                try {
                    if (!requestCounter.await(30, TimeUnit.SECONDS)) {
                        fail(failMessage);
                    }
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                mockServerConfig.getMockServerClient().clear(request);
            }
        };
    }

    private void emitMockedIngressAction() throws IOException {
        Action action = new Action();
        action.setAccountId("tenant");
        action.setVersion("v1.0.0");
        action.setBundle(BUNDLE_NAME);
        action.setApplication(APP_NAME);
        action.setEventType(EVENT_TYPE_NAME);
        action.setTimestamp(LocalDateTime.now());
        action.setContext(Map.of());
        action.setRecipients(List.of());
        action.setEvents(List.of(
                Event.newBuilder()
                        .setMetadataBuilder(Metadata.newBuilder())
                        .setPayload(Map.of())
                        .build()
        ));

        String serializedAction = serializeAction(action);
        inMemoryConnector.source("ingress").send(serializedAction);
    }

    private void setupEmailMock(String accountId, String username) {
        Mockito.when(emailTemplateFactory.get(anyString(), anyString())).then(_ignored -> new Blank());

        User user = new User();
        user.setUsername(username);
        user.setAdmin(true);
        user.setActive(true);
        user.setEmail("user email");
        user.setFirstName("user firstname");
        user.setLastName("user lastname");

        Mockito.when(rbacRecipientUsersProvider.getUsers(
                eq(accountId),
                eq(true)
        )).thenReturn(Uni.createFrom().item(List.of(user)));

        updateField(
                emailSender,
                "bopUrl",
                "http://" + mockServerConfig.getRunningAddress() + EMAIL_SENDER_MOCK_PATH,
                EmailSender.class
        );
    }

    private HttpRequest setupEmailMockServer(CountDownLatch requestsCounter) {
        HttpRequest expectedRequestPattern = new HttpRequest()
                .withPath(EMAIL_SENDER_MOCK_PATH)
                .withMethod("POST");

        mockServerConfig.getMockServerClient()
                .withSecure(false)
                .when(expectedRequestPattern)
                .respond(request -> {
                    requestsCounter.countDown();
                    return response().withStatusCode(200).withBody("Success");
                });

        return expectedRequestPattern;
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
                .statusCode(200)
                .contentType(TEXT);
    }

    private void linkDefaultBehaviorGroup(String eventTypeId, String behaviorGroupId) {
        given()
                .basePath(API_INTERNAL)
                .pathParam("behaviorGroupId", behaviorGroupId)
                .pathParam("eventTypeId", eventTypeId)
                .when()
                .put("/defaultBehaviorGroups/{behaviorGroupId}/eventType/{eventTypeId}")
                .then()
                .statusCode(200)
                .contentType(TEXT);
    }

    private void unlinkDefaultBehaviorGroup(String eventTypeId, String behaviorGroupId) {
        given()
                .basePath(API_INTERNAL)
                .pathParam("behaviorGroupId", behaviorGroupId)
                .pathParam("eventTypeId", eventTypeId)
                .when()
                .delete("/defaultBehaviorGroups/{behaviorGroupId}/eventType/{eventTypeId}")
                .then()
                .statusCode(200)
                .contentType(TEXT);
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

    private void retry(Supplier<Boolean> checkEndpointHistoryResult) {
        await()
                .pollInterval(Duration.ofSeconds(1L))
                .atMost(Duration.ofSeconds(5L))
                .until(checkEndpointHistoryResult::get);
    }

    private boolean checkEndpointHistory(Header identityHeader, String endpointId, int expectedHistoryEntries, boolean expectedInvocationResult, int expectedHttpStatus) {
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
                            .contentType(JSON)
                            .extract().body().asString();

                    JsonObject jsonDetails = new JsonObject(responseBody);
                    assertFalse(jsonDetails.isEmpty());
                    assertEquals(expectedHttpStatus, jsonDetails.getInteger("code"));
                    assertNotNull(jsonDetails.getString("url"));
                    assertNotNull(jsonDetails.getString("method"));
                }
            }

            return true;
        } catch (AssertionError e) {
            e.printStackTrace();
            return false;
        }
    }

    private void deleteBundle(String bundleId) {
        given()
                .basePath(API_INTERNAL)
                .when()
                .pathParam("bundleId", bundleId)
                .delete("/bundles/{bundleId}")
                .then()
                .statusCode(200)
                .contentType(JSON);
    }

    private void subscribeUserPreferences(Header identityHeader, String bundleName, String appName) {
        SettingsValues values = new SettingsValues();
        SettingsValues.ApplicationSettingsValue applicationSettingsValue = new SettingsValues.ApplicationSettingsValue();
        applicationSettingsValue.notifications.put(EmailSubscriptionType.INSTANT, true);
        SettingsValues.BundleSettingsValue bundleSettingsValue = new SettingsValues.BundleSettingsValue();
        bundleSettingsValue.applications.put(appName, applicationSettingsValue);
        values.bundles.put(bundleName, bundleSettingsValue);

        given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(values))
                .when()
                .post("/user-config/notification-preference")
                .then()
                .statusCode(200)
                .contentType(TEXT);
    }
}
