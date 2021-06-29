package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.ingress.Action;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.mutiny.Uni;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.JsonDecoder;
import org.apache.avro.specific.SpecificDatumReader;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment.Strategy;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class EventConsumer {

    public static final String REJECTED_COUNTER_NAME = "input.rejected";
    public static final String PROCESSING_ERROR_COUNTER_NAME = "input.processing.error";

    private static final Logger log = Logger.getLogger(EventConsumer.class.getName());

    @Inject
    MeterRegistry registry;

    @Inject
    EndpointProcessor endpointProcessor;

    private Counter rejectedCount;
    private Counter processingErrorCount;

    @PostConstruct
    public void init() {
        rejectedCount = registry.counter(REJECTED_COUNTER_NAME);
        processingErrorCount = registry.counter(PROCESSING_ERROR_COUNTER_NAME);
    }

    @Incoming("ingress")
    @Acknowledgment(Strategy.PRE_PROCESSING)
    // Can be modified to use Multi<Message<String>> input also for more concurrency
    public Uni<Void> processAsync(Message<String> input) {
        return Uni.createFrom().item(() -> input.getPayload())
                .stage(self -> self
                                .onItem().transform(this::extractPayload)
                                .onItem().invoke(payload -> log.info(() -> "Processing received payload: (" + payload.getAccountId() + ") " + payload.getBundle() + "/" + payload.getApplication() + "/" + payload.getEventType()))
                                .onFailure().invoke(t -> rejectedCount.increment())
                )
                .stage(self -> self
                                // Second pipeline stage - enrich from input to destination (webhook) processor format
                                .onItem()
                                .transformToUni(action -> endpointProcessor.process(action)
                                        .onFailure().invoke(t -> processingErrorCount.increment())
                                )
                        // Receive only notification of completion
                )
                // Third pipeline stage - ack the Kafka topic
                .onItemOrFailure()
                .transformToUni((unused, t) -> {
                    if (t != null) {
                        log.log(Level.INFO, "Could not process the payload: " + input.getPayload(), t);
                    }
                    return Uni.createFrom().voidItem();
                });
    }

    private Action extractPayload(String payload) {
        // I need the schema here..
        Action action = new Action();
        try {
            // Which ones can I reuse?
            JsonDecoder jsonDecoder = DecoderFactory.get().jsonDecoder(Action.getClassSchema(), payload);
            DatumReader<Action> reader = new SpecificDatumReader<>(Action.class);
            reader.read(action, jsonDecoder);
        } catch (IOException e) {
            throw new IllegalArgumentException("Payload extraction failed", e);
        }
        return action;
    }
}
