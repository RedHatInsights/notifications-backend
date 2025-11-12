package com.redhat.cloud.notifications.connector.v2;

import com.redhat.cloud.notifications.connector.v2.models.HandledExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
public class OutgoingMessageSender {

    @Inject
    OutgoingCloudEventBuilder outgoingCloudEventBuilder;

    @Inject
    @Channel("outgoingmessages")
    Emitter<String> emitter;

    public void sendSuccess(IncomingCloudEventMetadata<JsonObject> cloudEventMetadata, HandledMessageDetails processedMessageDetails, long startTime) {
        processedMessageDetails.outcomeMessage = String.format("Event %s sent successfully", cloudEventMetadata.getId());
        Message<String> cloudEventMessage = outgoingCloudEventBuilder.buildSuccess(cloudEventMetadata, processedMessageDetails, startTime);

        sendResponse(cloudEventMessage);
    }

    public void sendFailure(IncomingCloudEventMetadata<JsonObject> cloudEventMetadata, HandledExceptionDetails processedExceptionDetails, long startTime) {
        Message<String> cloudEventMessage = outgoingCloudEventBuilder.buildFailure(cloudEventMetadata, processedExceptionDetails, startTime);

        sendResponse(cloudEventMessage);
    }

    private void sendResponse(Message<String> cloudEventMessage) {
        try {
            emitter.send(cloudEventMessage);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send response to engine", e);
        }
    }

}
