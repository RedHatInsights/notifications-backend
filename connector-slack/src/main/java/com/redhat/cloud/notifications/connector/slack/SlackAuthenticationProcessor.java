package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Processes authentication for Slack connector.
 * Slack typically uses webhook URLs but can also support Bearer tokens for API access.
 */
@ApplicationScoped
public class SlackAuthenticationProcessor {

    public Uni<Void> processAuthentication(ExceptionProcessor.ProcessingContext context) {
        // Simple authentication processing for Slack
        String secretPassword = context.getAdditionalProperty("SECRET_PASSWORD", String.class);

        if (secretPassword != null) {
            // For Slack, usually Bearer token authentication
            String headerValue = "Bearer " + secretPassword;
            context.setAdditionalProperty("AUTHORIZATION_HEADER", headerValue);
        }

        return Uni.createFrom().voidItem();
    }
}
