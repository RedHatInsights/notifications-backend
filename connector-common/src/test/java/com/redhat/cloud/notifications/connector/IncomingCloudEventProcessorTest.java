package com.redhat.cloud.notifications.connector;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORIGINAL_CLOUD_EVENT;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.RETURN_SOURCE;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.START_TIME;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TYPE;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_DATA;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_ID;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_TYPE;

@QuarkusTest
public class IncomingCloudEventProcessorTest extends CamelQuarkusTestSupport {
    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    IncomingCloudEventProcessor incomingCloudEventProcessor;

    /**
     * Tests that the incoming Cloud Event is properly processed.
     * @throws Exception if any unexpected exception is thrown during the
     * processing of the Cloud Event.
     */
    @Test
    void processIncomingCloudEvent() throws Exception {
        // Prepare the JSON payload.
        final String cloudEventId = UUID.randomUUID().toString();
        final String cloudEventType = "cloud-event-type";

        final JsonObject incomingCloudEvent = new JsonObject();

        incomingCloudEvent.put(CLOUD_EVENT_ID, cloudEventId);
        incomingCloudEvent.put(CLOUD_EVENT_TYPE, cloudEventType);

        // Cloud Event's data.
        final String cloudEventOrgId = UUID.randomUUID().toString();
        final JsonObject cloudEventData = new JsonObject();
        cloudEventData.put("org_id", cloudEventOrgId);

        incomingCloudEvent.put(CLOUD_EVENT_DATA, cloudEventData);

        // Prepare the exchange.
        final Exchange exchange = this.createExchangeWithBody(incomingCloudEvent.encode());

        // Call the processor under test.
        this.incomingCloudEventProcessor.process(exchange);

        // Assert that the exchange contains the expected properties.
        Assertions.assertEquals(incomingCloudEvent.encode(), exchange.getProperty(ORIGINAL_CLOUD_EVENT), "the original Cloud Event was not properly set in the exchange's property");
        Assertions.assertEquals(cloudEventId, exchange.getProperty(ID), "the Cloud Event's ID was not properly extracted");
        Assertions.assertEquals(cloudEventType, exchange.getProperty(TYPE), "the Cloud Event's type was not properly extracted");
        Assertions.assertNotNull(exchange.getProperty(START_TIME), "the start time of the processing wsa not properly set in the exchange");
        Assertions.assertEquals(this.connectorConfig.getConnectorName(), exchange.getProperty(RETURN_SOURCE), "the source of the CLoud Event was not properly set in the exchange");
        Assertions.assertEquals(cloudEventOrgId, exchange.getProperty(ORG_ID), "the Cloud Event's ORG ID was not properly set in the exchange");
    }
}
