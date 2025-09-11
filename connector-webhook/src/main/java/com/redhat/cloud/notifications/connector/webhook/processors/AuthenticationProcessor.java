package com.redhat.cloud.notifications.connector.webhook.processors;

import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Processor for handling webhook authentication.
 */
@ApplicationScoped
public class AuthenticationProcessor {

    /**
     * Process authentication for webhook requests.
     *
     * @param payload the webhook payload
     * @return the processed payload with authentication headers
     */
    public JsonObject processAuthentication(JsonObject payload) {
        if (payload == null) {
            return new JsonObject();
        }

        JsonObject authentication = payload.getJsonObject("authentication");
        if (authentication == null) {
            return payload;
        }

        String authType = authentication.getString("type");
        if (AuthenticationType.SECRET_TOKEN.name().equals(authType)) {
            // Handle secret token authentication
            String secretId = authentication.getString("secretId");
            if (secretId != null) {
                // In a real implementation, you would load the secret and add it to headers
                payload.put("_auth_secret_id", secretId);
            }
        } else if (AuthenticationType.BEARER.name().equals(authType)) {
            // Handle bearer token authentication
            String secretId = authentication.getString("secretId");
            if (secretId != null) {
                // In a real implementation, you would load the secret and add it to headers
                payload.put("_auth_bearer_secret_id", secretId);
            }
        }

        return payload;
    }
}


