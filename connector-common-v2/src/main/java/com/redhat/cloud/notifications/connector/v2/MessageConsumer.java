package com.redhat.cloud.notifications.connector.v2;

import com.redhat.cloud.notifications.connector.v2.models.HandledExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
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

    public static final String SUCCEEDED_COUNTER_NAME = "notifications.connector.messages.succeeded";
    public static final String FAILED_COUNTER_NAME = "notifications.connector.messages.failed";
    public static final String HANDLER_DURATION_TIMER_NAME = "notifications.connector.handler.duration";

    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    ExceptionHandler exceptionProcessor;

    @Inject
    MessageHandler messageHandler;

    @Inject
    OutgoingMessageSender outgoingMessageSender;

    @Inject
    MeterRegistry meterRegistry;

    private Counter succeededCounter;
    private Counter failedCounter;
    private Timer handlerDurationTimer;

    public static final String X_RH_NOTIFICATIONS_CONNECTOR_HEADER = "x-rh-notifications-connector";

    @PostConstruct
    void init() {
        succeededCounter = Counter.builder(SUCCEEDED_COUNTER_NAME)
            .description("Total number of messages successfully processed by the connector")
            .tag("connector", connectorConfig.getConnectorName())
            .register(meterRegistry);
        failedCounter = Counter.builder(FAILED_COUNTER_NAME)
            .description("Total number of messages that failed processing in the connector")
            .tag("connector", connectorConfig.getConnectorName())
            .register(meterRegistry);
        handlerDurationTimer = Timer.builder(HANDLER_DURATION_TIMER_NAME)
            .description("Duration of the connector message handler processing")
            .tag("connector", connectorConfig.getConnectorName())
            .register(meterRegistry);
    }

    @Incoming("incomingmessages")
    @Blocking("connector-thread-pool")
    @RunOnVirtualThread
    public CompletionStage<Void> processMessage(Message<JsonObject> message) {
        Optional<String> connectorHeader = extractConnectorHeader(message);
        if (connectorHeader.isEmpty() || !connectorConfig.getSupportedConnectorHeaders().contains(connectorHeader.get())) {
            Log.debugf("Message filtered out for connector %s", connectorConfig.getConnectorName());
            return message.ack();
        }
        return handleMessage(message);
    }

    CompletionStage<Void> handleMessage(Message<JsonObject> message) {
        final long startTime = System.currentTimeMillis();

        Optional<IncomingCloudEventMetadata> cloudEventOpt = message.getMetadata(IncomingCloudEventMetadata.class);
        if (cloudEventOpt.isEmpty() || null == cloudEventOpt.get().getData()) {
            Log.error("Incoming CloudEvent metadata and data must not be null");
            failedCounter.increment();
            return message.ack();
        }

        final IncomingCloudEventMetadata<JsonObject> cloudEventMetadata = cloudEventOpt.get();

        Timer.Sample sample = Timer.start(meterRegistry);
        boolean success = false;
        try {
            Log.debugf("Processing %s", message.getPayload());

            // Handle the message using the connector-specific handler
            HandledMessageDetails additionalConnectorDetails = messageHandler.handle(cloudEventMetadata);

            // Send success response back to engine
            outgoingMessageSender.sendSuccess(cloudEventMetadata, additionalConnectorDetails, startTime);
            success = true;
        } catch (Exception e) {
            Log.errorf(e, "Error processing message: %s", e.getMessage());
            HandledExceptionDetails processedExceptionDetails = exceptionProcessor.processException(e, cloudEventMetadata);

            // Send failure response back to engine
            outgoingMessageSender.sendFailure(cloudEventMetadata, processedExceptionDetails, startTime);
        } finally {
            sample.stop(handlerDurationTimer);
            if (success) {
                succeededCounter.increment();
            } else {
                failedCounter.increment();
            }
        }
        return message.ack();
    }

    public Optional<String> extractConnectorHeader(Message<JsonObject> message) {
        Optional<KafkaMessageMetadata> metadata = message.getMetadata(KafkaMessageMetadata.class);
        if (metadata.isPresent()) {
            return StreamSupport.stream(metadata.get().getHeaders().headers(X_RH_NOTIFICATIONS_CONNECTOR_HEADER).spliterator(), false)
                .filter(header -> header.key().equals(X_RH_NOTIFICATIONS_CONNECTOR_HEADER))
                .findFirst()
                .map(header -> new String(header.value(), UTF_8));
        }
        return Optional.empty();
    }
}

