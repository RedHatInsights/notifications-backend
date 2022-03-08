package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Encoder;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.models.Endpoint;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.vertx.core.json.Json;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
    StatelessSessionFactory statelessSessionFactory;

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

    @ConfigProperty(name = "reinject.enabled", defaultValue = "false")
    boolean enableReInject;

    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    @Incoming(FROMCAMEL_CHANNEL)
    public void processAsync(String payload) {
        try {
            log.infof("Processing return from camel: %s", payload);
            Map<String, Object> decodedPayload = decodeItem(payload);
            statelessSessionFactory.withSession(statelessSession -> {
                reinjectIfNeeded(decodedPayload);
                try {
                    notificationHistoryRepository.updateHistoryItem(decodedPayload);
                } catch (Exception e) {
                    log.info("|  Update Fail", e);
                }
            });
        } catch (Exception e) {
            messagesErrorCounter.increment();
            log.error("|  Failure to update the history", e);
        } finally {
            messagesProcessedCounter.increment();
        }
    }

    private void reinjectIfNeeded(Map<String, Object> payload) {
        if (!enableReInject || (payload.containsKey("successful") && ((Boolean) payload.get("successful")))) {
            return;
        }

        String historyId = (String) payload.get("historyId");
        log.infof("Notification with id %s was not successful, resubmitting for further processing", historyId);

        Endpoint ep = notificationHistoryRepository.getEndpointForHistoryId(historyId);

        Event event = new Event();

        // TODO augment with details from Endpoint and original event
        event.setPayload(payload);

        // Save the original id, as we may need it in the future.
        Map<String, String> context = new HashMap<>();
        context.put("original-id", historyId);
        if (ep != null) { // TODO For the current tests. EP should not be null in real life
            context.put("failed-integration", ep.getName());
        }

        Action action = Action.newBuilder()
                .setBundle("console")
                .setApplication("notifications")
                .setEventType("integration-failed")
                .setAccountId(ep != null ? ep.getAccountId() : "")
                .setContext(context)
                .setTimestamp(LocalDateTime.now(ZoneOffset.UTC))
                .setEvents(Collections.singletonList(event))
                .setVersion("v1.1.0")
                .setRecipients(Collections.singletonList(
                        Recipient.newBuilder()
                                .setOnlyAdmins(true)
                                .setIgnoreUserPreferences(true)
                                .build()))
                .build();

        String ser = new Encoder().encode(action);
        // Add the message id in Kafka header for the de-duplicator
        Message<String> message = buildMessageWithId(ser);
        emitter.send(message);
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
