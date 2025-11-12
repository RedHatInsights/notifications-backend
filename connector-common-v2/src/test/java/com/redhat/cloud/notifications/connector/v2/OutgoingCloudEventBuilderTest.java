package com.redhat.cloud.notifications.connector.v2;

import com.redhat.cloud.notifications.connector.v2.models.HandledExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OutgoingCloudEventBuilderTest {

    @Inject
    OutgoingCloudEventBuilder outgoingCloudEventBuilder;

    @Inject
    ConnectorConfig connectorConfig;

    @Test
    void testBuildSuccessfulCloudEvent() {

        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = BaseConnectorIntegrationTest.buildIncomingCloudEvent(
            "test-cloud-event-id",
            "com.redhat.console.notification.toCamel.test",
            new JsonObject().put("test", "data")
        );

        HandledMessageDetails processedMessageDetails = new HandledMessageDetails("Event sent successfully");

        // When
        Message<String> cloudEventMessage = outgoingCloudEventBuilder.buildSuccess(incomingCloudEvent, processedMessageDetails, System.currentTimeMillis());

        // Then - verify message exists
        assertNotNull(cloudEventMessage);
        assertNotNull(cloudEventMessage.getPayload());

        // Verify CloudEvent metadata
        OutgoingCloudEventMetadata<?> metadata = cloudEventMessage.getMetadata(OutgoingCloudEventMetadata.class)
            .orElseThrow(() -> new AssertionError("CloudEvent metadata not found"));

        assertEquals("test-cloud-event-id", metadata.getId());
        assertEquals(OutgoingCloudEventBuilder.CE_TYPE, metadata.getType());
        assertEquals(OutgoingCloudEventBuilder.CE_SPEC_VERSION, metadata.getSpecVersion());
        assertEquals(URI.create(connectorConfig.getConnectorName()), metadata.getSource());
        assertEquals("application/json", metadata.getDataContentType().orElse(null));
        assertNotNull(metadata.getTimeStamp());

        // Verify payload data
        JsonObject data = new JsonObject(cloudEventMessage.getPayload());
        assertEquals(true, data.getBoolean("successful"));
        assertNotNull(data.getLong("duration"));
        assertTrue(data.getLong("duration") >= 0);

        JsonObject details = data.getJsonObject("details");
        assertNotNull(details);
        assertEquals("com.redhat.console.notification.toCamel.test", details.getString("type"));
        assertEquals("Event sent successfully", details.getString("outcome"));
    }

    @Test
    void testBuildFailedCloudEvent() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = BaseConnectorIntegrationTest.buildIncomingCloudEvent(
            "test-cloud-event-id",
            "com.redhat.console.notification.toCamel.test",
            new JsonObject().put("test", "data")
        );

        // Given - set failure properties
        HandledExceptionDetails processedMessageDetails = new HandledExceptionDetails("Connection refused");

        // When
        Message<String> cloudEventMessage = outgoingCloudEventBuilder.buildFailure(incomingCloudEvent, processedMessageDetails, System.currentTimeMillis());

        // Then - verify message exists
        assertNotNull(cloudEventMessage);

        // Verify CloudEvent metadata
        OutgoingCloudEventMetadata<?> metadata = cloudEventMessage.getMetadata(OutgoingCloudEventMetadata.class)
            .orElseThrow(() -> new AssertionError("CloudEvent metadata not found"));

        assertEquals("test-cloud-event-id", metadata.getId());
        assertEquals(OutgoingCloudEventBuilder.CE_TYPE, metadata.getType());

        // Verify payload data shows failure
        JsonObject data = new JsonObject(cloudEventMessage.getPayload());
        assertEquals(false, data.getBoolean("successful"));

        JsonObject details = data.getJsonObject("details");
        assertEquals("Connection refused", details.getString("outcome"));
    }

    @Test
    void testPreservesIncomingCloudEventId() {
        // Given - different cloud event ID
        String expectedId = "unique-event-12345";
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = BaseConnectorIntegrationTest.buildIncomingCloudEvent(
            expectedId,
            "com.redhat.console.notification.toCamel.test",
            new JsonObject().put("test", "data")
        );

        HandledMessageDetails processedMessageDetails = new HandledMessageDetails("Event sent successfully");

        // When
        Message<String> cloudEventMessage = outgoingCloudEventBuilder.buildSuccess(incomingCloudEvent, processedMessageDetails, System.currentTimeMillis());

        // Then - verify the ID is preserved
        OutgoingCloudEventMetadata<?> metadata = cloudEventMessage.getMetadata(OutgoingCloudEventMetadata.class)
            .orElseThrow(() -> new AssertionError("CloudEvent metadata not found"));

        assertEquals(expectedId, metadata.getId());
    }

    @Test
    void testCalculatesDuration() {

        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = BaseConnectorIntegrationTest.buildIncomingCloudEvent(
            "test-cloud-event-id",
            "com.redhat.console.notification.toCamel.test",
            new JsonObject().put("test", "data")
        );

        // Given - set start time 100ms in the past
        long startTime = System.currentTimeMillis() - 100;

        HandledMessageDetails processedMessageDetails = new HandledMessageDetails("Event sent successfully");

        // When
        Message<String> cloudEventMessage = outgoingCloudEventBuilder.buildSuccess(incomingCloudEvent, processedMessageDetails, startTime);

        // Then - verify duration is calculated
        JsonObject data = new JsonObject(cloudEventMessage.getPayload());
        long duration = data.getLong("duration");

        assertTrue(duration >= 100, "Duration should be at least 100ms, but was: " + duration);
        assertTrue(duration < 10000, "Duration should be less than 10 seconds, but was: " + duration);
    }
}
