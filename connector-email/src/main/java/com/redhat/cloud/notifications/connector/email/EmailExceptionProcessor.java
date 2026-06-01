package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.model.HandledEmailExceptionDetails;
import com.redhat.cloud.notifications.connector.email.payload.PayloadDetails;
import com.redhat.cloud.notifications.connector.v2.http.HttpExceptionHandler;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpExceptionDetails;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@ApplicationScoped
@Alternative
@Priority(0) // The value doesn't matter.
public class EmailExceptionProcessor extends HttpExceptionHandler {

    @Override
    protected HandledHttpExceptionDetails process(Throwable t, IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        HandledHttpExceptionDetails processedExceptionDetails = super.process(t, incomingCloudEvent);
        HandledEmailExceptionDetails emailDetails = new HandledEmailExceptionDetails(processedExceptionDetails);

        // add payload ID if exists (used to retrieve large payloads from database trough engine)
        JsonObject data = incomingCloudEvent.getData();
        emailDetails.payloadId = data != null ? data.getString(PayloadDetails.PAYLOAD_DETAILS_ID_KEY) : null;
        return emailDetails;
    }
}
