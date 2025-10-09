package com.redhat.cloud.notifications.connector.v2;

import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static com.redhat.cloud.notifications.connector.v2.IncomingCloudEventFilter.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;

@ApplicationScoped
public class MessageProcessor {

    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    IncomingCloudEventFilter incomingCloudEventFilter;

    @Inject
    IncomingCloudEventProcessor incomingCloudEventProcessor;

    @Inject
    ExceptionProcessor exceptionProcessor;

    @Inject
    MessageHandler messageHandler;

    @Inject
    KafkaHeadersExtractor kafkaHeadersExtractor;

    @Incoming("incoming-messages")
    @Blocking("main-worker")
    @RunOnVirtualThread
    public CompletionStage<Void> processMessage(Message<JsonObject> message) {
        // Create a message context to hold the data
        MessageContext context = new MessageContext();

        // Handle Kafka headers if available
        context.setHeaders(kafkaHeadersExtractor.extract(message, X_RH_NOTIFICATIONS_CONNECTOR_HEADER));

        Optional<IncomingCloudEventMetadata> cloudEventMetadata = message.getMetadata(IncomingCloudEventMetadata.class);
        context.setIncomingCloudEventMetadata(cloudEventMetadata.get());

        // Check if message should be filtered
        if (!incomingCloudEventFilter.accept(context)) {
            Log.debugf("Message filtered out for connector %s", connectorConfig.getConnectorName());
            return message.ack();
        }

        try {
            Log.infof("Processing %s", message.getPayload());

            // Process the cloud event
            incomingCloudEventProcessor.process(context);

            // Handle the message using the connector-specific handler
            messageHandler.handle(context);

        } catch (Exception e) {
            Log.errorf(e, "Error processing message: %s", e.getMessage());
            exceptionProcessor.processException(e, context);
        }
        return message.ack();
    }
}

