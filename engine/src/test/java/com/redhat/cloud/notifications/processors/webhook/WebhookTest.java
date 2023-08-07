package com.redhat.cloud.notifications.processors.webhook;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.events.IntegrationDisabledNotifier;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import dev.failsafe.Failsafe;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import io.smallrye.reactive.messaging.providers.connectors.InMemorySink;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Any;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.processors.ConnectorSender.TOCAMEL_CHANNEL;
import static com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor.CLIENT_TAG_VALUE;
import static com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor.DISABLED_WEBHOOKS_COUNTER;
import static com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor.ERROR_TYPE_TAG_KEY;
import static com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor.FAILED_EMAIL_COUNTER;
import static com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor.FAILED_WEBHOOK_COUNTER;
import static com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor.PROCESSED_EMAIL_COUNTER;
import static com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor.PROCESSED_WEBHOOK_COUNTER;
import static com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor.RETRIED_EMAIL_COUNTER;
import static com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor.RETRIED_WEBHOOK_COUNTER;
import static com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor.SERVER_TAG_VALUE;
import static com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor.SUCCESSFUL_EMAIL_COUNTER;
import static com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor.SUCCESSFUL_WEBHOOK_COUNTER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class WebhookTest {

    private static final int MAX_RETRIES = 3;

    private static final int MAX_ATTEMPTS = MAX_RETRIES + 1;

    @Inject
    WebhookTypeProcessor webhookTypeProcessor;

    @Inject
    EntityManager entityManager;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @Inject
    FeatureFlipper featureFlipper;

    @InjectMock
    IntegrationDisabledNotifier integrationDisabledNotifier;

    @InjectMock
    NotificationHistoryRepository notificationHistoryRepository;

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    private InMemorySink<JsonObject> inMemorySink;

    @Inject
    BaseTransformer transformer;

    @PostConstruct
    void postConstruct() {
        inMemorySink = inMemoryConnector.sink(TOCAMEL_CHANNEL);
    }

    @BeforeEach
    void beforeEach() {
        inMemorySink.clear();
        micrometerAssertionHelper.saveCounterValuesBeforeTest(PROCESSED_WEBHOOK_COUNTER, PROCESSED_EMAIL_COUNTER, FAILED_WEBHOOK_COUNTER, FAILED_EMAIL_COUNTER, RETRIED_WEBHOOK_COUNTER, RETRIED_EMAIL_COUNTER, SUCCESSFUL_WEBHOOK_COUNTER, SUCCESSFUL_EMAIL_COUNTER);
        micrometerAssertionHelper.saveCounterValueWithTagsBeforeTest(DISABLED_WEBHOOKS_COUNTER, ERROR_TYPE_TAG_KEY);
    }

    @AfterEach
    void afterEach() {
        micrometerAssertionHelper.clearSavedValues();
    }

    private HttpRequest getMockHttpRequest(String path, ExpectationResponseCallback expectationResponseCallback) {
        HttpRequest postReq = new HttpRequest()
                .withPath(path)
                .withMethod("POST");
        MockServerLifecycleManager.getClient()
                .withSecure(false)
                .when(postReq)
                .respond(expectationResponseCallback);
        return postReq;
    }

    List<String> commonTestWebhook(boolean isEmail) {
        String url = getMockServerUrl() + "/foobar";

        final List<String> bodyRequests = new ArrayList<>();
        ExpectationResponseCallback verifyEmptyRequest = req -> {
            bodyRequests.add(req.getBodyAsString());
            return response().withStatusCode(200);
        };

        HttpRequest postReq = getMockHttpRequest("/foobar", verifyEmptyRequest);

        Action webhookActionMessage = buildWebhookAction();
        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(webhookActionMessage));
        Endpoint ep = buildWebhookEndpoint(url);
        if (isEmail) {
            ep.setType(EndpointType.EMAIL_SUBSCRIPTION);
        }
        try {
            webhookTypeProcessor.process(event, List.of(ep));
            ArgumentCaptor<NotificationHistory> historyArgumentCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
            verify(notificationHistoryRepository, times(1)).createNotificationHistory(historyArgumentCaptor.capture());
            NotificationHistory history = historyArgumentCaptor.getAllValues().get(0);
            assertTrue(history.isInvocationResult());
            assertEquals(NotificationStatus.SUCCESS, history.getStatus());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e);
        } finally {
            // Remove expectations
            MockServerLifecycleManager.getClient().clear(postReq);
        }
        return bodyRequests;
    }

    @Test
    void testEmailWebhook() {
        commonTestWebhook(true);
        validateCounters(0, 1, 0, 1, 0, 0, 0, 0);
    }

    @Test
    void testWebhookUsingConnector() {
        try {
            featureFlipper.setWebhookConnectorKafkaProcessingEnabled(true);

            String testUrl = "https://my.webhook.connector.com";
            Action webhookActionMessage = buildWebhookAction();
            Event event = new Event();
            event.setEventWrapper(new EventWrapperAction(webhookActionMessage));
            Endpoint ep = buildWebhookEndpoint("https://my.webhook.connector.com");
            try {
                webhookTypeProcessor.process(event, List.of(ep));
                ArgumentCaptor<NotificationHistory> historyArgumentCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
                verify(notificationHistoryRepository, times(1)).createNotificationHistory(historyArgumentCaptor.capture());
                NotificationHistory history = historyArgumentCaptor.getAllValues().get(0);
                assertFalse(history.isInvocationResult());
                assertEquals(NotificationStatus.PROCESSING, history.getStatus());
                // Now let's check the Kafka messages sent to the outgoing channel.
                // The channel should have received two messages.
                assertEquals(1, inMemorySink.received().size());

                // We'll only check the payload and metadata of the first Kafka message.
                Message<JsonObject> message = inMemorySink.received().get(0);
                JsonObject payload = message.getPayload();
                assertEquals(testUrl, ((WebhookProperties) payload.getValue("endpoint_properties")).getUrl());

                final JsonObject payloadToSent = transformer.toJsonObject(event);
                assertEquals(payloadToSent, payload.getJsonObject("payload"));

            } catch (Exception e) {
                e.printStackTrace();
                fail(e);
            }
        } finally {
            featureFlipper.setWebhookConnectorKafkaProcessingEnabled(false);
        }
    }

    @Test
    void testWebhook() {
        final List<String> bodyRequests = commonTestWebhook(false);

        assertEquals(1, bodyRequests.size());
        JsonObject webhookInput = new JsonObject(bodyRequests.get(0));

        assertEquals("mybundle", webhookInput.getString("bundle"));
        assertEquals("WebhookTest", webhookInput.getString("application"));
        assertEquals("testWebhook", webhookInput.getString("event_type"));
        assertEquals("tenant", webhookInput.getString("account_id"));
        assertEquals(DEFAULT_ORG_ID, webhookInput.getString("org_id"));

        JsonObject webhookInputContext = webhookInput.getJsonObject("context");
        assertEquals("more", webhookInputContext.getString("free"));
        assertEquals(1, webhookInputContext.getInteger("format"));
        assertEquals("stuff", webhookInputContext.getString("here"));

        JsonArray webhookInputEvents = webhookInput.getJsonArray("events");
        assertEquals(2, webhookInputEvents.size());

        JsonObject webhookInputPayload1 = webhookInputEvents.getJsonObject(0).getJsonObject("payload");
        assertEquals("thing", webhookInputPayload1.getString("any"));
        assertEquals(1, webhookInputPayload1.getInteger("we"));
        assertEquals("here", webhookInputPayload1.getString("want"));
    }

    @Test
    void testRetryWithFinalSuccess() {
        testRetry(true, false);
        validateCounters(1, 0, 1, 0, 0, 0, MAX_RETRIES, 0);
    }

    @Test
    void testRetryWithFinalFailure() {
        testRetry(false, false);
        validateCounters(1, 0, 0, 0, 1, 0, MAX_RETRIES, 0);
    }

    @Test
    void testEmailRetryWithFinalSuccess() {
        testRetry(true, true);
        validateCounters(0, 1, 0, 1, 0, 0, 0, MAX_RETRIES);
    }

    @Test
    void testEmailRetryWithFinalFailure() {
        testRetry(false, true);
        validateCounters(0, 1, 0, 0, 0, 1, 0, MAX_RETRIES);
    }

    @Test
    void testFailuresAsException() {
        // Mocks the static Failsafe method "with" to trigger a synthetic runtime exception
        try (MockedStatic<Failsafe> failsafeMockedStatic = mockStatic(Failsafe.class)) {
            failsafeMockedStatic.when(() -> Failsafe.with(any())).thenThrow(new RuntimeException());
            String url = getMockServerUrl() + "/foobar";
            Action action = buildWebhookAction();
            Event event = new Event();
            event.setEventWrapper(new EventWrapperAction(action));
            Endpoint ep = buildWebhookEndpoint(url);
            webhookTypeProcessor.process(event, List.of(ep));

            ArgumentCaptor<NotificationHistory> historyArgumentCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
            verify(notificationHistoryRepository, times(1)).createNotificationHistory(historyArgumentCaptor.capture());
            NotificationHistory history = historyArgumentCaptor.getAllValues().get(0);

            assertFalse(history.isInvocationResult());
            assertEquals(NotificationStatus.FAILED_INTERNAL, history.getStatus());
            validateCounters(1, 0, 0, 0, 1, 0, 0, 0);
        }
    }

    private void testRetry(boolean shouldSucceedEventually, boolean isEmailEndpoint) {
        String url = getMockServerUrl() + "/foobar";

        AtomicInteger callsCounter = new AtomicInteger();
        ExpectationResponseCallback expectationResponseCallback = request -> {
            if (callsCounter.incrementAndGet() == MAX_ATTEMPTS && shouldSucceedEventually) {
                return response().withStatusCode(200);
            } else {
                return response().withStatusCode(500);
            }
        };

        HttpRequest mockServerRequest = getMockHttpRequest("/foobar", expectationResponseCallback);
        try {
            Action action = buildWebhookAction();
            Event event = new Event();
            event.setEventWrapper(new EventWrapperAction(action));
            Endpoint ep = buildWebhookEndpoint(url);
            if (isEmailEndpoint) {
                ep.setType(EndpointType.EMAIL_SUBSCRIPTION);
            }
            webhookTypeProcessor.process(event, List.of(ep));
            ArgumentCaptor<NotificationHistory> historyArgumentCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
            verify(notificationHistoryRepository, times(1)).createNotificationHistory(historyArgumentCaptor.capture());
            NotificationHistory history = historyArgumentCaptor.getAllValues().get(0);

            assertEquals(shouldSucceedEventually, history.isInvocationResult());
            assertEquals(shouldSucceedEventually ? NotificationStatus.SUCCESS : NotificationStatus.FAILED_INTERNAL, history.getStatus());
            assertEquals(MAX_ATTEMPTS, callsCounter.get());
        } finally {
            // Remove expectations
            MockServerLifecycleManager.getClient().clear(mockServerRequest);
        }
    }

    @Test
    void testDisableEndpointOnClientError() {
        featureFlipper.setDisableWebhookEndpointsOnFailure(true);

        HttpRequest mockServerRequest = getMockHttpRequest("/client-error", request -> response().withStatusCode(401));
        try {
            Action action = buildWebhookAction();
            Event event = new Event();
            event.setEventWrapper(new EventWrapperAction(action));
            Endpoint ep = buildWebhookEndpoint(getMockServerUrl() + "/client-error");
            persistEndpoint(ep);
            assertTrue(ep.isEnabled());
            webhookTypeProcessor.process(event, List.of(ep));
            micrometerAssertionHelper.assertCounterIncrement(DISABLED_WEBHOOKS_COUNTER, 1, ERROR_TYPE_TAG_KEY, CLIENT_TAG_VALUE);
            verify(integrationDisabledNotifier, times(1)).clientError(eq(ep), eq(401));
            assertFalse(getEndpoint(ep.getId()).isEnabled());
        } finally {
            // Remove expectations
            MockServerLifecycleManager.getClient().clear(mockServerRequest);
        }
        validateCounters(1, 0, 0, 0, 1, 0, 0, 0);
        featureFlipper.setDisableWebhookEndpointsOnFailure(false);
    }

    @Test
    void testDisableEndpointOnServerError() {
        featureFlipper.setDisableWebhookEndpointsOnFailure(true);

        HttpRequest mockServerRequest = getMockHttpRequest("/server-error", request -> response().withStatusCode(503));
        try {
            Action action = buildWebhookAction();
            Event event = new Event();
            event.setEventWrapper(new EventWrapperAction(action));
            Endpoint ep = buildWebhookEndpoint(getMockServerUrl() + "/server-error");
            persistEndpoint(ep);
            assertTrue(ep.isEnabled());
            for (int i = 0; i < 4; i++) {
                /*
                 * The processor retries 3 times in case of server error,
                 * so the endpoint will actually be called 16 times in this test.
                 */
                webhookTypeProcessor.process(event, List.of(ep));
            }
            micrometerAssertionHelper.assertCounterIncrement(DISABLED_WEBHOOKS_COUNTER, 1, ERROR_TYPE_TAG_KEY, SERVER_TAG_VALUE);
            verify(integrationDisabledNotifier, times(1)).tooManyServerErrors(eq(ep), eq(10));
            assertFalse(getEndpoint(ep.getId()).isEnabled());
        } finally {
            // Remove expectations
            MockServerLifecycleManager.getClient().clear(mockServerRequest);
        }
        validateCounters(4, 0, 0, 0, 4, 0, 12, 0);
        featureFlipper.setDisableWebhookEndpointsOnFailure(false);
    }

    @Test
    void testEmailsOnlyMode() {
        featureFlipper.setEmailsOnlyMode(true);
        try {

            Event event = new Event();
            event.setEventWrapper(new EventWrapperAction(buildWebhookAction()));

            webhookTypeProcessor.process(event, List.of(new Endpoint()));
            micrometerAssertionHelper.assertCounterIncrement(PROCESSED_WEBHOOK_COUNTER, 0);

        } finally {
            featureFlipper.setEmailsOnlyMode(false);
        }
        validateCounters(0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Transactional
    void persistEndpoint(Endpoint endpoint) {
        entityManager.persist(endpoint);
    }

    Endpoint getEndpoint(UUID id) {
        String hql = "FROM Endpoint WHERE id = :id";
        return entityManager.createQuery(hql, Endpoint.class)
                .setParameter("id", id)
                .getSingleResult();
    }

    private static Action buildWebhookAction() {
        Action webhookActionMessage = new Action();
        webhookActionMessage.setBundle("mybundle");
        webhookActionMessage.setApplication("WebhookTest");
        webhookActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        webhookActionMessage.setEventType("testWebhook");
        webhookActionMessage.setAccountId("tenant");
        webhookActionMessage.setOrgId(DEFAULT_ORG_ID);

        Payload payload1 = new Payload.PayloadBuilder()
                .withAdditionalProperty("any", "thing")
                .withAdditionalProperty("we", 1)
                .withAdditionalProperty("want", "here")
                .build();

        Context context = new Context.ContextBuilder()
                .withAdditionalProperty("free", "more")
                .withAdditionalProperty("format", 1)
                .withAdditionalProperty("here", "stuff")
                .build();

        webhookActionMessage.setEvents(
                List.of(
                        new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                                .withMetadata(new Metadata.MetadataBuilder().build())
                                .withPayload(payload1)
                                .build(),
                        new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                                .withMetadata(new Metadata.MetadataBuilder().build())
                                .withPayload(new Payload.PayloadBuilder().build())
                                .build()
                )
        );

        webhookActionMessage.setContext(context);

        return webhookActionMessage;
    }

    private static Endpoint buildWebhookEndpoint(String url) {
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setUrl(url);

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("positive feeling");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);
        ep.setCreated(LocalDateTime.now());

        return ep;
    }


    private void validateCounters(int processedWebhook, int processedEmail, int successfulWebhook, int successfulEmail, int failedWebhook, int failedEmail, int retriedWebhook, int retiedEmail) {
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_WEBHOOK_COUNTER, processedWebhook);
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_EMAIL_COUNTER, processedEmail);
        micrometerAssertionHelper.assertCounterIncrement(SUCCESSFUL_WEBHOOK_COUNTER, successfulWebhook);
        micrometerAssertionHelper.assertCounterIncrement(SUCCESSFUL_EMAIL_COUNTER, successfulEmail);
        micrometerAssertionHelper.assertCounterIncrement(FAILED_WEBHOOK_COUNTER, failedWebhook);
        micrometerAssertionHelper.assertCounterIncrement(FAILED_EMAIL_COUNTER, failedEmail);
        micrometerAssertionHelper.assertCounterIncrement(RETRIED_WEBHOOK_COUNTER, retriedWebhook);
        micrometerAssertionHelper.assertCounterIncrement(RETRIED_EMAIL_COUNTER, retiedEmail);
    }
}
