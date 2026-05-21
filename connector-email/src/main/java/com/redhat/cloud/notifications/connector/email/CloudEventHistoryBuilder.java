package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.models.HandledEmailExceptionDetails;
import com.redhat.cloud.notifications.connector.email.models.HandledEmailMessageDetails;
import com.redhat.cloud.notifications.connector.email.payload.PayloadDetails;
import com.redhat.cloud.notifications.connector.v2.OutgoingCloudEventBuilder;
import com.redhat.cloud.notifications.connector.v2.models.HandledExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@ApplicationScoped
@Alternative
@Priority(0)
public class CloudEventHistoryBuilder extends OutgoingCloudEventBuilder {

    public static final String TOTAL_RECIPIENTS_KEY = "total_recipients";
    public static final String ADDITIONAL_ERROR_DETAILS = "additionalErrorDetails";

    @Override
    public JsonObject buildSuccess(HandledMessageDetails processedMessageDetails) {
        JsonObject data = new JsonObject();
        if (processedMessageDetails instanceof HandledEmailMessageDetails emailDetails) {
            data.put("details", new JsonObject()
                .put(TOTAL_RECIPIENTS_KEY, emailDetails.totalRecipients));

            if (emailDetails.payloadId != null) {
                data.put(PayloadDetails.PAYLOAD_DETAILS_ID_KEY, emailDetails.payloadId);
            }
        }
        return data;
    }

    @Override
    public JsonObject buildFailure(HandledExceptionDetails processedExceptionDetails) {
        JsonObject data = new JsonObject();
        JsonObject details = new JsonObject();
        details.put(TOTAL_RECIPIENTS_KEY, 0);

        if (processedExceptionDetails instanceof HandledEmailExceptionDetails emailDetails) {
            if (emailDetails.additionalErrorDetails != null) {
                try {
                    details.put(ADDITIONAL_ERROR_DETAILS, new JsonObject(emailDetails.additionalErrorDetails));
                } catch (Exception e) {
                    details.put(ADDITIONAL_ERROR_DETAILS, emailDetails.additionalErrorDetails);
                }
            }
        }

        data.put("details", details);
        return data;
    }
}
