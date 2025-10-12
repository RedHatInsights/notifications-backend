package com.redhat.cloud.notifications.connector.v2.http;

import com.redhat.cloud.notifications.connector.v2.MessageContext;
import com.redhat.cloud.notifications.connector.v2.OutgoingCloudEventBuilder;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Message;

import static com.redhat.cloud.notifications.connector.v2.http.CommonHttpConstants.HTTP_ERROR_TYPE;
import static com.redhat.cloud.notifications.connector.v2.http.CommonHttpConstants.HTTP_STATUS_CODE;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.HTTP_3XX;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.HTTP_4XX;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.HTTP_5XX;

@ApplicationScoped
public class HttpOutgoingCloudEventBuilder extends OutgoingCloudEventBuilder {

    @Override
    public Message<String> build(MessageContext context) throws Exception {
        Message<String> cloudEventMessage = super.build(context);

        HttpErrorType httpErrorType = context.getProperty(HTTP_ERROR_TYPE, HttpErrorType.class);
        if (httpErrorType != null) {
            // Extract the current payload and add error information
            JsonObject data = new JsonObject(cloudEventMessage.getPayload());
            JsonObject error = new JsonObject();
            error.put("error_type", httpErrorType);
            if (httpErrorType == HTTP_3XX || httpErrorType == HTTP_4XX || httpErrorType == HTTP_5XX) {
                error.put("http_status_code", context.getProperty(HTTP_STATUS_CODE));
            }
            data.put("error", error);

            // Create a new message with the updated payload, preserving the CloudEvent metadata
            return cloudEventMessage.withPayload(data.encode());
        }

        return cloudEventMessage;
    }
}
