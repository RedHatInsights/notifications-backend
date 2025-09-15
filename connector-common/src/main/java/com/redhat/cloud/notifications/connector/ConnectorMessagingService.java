package com.redhat.cloud.notifications.connector;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.time.Duration;

/**
 * Service that handles messaging between connectors and the engine.
 * Replaces Camel ProducerTemplate functionality.
 */
@ApplicationScoped
public class ConnectorMessagingService {

    @Inject
    @Channel("connector-to-engine")
    MutinyEmitter<String> outgoingEmitter;

    @Inject
    @Channel("engine-to-connector")
    MutinyEmitter<String> reinjectionEmitter;

    @Inject
    OutgoingCloudEventBuilder outgoingCloudEventBuilder;

    @Inject
    ConnectorConfig connectorConfig;

    /**
     * Send a connector result to the engine via Kafka.
     * Replaces the direct(CONNECTOR_TO_ENGINE) route.
     */
    public Uni<Void> sendToEngine(ConnectorProcessor.ConnectorResult result) {
        try {
            JsonObject cloudEvent = outgoingCloudEventBuilder.build(result);
            String cloudEventStr = cloudEvent.encode();

            Log.debugf("Sending result to engine: {0}", cloudEventStr);

            outgoingEmitter.send(Message.of(cloudEventStr));
            return Uni.createFrom().voidItem();

        } catch (Exception e) {
            Log.error("Failed to build outgoing cloud event", e);
            return Uni.createFrom().failure(e);
        }
    }

    /**
     * Reinject a message back to the incoming topic for retry.
     * Replaces the direct(KAFKA_REINJECTION) route.
     */
    public Uni<Void> reinjectMessage(Message<String> originalMessage, ExceptionProcessor.ProcessingContext context) {
        try {
            // Increment reinjection count
            context.setKafkaReinjectionCount(context.getKafkaReinjectionCount() + 1);

            // Add reinjection metadata to the cloud event
            JsonObject cloudEvent = context.getOriginalCloudEvent().copy();
            cloudEvent.put("reinjectionCount", context.getKafkaReinjectionCount());
            cloudEvent.put("reinjectionTimestamp", System.currentTimeMillis());

            String messageBody = cloudEvent.encode();

            Log.infof("Reinjecting message [orgId={0}, historyId={1}, attempt={2}]",
                    context.getOrgId(), context.getId(), context.getKafkaReinjectionCount());

            // Calculate delay (exponential backoff)
            Duration delay = calculateReinjectionDelay(context.getKafkaReinjectionCount());

            return Uni.createFrom().voidItem()
                    .onItem().delayIt().by(delay)
                    .onItem().invoke(ignored -> {
                        reinjectionEmitter.send(Message.of(messageBody));
                    })
                    .replaceWithVoid();

        } catch (Exception e) {
            Log.error("Failed to prepare message for reinjection", e);
            return Uni.createFrom().failure(e);
        }
    }

    /**
     * Calculate reinjection delay with exponential backoff.
     * Replaces Camel's redeliveryDelay functionality.
     */
    private Duration calculateReinjectionDelay(int attemptNumber) {
        long baseDelay = connectorConfig.getRedeliveryDelay();
        long delay = baseDelay * (long) Math.pow(2, attemptNumber - 1);

        // Cap the maximum delay to prevent excessive wait times
        long maxDelay = Duration.ofMinutes(5).toMillis();
        delay = Math.min(delay, maxDelay);

        return Duration.ofMillis(delay);
    }

    /**
     * Send a success result to the engine.
     * Replaces the direct(SUCCESS) route.
     */
    public Uni<Void> sendSuccess(String id, String orgId, JsonObject originalCloudEvent) {
        ConnectorProcessor.ConnectorResult result = new ConnectorProcessor.ConnectorResult(
                true,
                String.format("Event %s sent successfully", id),
                id,
                orgId,
                originalCloudEvent
        );

        return sendToEngine(result);
    }
}
