package com.redhat.cloud.notifications.connector.http;

import com.redhat.cloud.notifications.connector.ConnectorProcessor;
import com.redhat.cloud.notifications.connector.OutgoingCloudEventBuilder;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.connector.http.HttpErrorType.HTTP_3XX;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.HTTP_4XX;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.HTTP_5XX;

/**
 * HTTP-specific outgoing CloudEvent builder that extends the base builder
 * to include HTTP error information.
 * This is the new version that replaces the Camel-based HttpOutgoingCloudEventBuilder.
 */
@ApplicationScoped
public class HttpOutgoingCloudEventBuilder extends OutgoingCloudEventBuilder {

    @Override
    public JsonObject build(ConnectorProcessor.ConnectorResult result) {
        JsonObject cloudEvent = super.build(result);

        // Add HTTP-specific error information if available
        addHttpErrorInfo(cloudEvent, result);

        return cloudEvent;
    }

    public JsonObject build(
            String id,
            String orgId,
            boolean successful,
            String outcome,
            HttpErrorType httpErrorType,
            Integer httpStatusCode,
            int deliveryAttempts) {

        JsonObject details = new JsonObject();
        details.put("outcome", outcome);

        // Add HTTP error information
        if (httpErrorType != null) {
            JsonObject error = new JsonObject();
            error.put("error_type", httpErrorType.toString());
            error.put("delivery_attempts", deliveryAttempts);

            if (httpStatusCode != null
                && (httpErrorType == HTTP_3XX || httpErrorType == HTTP_4XX || httpErrorType == HTTP_5XX)) {
                error.put("http_status_code", httpStatusCode);
            }

            details.put("error", error);
        }

        return super.build(id, orgId, successful, outcome, details);
    }

    private void addHttpErrorInfo(JsonObject cloudEvent, ConnectorProcessor.ConnectorResult result) {
        // Extract HTTP error information from the processing context if available
        JsonObject originalCloudEvent = result.getOriginalCloudEvent();
        if (originalCloudEvent != null) {
            // Check if HTTP error information was stored during processing
            JsonObject data = cloudEvent.getJsonObject("data");
            if (data != null) {
                String dataStr = data.getString("data");
                if (dataStr != null) {
                    try {
                        JsonObject parsedData = new JsonObject(dataStr);
                        JsonObject details = parsedData.getJsonObject("details");
                        if (details != null && details.containsKey("error")) {
                            // HTTP error info already included
                            return;
                        }
                    } catch (Exception e) {
                        // Ignore parsing errors
                    }
                }
            }
        }
    }
}
