package com.redhat.cloud.notifications.connector.google.chat;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Processes authentication for Google Chat connector.
 * Google Chat typically uses webhook URLs but can also support Bearer tokens for bot authentication.
 */
@ApplicationScoped
public class GoogleChatAuthenticationProcessor {

    public Uni<Void> processAuthentication(ExceptionProcessor.ProcessingContext context) {
        // Simple authentication processing for Google Chat
        String secretPassword = context.getAdditionalProperty("SECRET_PASSWORD", String.class);

        if (secretPassword != null) {
            // For Google Chat, usually Bearer token authentication
            String headerValue = "Bearer " + secretPassword;
            context.setAdditionalProperty("AUTHORIZATION_HEADER", headerValue);
        }

        return Uni.createFrom().voidItem();
    }
}
