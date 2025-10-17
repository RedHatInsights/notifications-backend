package com.redhat.cloud.notifications.connector.v2;

import com.redhat.cloud.notifications.connector.v2.pojo.HandledExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.pojo.HandledMessageDetails;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.stream.StreamSupport;

import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class MessageConsumer {

    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    ExceptionHandler exceptionProcessor;

    @Inject
    MessageHandler messageHandler;

    @Inject
    OutgoingMessageSender outgoingMessageSender;

    public static final String X_RH_NOTIFICATIONS_CONNECTOR_HEADER = "x-rh-notifications-connector";

    @Incoming("incoming-messages")
    @Blocking("main-worker")
    @RunOnVirtualThread
    public CompletionStage<Void> processMessage(Message<JsonObject> message) {
        final long startTime = System.currentTimeMillis();

        // Handle Kafka headers if available
        String connectorHeader = extractConnectorHeader(message);

        IncomingCloudEventMetadata<JsonObject> cloudEventMetadata = message.getMetadata(IncomingCloudEventMetadata.class).get();

        // Check if message should be filtered
        if (!connectorConfig.getSupportedConnectorHeaders().contains(connectorHeader)) {
            Log.debugf("Message filtered out for connector %s", connectorConfig.getConnectorName());
            return message.ack();
        }

        try {
            Log.infof("Processing %s", message.getPayload());

            // Handle the message using the connector-specific handler
            HandledMessageDetails additionalConnectorDetails = messageHandler.handle(cloudEventMetadata);

            // Send success response back to engine
            outgoingMessageSender.sendSuccess(cloudEventMetadata, additionalConnectorDetails, startTime);
        } catch (Exception e) {
            Log.errorf(e, "Error processing message: %s", e.getMessage());
            HandledExceptionDetails processedExceptionDetails = exceptionProcessor.processException(e, cloudEventMetadata);

            // Send failure response back to engine
            outgoingMessageSender.sendFailure(cloudEventMetadata, processedExceptionDetails, startTime);
        }
        return message.ack();
    }

    public String extractConnectorHeader(Message<JsonObject> message) {
        Optional<KafkaMessageMetadata> metadata = message.getMetadata(KafkaMessageMetadata.class);
        if (metadata.isPresent()) {
            return StreamSupport.stream(metadata.get().getHeaders().headers(X_RH_NOTIFICATIONS_CONNECTOR_HEADER).spliterator(), false)
                .filter(header -> header.key().equals(X_RH_NOTIFICATIONS_CONNECTOR_HEADER))
                .findFirst()
                .map(header -> new String(header.value(), UTF_8)).orElse(null);
        }
        return null;
    }
}

