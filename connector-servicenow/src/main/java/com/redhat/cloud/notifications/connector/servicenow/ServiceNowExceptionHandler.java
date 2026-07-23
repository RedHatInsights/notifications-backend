package com.redhat.cloud.notifications.connector.servicenow;

import com.redhat.cloud.notifications.connector.v2.http.HttpExceptionHandler;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpExceptionDetails;
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
    protected HandledHttpExceptionDetails process(Throwable t, IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        HandledHttpExceptionDetails details = super.process(t, incomingCloudEvent);
        JsonObject data = incomingCloudEvent.getData();
        if (data != null) {
            Object metadataObj = data.getValue(ServiceNowMessageHandler.NOTIF_METADATA);
            if (metadataObj instanceof JsonObject metadata) {
                details.targetUrl = metadata.getString(URL_KEY);
            }
        }
        return details;
    }
}
