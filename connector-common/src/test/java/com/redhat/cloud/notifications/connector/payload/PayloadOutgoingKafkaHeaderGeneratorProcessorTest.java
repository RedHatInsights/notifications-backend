package com.redhat.cloud.notifications.connector.payload;

import com.redhat.cloud.notifications.connector.Constants;
import com.redhat.cloud.notifications.connector.ExchangeProperty;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

@QuarkusTest
public class PayloadOutgoingKafkaHeaderGeneratorProcessorTest extends CamelQuarkusTestSupport {
    @Inject
    PayloadOutgoingKafkaHeaderGeneratorProcessor payloadOutgoingKafkaHeaderGeneratorProcessor;

    /**
     * Tests that if the exchange contains the event ID in one of its
     * properties then the corresponding header is set to notify the engine
     * about it.
     */
    @Test
    void testProcessor() {
        // Set up teh event ID in the exchange's property.
        final String eventId = UUID.randomUUID().toString();

        final Exchange exchange = this.createExchangeWithBody("");
        exchange.setProperty(ExchangeProperty.DATABASE_PAYLOAD_EVENT_ID, eventId);

        // Call the processor under test.
        this.payloadOutgoingKafkaHeaderGeneratorProcessor.process(exchange);

        // Assert that the header was set.
        Assertions.assertEquals(eventId, exchange.getMessage().getHeader(Constants.X_RH_NOTIFICATIONS_CONNECTOR_PAYLOAD_HEADER), "unexpected event ID set in the header");
    }
}
