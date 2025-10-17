package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.drawer.model.HandledDrawerExceptionDetails;
import com.redhat.cloud.notifications.connector.drawer.model.HandledDrawerMessageDetails;
import com.redhat.cloud.notifications.connector.v2.OutgoingCloudEventBuilder;
import com.redhat.cloud.notifications.connector.v2.pojo.HandledExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.pojo.HandledMessageDetails;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import static com.redhat.cloud.notifications.connector.drawer.constant.Constants.ADDITIONAL_ERROR_DETAILS;
import static com.redhat.cloud.notifications.connector.drawer.constant.Constants.RESOLVED_RECIPIENT_LIST;

@ApplicationScoped
@Alternative
@Priority(0) // The value doesn't matter.
public class CloudEventHistoryBuilder extends OutgoingCloudEventBuilder {

    public static final String TOTAL_RECIPIENTS_KEY = "total_recipients";

    @Override
    public JsonObject buildSuccess(HandledMessageDetails processedMessageDetails) {
        JsonObject data = new JsonObject();

        if (processedMessageDetails instanceof HandledDrawerMessageDetails processedDrawerMessageDetails) {
            data.put("details", new JsonObject());

            data.getJsonObject("details").put(RESOLVED_RECIPIENT_LIST, processedDrawerMessageDetails.recipientsList);
            data.getJsonObject("details").put(TOTAL_RECIPIENTS_KEY, processedDrawerMessageDetails.recipientsList.size());
        }
        return data;
    }

    @Override
    public JsonObject buildFailure(HandledExceptionDetails processedExceptionDetails) {
        JsonObject data = new JsonObject();

        if (processedExceptionDetails instanceof HandledDrawerExceptionDetails processedDrawerExceptionDetails) {
            data.put("details", new JsonObject());

            if (null != processedDrawerExceptionDetails.additionalErrorDetails) {
                try {
                    data.getJsonObject("details").put(ADDITIONAL_ERROR_DETAILS,
                        new JsonObject(processedDrawerExceptionDetails.additionalErrorDetails));
                } catch (Exception e) {
                    data.getJsonObject("details").put(ADDITIONAL_ERROR_DETAILS,
                        processedDrawerExceptionDetails.additionalErrorDetails);
                }
            }
        }
        return data;
    }
}
