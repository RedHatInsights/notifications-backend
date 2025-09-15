package com.redhat.cloud.notifications.connector;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.time.Duration;

/**
 * Base class for all connector processors that replace Camel RouteBuilder functionality.
 * Uses Quarkus reactive messaging instead of Camel routes.
 */
@ApplicationScoped
public abstract class ConnectorProcessor {

    public static final String ENGINE_TO_CONNECTOR_CHANNEL = "engine-to-connector";
    public static final String CONNECTOR_TO_ENGINE_CHANNEL = "connector-to-engine";

    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    IncomingCloudEventFilter incomingCloudEventFilter;

    @Inject
    IncomingCloudEventProcessor incomingCloudEventProcessor;

    @Inject
    ConnectorMessagingService messagingService;

    @Inject
    ExceptionProcessor exceptionProcessor;

    /**
     * Receives messages from the engine and processes them.
     * This replaces the Camel route that consumes from Kafka.
     */
    @Incoming(ENGINE_TO_CONNECTOR_CHANNEL)
    public Uni<Void> processIncomingMessage(Message<String> message) {
        return Uni.createFrom().item(message)
                .onItem().transform(this::extractCloudEvent)
                .onItem().transformToUni(cloudEvent -> {
                    if (!incomingCloudEventFilter.accept(cloudEvent)) {
                        Log.debug("Message filtered out by IncomingCloudEventFilter");
                        return Uni.createFrom().voidItem();
                    }

                    try {
                        // Process the cloud event to extract context
                        ExceptionProcessor.ProcessingContext context = incomingCloudEventProcessor.process(cloudEvent);

                        // Process the actual connector logic
                        return processCloudEvent(context)
                                .onItem().transformToUni(result -> messagingService.sendToEngine(result))
                                .onFailure().invoke(failure -> handleException(failure, message, context));

                    } catch (Exception e) {
                        Log.error("Failed to process cloud event", e);
                        return Uni.createFrom().failure(e);
                    }
                })
                .onFailure().retry().withBackOff(Duration.ofSeconds(1)).atMost(connectorConfig.getRedeliveryMaxAttempts())
                .onFailure().invoke(failure -> Log.error("Failed to process message after retries", failure))
                .onFailure().recoverWithNull()
                .onItem().invoke(() -> message.ack())
                .replaceWithVoid();
    }


    /**
     * Extract CloudEvent from the incoming message.
     * This replaces Camel message transformation.
     */
    protected JsonObject extractCloudEvent(Message<String> message) {
        try {
            return new JsonObject(message.getPayload());
        } catch (Exception e) {
            Log.error("Failed to parse CloudEvent from message: {0}", message.getPayload(), e);
            throw new RuntimeException("Invalid CloudEvent format", e);
        }
    }

    /**
     * Process the actual cloud event.
     * This method should be implemented by each connector.
     */
    protected abstract Uni<ConnectorResult> processCloudEvent(ExceptionProcessor.ProcessingContext context);

    /**
     * Handle exceptions during processing.
     * This replaces Camel's onException handling.
     */
    protected void handleException(Throwable failure, Message<String> originalMessage, ExceptionProcessor.ProcessingContext context) {
        exceptionProcessor.process(failure, originalMessage, context)
            .subscribe().with(
                    success -> Log.debug("Exception processed successfully"),
                    error -> Log.error("Exception processor failed", error)
            );
    }

    /**
     * Result class for connector processing
     */
    public static class ConnectorResult {
        private final boolean successful;
        private final String outcome;
        private final String id;
        private final String orgId;
        private final JsonObject originalCloudEvent;

        public ConnectorResult(boolean successful, String outcome, String id, String orgId, JsonObject originalCloudEvent) {
            this.successful = successful;
            this.outcome = outcome;
            this.id = id;
            this.orgId = orgId;
            this.originalCloudEvent = originalCloudEvent;
        }

        // Getters
        public boolean isSuccessful() {
            return successful;
        }

        public String getOutcome() {
            return outcome;
        }

        public String getId() {
            return id;
        }

        public String getOrgId() {
            return orgId;
        }

        public JsonObject getOriginalCloudEvent() {
            return originalCloudEvent;
        }
    }
}
