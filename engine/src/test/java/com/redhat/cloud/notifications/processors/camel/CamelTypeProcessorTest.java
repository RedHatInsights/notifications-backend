package com.redhat.cloud.notifications.processors.camel;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.converters.MapConverter;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.openbridge.Bridge;
import com.redhat.cloud.notifications.openbridge.BridgeHelper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.TracingMetadata;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import io.smallrye.reactive.messaging.providers.connectors.InMemorySink;
import io.vertx.core.json.JsonObject;
import org.apache.kafka.common.header.Headers;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_HEADER;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor.CAMEL_SUBTYPE_HEADER;
import static com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor.CLOUD_EVENT_ACCOUNT_EXTENSION_KEY;
import static com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor.CLOUD_EVENT_TYPE_PREFIX;
import static com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor.NOTIF_METADATA_KEY;
import static com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor.PROCESSED_COUNTER_NAME;
import static com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor.TOCAMEL_CHANNEL;
import static com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor.TOKEN_HEADER;
import static java.lang.Boolean.TRUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class CamelTypeProcessorTest {

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @Inject
    CamelTypeProcessor processor;

    @Inject
    BridgeHelper bridgeHelper;

    @BeforeEach
    void beforeEach() {
        micrometerAssertionHelper.saveCounterValuesBeforeTest(PROCESSED_COUNTER_NAME);
    }

    @AfterEach
    void afterEach() {
        micrometerAssertionHelper.clearSavedValues();
    }

    @Test
    void testCamelEndpointProcessing() {

        // We need input data for the test.
        Event event = buildEvent();
        Endpoint endpoint1 = buildCamelEndpoint(event.getAction().getAccountId());
        CamelProperties properties1 = endpoint1.getProperties(CamelProperties.class);
        Endpoint endpoint2 = buildCamelEndpoint(event.getAction().getAccountId());

        // Let's trigger the processing.
        List<NotificationHistory> result = processor.process(event, List.of(endpoint1, endpoint2));

        // Two endpoints should have been processed.
        assertEquals(2, result.size());
        // Metrics should report the same thing.
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_COUNTER_NAME, 2);

        // Let's have a look at the first result entry fields.
        assertEquals(event, result.get(0).getEvent());
        assertEquals(endpoint1, result.get(0).getEndpoint());
        assertEquals(CAMEL, result.get(0).getEndpointType());
        assertNotNull(result.get(0).getInvocationTime());
        // The invocation will be complete when the response from Camel has been received.
        assertFalse(result.get(0).isInvocationResult());

        // Now let's check the Kafka messages sent to the outgoing channel.
        InMemorySink<String> inMemorySink = inMemoryConnector.sink(TOCAMEL_CHANNEL);
        // The channel should have received two messages.
        assertEquals(2, inMemorySink.received().size());

        // We'll only check the payload and metadata of the first Kafka message.
        Message<String> message = inMemorySink.received().get(0);

        // The payload should contain the action events.
        JsonObject payload = new JsonObject(message.getPayload());
        assertNotNull(payload.getJsonArray("events").getJsonObject(0).getString("payload"));

        // The processor added a 'notif-metadata' field to the payload, let's have a look at it.
        JsonObject notifMetadata = payload.getJsonObject(NOTIF_METADATA_KEY);
        assertEquals(properties1.getDisableSslVerification().toString(), notifMetadata.getString("trustAll"));
        assertEquals(properties1.getUrl(), notifMetadata.getString("url"));
        assertEquals(endpoint1.getSubType(), notifMetadata.getString("type"));
        // Todo: NOTIF-429 backward compatibility change - Remove soon.
        assertEquals(properties1.getSubType(), notifMetadata.getString("type"));

        assertEquals(new MapConverter().convertToDatabaseColumn(properties1.getExtras()), notifMetadata.getString("extras"));
        assertEquals(properties1.getSecretToken(), notifMetadata.getString(TOKEN_HEADER));
        checkBasicAuthentication(notifMetadata, properties1.getBasicAuthentication());

        // Finally, we need to check the Kafka message metadata.
        UUID historyId = result.get(0).getId();
        checkKafkaMetadata(message, historyId, endpoint1.getSubType());
        checkCloudEventMetadata(message, historyId, endpoint1.getAccountId(), endpoint1.getSubType());
        checkTracingMetadata(message);
    }

    @Test
    void testOBEndpointProcessing() {

        // We need input data for the test.
        Event event = buildEvent();
        event.setAccountId("rhid123");
        Endpoint endpoint = buildCamelEndpoint(event.getAction().getAccountId());
        endpoint.setSubType("slack");

        processor.obEnabled = true;
        bridgeHelper.setObEnabled(true);

        // Let's trigger the processing.
        // First with 'random OB endpoints', so we expect this to fail
        List<NotificationHistory> result = processor.process(event, List.of(endpoint));

        // One endpoint should have been processed.
        assertEquals(1, result.size());
        // Metrics should report the same thing.
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_COUNTER_NAME, 1);

        // Let's have a look at the first result entry fields.
        NotificationHistory historyItem = result.get(0);
        assertEquals(event, historyItem.getEvent());
        assertEquals(endpoint, historyItem.getEndpoint());
        assertEquals(CAMEL, historyItem.getEndpointType());
        assertEquals("slack", historyItem.getEndpointSubType());
        assertEquals(1, historyItem.getDetails().size());
        Map<String, Object> details = historyItem.getDetails();
        assertTrue(details.containsKey("failure"));

        // Now set up some mock OB endpoints (simulate valid bridge)
        String eventsEndpoint = MockServerLifecycleManager.getContainerUrl() + "/events";
        System.out.println("==> Setting events endpoint to " + eventsEndpoint);
        Bridge bridge = new Bridge("321", eventsEndpoint, "my bridge");
        Map<String, String> auth = new HashMap<>();
        auth.put("access_token", "li-la-lu-token");
        Map<String, String> obProcessor = new HashMap<>();
        obProcessor.put("id", "p-my-id");

        MockServerConfig.addOpenBridgeEndpoints(auth, bridge);
        bridgeHelper.setOurBridge("321");

        System.out.println("==> Auth token " + bridgeHelper.getAuthToken());
        System.out.println("==> The bridge " + bridgeHelper.getBridgeIfNeeded());

        // Process again
        result = processor.process(event, List.of(endpoint));

        // One endpoint should have been processed.
        assertEquals(1, result.size());
        // Metrics should report the same thing.
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_COUNTER_NAME, 2);

        // Let's have a look at the first result entry fields.
        historyItem = result.get(0);
        assertEquals(event, historyItem.getEvent());
        assertEquals(endpoint, historyItem.getEndpoint());
        assertEquals(CAMEL, historyItem.getEndpointType());
        assertEquals("slack", historyItem.getEndpointSubType());
        assertNull(historyItem.getDetails());

        // Now try again, but the remote throws an error
        event.getAction().setAccountId("something-random");
        result = processor.process(event, List.of(endpoint));
        assertEquals(1, result.size());
        // Metrics should report the same thing.
        micrometerAssertionHelper.assertCounterIncrement(PROCESSED_COUNTER_NAME, 3);

        // Let's have a look at the first result entry fields.
        historyItem = result.get(0);
        assertEquals(event, historyItem.getEvent());
        assertEquals(1, historyItem.getDetails().size());
        details = historyItem.getDetails();
        assertTrue(details.containsKey("failure"));

        assertNotNull(historyItem.getInvocationTime());
        // The invocation will be complete when the response from Camel has been received.
        assertFalse(historyItem.isInvocationResult());

        MockServerConfig.clearOpenBridgeEndpoints(bridge);
        processor.obEnabled = false;
        bridgeHelper.setObEnabled(false);

    }

    private static Event buildEvent() {
        Action action = new Action();
        action.setVersion("v1.0.0");
        action.setBundle("bundle");
        action.setApplication("app");
        action.setEventType("event-type");
        action.setTimestamp(LocalDateTime.now());
        action.setAccountId("account-id");
        action.setRecipients(List.of());
        action.setContext(new HashMap<>());
        action.setEvents(
                List.of(
                        com.redhat.cloud.notifications.ingress.Event
                                .newBuilder()
                                .setMetadataBuilder(Metadata.newBuilder())
                                .setPayload(Map.of("k1", "v1", "k2", "v2", "k3", "v3"))
                                .build()
                )
        );

        Event event = new Event();
        event.setAction(action);
        return event;
    }

    private static Endpoint buildCamelEndpoint(String accountId) {
        BasicAuthentication basicAuth = new BasicAuthentication("john", "doe");

        CamelProperties properties = new CamelProperties();
        properties.setUrl("https://redhat.com");
        properties.setDisableSslVerification(TRUE);
        properties.setSecretToken("top-secret");
        properties.setBasicAuthentication(basicAuth);
        properties.setExtras(Map.of("foo", "bar"));
        // Todo: NOTIF-429 backward compatibility change - Remove soon.
        properties.setSubType("sub-type");

        Endpoint endpoint = new Endpoint();
        endpoint.setAccountId(accountId);
        endpoint.setType(CAMEL);
        endpoint.setSubType("sub-type");
        endpoint.setProperties(properties);
        return endpoint;
    }

    private void checkBasicAuthentication(JsonObject notifMetadata, BasicAuthentication expectedBasicAuth) {
        String credentials = expectedBasicAuth.getUsername() + ":" + expectedBasicAuth.getPassword();
        String expectedBase64Credentials = new String(Base64.getEncoder().encode(credentials.getBytes(UTF_8)), UTF_8);
        assertEquals(expectedBase64Credentials, notifMetadata.getString("basicAuth"));
    }

    private static void checkKafkaMetadata(Message<String> message, UUID expectedMessageId, String expectedSubType) {
        Headers kafkaHeaders = message.getMetadata(KafkaMessageMetadata.class).get().getHeaders();

        // The 'rh-message-id' header should contain the notification history ID.
        byte[] messageId = kafkaHeaders.headers(MESSAGE_ID_HEADER).iterator().next().value();
        assertEquals(expectedMessageId, UUID.fromString(new String(messageId, UTF_8)));

        // The 'CAMEL_SUBTYPE' header should contain the endpoint subtype.
        assertEquals(expectedSubType, new String(kafkaHeaders.headers(CAMEL_SUBTYPE_HEADER).iterator().next().value(), UTF_8));
    }

    private static void checkCloudEventMetadata(Message<String> message, UUID expectedId, String expectedAccountId, String expectedSubType) {
        CloudEventMetadata metadata = message.getMetadata(CloudEventMetadata.class).get();
        assertEquals(expectedId.toString(), metadata.getId());
        assertEquals(expectedAccountId, metadata.getExtension(CLOUD_EVENT_ACCOUNT_EXTENSION_KEY).get());
        assertEquals(CLOUD_EVENT_TYPE_PREFIX + expectedSubType, metadata.getType());
    }

    private static void checkTracingMetadata(Message<String> message) {
        TracingMetadata metadata = message.getMetadata(TracingMetadata.class).get();
        assertNotNull(metadata.getPreviousContext());
    }
}
