package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.config.EngineConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class ReplayEventConsumer {

    public static final String INGRESS_REPLAY_CHANNEL = "ingressreplay";
    public static final String REPLAYED_MESSAGE_COUNTER_NAME = "input.processing.replayed";

    @Inject
    EventConsumer eventConsumer;

    @Inject
    MeterRegistry registry;

    @Inject
    EngineConfig config;

    Instant replayStartTime;
    Instant replayEndTime;

    private Counter replayedMessageCounter;
    final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    @PostConstruct
    public void init() {
        replayedMessageCounter = registry.counter(REPLAYED_MESSAGE_COUNTER_NAME);

        replayStartTime = Instant.parse(config.getReplayStartTime());
        replayEndTime = Instant.parse(config.getReplayEndTime());
    }

    @Incoming(INGRESS_REPLAY_CHANNEL)
    @Blocking
    public CompletionStage<Void> consume(Message<String> message) {

        Log.trace("Consuming replay event from ingress");
        if (config.isSkipMessageProcessing()) {
            return message.ack();
        }

        Optional<KafkaMessageMetadata> metadata = message.getMetadata(KafkaMessageMetadata.class);
        if (metadata.isPresent()) {
            Instant kafkaTimestamp = metadata.get().getTimestamp();
            if (kafkaTimestamp != null) {
                Log.tracef("Consuming replay event from ingress, timestamp: %s", formatter.format(kafkaTimestamp));
                if (kafkaTimestamp.isAfter(replayStartTime) && kafkaTimestamp.isBefore(replayEndTime)) {
                    Log.trace("Replaying event");
                    eventConsumer.process(message);
                    replayedMessageCounter.increment();
                }
            }
        }
        return message.ack();
    }
}
