package com.redhat.cloud.notifications.connector.webhook.authentication;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.INSIGHT_TOKEN_HEADER;

public class InsightsTokenAuthenticationProcessor implements Processor {

    public void process(Exchange exchange) {

        String token = exchange.getProperty(INSIGHT_TOKEN_HEADER, String.class);
        if (token == null || token.isBlank()) {
            return;
        }

        exchange.getIn().setHeader(INSIGHT_TOKEN_HEADER, token);
    }
}
