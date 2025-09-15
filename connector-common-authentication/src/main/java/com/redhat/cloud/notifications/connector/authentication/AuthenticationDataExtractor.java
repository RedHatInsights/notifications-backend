package com.redhat.cloud.notifications.connector.authentication;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Extracts authentication data from CloudEvent data and stores it in the processing context.
 * This is the new version that replaces the Camel-based AuthenticationDataExtractor.
 */
@ApplicationScoped
public class AuthenticationDataExtractor {

    public void extract(ExceptionProcessor.ProcessingContext context, JsonObject authentication) {
        /*
         * The authentication data extraction is optional.
         * Each connector has its own logic which makes the authentication mandatory or not.
         */
        if (authentication != null) {

            // If the authentication data is available, an exception is thrown if anything is wrong with it.

            String type = authentication.getString("type");
            if (type == null) {
                throw new IllegalStateException("Invalid payload: the authentication type is missing");
            }

            AuthenticationType authenticationType;
            try {
                authenticationType = AuthenticationType.valueOf(type);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid payload: the authentication type is unknown (" + type + ")", e);
            }

            Long secretId = authentication.getLong("secretId");
            if (secretId == null) {
                throw new IllegalStateException("Invalid payload: the secret ID is missing");
            }

            context.setAdditionalProperty("AUTHENTICATION_TYPE", authenticationType);
            context.setAdditionalProperty("SECRET_ID", secretId);
        }
    }
}
