package com.redhat.cloud.notifications.connector.teams;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Processes authentication for Microsoft Teams connector.
 * Teams typically uses webhook URLs but can also support Bearer tokens for bot authentication.
 */
@ApplicationScoped
public class TeamsAuthenticationProcessor {

    public Uni<Void> processAuthentication(ExceptionProcessor.ProcessingContext context) {
        // Simple authentication processing for Teams
        String secretPassword = context.getAdditionalProperty("SECRET_PASSWORD", String.class);

        if (secretPassword != null) {
            // For Teams, usually Bearer token authentication
            String headerValue = "Bearer " + secretPassword;
            context.setAdditionalProperty("AUTHORIZATION_HEADER", headerValue);
        }

        return Uni.createFrom().voidItem();
    }
}
