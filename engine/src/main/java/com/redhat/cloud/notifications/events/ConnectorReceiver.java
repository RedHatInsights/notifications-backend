package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.db.repositories.PayloadDetailsRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.processors.drawer.DrawerProcessor;
import com.redhat.cloud.notifications.processors.payload.PayloadDetails;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * We sent data via Camel. Now Camel informs us about the outcome,
 * which we need to put into the notifications history.
 */
@ApplicationScoped
public class ConnectorReceiver {

    public static final String FROMCAMEL_CHANNEL = "fromcamel";
    public static final String MESSAGES_ERROR_COUNTER_NAME = "camel.messages.error";
    public static final String MESSAGES_PROCESSED_COUNTER_NAME = "camel.messages.processed";
    public static final String EGRESS_CHANNEL = "egress";

    @Inject
    NotificationHistoryRepository notificationHistoryRepository;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    CamelHistoryFillerHelper camelHistoryFillerHelper;

    @Inject
    EndpointErrorFromConnectorHelper endpointErrorFromConnectorHelper;

    @Inject
    PayloadDetailsRepository payloadDetailsRepository;

    private Counter messagesProcessedCounter;
    private Counter messagesErrorCounter;

    @PostConstruct
    void init() {
        messagesProcessedCounter = meterRegistry.counter(MESSAGES_PROCESSED_COUNTER_NAME);
        messagesErrorCounter = meterRegistry.counter(MESSAGES_ERROR_COUNTER_NAME);
    }

    @Inject
    EngineConfig engineConfig;

    @Inject
    DrawerProcessor drawerProcessor;

    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    @Incoming(FROMCAMEL_CHANNEL)
    @Blocking
    @ActivateRequestContext
    public CompletionStage<Void> processAsync(final Message<String> message) {
        try {
            final String payload = message.getPayload();

            Log.infof("Processing return from camel: %s", payload);
            Map<String, Object> decodedPayload = decodeItem(payload);

            String historyId = (String) decodedPayload.get("historyId");
            final Endpoint endpoint = notificationHistoryRepository.getEndpointForHistoryId(historyId);

            if (engineConfig.isDrawerEnabled()) {
                drawerProcessor.manageConnectorDrawerReturnsIfNeeded(decodedPayload, UUID.fromString(historyId));
            }
            boolean updated = camelHistoryFillerHelper.updateHistoryItem(decodedPayload);
            if (!updated) {
                Log.warnf("Camel notification history update failed because no record was found with [id=%s]", decodedPayload.get("historyId"));
            }
            endpointErrorFromConnectorHelper.manageEndpointDisablingIfNeeded(endpoint, new JsonObject(payload));

            this.removePayloadFromDatabase(message);
        } catch (Exception e) {
            messagesErrorCounter.increment();
            Log.error("|  Failure to update the history", e);
        } finally {
            messagesProcessedCounter.increment();
        }

        return message.ack();
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

    /**
     * Removes the payload from the database if we receive a Kafka header from
     * the connectors signaling so.
     * @param message the message to check the headers from.
     */
    private void removePayloadFromDatabase(final Message<String> message) {
        final Optional<IncomingKafkaRecordMetadata> metadata = message.getMetadata(IncomingKafkaRecordMetadata.class);

        // If there is no message metadata do nothing.
        if (metadata.isEmpty()) {
            return;
        }

        final Headers headers = metadata.get().getHeaders();
        for (final Header header : headers.headers(PayloadDetails.X_RH_NOTIFICATIONS_CONNECTOR_PAYLOAD_HEADER)) {
            if (PayloadDetails.X_RH_NOTIFICATIONS_CONNECTOR_PAYLOAD_HEADER.equals(header.key())) {
                final String headerValue = new String(header.value(), StandardCharsets.UTF_8);

                final UUID eventId = UUID.fromString(headerValue);

                this.payloadDetailsRepository.deleteById(eventId);
            }
        }
    }
}
