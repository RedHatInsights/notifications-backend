package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.v2.MessageContext;
import com.redhat.cloud.notifications.connector.v2.OutgoingCloudEventBuilder;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import static com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty.ADDITIONAL_ERROR_DETAILS;
import static com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty.RESOLVED_RECIPIENT_LIST;

@ApplicationScoped
@Alternative
@Priority(0) // The value doesn't matter.
public class CloudEventHistoryBuilder extends OutgoingCloudEventBuilder {

    public static final String TOTAL_RECIPIENTS_KEY = "total_recipients";

    @Override
    public JsonObject build(MessageContext context) throws Exception {
        JsonObject cloudEvent = super.build(context);
        Integer totalRecipients = context.getProperty(TOTAL_RECIPIENTS_KEY, Integer.class);
        if (totalRecipients == null) {
            totalRecipients = 0;
        }

        JsonObject data = new JsonObject(cloudEvent.getString("data"));
        data.getJsonObject("details").put(RESOLVED_RECIPIENT_LIST, context.getProperty(RESOLVED_RECIPIENT_LIST));
        data.getJsonObject("details").put(TOTAL_RECIPIENTS_KEY, totalRecipients);
        if (context.getProperties().containsKey(ADDITIONAL_ERROR_DETAILS)) {
            data.getJsonObject("details").put(ADDITIONAL_ERROR_DETAILS, getErrorDetail(context));
        }

        cloudEvent.put("data", data.encode());
        return cloudEvent;
    }

    private Object getErrorDetail(final MessageContext context) {
        try {
            return new JsonObject(context.getProperty(ADDITIONAL_ERROR_DETAILS, String.class));
        } catch (Exception e) {
            return context.getProperty(ADDITIONAL_ERROR_DETAILS, String.class);
        }
    }
}
