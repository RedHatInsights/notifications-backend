package com.redhat.cloud.notifications.processors.eventing;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.TracingMetadata;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static com.redhat.cloud.notifications.processors.AuthenticationType.SECRET_TOKEN;
import static com.redhat.cloud.notifications.processors.ConnectorSender.CLOUD_EVENT_TYPE_PREFIX;
import static com.redhat.cloud.notifications.processors.ConnectorSender.TOCAMEL_CHANNEL;
import static com.redhat.cloud.notifications.processors.eventing.EventingProcessor.NOTIF_METADATA_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class EventingTypeProcessorTest {

    public static final String SUB_TYPE_KEY = "subType";
    public static final String SUB_TYPE = "sub-type";

    /**
     * Event fixtures for the {@link #buildEvent()} function.
     */
    private static final UUID FIXTURE_EVENT_ORIGINAL_UUID = UUID.randomUUID();
    private static final String FIXTURE_ACTION_ORG_ID = "test-event-org-id";

    /**
     * Action fixtures for the {@link #buildEvent()} function.
     */
    private static final String FIXTURE_ACTION_APP = "app";
    private static final String FIXTURE_ACTION_VERSION = "v1.0.0";
    private static final String FIXTURE_ACTION_BUNDLE = "bundle";
    private static final String FIXTURE_ACTION_EVENT_TYPE = "event-type";
    private static final LocalDateTime FIXTURE_ACTION_TIMESTAMP = LocalDateTime.now();
    private static final String FIXTURE_ACTION_ACCOUNT_ID = "account-id";
    private static final List<Recipient> FIXTURE_ACTION_RECIPIENTS = List.of();
    private static final String FIXTURE_ACTION_CONTEXT = "context-key";
    private static final String FIXTURE_ACTION_CONTEXT_VALUE = "context-value";
    private static final String FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY = "k1";
    private static final String FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_VALUE = "v1";
    private static final String FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_2 = "k2";
    private static final String FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_2_VALUE = "v2";
    private static final String FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_3 = "k3";
    private static final String FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_3_VALUE = "v3";

    /**
     * Camel endpoint fixtures for the {@link #buildCamelEndpoint(String)} function.
     */
    private static final String FIXTURE_CAMEL_URL = "https://redhat.com";
    private static final Boolean FIXTURE_CAMEL_SSL_VERIFICATION = true;

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @Inject
    EventingProcessor camelProcessor;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectMock
    NotificationHistoryRepository notificationHistoryRepository;

    @InjectMock
    EngineConfig engineConfig;

    private InMemorySink<JsonObject> inMemorySink;

    @PostConstruct
    void postConstruct() {
        inMemorySink = inMemoryConnector.sink(TOCAMEL_CHANNEL);
    }

    @BeforeEach
    void beforeEach() {
        inMemorySink.clear();
        micrometerAssertionHelper.saveCounterValueWithTagsBeforeTest(EventingProcessor.PROCESSED_COUNTER_NAME, SUB_TYPE_KEY);
    }

    @AfterEach
    void afterEach() {
        micrometerAssertionHelper.clearSavedValues();
    }

    @Test
    void testCamelEndpointProcessing() {
        // Make sure that the payload does not get stored in the database.
        Mockito.when(this.engineConfig.getKafkaToCamelMaximumRequestSize()).thenReturn(Integer.MAX_VALUE);

        // We need input data for the test.
        Event event = buildEvent();
        Endpoint endpoint1 = buildCamelEndpoint(event.getEventWrapper().getAccountId());
        CamelProperties properties1 = endpoint1.getProperties(CamelProperties.class);
        Endpoint endpoint2 = buildCamelEndpoint(event.getEventWrapper().getAccountId());

        // Let's trigger the processing.
        camelProcessor.process(event, List.of(endpoint1, endpoint2));
        ArgumentCaptor<NotificationHistory> historyArgumentCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(notificationHistoryRepository, times(2)).createNotificationHistory(historyArgumentCaptor.capture());
        List<NotificationHistory> result = historyArgumentCaptor.getAllValues();

        // Two endpoints should have been processed.
        assertEquals(2, result.size());
        // Metrics should report the same thing.
        micrometerAssertionHelper.assertCounterIncrement(EventingProcessor.PROCESSED_COUNTER_NAME, 2, SUB_TYPE_KEY, SUB_TYPE);
        micrometerAssertionHelper.assertCounterIncrement(EventingProcessor.PROCESSED_COUNTER_NAME, 0, SUB_TYPE_KEY, "other-type");
        micrometerAssertionHelper.assertCounterIncrement(EventingProcessor.PROCESSED_COUNTER_NAME, 0);

        // Let's have a look at the first result entry fields.
        assertEquals(event, result.get(0).getEvent());
        assertEquals(endpoint1, result.get(0).getEndpoint());
        assertEquals(CAMEL, result.get(0).getEndpointType());
        assertNotNull(result.get(0).getInvocationTime());
        // The invocation will be complete when the response from Camel has been received.
        assertFalse(result.get(0).isInvocationResult());
        assertEquals(NotificationStatus.PROCESSING, result.get(0).getStatus());

        // Now let's check the Kafka messages sent to the outgoing channel.
        // The channel should have received two messages.
        assertEquals(2, inMemorySink.received().size());

        // We'll only check the payload and metadata of the first Kafka message.
        Message<JsonObject> message = inMemorySink.received().get(0);

        // The payload should contain the action events and application URL.
        JsonObject payload = message.getPayload();
        assertNotNull(payload.getJsonArray("events").getJsonObject(0).getString("payload"));
        assertEquals("https://localhost/insights/app?from=notifications&integration=sub-type", payload.getString("application_url"));

        // The processor added a 'notif-metadata' field to the payload, let's have a look at it.
        JsonObject notifMetadata = payload.getJsonObject(NOTIF_METADATA_KEY);
        assertEquals(properties1.getDisableSslVerification().toString(), notifMetadata.getString("trustAll"));
        assertEquals(properties1.getUrl(), notifMetadata.getString("url"));
        assertEquals(endpoint1.getSubType(), notifMetadata.getString("type"));

        assertEquals(SECRET_TOKEN.name(), notifMetadata.getJsonObject("authentication").getString("type"));
        assertEquals(properties1.getSecretTokenSourcesId(), notifMetadata.getJsonObject("authentication").getLong("secretId"));

        // The processor also added an Insights application URL, and potentially an inventory URL.
        assertEquals("https://localhost/insights/app?from=notifications&integration=sub-type", payload.getString("application_url"));
        assertFalse(payload.containsKey("inventory_url"));

        // Finally, we need to check the Kafka message metadata.
        UUID historyId = result.get(0).getId();
        checkCloudEventMetadata(message, historyId, endpoint1.getAccountId(), endpoint1.getOrgId(), endpoint1.getSubType());
        checkTracingMetadata(message);

        // DB and Kafka data must be cleared to prevent a side effect on other tests.
        inMemorySink.clear();
        resourceHelpers.deleteEndpoint(endpoint1.getId());
        resourceHelpers.deleteEndpoint(endpoint2.getId());
    }

    @Test
    void testEmailsOnlyModeCamelProcessor() {
        when(engineConfig.isEmailsOnlyModeEnabled()).thenReturn(true);

        camelProcessor.process(buildEvent(), List.of(new Endpoint()));
        micrometerAssertionHelper.assertCounterIncrement(EventingProcessor.PROCESSED_COUNTER_NAME, 0);
    }

    @Test
    void testSplunkEndpointQueryParams() {
        // Make sure that the payload does not get stored in the database.
        Mockito.when(this.engineConfig.getKafkaToCamelMaximumRequestSize()).thenReturn(Integer.MAX_VALUE);

        // We need input data for the test.
        Event event = buildEvent();
        Endpoint endpoint = buildCamelEndpoint(event.getEventWrapper().getAccountId());
        endpoint.setSubType("splunk");

        // Let's trigger the processing.
        camelProcessor.process(event, List.of(endpoint));
        ArgumentCaptor<NotificationHistory> historyArgumentCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(notificationHistoryRepository, times(1)).createNotificationHistory(historyArgumentCaptor.capture());
        List<NotificationHistory> result = historyArgumentCaptor.getAllValues();

        // One endpoint should have been processed, and the channel should have received one message.
        assertEquals(1, result.size());
        assertEquals(1, inMemorySink.received().size());

        // We'll only check the payload of the first Kafka message.
        JsonObject payload = inMemorySink.received().get(0).getPayload();

        // Assert that the environment URL and query params are provided to a Splunk endpoint.
        assertEquals("https://localhost", payload.getString("environment_url"));
        assertEquals("?from=notifications&integration=splunk", payload.getString("query_params"));

        // DB and Kafka data must be cleared to prevent a side effect on other tests.
        inMemorySink.clear();
        resourceHelpers.deleteEndpoint(endpoint.getId());
    }

    private static Event buildEvent() {
        Action action = new Action();
        action.setVersion(FIXTURE_ACTION_VERSION);
        action.setBundle(FIXTURE_ACTION_BUNDLE);
        action.setApplication(FIXTURE_ACTION_APP);
        action.setEventType(FIXTURE_ACTION_EVENT_TYPE);
        action.setTimestamp(FIXTURE_ACTION_TIMESTAMP);
        action.setAccountId(FIXTURE_ACTION_ACCOUNT_ID);
        action.setOrgId(FIXTURE_ACTION_ORG_ID);
        action.setRecipients(FIXTURE_ACTION_RECIPIENTS);

        Context context = new Context.ContextBuilder().build();
        context.setAdditionalProperty(FIXTURE_ACTION_CONTEXT, FIXTURE_ACTION_CONTEXT_VALUE);
        action.setContext(context);

        action.setEvents(
            List.of(
                new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                    .withMetadata(new Metadata.MetadataBuilder().build())
                    .withPayload(
                        new Payload.PayloadBuilder()
                            .withAdditionalProperty(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY, FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_VALUE)
                            .withAdditionalProperty(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_2, FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_2_VALUE)
                            .withAdditionalProperty(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_3, FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_3_VALUE)
                            .build()
                    )
                .build()
            )
        );

        Event event = new Event();
        event.setId(FIXTURE_EVENT_ORIGINAL_UUID);
        event.setEventWrapper(new EventWrapperAction(action));
        event.setOrgId(DEFAULT_ORG_ID);
        event.setApplicationDisplayName("policies");
        Application application = new Application();
        application.setName("policies");
        EventType eventType = new EventType();
        eventType.setApplication(application);
        eventType.setName("policy-triggered");
        event.setEventType(eventType);
        return event;
    }

    private static Endpoint buildCamelEndpoint(String accountId) {

        CamelProperties properties = new CamelProperties();
        properties.setUrl(FIXTURE_CAMEL_URL);
        properties.setDisableSslVerification(FIXTURE_CAMEL_SSL_VERIFICATION);
        properties.setSecretTokenSourcesId(123L);

        Endpoint endpoint = new Endpoint();
        endpoint.setAccountId(accountId);
        endpoint.setOrgId(DEFAULT_ORG_ID);
        endpoint.setType(CAMEL);
        endpoint.setSubType(SUB_TYPE);
        endpoint.setProperties(properties);
        return endpoint;
    }

    private void checkCloudEventMetadata(Message<JsonObject> message, UUID expectedId, String expectedAccountId, String expectedOrgId, String expectedSubType) {
        CloudEventMetadata metadata = message.getMetadata(CloudEventMetadata.class).get();
        assertEquals(expectedId.toString(), metadata.getId());
        assertEquals(CLOUD_EVENT_TYPE_PREFIX + expectedSubType, metadata.getType());
    }

    private static void checkTracingMetadata(Message<JsonObject> message) {
        TracingMetadata metadata = message.getMetadata(TracingMetadata.class).get();
        assertNotNull(metadata.getPreviousContext());
    }
}
