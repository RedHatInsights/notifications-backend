package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.pagerduty.config.PagerDutyConnectorConfig;
import com.redhat.cloud.notifications.connector.v2.http.HttpExceptionHandler;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledExceptionDetails;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

@ApplicationScoped
@Alternative
@Priority(0)
public class PagerDutyExceptionHandler extends HttpExceptionHandler {

    @Inject
    PagerDutyConnectorConfig connectorConfig;

    @Override
    protected HandledExceptionDetails process(Throwable t, IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        HandledExceptionDetails details = super.process(t, incomingCloudEvent);
        if (details instanceof HandledHttpExceptionDetails httpDetails) {
            httpDetails.targetUrl = connectorConfig.getPagerDutyUrl();
        }
        return details;
    }
}
