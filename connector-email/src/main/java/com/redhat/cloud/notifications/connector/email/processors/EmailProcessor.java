package com.redhat.cloud.notifications.connector.email.processors;

import com.redhat.cloud.notifications.connector.email.clients.BOPClient;
import com.redhat.cloud.notifications.connector.email.dto.BOPRequest;
import com.redhat.cloud.notifications.connector.email.dto.BOPResponse;
import com.redhat.cloud.notifications.connector.email.dto.EmailNotification;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

/**
 * Processor for handling email notifications.
 * Replaces the Camel-based email processing logic.
 */
@ApplicationScoped
public class EmailProcessor {

    @Inject
    @RestClient
    BOPClient bopClient;

    /**
     * Process an email notification by sending it to the BOP service.
     */
    public JsonObject processEmail(EmailNotification emailNotification) {
        try {
            Log.debugf("Processing email notification for orgId=%s", emailNotification.getOrgId());

            // Build BOP request
            BOPRequest bopRequest = buildBOPRequest(emailNotification);

            // Send email via BOP
            BOPResponse bopResponse = bopClient.sendEmail(bopRequest);

            // Process response
            if (bopResponse.isSuccess()) {
                Log.infof("Email sent successfully for orgId=%s, messageId=%s",
                    emailNotification.getOrgId(), bopResponse.getMessageId());

                return new JsonObject()
                    .put("success", true)
                    .put("messageId", bopResponse.getMessageId())
                    .put("statusCode", bopResponse.getStatusCode());
            } else {
                Log.errorf("Failed to send email for orgId=%s: %s",
                    emailNotification.getOrgId(), bopResponse.getErrorMessage());

                return new JsonObject()
                    .put("success", false)
                    .put("error", bopResponse.getErrorMessage())
                    .put("statusCode", bopResponse.getStatusCode());
            }

        } catch (Exception e) {
            Log.errorf(e, "Error processing email for orgId=%s", emailNotification.getOrgId());
            throw new RuntimeException("Failed to process email notification", e);
        }
    }

    /**
     * Build BOP request from email notification.
     */
    private BOPRequest buildBOPRequest(EmailNotification emailNotification) {
        return new BOPRequest(
            emailNotification.getSubject(),
            emailNotification.getBody(),
            emailNotification.getSender(),
            emailNotification.getOrgId(),
            List.copyOf(emailNotification.getRecipients()),
            emailNotification.getEventType()
        );
    }
}


