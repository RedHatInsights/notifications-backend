package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.AUTHENTICATION_TYPE;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_PASSWORD;

@ApplicationScoped
public class AuthenticationProcessor implements Processor {

    private static final String ROUTING_KEY = "routing_key";

    @Override
    public void process(Exchange exchange) {
        AuthenticationType authType = exchange.getProperty(AUTHENTICATION_TYPE, AuthenticationType.class);
        if (authType != null) {

            String secretPassword = exchange.getProperty(SECRET_PASSWORD, String.class);

            switch (authType) {
                case BASIC -> throw new IllegalStateException("Unsupported authentication type: BASIC");
                case BEARER -> throw new IllegalStateException("Unsupported authentication type: BEARER");
                case SECRET_TOKEN -> {
                    if (secretPassword != null) {
                        JsonObject message = exchange.getIn().getBody(JsonObject.class);
                        message.put(ROUTING_KEY, secretPassword);
                        exchange.getIn().setBody(message);
                    }
                }
                default -> throw new IllegalStateException("Unexpected authentication type: " + authType);
            }
        }
    }
}
