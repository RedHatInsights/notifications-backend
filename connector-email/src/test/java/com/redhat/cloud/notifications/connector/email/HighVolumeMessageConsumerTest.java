package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.v2.ConnectorConfig;
import com.redhat.cloud.notifications.connector.v2.MessageConsumer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletionStage;

import static com.redhat.cloud.notifications.connector.v2.MessageConsumer.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class HighVolumeMessageConsumerTest {

    @Inject
    HighVolumeMessageConsumer highVolumeMessageConsumer;

    @InjectSpy
    EmailConnectorConfig emailConnectorConfig;

    @InjectSpy
    MessageConsumer messageConsumer;

    @Inject
    ConnectorConfig connectorConfig;

    @Test
    void testMessageAckedWhenHighVolumeTopicDisabled() {
        doReturn(false).when(emailConnectorConfig).isIncomingKafkaHighVolumeTopicEnabled();

        Message<JsonObject> message = buildMessage();
        CompletionStage<Void> result = highVolumeMessageConsumer.processMessage(message);

        assertDoesNotThrow(() -> result.toCompletableFuture().get(5, SECONDS));
        verify(messageConsumer, never()).processMessage(any());
    }

    @Test
    void testMessageForwardedWhenHighVolumeTopicEnabled() {
        doReturn(true).when(emailConnectorConfig).isIncomingKafkaHighVolumeTopicEnabled();

        Message<JsonObject> message = buildMessage();
        highVolumeMessageConsumer.processMessage(message);

        verify(messageConsumer).processMessage(message);
    }

    private Message<JsonObject> buildMessage() {
        Headers headers = new RecordHeaders()
            .add(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, connectorConfig.getConnectorName().getBytes(UTF_8));

        OutgoingKafkaRecordMetadata<String> kafkaHeaders = OutgoingKafkaRecordMetadata.<String>builder()
            .withHeaders(headers)
            .build();

        return Message.of(new JsonObject())
            .addMetadata(kafkaHeaders);
    }
}
