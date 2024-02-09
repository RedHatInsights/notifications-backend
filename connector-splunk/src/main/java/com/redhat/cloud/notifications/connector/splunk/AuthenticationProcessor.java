package com.redhat.cloud.notifications.connector.splunk;

import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.AUTHENTICATION_TYPE;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_PASSWORD;

@ApplicationScoped
public class AuthenticationProcessor implements Processor {

    @Override
    public void process(Exchange exchange) {

        // TODO: Is it possible to send a request to Splunk with no authentication? If not, make the secret mandatory everywhere (frontend and backend) and throw an exception here.

        AuthenticationType authType = exchange.getProperty(AUTHENTICATION_TYPE, AuthenticationType.class);
        if (authType != null) {

            String secretPassword = exchange.getProperty(SECRET_PASSWORD, String.class);

            switch (authType) {
                case BASIC -> {
                    throw new IllegalStateException("Unsupported authentication type: BASIC");
                }
                case BEARER -> {
                    throw new IllegalStateException("Unsupported authentication type: BEARER");
                }
                case SECRET_TOKEN -> {
                    if (secretPassword != null) {
                        String headerValue = "Splunk " + secretPassword;
                        exchange.getIn().setHeader("Authorization", headerValue);
                    }
                }
                default -> {
                    throw new IllegalStateException("Unexpected authentication type: " + authType);
                }
            }
        }
    }
}
