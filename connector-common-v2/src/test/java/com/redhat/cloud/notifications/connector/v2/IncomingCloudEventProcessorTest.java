package com.redhat.cloud.notifications.connector.v2;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.redhat.cloud.notifications.connector.v2.ExchangeProperty.ENDPOINT_ID;
import static com.redhat.cloud.notifications.connector.v2.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.v2.ExchangeProperty.RETURN_SOURCE;
import static com.redhat.cloud.notifications.connector.v2.ExchangeProperty.START_TIME;
import static com.redhat.cloud.notifications.connector.v2.IncomingCloudEventProcessor.CLOUD_EVENT_DATA;
import static com.redhat.cloud.notifications.connector.v2.IncomingCloudEventProcessor.CLOUD_EVENT_ID;
import static com.redhat.cloud.notifications.connector.v2.IncomingCloudEventProcessor.CLOUD_EVENT_TYPE;

@QuarkusTest
public class IncomingCloudEventProcessorTest {
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
        final String cloudEventEndpointId = UUID.randomUUID().toString();
        final JsonObject cloudEventData = new JsonObject();
        cloudEventData.put("org_id", cloudEventOrgId);
        cloudEventData.put("endpoint_id", cloudEventEndpointId);

        incomingCloudEvent.put(CLOUD_EVENT_DATA, cloudEventData);

        IncomingCloudEventMetadata<JsonObject> incomingCloudEventMetadata = BaseConnectorIntegrationTest.buildIncomingCloudEvent(cloudEventId, cloudEventType, cloudEventData);

        // Prepare the message context.
        final MessageContext context = new MessageContext();

        context.setIncomingCloudEventMetadata(incomingCloudEventMetadata);

        // Call the processor under test.
        incomingCloudEventProcessor.process(context);

        // Assert that the context contains the expected properties.
        Assertions.assertEquals(cloudEventId, context.getIncomingCloudEventMetadata().getId(), "the Cloud Event's ID was not properly extracted");
        Assertions.assertEquals(cloudEventType, context.getIncomingCloudEventMetadata().getType(), "the Cloud Event's type was not properly extracted");
        Assertions.assertNotNull(context.getProperty(START_TIME), "the start time of the processing was not properly set in the context");
        Assertions.assertEquals(connectorConfig.getConnectorName(), context.getProperty(RETURN_SOURCE), "the source of the Cloud Event was not properly set in the context");
        Assertions.assertEquals(cloudEventOrgId, context.getProperty(ORG_ID), "the Cloud Event's ORG ID was not properly set in the context");
        Assertions.assertEquals(cloudEventEndpointId, context.getProperty(ENDPOINT_ID), "the Cloud Event's ENDPOINT ID was not properly set in the context");
    }
}

