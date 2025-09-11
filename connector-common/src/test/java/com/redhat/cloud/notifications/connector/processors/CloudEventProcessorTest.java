package com.redhat.cloud.notifications.connector.processors;

import com.redhat.cloud.notifications.connector.QuarkusConnectorBase.CloudEventData;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Test class for CloudEventProcessor.
 */
@ExtendWith(MockitoExtension.class)
class CloudEventProcessorTest {

    private CloudEventProcessor cloudEventProcessor;

    @Mock
    CloudEventMetadata<JsonObject> cloudEventMetadata;

    @Mock
    Message<JsonObject> message;

    @BeforeEach
    void setUp() {
        cloudEventProcessor = new CloudEventProcessor();
    }

    @Test
    void testExtractCloudEventDataWithCloudEventMetadata() {
        // Given
        JsonObject payload = new JsonObject()
            .put("org_id", "test-org-id")
            .put("message", "test message");

        when(message.getPayload()).thenReturn(payload);
        when(message.getMetadata(CloudEventMetadata.class)).thenReturn(Optional.of(cloudEventMetadata));
        when(cloudEventMetadata.getId()).thenReturn("test-history-id");
        when(cloudEventMetadata.getType()).thenReturn("com.redhat.console.notification.toCamel.webhook");

        // When
        CloudEventData result = cloudEventProcessor.extractCloudEventData(message);

        // Then
        assertNotNull(result);
        assertEquals("test-org-id", result.getOrgId());
        assertEquals("test-history-id", result.getHistoryId());
        assertEquals("webhook", result.getConnector());
        assertEquals(payload, result.getPayload());
    }

    @Test
    void testExtractCloudEventDataWithoutCloudEventMetadata() {
        // Given
        JsonObject payload = new JsonObject()
            .put("org_id", "test-org-id")
            .put("id", "fallback-id")
            .put("message", "test message");

        when(message.getPayload()).thenReturn(payload);
        when(message.getMetadata(CloudEventMetadata.class)).thenReturn(Optional.empty());

        // When
        CloudEventData result = cloudEventProcessor.extractCloudEventData(message);

        // Then
        assertNotNull(result);
        assertEquals("test-org-id", result.getOrgId());
        assertEquals("fallback-id", result.getHistoryId());
        assertEquals("unknown", result.getConnector());
        assertEquals(payload, result.getPayload());
    }

    @Test
    void testExtractCloudEventDataWithEmptyCloudEventId() {
        // Given
        JsonObject payload = new JsonObject()
            .put("org_id", "test-org-id")
            .put("message", "test message");

        when(message.getPayload()).thenReturn(payload);
        when(message.getMetadata(CloudEventMetadata.class)).thenReturn(Optional.of(cloudEventMetadata));
        when(cloudEventMetadata.getId()).thenReturn("");
        when(cloudEventMetadata.getType()).thenReturn("com.redhat.console.notification.toCamel.email");

        // When
        CloudEventData result = cloudEventProcessor.extractCloudEventData(message);

        // Then
        assertNotNull(result);
        assertEquals("test-org-id", result.getOrgId());
        assertEquals("unknown", result.getHistoryId());
        assertEquals("email", result.getConnector());
        assertEquals(payload, result.getPayload());
    }

    @Test
    void testExtractCloudEventDataWithInvalidCloudEventType() {
        // Given
        JsonObject payload = new JsonObject()
            .put("org_id", "test-org-id")
            .put("message", "test message");

        when(message.getPayload()).thenReturn(payload);
        when(message.getMetadata(CloudEventMetadata.class)).thenReturn(Optional.of(cloudEventMetadata));
        when(cloudEventMetadata.getId()).thenReturn("test-history-id");
        when(cloudEventMetadata.getType()).thenReturn("invalid.type");

        // When
        CloudEventData result = cloudEventProcessor.extractCloudEventData(message);

        // Then
        assertNotNull(result);
        assertEquals("test-org-id", result.getOrgId());
        assertEquals("test-history-id", result.getHistoryId());
        assertEquals("unknown", result.getConnector());
        assertEquals(payload, result.getPayload());
    }
}
