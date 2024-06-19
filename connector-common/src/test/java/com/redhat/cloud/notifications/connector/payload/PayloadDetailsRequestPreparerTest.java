package com.redhat.cloud.notifications.connector.payload;

import com.redhat.cloud.notifications.connector.Constants;
import com.redhat.cloud.notifications.connector.ExchangeProperty;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.http.HttpMethod;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

@QuarkusTest
public class PayloadDetailsRequestPreparerTest extends CamelQuarkusTestSupport {
    @Inject
    PayloadDetailsRequestPreparer payloadDetailsRequestPreparer;

    /**
     * Tests that the processor adds the event's ID and the original Cloud
     * Event as properties and that sets up the headers for the request.
     * @throws Exception if any unexpected error occurs.
     */
    @Test
    void testProcessor() throws Exception {
        // Prepare the exchange with what the processor expects.
        final String testCloudEvent = "this is supossed to be a Cloud Event";
        final String eventId = UUID.randomUUID().toString();

        final Exchange exchange = this.createExchangeWithBody(testCloudEvent);

        exchange.getMessage().setHeader(Constants.X_RH_NOTIFICATIONS_CONNECTOR_PAYLOAD_ID_HEADER, eventId);

        // Call the processor under test.
        this.payloadDetailsRequestPreparer.process(exchange);

        // Assert that the correct properties were set.
        Assertions.assertEquals(eventId, exchange.getProperty(ExchangeProperty.PAYLOAD_ID, String.class), "the payload ID was not properly set in the exchange");
        Assertions.assertEquals(testCloudEvent, exchange.getProperty(ExchangeProperty.ORIGINAL_CLOUD_EVENT, String.class), "the Cloud Event was not properly stored in the exchange");

        // Assert that the HTTP headers were set for the request.
        Assertions.assertEquals(HttpMethod.GET, exchange.getMessage().getHeader(Exchange.HTTP_METHOD), "the proper method was not set in the exchange to perform the request to the engine");
        Assertions.assertEquals(String.format("/internal/payloads/%s", eventId), exchange.getMessage().getHeader(Exchange.HTTP_PATH), "the proper path was not set in the exchange to perform the request to the engine");
    }
}
