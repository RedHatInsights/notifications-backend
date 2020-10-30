package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.ingress.Action;
import io.smallrye.mutiny.Uni;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.JsonDecoder;
import org.apache.avro.specific.SpecificDatumReader;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class EventConsumer {

    @Inject
    EndpointProcessor destinations;

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
                        // TODO Handle here and set the counters for broken input data and produce empty message?
                )
                .stage(self -> self
                                // Second pipeline stage - enrich from input to destination (webhook) processor format
                                .onItem()
                                .transformToUni(action -> destinations.process(action))
                        // Receive only notification of completion
                )
                .onFailure().invoke(Throwable::printStackTrace) // TODO Proper error handling
                // Third pipeline stage - handle failures (nothing should be here) and ack the Kafka topic
                .onItem().transformToUni(m -> {
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
