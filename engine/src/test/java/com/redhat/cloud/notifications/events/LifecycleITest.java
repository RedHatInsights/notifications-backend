package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.BehaviorGroupAction;
import com.redhat.cloud.notifications.models.BehaviorGroupActionId;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscriptionId;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeBehavior;
import com.redhat.cloud.notifications.models.EventTypeBehaviorId;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.processors.email.EmailSender;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import com.redhat.cloud.notifications.templates.Blank;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockserver.model.HttpRequest;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.ReflectionHelper.updateField;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
import static com.redhat.cloud.notifications.events.EndpointProcessor.PROCESSED_ENDPOINTS_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EndpointProcessor.PROCESSED_MESSAGES_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.PROCESSING_EXCEPTION_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.REJECTED_COUNTER_NAME;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class LifecycleITest {

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

    @Inject
    EntityManager entityManager;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    FeatureFlipper featureFlipper;

    @Test
    void test_AccountId() {
        featureFlipper.setUseOrgId(false);
        test();
    }

    @Test
    void test_OrgId() {
        featureFlipper.setUseOrgId(true);
        test();
        featureFlipper.setUseOrgId(false);
    }

    void test() {
        final String accountId = "tenant";
        final String username = "user";
        setupEmailMock(accountId, username);

        // First, we need a bundle, an app and an event type. Let's create them!
        Bundle bundle = resourceHelpers.createBundle(BUNDLE_NAME);
        Application app = resourceHelpers.createApp(bundle.getId(), APP_NAME);
        EventType eventType = resourceHelpers.createEventType(app.getId(), EVENT_TYPE_NAME);

        // We also need behavior groups.
        BehaviorGroup behaviorGroup1 = createBehaviorGroup(accountId, bundle);
        BehaviorGroup behaviorGroup2 = createBehaviorGroup(accountId, bundle);
        BehaviorGroup defaultBehaviorGroup = createBehaviorGroup(null, bundle);

        // We need actions for our behavior groups.
        Endpoint endpoint1 = createWebhookEndpoint(accountId, SECRET_TOKEN);
        Endpoint endpoint2 = createWebhookEndpoint(accountId, SECRET_TOKEN);
        Endpoint endpoint3 = createWebhookEndpoint(accountId, "wrong-secret-token");

        // We'll start with a first behavior group actions configuration. This will slightly change later in the test.
        addBehaviorGroupAction(behaviorGroup1.getId(), endpoint1.getId());
        addBehaviorGroupAction(behaviorGroup1.getId(), endpoint2.getId());
        addBehaviorGroupAction(behaviorGroup2.getId(), endpoint3.getId());

        // Adding an email endpoint to the default behavior group
        addDefaultBehaviorGroupAction(defaultBehaviorGroup);

        // Let's push a first message! It should not trigger any webhook call since we didn't link the event type with any behavior group.
        pushMessage(0, 0, 0, 0);

        // Now we'll link the event type with one behavior group.
        addEventTypeBehavior(eventType.getId(), behaviorGroup1.getId());

        // Get the account canonical email endpoint
        Endpoint emailEndpoint = statelessSessionFactory.withSession(statelessSession -> {
            return getAccountCanonicalEmailEndpoint(accountId, DEFAULT_ORG_ID);
        });

        // Pushing a new message should trigger two webhook calls.
        pushMessage(2, 0, 0, 0);

        // Let's check the notifications history.
        retry(() -> checkEndpointHistory(endpoint1, 1, true));
        retry(() -> checkEndpointHistory(endpoint2, 1, true));
        retry(() -> checkEndpointHistory(emailEndpoint, 0, true));

        // We'll link the event type with the default behavior group
        addEventTypeBehavior(eventType.getId(), defaultBehaviorGroup.getId());

        // We'll link an additional behavior group to the event type.
        addEventTypeBehavior(eventType.getId(), behaviorGroup2.getId());

        // Pushing a new message should trigger three webhook calls and 1 emails - email is not sent as the user is not subscribed
        pushMessage(3, 1, 0, 0);

        // Let's check the notifications history again.
        retry(() -> checkEndpointHistory(endpoint1, 2, true));
        retry(() -> checkEndpointHistory(endpoint2, 2, true));
        retry(() -> checkEndpointHistory(endpoint3, 1, false));
        retry(() -> checkEndpointHistory(emailEndpoint, 0, true));

        // Lets subscribe the user to the email preferences
        subscribeUserPreferences(accountId, username, app.getId());

        // Pushing a new message should trigger three webhook calls and 1 email
        pushMessage(3, 1, 1, 0);

        // Let's check the notifications history again.
        retry(() -> checkEndpointHistory(endpoint1, 3, true));
        retry(() -> checkEndpointHistory(endpoint2, 3, true));
        retry(() -> checkEndpointHistory(endpoint3, 2, false));
        retry(() -> checkEndpointHistory(emailEndpoint, 1, true));

        /*
         * Let's change the behavior group actions configuration by adding an action to the second behavior group.
         * Endpoint 2 is now an action for both behavior groups, but it should not be notified twice on each message because we don't want duplicate notifications.
         */
        addBehaviorGroupAction(behaviorGroup2.getId(), endpoint2.getId());

        // Pushing a new message should trigger three webhook calls.
        pushMessage(3, 1, 1, 0);

        // Let's check the notifications history again.
        retry(() -> checkEndpointHistory(endpoint1, 4, true));
        retry(() -> checkEndpointHistory(endpoint2, 4, true));
        retry(() -> checkEndpointHistory(endpoint3, 3, false));
        retry(() -> checkEndpointHistory(emailEndpoint, 2, true));

        /*
         * What happens if we unlink the event type from the behavior groups?
         * Pushing a new message should not trigger any webhook call.
         */
        // Unlinking user behavior group
        clearEventTypeBehaviors(eventType);

        pushMessage(0, 0, 0, 0);

        // The notifications history should be exactly the same than last time.
        retry(() -> checkEndpointHistory(endpoint1, 4, true));
        retry(() -> checkEndpointHistory(endpoint2, 4, true));
        retry(() -> checkEndpointHistory(endpoint3, 3, false));
        retry(() -> checkEndpointHistory(emailEndpoint, 2, true));

        // Linking the default behavior group again
        addEventTypeBehavior(eventType.getId(), defaultBehaviorGroup.getId());
        pushMessage(0, 1, 1, 0);

        // Deleting the default behavior group should unlink it
        deleteBehaviorGroup(defaultBehaviorGroup);
        pushMessage(0, 0, 0, 0);

        // We'll finish with a bundle removal.
        deleteBundle(bundle);
    }

    @Transactional
    BehaviorGroup createBehaviorGroup(String accountId, Bundle bundle) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setAccountId(accountId);
        behaviorGroup.setOrgId(DEFAULT_ORG_ID);
        behaviorGroup.setDisplayName("Behavior group");
        behaviorGroup.setBundle(bundle);
        behaviorGroup.setBundleId(bundle.getId());
        entityManager.persist(behaviorGroup);
        return behaviorGroup;
    }

    @Transactional
    void deleteBehaviorGroup(BehaviorGroup behaviorGroup) {
        entityManager.createQuery("DELETE FROM BehaviorGroup WHERE id = :id")
                .setParameter("id", behaviorGroup.getId())
                .executeUpdate();
    }

    Endpoint getAccountCanonicalEmailEndpoint(String accountId, String orgId) {
        return endpointRepository.getOrCreateDefaultEmailSubscription(accountId, orgId);
    }

    private Endpoint createWebhookEndpoint(String accountId, String secretToken) {
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setDisableSslVerification(true);
        properties.setSecretToken(secretToken);
        properties.setUrl(getMockServerUrl() + WEBHOOK_MOCK_PATH);
        return createEndpoint(accountId, WEBHOOK, "endpoint", "Endpoint", properties);
    }

    private void addDefaultBehaviorGroupAction(BehaviorGroup behaviorGroup) {
        EmailSubscriptionProperties properties = new EmailSubscriptionProperties();
        properties.setOnlyAdmins(true);
        Endpoint endpoint = createEndpoint(null, EMAIL_SUBSCRIPTION, "Email endpoint", "System email endpoint", properties);
        addBehaviorGroupAction(behaviorGroup.getId(), endpoint.getId());
    }

    @Transactional
    Endpoint createEndpoint(String accountId, EndpointType type, String name, String description, EndpointProperties properties) {
        Endpoint endpoint = new Endpoint();
        endpoint.setType(type);
        endpoint.setAccountId(accountId);
        endpoint.setOrgId(DEFAULT_ORG_ID);
        endpoint.setEnabled(true);
        endpoint.setName(name);
        endpoint.setDescription(description);
        endpoint.setProperties(properties);
        properties.setEndpoint(endpoint);

        entityManager.persist(endpoint);
        entityManager.persist(endpoint.getProperties());
        return endpoint;
    }

    @Transactional
    void addBehaviorGroupAction(UUID behaviorGroupId, UUID endpointId) {
        BehaviorGroup behaviorGroup = entityManager.find(BehaviorGroup.class, behaviorGroupId);
        Endpoint endpoint = entityManager.find(Endpoint.class, endpointId);
        BehaviorGroupAction action = new BehaviorGroupAction();
        action.setId(new BehaviorGroupActionId());
        action.setBehaviorGroup(behaviorGroup);
        action.setEndpoint(endpoint);
        entityManager.persist(action);
    }

    /*
     * Pushes a single message to the 'ingress' channel.
     * Depending on the event type, behavior groups and endpoints configuration, it will trigger zero or more webhook calls.
     */
    private void pushMessage(int expectedWebhookCalls, int expectedEmailEndpoints, int expectedSentEmails, int expectedExceptionCount) {
        micrometerAssertionHelper.saveCounterValuesBeforeTest(REJECTED_COUNTER_NAME, PROCESSING_EXCEPTION_COUNTER_NAME, PROCESSED_MESSAGES_COUNTER_NAME, PROCESSED_ENDPOINTS_COUNTER_NAME);

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

        try {
            emitMockedIngressAction();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        waitForWebhooks.run();
        waitForEmails.run();

        micrometerAssertionHelper.awaitAndAssertCounterIncrement(PROCESSED_MESSAGES_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(REJECTED_COUNTER_NAME, 0);
        micrometerAssertionHelper.assertCounterIncrement(PROCESSING_EXCEPTION_COUNTER_NAME, expectedExceptionCount);
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
                MockServerLifecycleManager.getClient().clear(request);
            }
        };
    }

    private void emitMockedIngressAction() throws IOException {
        Action action = new Action();
        action.setAccountId("tenant");
        action.setOrgId(DEFAULT_ORG_ID);
        action.setVersion("v1.0.0");
        action.setBundle(BUNDLE_NAME);
        action.setApplication(APP_NAME);
        action.setEventType(EVENT_TYPE_NAME);
        action.setTimestamp(LocalDateTime.now());
        action.setContext(new Context.ContextBuilder().build());
        action.setRecipients(List.of());
        action.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(new Payload.PayloadBuilder().build())
                        .build()
        ));

        String serializedAction = serializeAction(action);
        inMemoryConnector.source("ingress").send(serializedAction);
    }

    private void setupEmailMock(String accountId, String username) {
        Mockito.when(emailTemplateFactory.get(anyString(), anyString())).thenReturn(new Blank());

        User user = new User();
        user.setUsername(username);
        user.setAdmin(true);
        user.setActive(true);
        user.setEmail("user email");
        user.setFirstName("user firstname");
        user.setLastName("user lastname");

        Mockito.when(rbacRecipientUsersProvider.getUsers(
                eq(accountId),
                eq(DEFAULT_ORG_ID),
                eq(true)
        )).thenReturn(List.of(user));

        updateField(
                emailSender,
                "bopUrl",
                getMockServerUrl() + EMAIL_SENDER_MOCK_PATH,
                EmailSender.class
        );
    }

    private HttpRequest setupEmailMockServer(CountDownLatch requestsCounter) {
        HttpRequest expectedRequestPattern = new HttpRequest()
                .withPath(EMAIL_SENDER_MOCK_PATH)
                .withMethod("POST");

        MockServerLifecycleManager.getClient()
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

        MockServerLifecycleManager.getClient()
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

    @Transactional
    void addEventTypeBehavior(UUID eventTypeId, UUID behaviorGroupId) {
        EventType eventType = entityManager.find(EventType.class, eventTypeId);
        BehaviorGroup behaviorGroup = entityManager.find(BehaviorGroup.class, behaviorGroupId);
        EventTypeBehavior behavior = new EventTypeBehavior();
        behavior.setId(new EventTypeBehaviorId());
        behavior.setEventType(eventType);
        behavior.setBehaviorGroup(behaviorGroup);
        entityManager.persist(behavior);
    }

    @Transactional
    void clearEventTypeBehaviors(EventType eventType) {
        entityManager.createQuery("DELETE FROM EventTypeBehavior WHERE eventType = :eventType")
                .setParameter("eventType", eventType)
                .executeUpdate();
    }

    private void retry(Supplier<Boolean> checkEndpointHistoryResult) {
        await()
                .pollInterval(Duration.ofSeconds(1L))
                .atMost(Duration.ofSeconds(5L))
                .until(checkEndpointHistoryResult::get);
    }

    @Transactional
    boolean checkEndpointHistory(Endpoint endpoint, int expectedHistoryEntries, boolean expectedInvocationResult) {
        return entityManager.createQuery("FROM NotificationHistory WHERE endpoint = :endpoint AND invocationResult = :invocationResult", NotificationHistory.class)
                .setParameter("endpoint", endpoint)
                .setParameter("invocationResult", expectedInvocationResult)
                .getResultList().size() == expectedHistoryEntries;
    }

    @Transactional
    void deleteBundle(Bundle bundle) {
        entityManager.createQuery("DELETE FROM Bundle WHERE id = :id")
                .setParameter("id", bundle.getId())
                .executeUpdate();
    }

    @Transactional
    void subscribeUserPreferences(String accountId, String userId, UUID appId) {
        Application application = entityManager.find(Application.class, appId);
        EmailSubscription subscription = new EmailSubscription();
        subscription.setId(new EmailSubscriptionId());
        subscription.setAccountId(accountId);
        subscription.setOrgId(DEFAULT_ORG_ID);
        subscription.setUserId(userId);
        subscription.setApplication(application);
        subscription.setType(INSTANT);
        entityManager.persist(subscription);
    }
}
