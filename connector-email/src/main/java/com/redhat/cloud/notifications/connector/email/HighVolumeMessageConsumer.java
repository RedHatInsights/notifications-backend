package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.v2.ExceptionHandler;
import com.redhat.cloud.notifications.connector.v2.MessageHandler;
import com.redhat.cloud.notifications.connector.v2.OutgoingMessageSender;
import com.redhat.cloud.notifications.connector.v2.models.HandledExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static com.redhat.cloud.notifications.connector.v2.MessageConsumer.FAILED_COUNTER_NAME;
import static com.redhat.cloud.notifications.connector.v2.MessageConsumer.HANDLER_DURATION_TIMER_NAME;
import static com.redhat.cloud.notifications.connector.v2.MessageConsumer.SUCCEEDED_COUNTER_NAME;

@ApplicationScoped
public class HighVolumeMessageConsumer {

    @Inject
    EmailConnectorConfig emailConnectorConfig;

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

    @PostConstruct
    void init() {
        succeededCounter = Counter.builder(SUCCEEDED_COUNTER_NAME)
            .description("Total number of messages successfully processed by the connector")
            .tag("connector", emailConnectorConfig.getConnectorName())
            .register(meterRegistry);
        failedCounter = Counter.builder(FAILED_COUNTER_NAME)
            .description("Total number of messages that failed processing in the connector")
            .tag("connector", emailConnectorConfig.getConnectorName())
            .register(meterRegistry);
        handlerDurationTimer = Timer.builder(HANDLER_DURATION_TIMER_NAME)
            .description("Duration of the connector message handler processing")
            .tag("connector", emailConnectorConfig.getConnectorName())
            .register(meterRegistry);
    }

    @Incoming("highvolumemessages")
    @Blocking("connector-thread-pool")
    @RunOnVirtualThread
    public CompletionStage<Void> processMessage(Message<JsonObject> message) {
        if (!emailConnectorConfig.isIncomingKafkaHighVolumeTopicEnabled()) {
            return message.ack();
        }

        final long startTime = System.currentTimeMillis();

        Optional<IncomingCloudEventMetadata> cloudEventOpt = message.getMetadata(IncomingCloudEventMetadata.class);
        if (cloudEventOpt.isEmpty() || null == cloudEventOpt.get().getData()) {
            Log.error("Incoming CloudEvent metadata and data must not be null");
            failedCounter.increment();
            return message.ack();
        }

        @SuppressWarnings("unchecked")
        final IncomingCloudEventMetadata<JsonObject> cloudEventMetadata = cloudEventOpt.get();

        Timer.Sample sample = Timer.start(meterRegistry);
        boolean success = false;
        try {
            Log.debugf("Processing high-volume message %s", message.getPayload());
            HandledMessageDetails additionalConnectorDetails = messageHandler.handle(cloudEventMetadata);
            outgoingMessageSender.sendSuccess(cloudEventMetadata, additionalConnectorDetails, startTime);
            success = true;
        } catch (Exception e) {
            Log.errorf(e, "Error processing high-volume message: %s", e.getMessage());
            HandledExceptionDetails processedExceptionDetails = exceptionProcessor.processException(e, cloudEventMetadata);
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
}
