package com.redhat.cloud.notifications.connector.v2;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static com.redhat.cloud.notifications.connector.v2.CommonConstants.ENDPOINT_ID;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.ORG_ID;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.OUTCOME;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.RETURN_SOURCE;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.START_TIME;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.SUCCESSFUL;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.TARGET_URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OutgoingCloudEventBuilderTest {

    @Inject
    OutgoingCloudEventBuilder outgoingCloudEventBuilder;

    private MessageContext context;

    @BeforeEach
    void setUp() {
        context = new MessageContext();

        // Set up incoming CloudEvent metadata
        context.setIncomingCloudEventMetadata(
            BaseConnectorIntegrationTest.buildIncomingCloudEvent(
                "test-cloud-event-id",
                "com.redhat.console.notification.toCamel.test",
                new JsonObject().put("test", "data")
            )
        );

        // Set required properties
        context.setProperty(ORG_ID, "test-org-123");
        context.setProperty(ENDPOINT_ID, "endpoint-456");
        context.setProperty(RETURN_SOURCE, "notifications-connector-test");
        context.setProperty(TARGET_URL, "https://example.com/webhook");
        context.setProperty(START_TIME, System.currentTimeMillis());
        context.setProperty(SUCCESSFUL, true);
        context.setProperty(OUTCOME, "Event sent successfully");
    }

    @Test
    void testBuildSuccessfulCloudEvent() throws Exception {
        // When
        Message<String> cloudEventMessage = outgoingCloudEventBuilder.build(context);

        // Then - verify message exists
        assertNotNull(cloudEventMessage);
        assertNotNull(cloudEventMessage.getPayload());

        // Verify CloudEvent metadata
        OutgoingCloudEventMetadata<?> metadata = cloudEventMessage.getMetadata(OutgoingCloudEventMetadata.class)
            .orElseThrow(() -> new AssertionError("CloudEvent metadata not found"));

        assertEquals("test-cloud-event-id", metadata.getId());
        assertEquals(OutgoingCloudEventBuilder.CE_TYPE, metadata.getType());
        assertEquals(OutgoingCloudEventBuilder.CE_SPEC_VERSION, metadata.getSpecVersion());
        assertEquals(URI.create("notifications-connector-test"), metadata.getSource());
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
        assertEquals("https://example.com/webhook", details.getString("target"));
        assertEquals("Event sent successfully", details.getString("outcome"));
    }

    @Test
    void testBuildFailedCloudEvent() throws Exception {
        // Given - set failure properties
        context.setProperty(SUCCESSFUL, false);
        context.setProperty(OUTCOME, "Connection refused");

        // When
        Message<String> cloudEventMessage = outgoingCloudEventBuilder.build(context);

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
    void testPreservesIncomingCloudEventId() throws Exception {
        // Given - different cloud event ID
        String expectedId = "unique-event-12345";
        context.setIncomingCloudEventMetadata(
            BaseConnectorIntegrationTest.buildIncomingCloudEvent(
                expectedId,
                "com.redhat.console.notification.toCamel.test",
                new JsonObject()
            )
        );

        // When
        Message<String> cloudEventMessage = outgoingCloudEventBuilder.build(context);

        // Then - verify the ID is preserved
        OutgoingCloudEventMetadata<?> metadata = cloudEventMessage.getMetadata(OutgoingCloudEventMetadata.class)
            .orElseThrow(() -> new AssertionError("CloudEvent metadata not found"));

        assertEquals(expectedId, metadata.getId());
    }

    @Test
    void testCalculatesDuration() throws Exception {
        // Given - set start time 100ms in the past
        long startTime = System.currentTimeMillis() - 100;
        context.setProperty(START_TIME, startTime);

        // When
        Message<String> cloudEventMessage = outgoingCloudEventBuilder.build(context);

        // Then - verify duration is calculated
        JsonObject data = new JsonObject(cloudEventMessage.getPayload());
        long duration = data.getLong("duration");

        assertTrue(duration >= 100, "Duration should be at least 100ms, but was: " + duration);
        assertTrue(duration < 10000, "Duration should be less than 10 seconds, but was: " + duration);
    }
}
