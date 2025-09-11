package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.QuarkusConnectorBase;
import com.redhat.cloud.notifications.connector.email.dto.EmailNotification;
import com.redhat.cloud.notifications.connector.email.processors.EmailProcessor;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import java.util.concurrent.CompletionStage;


/**
 * Quarkus-based email connector that replaces the Camel-based EmailRouteBuilder.
 * This connector processes email notifications using native Quarkus components.
 */
@ApplicationScoped
public class EmailQuarkusConnector extends QuarkusConnectorBase {

    @Inject
    EmailProcessor emailProcessor;

    /**
     * Process incoming notification messages from Kafka.
     * This replaces the Camel route configuration.
     */
    @Incoming("incoming-notifications")
    @Outgoing("processed-notifications")
    public CompletionStage<Message<JsonObject>> processNotification(Message<JsonObject> message) {
        return super.processNotification(message);
    }

    @Override
    protected ProcessingResult processConnectorSpecificLogic(CloudEventData cloudEventData, JsonObject payload) {
        try {
            Log.debugf("Processing email notification for orgId=%s, historyId=%s",
                cloudEventData.getOrgId(), cloudEventData.getHistoryId());

            // Extract the data field from the CloudEvent payload
            JsonObject data = payload.getJsonObject("data");
            if (data == null) {
                throw new IllegalArgumentException("Missing 'data' field in CloudEvent payload");
            }

            EmailNotification emailNotification = data.mapTo(EmailNotification.class);

            // Check if we should use simplified email management
            return processSimplifiedEmail(emailNotification, cloudEventData);

        } catch (Exception e) {
            Log.errorf(e, "Error processing email notification for orgId=%s, historyId=%s",
                cloudEventData.getOrgId(), cloudEventData.getHistoryId());
            return ProcessingResult.failure(e);
        }
    }

    /**
     * Process email using simplified management (no recipients resolver).
     */
    private ProcessingResult processSimplifiedEmail(EmailNotification emailNotification, CloudEventData cloudEventData) {
        try {
            Log.debugf("Using simplified email processing for orgId=%s", cloudEventData.getOrgId());

            // Process the email directly
            JsonObject result = emailProcessor.processEmail(emailNotification);

            // Check if the email processing was successful
            boolean success = result.getBoolean("success", false);
            if (success) {
                return ProcessingResult.success(result);
            } else {
                // Create a failure exception with the error message
                String errorMessage = result.getString("error", "Email processing failed");
                int statusCode = result.getInteger("statusCode", 500);
                RuntimeException failureException = new RuntimeException(
                    String.format("Email processing failed (status %d): %s", statusCode, errorMessage));
                return ProcessingResult.failure(failureException);
            }

        } catch (Exception e) {
            Log.errorf(e, "Error in simplified email processing for orgId=%s", cloudEventData.getOrgId());
            return ProcessingResult.failure(e);
        }
    }
}

