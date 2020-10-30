package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.ingress.Action;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.mutiny.Uni;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.JsonDecoder;
import org.apache.avro.specific.SpecificDatumReader;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class EventConsumer {

    private static final Logger log = Logger.getLogger(EventConsumer.class);

    MeterRegistry registry;

    @Inject
    EndpointProcessor destinations;

    private Counter rejectedCount;
    private Counter processingErrorCount;

    public EventConsumer(MeterRegistry registry) {
        this.registry = registry;
        rejectedCount = registry.counter("input.rejected");
        processingErrorCount = registry.counter("input.processing.error");
    }

    @Incoming("ingress")
//    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    // Can be modified to use Multi<Message<String>> input also for more concurrency
    public Uni<Void> processAsync(Message<String> input) {
        return Uni.createFrom()
                .item(input)
                .stage(self -> self
                                // First pipeline stage - modify from Kafka message to processable entity
                                .onItem().transform(Message::getPayload)
                                .onItem().transform(this::extractPayload)
                                .onFailure().invoke(t -> rejectedCount.increment())
                        // TODO Handle here and set the counters for broken input data and produce empty message?
                )
                .stage(self -> self
                                // Second pipeline stage - enrich from input to destination (webhook) processor format
                                .onItem()
                                .transformToUni(action -> destinations.process(action))
                                .onFailure().invoke(t -> processingErrorCount.increment())
                        // Receive only notification of completion
                )
                // Third pipeline stage - handle failures (nothing should be here) and ack the Kafka topic
                .onItemOrFailure()
                .transformToUni((unused, t) -> {
                    // Log the throwable ?
                    log.errorf("Could not process the payload: %s", t.getMessage());
                    CompletionStage<Void> ack = input.ack();
                    return Uni.createFrom().completionStage(ack);
                });
    }

    public Action extractPayload(String payload) {
        // I need the schema here..
        Action action = new Action();
        try {
            // Which ones can I reuse?
            JsonDecoder jsonDecoder = DecoderFactory.get().jsonDecoder(Action.getClassSchema(), payload);
            DatumReader<Action> reader = new SpecificDatumReader<>(Action.class);
            reader.read(action, jsonDecoder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return action;
    }
}
