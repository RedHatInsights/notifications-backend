package com.redhat.cloud.notifications.connector.payload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.ExchangeProperty;
import com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

@ApplicationScoped
public class PayloadDetailsResponseProcessor implements Processor {
    @Inject
    ObjectMapper objectMapper;

    /**
     * Gets the original Cloud Event from the exchange property and modifies
     * it with the incoming event's payload from the engine's response.
     * @param exchange the exchange to process.
     * @throws Exception if any unexpected error occurs.
     */
    @Override
    public void process(final Exchange exchange) throws Exception {
        // Get the received Cloud Event from Kafka.
        final String originalPayload = exchange.getProperty(ExchangeProperty.ORIGINAL_CLOUD_EVENT, String.class);
        final JsonObject jsonPayload = new JsonObject(originalPayload);

        // Parse the incoming response from the engine.
        final PayloadDetails payloadDetails = this.objectMapper.readValue(exchange.getMessage().getBody(String.class), PayloadDetails.class);

        // Place the data object in the original Cloud Event.
        jsonPayload.put(IncomingCloudEventProcessor.CLOUD_EVENT_DATA, new JsonObject(payloadDetails.getContents()));

        // Set the original Cloud Event as the exchange's body, so that the
        // following processors can process the payload as if it all came from
        // Kafka.
        exchange.getMessage().setBody(jsonPayload.encode());

        // Clear the exchange from the temporary property we have used.
        exchange.removeProperty(ExchangeProperty.ORIGINAL_CLOUD_EVENT);
    }
}
