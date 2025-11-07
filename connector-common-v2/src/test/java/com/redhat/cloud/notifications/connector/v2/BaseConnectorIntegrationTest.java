package com.redhat.cloud.notifications.connector.v2;

import com.github.tomakehurst.wiremock.http.Fault;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.smallrye.reactive.messaging.ce.impl.DefaultIncomingCloudEventMetadata;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.net.URI;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
import static com.redhat.cloud.notifications.connector.v2.MessageConsumer.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Base class for connector integration tests that provides:
 * - Quarkus test setup with InMemory reactive messaging
 * - Mock HTTP server management
 * - Message injection and verification utilities
 * - Metrics assertions
 */
@QuarkusTest
public abstract class BaseConnectorIntegrationTest {

    @Inject
    protected ConnectorConfig connectorConfig;

    @Inject
    protected MicrometerAssertionHelper micrometerAssertionHelper;

    @Inject
    @Any
    protected InMemoryConnector inMemoryConnector;

    protected InMemorySource<Message<JsonObject>> incomingMessageSource;
    protected InMemorySink<String> outgoingMessageSink;

    // Abstract methods for connector-specific implementation
    protected abstract JsonObject buildIncomingPayload(String targetUrl);

    protected abstract void assertOutgoingPayload(JsonObject incomingPayload, JsonObject outgoingPayload);

    protected abstract String getConnectorSpecificTargetUrl();

    protected String getRemoteServerPath() {
        return "";
    }

    @BeforeEach
    void setUp() {
        getClient().resetAll();

        // Set up InMemory channels
        incomingMessageSource = inMemoryConnector.source("incoming-messages");
        outgoingMessageSink = inMemoryConnector.sink("outgoing-messages");

        // Clear any previous messages
        outgoingMessageSink.clear();

        // Save initial metrics
        saveConnectorMetrics();
    }

    @AfterEach
    void tearDown() {
        getClient().resetAll();
        micrometerAssertionHelper.clearSavedValues();

        if (outgoingMessageSink != null) {
            outgoingMessageSink.clear();
        }
    }

    /**
     * Sends a CloudEvent message to the connector and returns the CloudEvent ID
     */
    protected String sendCloudEventMessage(JsonObject payload) {
        String cloudEventId = UUID.randomUUID().toString();

        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent(cloudEventId, "com.redhat.console.notification.toCamel." + connectorConfig.getConnectorName(), payload);

        Headers headers = new RecordHeaders()
            .add(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, connectorConfig.getConnectorName().getBytes(UTF_8));

        OutgoingKafkaRecordMetadata<String> KafkaHeaders = OutgoingKafkaRecordMetadata.<String>builder()
            .withHeaders(headers)
            .build();

        // Send the message through InMemory source (headers will be added by the messaging framework)
        incomingMessageSource.send(
            Message.of(payload)
            .addMetadata(KafkaHeaders)
            .addMetadata(incomingCloudEvent)
        );

        return cloudEventId;
    }

    /**
     * Waits for and returns the response message payload from the outgoing channel
     */
    protected JsonObject waitForOutgoingMessage(String expectedCloudEventId) {
        Awaitility.await().until(() -> !outgoingMessageSink.received().isEmpty());
        Message<String> message = outgoingMessageSink.received().getFirst();

        OutgoingCloudEventMetadata<?> outgoingMessage = message.getMetadata(OutgoingCloudEventMetadata.class)
            .orElseThrow(() -> new IllegalArgumentException("Expected a Cloud Event"));

        assertEquals("com.redhat.console.notifications.history", outgoingMessage.getType());
        assertEquals("1.0", outgoingMessage.getSpecVersion());
        assertEquals(expectedCloudEventId, outgoingMessage.getId());
        assertNotNull(outgoingMessage.getSource());
        assertNotNull(outgoingMessage.getTimeStamp());

        return new JsonObject(message.getPayload());
    }

    /**
     * Asserts that the outgoing message matches expected patterns
     */
    protected void assertSuccessfulOutgoingMessage(String expectedCloudEventId, String expectedTargetUrl) {
        JsonObject data = waitForOutgoingMessage(expectedCloudEventId);

        assertEquals(true, data.getBoolean("successful"));
        assertNotNull(data.getString("duration"));

        JsonObject details = data.getJsonObject("details");
        assertNotNull(details.getString("type"));
        if (expectedTargetUrl != null) {
            assertEquals(expectedTargetUrl, details.getString("target"));
        }
    }

    /**
     * Asserts that the outgoing message indicates failure
     */
    protected void assertFailedOutgoingMessage(String expectedCloudEventId, String... expectedErrorSubstrings) {
        JsonObject data = waitForOutgoingMessage(expectedCloudEventId);

        assertEquals(false, data.getBoolean("successful"));

        String outcome = data.getJsonObject("details").getString("outcome");
        for (String expectedSubstring : expectedErrorSubstrings) {
            assert outcome.contains(expectedSubstring) :
                String.format("Expected outcome to contain '%s', but was: %s", expectedSubstring, outcome);
        }
    }

    /**
     * Mocks an HTTP server response
     */
    protected void mockHttpResponse(String path, int statusCode, String responseBody) {
        getClient().stubFor(
            post(urlEqualTo(path))
                .willReturn(aResponse()
                    .withStatus(statusCode)
                    .withBody(responseBody))
        );
    }

    /**
     * Mocks an HTTP server error
     */
    protected void mockHttpError(String path, int statusCode, String body) {
        mockHttpResponse(path, statusCode, body);
    }

    /**
     * Mocks a network failure
     */
    protected void mockNetworkFailure(String path) {
        getClient().stubFor(
            post(urlEqualTo(path))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
        );
    }

    /**
     * Gets the mock server URL
     */
    protected String getMockServerUrl() {
        return MockServerLifecycleManager.getMockServerUrl();
    }

    /**
     * Saves current metrics for later comparison
     */
    protected void saveConnectorMetrics() {
        // Save metrics that we want to track
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("connector.messages.processed", "connector", connectorConfig.getConnectorName());
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("connector.messages.succeeded", "connector", connectorConfig.getConnectorName());
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("connector.messages.failed", "connector", connectorConfig.getConnectorName());
    }

    /**
     * Asserts metrics changes
     */
    protected void assertMetricsIncrement(double expectedProcessed, double expectedSucceeded, double expectedFailed) {
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("connector.messages.processed", "connector", connectorConfig.getConnectorName(), expectedProcessed);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("connector.messages.succeeded", "connector", connectorConfig.getConnectorName(), expectedSucceeded);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("connector.messages.failed", "connector", connectorConfig.getConnectorName(), expectedFailed);
    }

    public static IncomingCloudEventMetadata<JsonObject> buildIncomingCloudEvent(String cloudEventId, String cloudEventType, JsonObject cloudEventData) {
        return new DefaultIncomingCloudEventMetadata<JsonObject>(
            "1.0.0",
            cloudEventId,
            URI.create("notification"),
            cloudEventType,
            "application/json",
            null,
            null,
            null,
            null,
            cloudEventData);
    }
}
