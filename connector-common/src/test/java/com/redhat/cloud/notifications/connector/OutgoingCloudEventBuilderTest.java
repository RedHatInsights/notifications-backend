package com.redhat.cloud.notifications.connector;

import com.redhat.cloud.notifications.connector.payload.PayloadDetails;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

@QuarkusTest
public class OutgoingCloudEventBuilderTest extends CamelQuarkusTestSupport {
    @Inject
    OutgoingCloudEventBuilder outgoingCloudEventBuilder;

    /**
     * Tests that when the exchange contains the payload identifier's property,
     * the processor under test adds it to the outgoing payload and that
     * removes the property from the exchange.
     *
     * @throws Exception if any unexpected error occurs.
     */
    @Test
    void testOutgoingCloudEventContainsPayloadId() throws Exception {
        // Prepare the exchange with the minimal elements on it.
        final String payloadId = UUID.randomUUID().toString();

        final Exchange exchange = this.createExchangeWithBody("");
        exchange.setProperty(ExchangeProperty.PAYLOAD_ID, payloadId);
        exchange.setProperty(ExchangeProperty.START_TIME, System.currentTimeMillis());

        // Call the processor under test.
        this.outgoingCloudEventBuilder.process(exchange);

        // Assert that the property got removed.
        Assertions.assertNull(exchange.getProperty(PayloadDetails.PAYLOAD_DETAILS_ID_KEY), "the property with the payload detail's identifier should have been removed");

        // Assert that the body contains the payload's identifier for the
        // engine.
        final JsonObject body = new JsonObject(exchange.getMessage().getBody(String.class));
        final JsonObject data = new JsonObject(body.getString("data"));

        Assertions.assertEquals(payloadId, data.getString(PayloadDetails.PAYLOAD_DETAILS_ID_KEY));
    }
}
