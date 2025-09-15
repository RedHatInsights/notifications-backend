package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Processes authentication for Webhook connector.
 * Supports Bearer tokens and other authentication methods for webhook calls.
 */
@ApplicationScoped
public class WebhookAuthenticationProcessor {

    public Uni<Void> processAuthentication(ExceptionProcessor.ProcessingContext context) {
        AuthenticationType authType = context.getAdditionalProperty("AUTHENTICATION_TYPE", AuthenticationType.class);

        if (authType != null) {
            String secretPassword = context.getAdditionalProperty("SECRET_PASSWORD", String.class);

            switch (authType) {
                case BEARER -> {
                    if (secretPassword != null) {
                        String headerValue = "Bearer " + secretPassword;
                        context.setAdditionalProperty("AUTHORIZATION_HEADER", headerValue);
                    }
                }
                case SECRET_TOKEN -> {
                    if (secretPassword != null) {
                        // For SECRET_TOKEN, we'll use it as-is as a custom header or basic auth
                        // This could be customized based on specific webhook requirements
                        context.setAdditionalProperty("AUTHORIZATION_HEADER", secretPassword);
                    }
                }
                default -> {
                    throw new IllegalStateException("Unexpected authentication type: " + authType);
                }
            }
        }

        return Uni.createFrom().voidItem();
    }
}
