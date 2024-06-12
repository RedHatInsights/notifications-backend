package com.redhat.cloud.notifications.processors;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.db.repositories.PayloadDetailsRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.payload.PayloadDetails;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.opentelemetry.context.Context;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.TracingMetadata;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.NotificationHistory.getHistoryStub;
import static com.redhat.cloud.notifications.models.NotificationStatus.FAILED_INTERNAL;
import static com.redhat.cloud.notifications.models.NotificationStatus.PROCESSING;
import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class ConnectorSender {

    public static final String TOCAMEL_CHANNEL = "tocamel";
    // TODO notification should end with a s but eventing-integrations does not expect it...
    public static final String CLOUD_EVENT_TYPE_PREFIX = "com.redhat.console.notification.toCamel.";
    public static final String X_RH_NOTIFICATIONS_CONNECTOR_HEADER = "x-rh-notifications-connector";

    private static final String NOTIFICATIONS_PAYLOAD_STORED_DATABASE_METRIC_NAME = "notifications.payload.stored.database";
    private static final String TAG_KEY_CONNECTOR = "connector";
    private static final String TAG_KEY_ORG_ID = "orgid";
    private static final String TAG_KEY_APPLICATION = "application";
    private static final String TAG_KEY_EVENT_TYPE = "event_type";

    @Inject
    @Channel(TOCAMEL_CHANNEL)
    Emitter<JsonObject> emitter;

    @Inject
    EngineConfig engineConfig;

    @Inject
    NotificationHistoryRepository notificationHistoryRepository;

    @Inject
    MeterRegistry registry;

    @Inject
    PayloadDetailsRepository payloadDetailsRepository;

    public void send(Event event, Endpoint endpoint, JsonObject payload) {
        payload.put("org_id", event.getOrgId());

        String connector = getConnector(endpoint);

        NotificationHistory history = getHistoryStub(endpoint, event, 0L, UUID.randomUUID());
        history.setStatus(PROCESSING);

        Log.infof("Sending notification to connector [orgId=%s, eventId=%s, connector=%s, historyId=%s]",
                event.getOrgId(), event.getId(), connector, history.getId());

        notificationHistoryRepository.createNotificationHistory(history);

        // Measure the payload size.
        final int payloadSize = payload.toString().getBytes().length;
        recordMetrics(event, connector, payloadSize);

        final Map<String, String> customHeaders = new HashMap<>();

        // When the payload to be sent is greater than the configured limit,
        // store the payload in the database so that we can fetch it from the
        // connectors themselves.
        if (this.engineConfig.getKafkaToCamelMaximumRequestSize() <= payloadSize) {
            customHeaders.put(PayloadDetails.X_RH_NOTIFICATIONS_CONNECTOR_PAYLOAD_HEADER, event.getId().toString());

            final PayloadDetails payloadDetails = new PayloadDetails(event, payload);
            this.payloadDetailsRepository.save(payloadDetails);

            payload = new JsonObject();

            this.registry.counter(
                NOTIFICATIONS_PAYLOAD_STORED_DATABASE_METRIC_NAME,
                Tags.of(TAG_KEY_CONNECTOR, connector, TAG_KEY_ORG_ID, event.getOrgId(), TAG_KEY_APPLICATION, event.getApplicationDisplayName(), TAG_KEY_EVENT_TYPE, event.getEventTypeDisplayName())
            ).increment();
        }

        try {
            Message<JsonObject> message = buildMessage(payload, history.getId(), connector, customHeaders);
            emitter.send(message);
        } catch (Exception e) {
            history.setStatus(FAILED_INTERNAL);
            history.setDetails(Map.of("failure", e.getMessage()));
            notificationHistoryRepository.updateHistoryItem(history);
            Log.infof(e, "Failed to send notification to connector [orgId=%s, eventId=%s, connector=%s, historyId=%s]",
                    event.getOrgId(), event.getId(), connector, history.getId());
        }
    }

    /**
     * Build a Kafka message read to be sent to the connectors.
     * @param payload the payload of the message.
     * @param historyId the history ID to include in the Cloud Event metadata.
     * @param connector the connector's name to include both in the Cloud Event
     *                  metadata and in the header for discerning the target
     *                  connector.
     * @param customHeaders any custom headers to include in the Kafka message.
     * @return the Kafka message ready to be sent.
     */
    private static Message<JsonObject> buildMessage(final JsonObject payload, final UUID historyId, final String connector, final Map<String, String> customHeaders) {
        OutgoingKafkaRecordMetadata<String> kafkaMetadata = buildOutgoingKafkaRecordMetadata(connector, customHeaders);

        String cloudEventId = historyId.toString();
        String cloudEventType = CLOUD_EVENT_TYPE_PREFIX + connector;
        CloudEventMetadata<String> cloudEventMetadata = buildCloudEventMetadata(cloudEventId, cloudEventType);

        TracingMetadata tracingMetadata = TracingMetadata.withPrevious(Context.current());

        return Message.of(payload)
                .addMetadata(kafkaMetadata)
                .addMetadata(cloudEventMetadata)
                .addMetadata(tracingMetadata);
    }

    /**
     * Build the headers for the Kafka message.
     * @param connector the connector the Kafka message is intended to.
     * @param customHeaders any custom headers to add to the Kafka message.
     * @return a metadata object including the headers.
     */
    private static OutgoingKafkaRecordMetadata<String> buildOutgoingKafkaRecordMetadata(final String connector, final Map<String, String> customHeaders) {
        final Headers headers = new RecordHeaders()
                .add(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, connector.getBytes(UTF_8));

        // Add any custom headers to the Kafka message.
        if (!customHeaders.isEmpty()) {
            customHeaders.forEach((key, value) -> headers.add(key, value.getBytes(UTF_8)));
        }

        return OutgoingKafkaRecordMetadata.<String>builder()
                .withHeaders(headers)
                .build();
    }

    private static OutgoingCloudEventMetadata<String> buildCloudEventMetadata(String id, String type) {
        return OutgoingCloudEventMetadata.<String>builder()
                .withId(id)
                .withType(type)
                .withDataContentType("application/json")
                .build();
    }

    private static String getConnector(Endpoint endpoint) {
        // TODO Endpoints types and subtypes made sense in the past but this is no longer true. We should get rid of subtypes and only use types.
        if (endpoint.getSubType() != null) {
            return endpoint.getSubType();
        } else {
            return endpoint.getType().name().toLowerCase();
        }
    }

    private void recordMetrics(Event event, String connector, int payloadSize) {
        DistributionSummary ds = DistributionSummary.builder("notifications.tocamel.payload.content.size")
            .baseUnit("bytes")
            .tags(TAG_KEY_CONNECTOR, connector)
            .tags(TAG_KEY_ORG_ID, event.getOrgId())
            .tags(TAG_KEY_APPLICATION, event.getApplicationDisplayName())
            .tags(TAG_KEY_EVENT_TYPE, event.getEventType().getName())
            .register(registry);
        ds.record(payloadSize);
    }
}
