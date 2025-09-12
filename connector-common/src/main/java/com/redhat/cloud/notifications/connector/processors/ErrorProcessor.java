package com.redhat.cloud.notifications.connector.processors;

import com.redhat.cloud.notifications.connector.QuarkusConnectorBase.CloudEventData;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * Processor for handling errors in connector processing.
 * Replaces the Camel-based error handling logic.
 */
@ApplicationScoped
public class ErrorProcessor {

    /**
     * Process an error that occurred during connector processing.
     */
    public void processError(CloudEventData cloudEventData, Throwable error, Message<JsonObject> message) {
        if (cloudEventData != null) {
            Log.errorf(error, "Error processing notification for orgId=%s, historyId=%s, connector=%s",
                cloudEventData.getOrgId(), cloudEventData.getHistoryId(), cloudEventData.getConnector());
        } else {
            Log.errorf(error, "Error processing notification with unknown context");
        }

        // In a real implementation, you might want to:
        // 1. Update notification history with error details
        // 2. Send error metrics
        // 3. Log to external systems
        // 4. Trigger alerts if needed
    }
}

