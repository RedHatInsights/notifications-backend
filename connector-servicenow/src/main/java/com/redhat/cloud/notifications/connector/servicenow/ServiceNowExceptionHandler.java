package com.redhat.cloud.notifications.connector.servicenow;

import com.redhat.cloud.notifications.connector.v2.http.HttpExceptionHandler;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledExceptionDetails;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import static com.redhat.cloud.notifications.connector.servicenow.ServiceNowNotification.URL_KEY;

@ApplicationScoped
@Alternative
@Priority(0)
public class ServiceNowExceptionHandler extends HttpExceptionHandler {

    @Override
    protected HandledExceptionDetails process(Throwable t, IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        HandledExceptionDetails details = super.process(t, incomingCloudEvent);
        if (details instanceof HandledHttpExceptionDetails httpDetails) {
            JsonObject data = incomingCloudEvent.getData();
            if (data != null) {
                Object metadataObj = data.getValue(ServiceNowMessageHandler.NOTIF_METADATA);
                if (metadataObj instanceof JsonObject metadata) {
                    httpDetails.targetUrl = metadata.getString(URL_KEY);
                }
            }
        }
        return details;
    }
}
