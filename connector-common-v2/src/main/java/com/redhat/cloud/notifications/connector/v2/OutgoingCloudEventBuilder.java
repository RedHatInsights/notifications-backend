package com.redhat.cloud.notifications.connector.v2;

import com.redhat.cloud.notifications.connector.v2.models.HandledExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import com.redhat.cloud.notifications.connector.v2.models.NotificationToConnector;
import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.net.URI;
import java.time.ZonedDateTime;

import static java.time.ZoneOffset.UTC;

@DefaultBean
@ApplicationScoped
public class OutgoingCloudEventBuilder {

    public static final String CE_SPEC_VERSION = "1.0";
    public static final String CE_TYPE = "com.redhat.console.notifications.history";

    @Inject
    ConnectorConfig connectorConfig;

    public Message<String> buildSuccess(IncomingCloudEventMetadata<JsonObject> incomingCloudEvent, HandledMessageDetails processedMessageDetails, long startTime) {
        JsonObject details = new JsonObject();
        details.put("outcome", processedMessageDetails.outcomeMessage);

        JsonObject metadata = buildSuccess(processedMessageDetails);

        return buildMessage(incomingCloudEvent, details, metadata, true, startTime);
    }

    public Message<String> buildFailure(IncomingCloudEventMetadata<JsonObject> incomingCloudEvent, HandledExceptionDetails processedExceptionDetails, long startTime) {
        JsonObject details = new JsonObject();
        details.put("outcome", processedExceptionDetails.outcomeMessage);

        JsonObject metadata = buildFailure(processedExceptionDetails);

        return buildMessage(incomingCloudEvent, details, metadata, false, startTime);
    }

    private Message<String> buildMessage(IncomingCloudEventMetadata<JsonObject> incomingCloudEvent, JsonObject details, JsonObject metadata, boolean successful, long startTime) {
        JsonObject data = new JsonObject();
        data.put("successful", successful);
        data.put("duration", System.currentTimeMillis() - startTime);
        details.put("type", incomingCloudEvent.getType());
        data.put("details", details);

        data.mergeIn(metadata, true);

        NotificationToConnector notificationToConnector = incomingCloudEvent.getData().mapTo(NotificationToConnector.class);

        Log.infof("Notification sent [orgId=%s, EndpointId=%s, type=%s, historyId=%s, duration=%d, successful=%b]",
            notificationToConnector.getOrgId(),
            notificationToConnector.getEndpointId(),
            connectorConfig.getConnectorName(),
            incomingCloudEvent.getId(),
            data.getLong("duration"),
            successful);

        OutgoingCloudEventMetadata<String> outgoingCloudEventMetadata = OutgoingCloudEventMetadata.<String>builder()
            .withId(incomingCloudEvent.getId())
            .withType(CE_TYPE)
            .withSpecVersion(CE_SPEC_VERSION)
            .withSource(URI.create(connectorConfig.getConnectorName()))
            .withTimestamp(ZonedDateTime.now(UTC))
            .withDataContentType("application/json")
            .build();

        return Message.of(data.encode()).addMetadata(outgoingCloudEventMetadata);
    }

    public JsonObject buildSuccess(HandledMessageDetails processedMessageDetails) {
        return new JsonObject();
    }

    public JsonObject buildFailure(HandledExceptionDetails processedExceptionDetails) {
        return new JsonObject();
    }
}
