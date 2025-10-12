package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.v2.MessageContext;
import com.redhat.cloud.notifications.connector.v2.OutgoingCloudEventBuilder;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.eclipse.microprofile.reactive.messaging.Message;

import static com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty.ADDITIONAL_ERROR_DETAILS;
import static com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty.RESOLVED_RECIPIENT_LIST;

@ApplicationScoped
@Alternative
@Priority(0) // The value doesn't matter.
public class CloudEventHistoryBuilder extends OutgoingCloudEventBuilder {

    public static final String TOTAL_RECIPIENTS_KEY = "total_recipients";

    @Override
    public Message<String> build(MessageContext context) throws Exception {
        Message<String> cloudEventMessage = super.build(context);
        Integer totalRecipients = context.getProperty(TOTAL_RECIPIENTS_KEY, Integer.class);
        if (totalRecipients == null) {
            totalRecipients = 0;
        }

        // Extract the current payload and add drawer-specific information
        JsonObject data = new JsonObject(cloudEventMessage.getPayload());
        data.getJsonObject("details").put(RESOLVED_RECIPIENT_LIST, context.getProperty(RESOLVED_RECIPIENT_LIST));
        data.getJsonObject("details").put(TOTAL_RECIPIENTS_KEY, totalRecipients);
        if (context.getProperties().containsKey(ADDITIONAL_ERROR_DETAILS)) {
            data.getJsonObject("details").put(ADDITIONAL_ERROR_DETAILS, getErrorDetail(context));
        }

        // Create a new message with the updated payload, preserving the CloudEvent metadata
        return cloudEventMessage.withPayload(data.encode());
    }

    private Object getErrorDetail(final MessageContext context) {
        try {
            return new JsonObject(context.getProperty(ADDITIONAL_ERROR_DETAILS, String.class));
        } catch (Exception e) {
            return context.getProperty(ADDITIONAL_ERROR_DETAILS, String.class);
        }
    }
}
