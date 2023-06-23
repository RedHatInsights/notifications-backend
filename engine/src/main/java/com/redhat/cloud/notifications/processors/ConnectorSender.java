package com.redhat.cloud.notifications.processors;

import io.opentelemetry.context.Context;
import io.smallrye.reactive.messaging.TracingMetadata;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.vertx.core.json.JsonObject;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class ConnectorSender {

    public static final String TOCAMEL_CHANNEL = "tocamel";
    // TODO notification should end with a s but eventing-integrations does not expect it...
    public static final String CLOUD_EVENT_TYPE_PREFIX = "com.redhat.console.notification.toCamel.";
    public static final String X_RH_NOTIFICATIONS_CONNECTOR_HEADER = "x-rh-notifications-connector";

    @Inject
    @Channel(TOCAMEL_CHANNEL)
    Emitter<JsonObject> emitter;

    public void send(JsonObject payload, UUID historyId, String endpointSubType) {
        Message<JsonObject> message = buildMessage(payload, historyId, endpointSubType);
        emitter.send(message);
    }

    private static Message<JsonObject> buildMessage(JsonObject payload, UUID historyId, String endpointSubType) {

        OutgoingKafkaRecordMetadata<String> kafkaMetadata = buildOutgoingKafkaRecordMetadata(endpointSubType);

        String cloudEventId = historyId.toString();
        String cloudEventType = CLOUD_EVENT_TYPE_PREFIX + endpointSubType;
        CloudEventMetadata<String> cloudEventMetadata = buildCloudEventMetadata(cloudEventId, cloudEventType);

        TracingMetadata tracingMetadata = TracingMetadata.withPrevious(Context.current());

        return Message.of(payload)
                .addMetadata(kafkaMetadata)
                .addMetadata(cloudEventMetadata)
                .addMetadata(tracingMetadata);
    }

    private static OutgoingKafkaRecordMetadata<String> buildOutgoingKafkaRecordMetadata(String connectorHeader) {
        Headers headers = new RecordHeaders()
                .add(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, connectorHeader.getBytes(UTF_8));
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
}
