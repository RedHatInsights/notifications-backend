package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.ResourceHelpers;
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
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeBehavior;
import com.redhat.cloud.notifications.models.EventTypeBehaviorId;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscriptionId;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
import static com.redhat.cloud.notifications.events.EndpointProcessor.PROCESSED_ENDPOINTS_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EndpointProcessor.PROCESSED_MESSAGES_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.PROCESSING_EXCEPTION_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.REJECTED_COUNTER_NAME;
import static com.redhat.cloud.notifications.models.EndpointStatus.READY;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;
import static com.redhat.cloud.notifications.processors.ConnectorSender.TOCAMEL_CHANNEL;
import static com.redhat.cloud.notifications.processors.ConnectorSender.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class LifecycleITest {

    /*
     * In the tests below, most JSON responses are verified using JsonObject/JsonArray instead of deserializing these
     * responses into model instances and checking their attributes values. That's because the model classes contain
     * attributes annotated with @JsonProperty(access = READ_ONLY) which can't be deserialized and therefore verified
     * here. The deserialization is still performed only to verify that the JSON responses data structure is correct.
     */

    private static final String WEBHOOK_MOCK_PATH = "/test/lifecycle";
    private static final String SECRET_TOKEN = "super-secret-token";

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @Inject
    EntityManager entityManager;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectSpy
    EngineConfig engineConfig;

    @InjectMock
    TemplateService templateService;

    String bundleName;
    String applicationName;
    String eventTypeName;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void test(final boolean useEndpointToEventTypeDirectLink) {
        final String accountId = "tenant";
        final String username = "user";

        when(engineConfig.isUseDirectEndpointToEventTypeEnabled()).thenReturn(useEndpointToEventTypeDirectLink);
        bundleName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        applicationName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        eventTypeName = RandomStringUtils.randomAlphabetic(10).toLowerCase();

        // First, we need a bundle, an app and an event type. Let's create them!
        Bundle bundle = resourceHelpers.createBundle(bundleName);
        Application app = resourceHelpers.createApp(bundle.getId(), applicationName);
        EventType eventType = resourceHelpers.createEventType(app.getId(), eventTypeName);
        setupEmailMock();

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
        Endpoint emailEndpoint = getAccountCanonicalEmailEndpoint(accountId, DEFAULT_ORG_ID);

        // Pushing a new message should trigger two webhook calls.
        pushMessage(2, 0, 0, 0);

        // Let's check the notifications history.
        retry(() -> checkEndpointHistory(endpoint1, 1));
        retry(() -> checkEndpointHistory(endpoint2, 1));
        retry(() -> checkEndpointHistory(emailEndpoint, 0));

        // We'll link the event type with the default behavior group
        addEventTypeBehavior(eventType.getId(), defaultBehaviorGroup.getId());

        // We'll link an additional behavior group to the event type.
        addEventTypeBehavior(eventType.getId(), behaviorGroup2.getId());

        // Pushing a new message should trigger three webhook calls and 1 emails - email is not sent as the user is not subscribed
        pushMessage(3, 1, 0, 0);

        // Let's check the notifications history again.
        retry(() -> checkEndpointHistory(endpoint1, 2));
        retry(() -> checkEndpointHistory(endpoint2, 2));
        retry(() -> checkEndpointHistory(endpoint3, 1));
        retry(() -> checkEndpointHistory(emailEndpoint, 0));

        // Lets subscribe the user to the email preferences
        subscribeUserPreferences(username, eventType.getId());

        // Pushing a new message should trigger three webhook calls and 1 email
        pushMessage(3, 1, 1, 0);

        // Let's check the notifications history again.
        retry(() -> checkEndpointHistory(endpoint1, 3));
        retry(() -> checkEndpointHistory(endpoint2, 3));
        retry(() -> checkEndpointHistory(endpoint3, 2));
        retry(() -> checkEndpointHistory(emailEndpoint, 1));

        /*
         * Let's change the behavior group actions configuration by adding an action to the second behavior group.
         * Endpoint 2 is now an action for both behavior groups, but it should not be notified twice on each message because we don't want duplicate notifications.
         */
        addBehaviorGroupAction(behaviorGroup2.getId(), endpoint2.getId());

        // Pushing a new message should trigger three webhook calls.
        pushMessage(3, 1, 1, 0);

        // Let's check the notifications history again.
        retry(() -> checkEndpointHistory(endpoint1, 4));
        retry(() -> checkEndpointHistory(endpoint2, 4));
        retry(() -> checkEndpointHistory(endpoint3, 3));
        retry(() -> checkEndpointHistory(emailEndpoint, 2));

        /*
         * What happens if we unlink the event type from the behavior groups?
         * Pushing a new message should not trigger any webhook call.
         */
        // Unlinking user behavior group
        clearEventTypeBehaviors(eventType);

        pushMessage(0, 0, 0, 0);

        // The notifications history should be exactly the same than last time.
        retry(() -> checkEndpointHistory(endpoint1, 4));
        retry(() -> checkEndpointHistory(endpoint2, 4));
        retry(() -> checkEndpointHistory(endpoint3, 3));
        retry(() -> checkEndpointHistory(emailEndpoint, 2));

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
        behaviorGroup.setDisplayName(UUID.randomUUID().toString());
        behaviorGroup.setBundle(bundle);
        behaviorGroup.setBundleId(bundle.getId());
        entityManager.persist(behaviorGroup);
        return behaviorGroup;
    }

    @Transactional
    void deleteBehaviorGroup(BehaviorGroup behaviorGroup) {
        List<UUID> endpoints = resourceHelpers.findEndpointsByBehaviorGroupId(behaviorGroup.getOrgId(), Set.of(behaviorGroup.getId()));
        int result = entityManager.createQuery("DELETE FROM BehaviorGroup WHERE id = :id")
            .setParameter("id", behaviorGroup.getId())
            .executeUpdate();

        if (result > 0) {
            resourceHelpers.refreshEndpointLinksToEventType(behaviorGroup.getOrgId(), endpoints);
        }
    }

    Endpoint getAccountCanonicalEmailEndpoint(String accountId, String orgId) {
        return endpointRepository.getOrCreateDefaultSystemSubscription(accountId, orgId, EMAIL_SUBSCRIPTION);
    }

    private Endpoint createWebhookEndpoint(String accountId, String secretToken) {
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setDisableSslVerification(true);
        properties.setSecretToken(secretToken);
        properties.setUrl(getMockServerUrl() + WEBHOOK_MOCK_PATH);
        return createEndpoint(accountId, WEBHOOK, UUID.randomUUID().toString(), "Endpoint", properties);
    }

    private void addDefaultBehaviorGroupAction(BehaviorGroup behaviorGroup) {
        SystemSubscriptionProperties properties = new SystemSubscriptionProperties();
        properties.setOnlyAdmins(true);
        Endpoint endpoint = createEndpoint(null, EMAIL_SUBSCRIPTION, "Email endpoint " + RandomStringUtils.randomAlphabetic(10), "System email endpoint", properties);
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
        endpoint.setStatus(READY);
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
        resourceHelpers.refreshEndpointLinksToEventType(behaviorGroup.getOrgId(), List.of(endpoint.getId()));
    }

    /*
     * Pushes a single message to the 'ingress' channel.
     * Depending on the event type, behavior groups and endpoints configuration, it will trigger zero or more webhook calls.
     */
    private void pushMessage(int expectedWebhookCalls, int expectedEmailEndpoints, int expectedSentEmails, int expectedExceptionCount) {
        entityManager.clear(); // The Hibernate L1 cache contains outdated data and needs to be cleared.

        micrometerAssertionHelper.saveCounterValuesBeforeTest(REJECTED_COUNTER_NAME, PROCESSING_EXCEPTION_COUNTER_NAME, PROCESSED_MESSAGES_COUNTER_NAME, PROCESSED_ENDPOINTS_COUNTER_NAME);

        InMemorySink<JsonObject> inMemorySink = inMemoryConnector.sink(TOCAMEL_CHANNEL);
        inMemorySink.clear();

        try {
            emitMockedIngressAction();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        await().until(() -> inMemorySink.received().size() == expectedWebhookCalls + expectedSentEmails);

        Map<String, Long> messagesCountByConnectorHeader = inMemorySink.received().stream()
                .collect(Collectors.groupingBy(this::extractConnectorHeader, Collectors.counting()));

        if (expectedWebhookCalls > 0) {
            assertEquals(expectedWebhookCalls, messagesCountByConnectorHeader.get("webhook"), "HttpServer never received the requests");
        }

        if (expectedSentEmails > 0) {
            assertEquals(expectedSentEmails, messagesCountByConnectorHeader.get("email_subscription"), "Emails were never sent");
        }

        /*
         * If the message isn't supposed to trigger any notification, we need to wait a bit
         * to prevent race conditions between this test and EndpointProcessor.
         */
        if (expectedWebhookCalls + expectedEmailEndpoints == 0) {
            try {
                Thread.sleep(5000L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        micrometerAssertionHelper.awaitAndAssertCounterIncrement(PROCESSED_MESSAGES_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(REJECTED_COUNTER_NAME, 0);
        micrometerAssertionHelper.assertCounterIncrement(PROCESSING_EXCEPTION_COUNTER_NAME, expectedExceptionCount);
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_ENDPOINTS_COUNTER_NAME, expectedWebhookCalls + expectedEmailEndpoints);
        micrometerAssertionHelper.clearSavedValues();
    }

    private String extractConnectorHeader(Message<JsonObject> message) {
        byte[] actualConnectorHeader = message.getMetadata(KafkaMessageMetadata.class)
                .get()
                .getHeaders().headers(X_RH_NOTIFICATIONS_CONNECTOR_HEADER)
                .iterator().next().value();
        return new String(actualConnectorHeader, UTF_8);
    }

    private void emitMockedIngressAction() throws IOException {
        Action action = new Action();
        action.setAccountId("tenant");
        action.setOrgId(DEFAULT_ORG_ID);
        action.setVersion("v1.0.0");
        action.setBundle(bundleName);
        action.setApplication(applicationName);
        action.setEventType(eventTypeName);
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

    private void setupEmailMock() {
        resourceHelpers.createBlankInstantEmailTemplate(bundleName, applicationName, eventTypeName);
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
        resourceHelpers.refreshEndpointLinksToEventTypeFromBehaviorGroup(behaviorGroup.getOrgId(), Set.of(behaviorGroupId));
    }

    @Transactional
    void clearEventTypeBehaviors(EventType eventType) {
        List<BehaviorGroup> listOfImpactedBg = entityManager.createQuery("SELECT behaviorGroup FROM EventTypeBehavior WHERE eventType = :eventType", BehaviorGroup.class)
            .setParameter("eventType", eventType)
            .getResultList();

        int result = entityManager.createQuery("DELETE FROM EventTypeBehavior WHERE eventType = :eventType")
                .setParameter("eventType", eventType)
                .executeUpdate();

        if (result > 0) {
            resourceHelpers.refreshEndpointLinksToEventTypeFromBehaviorGroup(listOfImpactedBg.getFirst().getOrgId(), listOfImpactedBg.stream().map(BehaviorGroup::getId).collect(Collectors.toSet()));
        }
    }

    private void retry(Supplier<Boolean> checkEndpointHistoryResult) {
        await()
                .pollInterval(Duration.ofSeconds(1L))
                .atMost(Duration.ofSeconds(5L))
                .until(checkEndpointHistoryResult::get);
    }

    @Transactional
    boolean checkEndpointHistory(Endpoint endpoint, int expectedHistoryEntries) {
        return entityManager.createQuery("FROM NotificationHistory WHERE endpoint = :endpoint", NotificationHistory.class)
                .setParameter("endpoint", endpoint)
                .getResultList().size() == expectedHistoryEntries;
    }

    @Transactional
    void deleteBundle(Bundle bundle) {
        entityManager.createQuery("DELETE FROM Bundle WHERE id = :id")
                .setParameter("id", bundle.getId())
                .executeUpdate();
    }

    @Transactional
    void subscribeUserPreferences(String userId, UUID eventTypeId) {
        EventType eventType = entityManager.find(EventType.class, eventTypeId);
        EventTypeEmailSubscription subscription = new EventTypeEmailSubscription();
        subscription.setId(new EventTypeEmailSubscriptionId());
        subscription.setSubscribed(true);
        subscription.setUserId(userId);
        subscription.setEventType(eventType);
        subscription.setOrgId(DEFAULT_ORG_ID);
        subscription.setType(INSTANT);
        entityManager.persist(subscription);
    }
}
