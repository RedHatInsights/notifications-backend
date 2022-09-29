package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.models.Endpoint;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.vertx.core.json.Json;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

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
    public static final String INTEGRATION_FAILED_EVENT_TYPE = "integration-failed";

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

    @Inject
    FeatureFlipper featureFlipper;

    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    @Incoming(FROMCAMEL_CHANNEL)
    @Blocking
    public void processAsync(String payload) {
        try {
            Log.infof("Processing return from camel: %s", payload);
            Map<String, Object> decodedPayload = decodeItem(payload);
            statelessSessionFactory.withSession(statelessSession -> {
                reinjectIfNeeded(decodedPayload);
                try {
                    notificationHistoryRepository.updateHistoryItem(decodedPayload);
                } catch (Exception e) {
                    Log.info("|  Update Fail", e);
                }
            });
        } catch (Exception e) {
            messagesErrorCounter.increment();
            Log.error("|  Failure to update the history", e);
        } finally {
            messagesProcessedCounter.increment();
        }
    }

    private void reinjectIfNeeded(Map<String, Object> payloadMap) {
        if (!featureFlipper.isEnableReInject() || (payloadMap.containsKey("successful") && ((Boolean) payloadMap.get("successful")))) {
            return;
        }

        String historyId = (String) payloadMap.get("historyId");
        Log.infof("Notification with id %s was not successful, resubmitting for further processing", historyId);

        Endpoint ep = notificationHistoryRepository.getEndpointForHistoryId(historyId);

        Event event = new Event();
        Payload.PayloadBuilder payloadBuilder = new Payload.PayloadBuilder();
        payloadMap.forEach(payloadBuilder::withAdditionalProperty);

        // TODO augment with details from Endpoint and original event
        event.setPayload(payloadBuilder.build());

        // Save the original id, as we may need it in the future.
        Context.ContextBuilder contextBuilder = new Context.ContextBuilder();
        contextBuilder.withAdditionalProperty("original-id", historyId);
        if (ep != null) { // TODO For the current tests. EP should not be null in real life
            contextBuilder.withAdditionalProperty("failed-integration", ep.getName());
        }

        Action action = new Action.ActionBuilder()
                .withId(UUID.randomUUID())
                .withBundle("console")
                .withApplication("integrations")
                .withEventType(INTEGRATION_FAILED_EVENT_TYPE)
                .withAccountId(ep != null ? ep.getAccountId() : "")
                .withOrgId(ep != null && ep.getOrgId() != null ? ep.getOrgId() : "")
                .withContext(contextBuilder.build())
                .withTimestamp(LocalDateTime.now(ZoneOffset.UTC))
                .withEvents(Collections.singletonList(event))
                .withRecipients(Collections.singletonList(
                        new Recipient.RecipientBuilder()
                                .withOnlyAdmins(true)
                                .withIgnoreUserPreferences(true)
                                .build()))
                .build();

        String ser = Parser.encode(action);
        // Add the message id in Kafka header for the de-duplicator
        Message<String> message = KafkaMessageWithIdBuilder.build(ser);
        emitter.send(message);
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
