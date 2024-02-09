package com.redhat.cloud.notifications.connector.authentication;

import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;

import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.AUTHENTICATION_TYPE;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_ID;

@ApplicationScoped
public class AuthenticationDataExtractor {

    public void extract(Exchange exchange, JsonObject authentication) {
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

            exchange.setProperty(AUTHENTICATION_TYPE, authenticationType);
            exchange.setProperty(SECRET_ID, secretId);
        }
    }
}
