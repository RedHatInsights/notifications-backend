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

import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.NotificationHistory.getHistoryStub;
import static com.redhat.cloud.notifications.models.NotificationStatus.FAILED_INTERNAL;
import static com.redhat.cloud.notifications.models.NotificationStatus.PROCESSING;
import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class ConnectorSender {
    /**
     * Constant for the name of what we consider a high volume application.
     */
    public static final String HIGH_VOLUME_APPLICATION = "errata-notifications";
    /**
     * Constant for the "high volume" Kafka topic where all the events that are
     * incoming from a high volume and traffic tenant are sent.
     */
    public static final String HIGH_VOLUME_CHANNEL = "highvolume";
    public static final String TOCAMEL_CHANNEL = "tocamel";
    // TODO notification should end with a s but eventing-integrations does not expect it...
    public static final String CLOUD_EVENT_TYPE_PREFIX = "com.redhat.console.notification.toCamel.";
    public static final String X_RH_NOTIFICATIONS_CONNECTOR_HEADER = "x-rh-notifications-connector";

    private static final String NOTIFICATIONS_PAYLOAD_STORED_DATABASE_METRIC_NAME = "notifications.payload.stored.database";
    private static final String TAG_KEY_CONNECTOR = "connector";
    private static final String TAG_KEY_APPLICATION = "application";
    private static final String TAG_KEY_EVENT_TYPE = "event_type";

    @Inject
    @Channel(HIGH_VOLUME_CHANNEL)
    Emitter<JsonObject> highVolumeEmitter;

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

        // When the payload to be sent is greater than the configured limit,
        // store the payload in the database so that we can fetch it from the
        // connectors themselves.
        if (this.engineConfig.getKafkaToCamelMaximumRequestSize() <= payloadSize) {
            final PayloadDetails payloadDetails = new PayloadDetails(event, payload);
            this.payloadDetailsRepository.save(payloadDetails);

            payload = new JsonObject();
            payload.put(PayloadDetails.PAYLOAD_DETAILS_ID_KEY, payloadDetails.getId());

            this.registry.counter(
                NOTIFICATIONS_PAYLOAD_STORED_DATABASE_METRIC_NAME,
                Tags.of(TAG_KEY_CONNECTOR, connector, TAG_KEY_APPLICATION, event.getApplicationDisplayName(), TAG_KEY_EVENT_TYPE, event.getEventTypeDisplayName())
            ).increment();
        }

        try {
            Message<JsonObject> message = buildMessage(payload, history.getId(), connector);

            if (this.engineConfig.isOutgoingKafkaHighVolumeTopicEnabled() && this.isEventFromHighVolumeApplication(event)) {
                this.highVolumeEmitter.send(message);
                Log.debugf("[event_id: %s] Event sent through high volume Kafka topic", event.getId());
            } else {
                this.emitter.send(message);
                Log.debugf("[event_id: %s] Event sent through regular Kafka topic", event.getId());
            }
        } catch (Exception e) {
            history.setStatus(FAILED_INTERNAL);
            history.setDetails(Map.of("failure", e.getMessage()));
            notificationHistoryRepository.updateHistoryItem(history);
            Log.infof(e, "Failed to send notification to connector [orgId=%s, eventId=%s, connector=%s, historyId=%s]",
                    event.getOrgId(), event.getId(), connector, history.getId());
        }
    }

    private static Message<JsonObject> buildMessage(JsonObject payload, UUID historyId, String connector) {

        OutgoingKafkaRecordMetadata<String> kafkaMetadata = buildOutgoingKafkaRecordMetadata(connector);

        String cloudEventId = historyId.toString();
        String cloudEventType = CLOUD_EVENT_TYPE_PREFIX + connector;
        CloudEventMetadata<String> cloudEventMetadata = buildCloudEventMetadata(cloudEventId, cloudEventType);

        TracingMetadata tracingMetadata = TracingMetadata.withPrevious(Context.current());

        return Message.of(payload)
                .addMetadata(kafkaMetadata)
                .addMetadata(cloudEventMetadata)
                .addMetadata(tracingMetadata);
    }

    private static OutgoingKafkaRecordMetadata<String> buildOutgoingKafkaRecordMetadata(String connector) {
        Headers headers = new RecordHeaders()
                .add(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, connector.getBytes(UTF_8));
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

    /**
     * Checks if the given event has been produced in an application that is
     * considered a "high volume" one, which means that produces a high amount
     * of traffic which needs to be diverted to special connectors.
     * @param event the event we have received.
     * @return {@code true} if the event comes from a high volume application.
     */
    private boolean isEventFromHighVolumeApplication(final Event event) {
        return HIGH_VOLUME_APPLICATION.equals(event.getEventType().getApplication().getName());
    }

    private void recordMetrics(Event event, String connector, int payloadSize) {
        DistributionSummary ds = DistributionSummary.builder("notifications.tocamel.payload.content.size")
            .baseUnit("bytes")
            .tags(TAG_KEY_CONNECTOR, connector)
            .tags(TAG_KEY_APPLICATION, event.getApplicationDisplayName())
            .tags(TAG_KEY_EVENT_TYPE, event.getEventType().getName())
            .register(registry);
        ds.record(payloadSize);
    }
}
