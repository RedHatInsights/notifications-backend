package com.redhat.cloud.notifications.connector.payload;

import com.redhat.cloud.notifications.connector.Constants;
import com.redhat.cloud.notifications.connector.ExchangeProperty;
import io.vertx.core.http.HttpMethod;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

@ApplicationScoped
public class PayloadDetailsRequestPreparer implements Processor {
    /**
     * Stores the incoming message's body on an exchange property to be able to
     * modify it later with the incoming payload contents from the engine.
     * Extracts the payload's ID from the headers, if present, and set its on
     * an exchange property so that we can use it later either in the message
     * reinjection or when sending a response to the engine. It also sets the
     * HTTP headers for Camel to make the request to the engine. And finally,
     * stores the original Cloud Event from Kafka so that we can add the
     * payload's data to it in the {@link PayloadDetailsResponseProcessor}.
     * @param exchange the exchange to extract the payload's ID from.
     * @throws Exception if any unexpected error occurs.
     */
    @Override
    public void process(final Exchange exchange) throws Exception {
        final String headersContents = exchange.getIn().getHeader(Constants.X_RH_NOTIFICATIONS_CONNECTOR_PAYLOAD_ID_HEADER, String.class);

        // Set the payload's ID in the properties so that we can add it later to
        // the Kafka headers when we are either reinjecting the message or
        // sending the delivery status to the engine.
        exchange.setProperty(ExchangeProperty.PAYLOAD_ID, headersContents);

        // Prepare the request's details.
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, HttpMethod.GET);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, String.format("/internal/payloads/%s", headersContents));

        // Store the incoming Cloud Event from Kafka to not lose the payload.
        exchange.setProperty(ExchangeProperty.ORIGINAL_CLOUD_EVENT, exchange.getMessage().getBody());
    }
}
