package com.redhat.cloud.notifications.connector.payload;

import com.redhat.cloud.notifications.connector.Constants;
import com.redhat.cloud.notifications.connector.ExchangeProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

@ApplicationScoped
public class PayloadOutgoingKafkaHeaderGeneratorProcessor implements Processor {
    /**
     * Appends a Kafka header with the database payload's event's ID to signal
     * the engine that it should delete it.
     * @param exchange the exchange to update.
     */
    @Override
    public void process(final Exchange exchange) {
        final String eventId = exchange.getProperty(ExchangeProperty.DATABASE_PAYLOAD_EVENT_ID, String.class);

        if (eventId != null && !eventId.isBlank()) {
            exchange.getMessage().setHeader(Constants.X_RH_NOTIFICATIONS_CONNECTOR_PAYLOAD_HEADER, eventId);
        }
    }
}
