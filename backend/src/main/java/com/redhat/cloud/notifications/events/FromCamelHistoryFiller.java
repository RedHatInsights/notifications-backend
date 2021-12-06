package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.models.Endpoint;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.vertx.core.json.Json;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * We sent data via Camel. Now Camel informs us about the outcome,
 * which we need to put into the notifications history.
 */
@ApplicationScoped
public class FromCamelHistoryFiller {

    public static final String FROMCAMEL_CHANNEL = "fromCamel";
    public static final String MESSAGES_ERROR_COUNTER_NAME = "camel.messages.error";
    public static final String MESSAGES_PROCESSED_COUNTER_NAME = "camel.messages.processed";
    public static final String EGRESS_CHANNEL = "egress";

    private static final Logger log = Logger.getLogger(FromCamelHistoryFiller.class);

    @Inject
    NotificationHistoryRepository notificationHistoryRepository;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Inject
    MeterRegistry meterRegistry;

    private Counter messagesProcessedCounter;
    private Counter messagesErrorCounter;

    @PostConstruct
    void init() {
        messagesProcessedCounter = meterRegistry.counter(MESSAGES_PROCESSED_COUNTER_NAME);
        messagesErrorCounter = meterRegistry.counter(MESSAGES_ERROR_COUNTER_NAME);
    }

    @Channel(EGRESS_CHANNEL)
    Emitter<String> emitter;

    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    @Incoming(FROMCAMEL_CHANNEL)
    // Can be modified to use Multi<Message<String>> input also for more concurrency
    public Uni<Void> processAsync(Message<String> input) {
        return Uni.createFrom().item(input::getPayload)
                .onItem().invoke(payload -> log.infof("Processing return from camel: %s", payload))
                .onItem().transform(this::decodeItem)
                .onItem().invoke(this::reinjectIfNeeded)
                .onItem()
                .transformToUni(payload -> {
                    return sessionFactory.withStatelessSession(statelessSession -> {
                        return notificationHistoryRepository.updateHistoryItem(payload)
                                .onFailure().invoke(t -> log.info("|  Update Fail", t)
                                );
                    });
                })
                .onFailure().recoverWithUni(t -> {
                    messagesErrorCounter.increment();
                    log.error("|  Failure to update the history", t);
                    return Uni.createFrom().voidItem();
                })
                .eventually(() -> messagesProcessedCounter.increment());
    }

    private void reinjectIfNeeded(Map<String, Object> payload) {
        if (!payload.containsKey("successful") || !((Boolean)payload.get("successful"))) {
            String historyId = (String) payload.get("historyId");
            log.infof("Event with id %s was not successful, resubmitting for further processing", historyId);

            Event event = new Event();

            Uni<Endpoint> endpointUni = notificationResources.getEndpointForHistoryId(historyId);
            endpointUni.onItem().transform(ep -> {

                // TODO augment with details from Endpoint and original event
                event.setPayload(payload);

                // Save the original id, as we may need it in the future.
                Map<String,String> context = new HashMap<>();
                context.put("original-id", historyId);
                context.put("failed-integration", ep.getName());

                Action action = new Action(
                        "platform",
                        "notifications",
                        "integration_failed",
                        LocalDateTime.now(),
                        ep.getAccountId(),
                        context, // context
                        Collections.singletonList(event),
                        "v1.0.0",
                        Collections.emptyList()
                    );
                try {
                    String ser = serializeAction(action);
                    // Add the message id in Kafka header for the de-duplicator
                    Message<String> message = buildMessageWithId(ser);
                    emitter.send(message);
                    return Uni.createFrom().voidItem();
                } catch (IOException e) {
                    e.printStackTrace();
                    return Uni.createFrom().failure(e);
                }

            })
            .subscribe()
                    .with(x -> x.onItem().invoke(item -> System.out.println(item))); // This triggers the actual work


        }
    }

    // Blindly copied from -gw . Perhaps put this into Schema project?
    private static String serializeAction(Action action) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonEncoder jsonEncoder = EncoderFactory.get().jsonEncoder(Action.getClassSchema(), baos);
        DatumWriter<Action> writer = new SpecificDatumWriter<>(Action.class);
        writer.write(action, jsonEncoder);
        jsonEncoder.flush();

        return baos.toString(UTF_8);
    }

    // Blindly copied from -gw.  Perhaps put this into Schema project
    private static Message buildMessageWithId(String payload) {
        byte[] messageId = UUID.randomUUID().toString().getBytes(UTF_8);
        OutgoingKafkaRecordMetadata metadata = OutgoingKafkaRecordMetadata.builder()
                .withHeaders(new RecordHeaders().add(MESSAGE_ID_HEADER, messageId))
                .build();
        return Message.of(payload).addMetadata(metadata);
    }



    private Map<String, Object> decodeItem(String s) {

        // 1st step CloudEvent as String -> map
        Map<String, Object> ceMap = Json.decodeValue(s, Map.class);

        // Take the id from the CloudEvent as the historyId
        String id = (String) ceMap.get("id");

        // 2nd step data item (as String) to final map
        Map<String, Object> map = Json.decodeValue((String) ceMap.get("data"), Map.class);
        map.put("historyId", id);
        return map;
    }

}
