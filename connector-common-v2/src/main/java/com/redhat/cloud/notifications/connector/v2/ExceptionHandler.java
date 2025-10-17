package com.redhat.cloud.notifications.connector.v2;

import com.redhat.cloud.notifications.connector.v2.pojo.HandledExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.pojo.NotificationToConnector;
import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Extend this class in an {@link ApplicationScoped} bean from a connector Maven module to change the
 * behavior of the connector in case of failure while calling an external service (e.g. Slack, Splunk...).
 * If this class is not extended, then the default implementation below will be used.
 */
@DefaultBean
@ApplicationScoped
public class ExceptionHandler {

    private static final String DEFAULT_LOG_MSG = "Message sending failed: [orgId=%s, historyId=%s]";

    public HandledExceptionDetails processException(Throwable t, IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        HandledExceptionDetails processedExceptionDetails = process(t, incomingCloudEvent);
        processedExceptionDetails.outcomeMessage = t.getMessage();
        return processedExceptionDetails;
    }

    protected final void logDefault(Throwable t, IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        NotificationToConnector notificationToConnector = incomingCloudEvent.getData().mapTo(NotificationToConnector.class);
        Log.errorf(
                t,
                DEFAULT_LOG_MSG,
                notificationToConnector.getOrgId(),
                incomingCloudEvent.getId()
        );
    }

    protected HandledExceptionDetails process(Throwable t, IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        logDefault(t, incomingCloudEvent);
        return null;
    }
}
