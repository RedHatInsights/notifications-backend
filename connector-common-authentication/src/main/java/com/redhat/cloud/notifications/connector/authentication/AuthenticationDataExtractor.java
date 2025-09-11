package com.redhat.cloud.notifications.connector.authentication;

import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Extractor for authentication data from payloads.
 * Updated for Quarkus implementation without Camel dependencies.
 */
@ApplicationScoped
public class AuthenticationDataExtractor {

    /**
     * Extract authentication data from a payload.
     * This replaces the Camel-based extract method.
     */
    public void extract(JsonObject payload, JsonObject authentication) {
        /*
         * The authentication data extraction is optional.
         * Each connector has its own logic which makes the authentication mandatory or not.
         */
        if (authentication != null) {

            // If the authentication data is available, an exception is thrown if anything is wrong with it.
            validateAuthenticationData(authentication);

            // Extract authentication type
            String authType = authentication.getString("type");
            if (authType != null) {
                payload.put("authentication_type", authType);
            }

            // Extract secret ID if present
            String secretId = authentication.getString("secret_id");
            if (secretId != null && !secretId.isEmpty()) {
                payload.put("secret_id", secretId);
            }

            // Extract other authentication properties
            String username = authentication.getString("username");
            if (username != null) {
                payload.put("secret_username", username);
            }

            String password = authentication.getString("password");
            if (password != null) {
                payload.put("secret_password", password);
            }
        }
    }

    /**
     * Validate authentication data.
     */
    private void validateAuthenticationData(JsonObject authentication) {
        if (authentication == null) {
            throw new IllegalArgumentException("Authentication data cannot be null");
        }

        String authType = authentication.getString("type");
        if (authType == null || authType.isEmpty()) {
            throw new IllegalArgumentException("Authentication type is required");
        }

        // Validate authentication type
        try {
            AuthenticationType.valueOf(authType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid authentication type: " + authType, e);
        }
    }
}

