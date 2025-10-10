package com.redhat.cloud.notifications.connector.v2;

import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import static com.redhat.cloud.notifications.connector.v2.CommonConstants.OUTCOME;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.SUCCESSFUL;

@ApplicationScoped
public class OutgoingMessageSender {

    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    OutgoingCloudEventBuilder outgoingCloudEventBuilder;

    @Inject
    @Channel("outgoing-messages")
    Emitter<String> emitter;

    public void sendSuccess(MessageContext context) {
        context.setProperty(SUCCESSFUL, true);
        context.setProperty(OUTCOME, String.format("Event %s sent successfully", context.getIncomingCloudEventMetadata().getId()));
        sendResponse(context);
    }

    public void sendFailure(MessageContext context, String errorMessage) {
        context.setProperty(SUCCESSFUL, false);
        context.setProperty(OUTCOME, errorMessage);
        sendResponse(context);
    }

    private void sendResponse(MessageContext context) {
        try {
            JsonObject cloudEvent = outgoingCloudEventBuilder.build(context);
            emitter.send(cloudEvent.encode());
        } catch (Exception e) {
            throw new RuntimeException("Failed to send response to engine", e);
        }
    }
}
