package com.redhat.cloud.notifications.connector.authentication.v2;

import io.vertx.core.json.JsonObject;
import java.util.Optional;

public class AuthenticationDataExtractor {

    public static Optional<AuthenticationRequest> extract(JsonObject payload) {
        if (payload == null || !payload.containsKey("authentication") || payload.getJsonObject("authentication") == null) {
            return Optional.empty();
        }
        final JsonObject authentication = payload.getJsonObject("authentication");
        /*
         * The authentication data extraction is optional.
         * Each connector has its own logic which makes the authentication mandatory or not.
         */

        // If the authentication data is available, an exception is thrown if anything is wrong with it.
        String type = authentication.getString("type");
        if (type == null) {
            throw new IllegalStateException("Invalid payload: the authentication type is missing");
        }

        final AuthenticationRequest secretRequest = new AuthenticationRequest();
        try {
            secretRequest.authenticationType = AuthenticationType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid payload: the authentication type is unknown (" + type + ")", e);
        }

        secretRequest.secretId = authentication.getLong("secretId");
        if (secretRequest.secretId == null) {
            throw new IllegalStateException("Invalid payload: the secret ID is missing");
        }
        return Optional.of(secretRequest);
    }
}
