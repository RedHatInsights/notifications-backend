package com.redhat.cloud.notifications.connector.payload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.ExchangeProperty;
import com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class PayloadDetailsResponseProcessorTest extends CamelQuarkusTestSupport {
    @Inject
    ObjectMapper objectMapper;

    @Inject
    PayloadDetailsResponseProcessor payloadDetailsResponseProcessor;

    /**
     * Test that the processor injects the fetched payload from the engine in
     * the original cloud event, and that the latter is removed from the
     * exchange properties.
     * @throws Exception if any unexpected error occurs.
     */
    @Test
    void testProcess() throws Exception {
        // Create a dummy Cloud Event with an empty data object to simulate
        // that we have received that from the engine.
        final JsonObject dummyCloudEvent = new JsonObject();
        dummyCloudEvent.put("one_field", "sample-value");
        dummyCloudEvent.put("another_field", "another-value");
        dummyCloudEvent.put(IncomingCloudEventProcessor.CLOUD_EVENT_DATA, new JsonObject());

        // Simulate that the following payload was received from the engine.
        final JsonObject engineResponseJson = new JsonObject();
        engineResponseJson.put("engine_response_field", "test");
        engineResponseJson.put("engine_response_another_field", "test-another");

        final PayloadDetails payloadDetails = new PayloadDetails(engineResponseJson.encode());
        final String engineResponsePayload = this.objectMapper.writeValueAsString(payloadDetails);

        // Prepare the exchange with all of the above.
        final Exchange exchange = this.createExchangeWithBody(engineResponsePayload);
        exchange.setProperty(ExchangeProperty.ORIGINAL_CLOUD_EVENT, dummyCloudEvent.encode());

        // Call the processor under test.
        this.payloadDetailsResponseProcessor.process(exchange);

        // Assert that the original Cloud Event keeps all its values and that
        // the data element was replaced with the engine's payload.
        final String processedBody = exchange.getMessage().getBody(String.class);
        final JsonObject processedBodyJson = new JsonObject(processedBody);

        Assertions.assertEquals(dummyCloudEvent.getString("one_field"), processedBodyJson.getString("one_field"));
        Assertions.assertEquals(dummyCloudEvent.getString("another_field"), processedBodyJson.getString("another_field"));

        // Assert that the "data" object was created.
        final JsonObject data = processedBodyJson.getJsonObject(IncomingCloudEventProcessor.CLOUD_EVENT_DATA);
        Assertions.assertEquals("test", data.getString("engine_response_field"));
        Assertions.assertEquals("test-another", data.getString("engine_response_another_field"));

        // Assert that the exchange property containing the original cloud
        // event has been removed.
        Assertions.assertFalse(exchange.getProperties().containsKey(ExchangeProperty.ORIGINAL_CLOUD_EVENT), "the original Cloud Event property was not removed from the exchange");
    }
}
