package com.redhat.cloud.notifications.connector.v2;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.connector.v2.metrics.ConnectorMetrics;
import com.redhat.cloud.notifications.connector.v2.metrics.NotificationTags;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.smallrye.reactive.messaging.ce.impl.DefaultIncomingCloudEventMetadata;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import static com.redhat.cloud.notifications.connector.v2.MessageConsumer.FAILED_COUNTER_NAME;
import static com.redhat.cloud.notifications.connector.v2.MessageConsumer.SUCCEEDED_COUNTER_NAME;
import static com.redhat.cloud.notifications.connector.v2.MessageConsumer.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class MessageConsumerTest {

    @Inject
    MessageConsumer messageConsumer;

    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @InjectMock
    ConnectorMetrics connectorMetrics;

    @InjectMock
    OutgoingMessageSender outgoingMessageSender;

    @BeforeEach
    void setUp() {
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(FAILED_COUNTER_NAME, "connector", connectorConfig.getConnectorName());
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest(SUCCEEDED_COUNTER_NAME, "connector", connectorConfig.getConnectorName());
    }

    @Test
    void testMissingCloudEventMetadata() {
        Headers headers = new RecordHeaders()
            .add(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, connectorConfig.getConnectorName().getBytes(UTF_8));

        OutgoingKafkaRecordMetadata<String> kafkaHeaders = OutgoingKafkaRecordMetadata.<String>builder()
            .withHeaders(headers)
            .build();

        Message<JsonObject> message = Message.of(new JsonObject())
            .addMetadata(kafkaHeaders);

        messageConsumer.processMessage(message);

        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(FAILED_COUNTER_NAME, "connector", connectorConfig.getConnectorName(), 1);
    }

    @Test
    void testMessageFilteredByWrongConnectorHeader() {
        Headers headers = new RecordHeaders()
            .add(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, "wrong-connector-name".getBytes(UTF_8));

        OutgoingKafkaRecordMetadata<String> kafkaHeaders = OutgoingKafkaRecordMetadata.<String>builder()
            .withHeaders(headers)
            .build();

        Message<JsonObject> message = Message.of(new JsonObject())
            .addMetadata(kafkaHeaders);

        CompletionStage<Void> result = messageConsumer.processMessage(message);

        assertDoesNotThrow(() -> result.toCompletableFuture().get(5, SECONDS));
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(FAILED_COUNTER_NAME, "connector", connectorConfig.getConnectorName(), 0);
    }

    @Test
    void testMessageFilteredByMissingConnectorHeader() {
        Message<JsonObject> message = Message.of(new JsonObject());

        CompletionStage<Void> result = messageConsumer.processMessage(message);

        assertDoesNotThrow(() -> result.toCompletableFuture().get(5, SECONDS));
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(FAILED_COUNTER_NAME, "connector", connectorConfig.getConnectorName(), 0);
    }

    @Test
    void testMetricsRecordedOnSuccess() throws Exception {
        Message<JsonObject> message = buildValidMessage();

        messageConsumer.handleMessage(message).toCompletableFuture().get(5, SECONDS);

        verify(connectorMetrics).recordNotificationSent(ArgumentMatchers.any(NotificationTags.class));
        verify(connectorMetrics).recordIntegrationTime(ArgumentMatchers.any(), ArgumentMatchers.any(NotificationTags.class));
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(SUCCEEDED_COUNTER_NAME, "connector", connectorConfig.getConnectorName(), 1);
    }

    @Test
    void testMetricsNotCalledOnHandlerFailure() throws Exception {
        doThrow(new RuntimeException("send failed")).when(outgoingMessageSender)
            .sendSuccess(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyLong());

        Message<JsonObject> message = buildValidMessage();

        messageConsumer.handleMessage(message).toCompletableFuture().get(5, SECONDS);

        verify(connectorMetrics, never()).recordNotificationSent(ArgumentMatchers.any());
        verify(connectorMetrics, never()).recordIntegrationTime(ArgumentMatchers.any(), ArgumentMatchers.any());
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(FAILED_COUNTER_NAME, "connector", connectorConfig.getConnectorName(), 1);
    }

    @Test
    void testMetricsExceptionSwallowed() throws Exception {
        doThrow(new RuntimeException("metrics failed")).when(connectorMetrics)
            .recordNotificationSent(ArgumentMatchers.any(NotificationTags.class));

        Message<JsonObject> message = buildValidMessage();

        assertDoesNotThrow(() -> messageConsumer.handleMessage(message).toCompletableFuture().get(5, SECONDS));
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(SUCCEEDED_COUNTER_NAME, "connector", connectorConfig.getConnectorName(), 1);
    }

    private Message<JsonObject> buildValidMessage() {
        String cloudEventId = UUID.randomUUID().toString();
        IncomingCloudEventMetadata<JsonObject> cloudEvent = new DefaultIncomingCloudEventMetadata<>(
            "1.0.0", cloudEventId, URI.create("notification"),
            "com.redhat.console.notification.toCamel." + connectorConfig.getConnectorName(),
            "application/json", null, null, null, null, new JsonObject());

        Headers headers = new RecordHeaders()
            .add(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, connectorConfig.getConnectorName().getBytes(UTF_8));
        OutgoingKafkaRecordMetadata<String> kafkaHeaders = OutgoingKafkaRecordMetadata.<String>builder()
            .withHeaders(headers).build();

        return Message.of(new JsonObject()).addMetadata(kafkaHeaders).addMetadata(cloudEvent);
    }

    @Test
    void testNullCloudEventData() {
        IncomingCloudEventMetadata<JsonObject> cloudEvent = mock(IncomingCloudEventMetadata.class);
        when(cloudEvent.getData()).thenReturn(null);

        Headers headers = new RecordHeaders()
            .add(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, connectorConfig.getConnectorName().getBytes(UTF_8));

        OutgoingKafkaRecordMetadata<String> kafkaHeaders = OutgoingKafkaRecordMetadata.<String>builder()
            .withHeaders(headers)
            .build();

        Message<JsonObject> message = Message.of(new JsonObject())
            .addMetadata(kafkaHeaders)
            .addMetadata(cloudEvent);

        messageConsumer.processMessage(message);

        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement(FAILED_COUNTER_NAME, "connector", connectorConfig.getConnectorName(), 1);
    }
}
