package com.redhat.cloud.notifications.connector.webhook.authentication;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.BEARER_TOKEN;

public class BearerTokenAuthenticationProcessor implements Processor {

    public void process(Exchange exchange) {

        String token = exchange.getProperty(BEARER_TOKEN, String.class);
        if (token == null || token.isBlank()) {
            return;
        }

        exchange.getIn().setHeader("Authorization", String.format("%s %s", BEARER_TOKEN, token));
    }
}
