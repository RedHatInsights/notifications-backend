package com.redhat.cloud.notifications.connector.slack;

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
public class SlackExceptionHandler extends HttpExceptionHandler {

    @Override
    protected HandledHttpExceptionDetails process(Throwable t, IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        HandledHttpExceptionDetails details = super.process(t, incomingCloudEvent);
        try {
            details.targetUrl = incomingCloudEvent.getData().getString("webhookUrl");
        } catch (Exception e) {
            Log.debugf(e, "Failed to extract target URL from notification during exception handling [historyId=%s]", incomingCloudEvent.getId());
        }
        return details;
    }
}
