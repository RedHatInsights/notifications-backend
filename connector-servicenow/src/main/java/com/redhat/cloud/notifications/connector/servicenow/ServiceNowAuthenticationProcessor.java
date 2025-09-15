package com.redhat.cloud.notifications.connector.servicenow;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Base64;

/**
 * Processes authentication for ServiceNow connector.
 * ServiceNow typically uses basic authentication or OAuth Bearer tokens.
 */
@ApplicationScoped
public class ServiceNowAuthenticationProcessor {

    public Uni<Void> processAuthentication(ExceptionProcessor.ProcessingContext context) {
        AuthenticationType authType = context.getAdditionalProperty("AUTHENTICATION_TYPE", AuthenticationType.class);

        if (authType != null) {
            String secretPassword = context.getAdditionalProperty("SECRET_PASSWORD", String.class);
            String secretUsername = context.getAdditionalProperty("SECRET_USERNAME", String.class);

            switch (authType) {
                case BEARER -> {
                    if (secretPassword != null) {
                        String headerValue = "Bearer " + secretPassword;
                        context.setAdditionalProperty("AUTHORIZATION_HEADER", headerValue);
                    }
                }
                case SECRET_TOKEN -> {
                    // For ServiceNow, SECRET_TOKEN might be used for basic auth or OAuth
                    if (secretUsername != null && secretPassword != null) {
                        // Basic authentication
                        String credentials = secretUsername + ":" + secretPassword;
                        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
                        String headerValue = "Basic " + encodedCredentials;
                        context.setAdditionalProperty("AUTHORIZATION_HEADER", headerValue);
                    } else if (secretPassword != null) {
                        // Treat as Bearer token if only password/token is provided
                        String headerValue = "Bearer " + secretPassword;
                        context.setAdditionalProperty("AUTHORIZATION_HEADER", headerValue);
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
