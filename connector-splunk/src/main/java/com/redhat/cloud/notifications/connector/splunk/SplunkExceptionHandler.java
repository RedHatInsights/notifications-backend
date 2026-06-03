package com.redhat.cloud.notifications.connector.splunk;

import com.redhat.cloud.notifications.connector.v2.http.HttpExceptionHandler;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpExceptionDetails;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@ApplicationScoped
@Alternative
@Priority(0)
public class SplunkExceptionHandler extends HttpExceptionHandler {

    @Override
    protected HandledHttpExceptionDetails process(Throwable t, IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        HandledHttpExceptionDetails details = super.process(t, incomingCloudEvent);
        try {
            JsonObject metadata = incomingCloudEvent.getData().getJsonObject(SplunkMessageHandler.NOTIF_METADATA);
            if (metadata != null) {
                details.targetUrl = metadata.getString(SplunkMessageHandler.URL_KEY);
            }
        } catch (ClassCastException | NullPointerException e) {
            Log.debugf(e, "Failed to extract target URL from notification during exception handling [historyId=%s]", incomingCloudEvent.getId());
        }
        return details;
    }
}
