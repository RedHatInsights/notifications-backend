package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.payload.PayloadDetails;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.START_TIME;
import static org.apache.camel.test.junit5.TestSupport.createExchangeWithBody;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
public class CloudEventHistoryBuilderTest extends CamelQuarkusTestSupport {

    @Inject
    CloudEventHistoryBuilder cloudEventHistoryBuilder;

    /**
     * Tests that when the exchange contains the payload identifier property,
     * the processor adds it to the outgoing cloud event data and removes
     * the property from the exchange.
     */
    @Test
    void testPayloadIdHandling() throws Exception {

        String payloadId = "123";

        Exchange exchange = createExchangeWithBody(context, "");
        exchange.setProperty(START_TIME, System.currentTimeMillis());
        exchange.setProperty(ExchangeProperty.PAYLOAD_ID, payloadId);

        cloudEventHistoryBuilder.process(exchange);

        assertNull(exchange.getProperty(ExchangeProperty.PAYLOAD_ID), "The payload ID property should have been removed from the exchange");

        Message in = exchange.getIn();
        JsonObject cloudEvent = new JsonObject(in.getBody(String.class));
        JsonObject data = new JsonObject(cloudEvent.getString("data"));
        assertEquals(payloadId, data.getString(PayloadDetails.PAYLOAD_DETAILS_ID_KEY), "The payload ID should have been added to the cloud event data");
    }
}
