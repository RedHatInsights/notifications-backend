package com.redhat.cloud.notifications.connector.webhook.authentication;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import java.util.Base64;

import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.BASIC_AUTH_PASSWORD;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.BASIC_AUTH_USERNAME;
import static java.nio.charset.StandardCharsets.UTF_8;

public class BasicAuthenticationProcessor implements Processor {

    public void process(Exchange exchange) {

        String username = exchange.getProperty(BASIC_AUTH_USERNAME, String.class);
        String password = exchange.getProperty(BASIC_AUTH_PASSWORD, String.class);
        if (username == null || username.isBlank()
            || password == null || password.isBlank()) {
            return;
        }

        exchange.getIn().setHeader("Authorization", "Basic " + encodeB64(String.format("%s:%s", username, password)));
    }

    private String encodeB64(String value) {
        return new String(Base64.getEncoder().encode(value.getBytes(UTF_8)), UTF_8);
    }
}
