package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.Base64;

import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.AUTHENTICATION_TYPE;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_PASSWORD;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_USERNAME;
import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class AuthenticationProcessor implements Processor {

    public static final String X_INSIGHT_TOKEN_HEADER = "X-Insight-Token";

    @Override
    public void process(Exchange exchange) {

        AuthenticationType authType = exchange.getProperty(AUTHENTICATION_TYPE, AuthenticationType.class);
        if (authType != null) {

            String secretUsername = exchange.getProperty(SECRET_USERNAME, String.class);
            String secretPassword = exchange.getProperty(SECRET_PASSWORD, String.class);

            switch (authType) {
                case BASIC -> {
                    if (secretUsername != null && secretPassword != null) {
                        String headerValue = "Basic " + encodeB64(String.format("%s:%s", secretUsername, secretPassword));
                        exchange.getIn().setHeader("Authorization", headerValue);
                    }
                }
                case BEARER -> {
                    if (secretPassword != null) {
                        String headerValue = "Bearer " + secretPassword;
                        exchange.getIn().setHeader("Authorization", headerValue);
                    }
                }
                case SECRET_TOKEN -> {
                    if (secretPassword != null) {
                        exchange.getIn().setHeader(X_INSIGHT_TOKEN_HEADER, secretPassword);
                    }
                }
                default -> {
                    throw new IllegalStateException("Unexpected authentication type: " + authType);
                }
            }
        }
    }

    private static String encodeB64(String value) {
        return new String(Base64.getEncoder().encode(value.getBytes(UTF_8)), UTF_8);
    }
}
