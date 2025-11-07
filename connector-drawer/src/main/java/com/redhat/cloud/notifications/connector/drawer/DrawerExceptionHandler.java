package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.drawer.models.HandledDrawerExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.http.HttpExceptionHandler;
import com.redhat.cloud.notifications.connector.v2.models.HandledExceptionDetails;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

@ApplicationScoped
@Alternative
@Priority(0) // The value doesn't matter.
public class DrawerExceptionHandler extends HttpExceptionHandler {

    @Override
    protected HandledExceptionDetails process(Throwable t, IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        HandledExceptionDetails processedExceptionDetails = super.process(t, incomingCloudEvent);
        HandledDrawerExceptionDetails processedDrawerExceptionDetails = new HandledDrawerExceptionDetails(processedExceptionDetails);
        if (t instanceof ClientWebApplicationException e) {
            processedDrawerExceptionDetails.additionalErrorDetails = e.getResponse().readEntity(String.class);
        }
        return processedDrawerExceptionDetails;
    }
}
