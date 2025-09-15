package com.redhat.cloud.notifications.connector.splunk;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Processes authentication for Splunk connector.
 * This is the new version that replaces the Camel-based AuthenticationProcessor.
 */
@ApplicationScoped
public class AuthenticationProcessor {

    public Uni<Void> processAuthentication(ExceptionProcessor.ProcessingContext context) {

        // TODO: Is it possible to send a request to Splunk with no authentication?
        // If not, make the secret mandatory everywhere (frontend and backend) and throw an exception here.

        AuthenticationType authType = context.getAdditionalProperty("AUTHENTICATION_TYPE", AuthenticationType.class);
        if (authType != null) {

            String secretPassword = context.getAdditionalProperty("SECRET_PASSWORD", String.class);

            switch (authType) {
                case BEARER -> {
                    throw new IllegalStateException("Unsupported authentication type: BEARER");
                }
                case SECRET_TOKEN -> {
                    if (secretPassword != null) {
                        String headerValue = "Splunk " + secretPassword;
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
