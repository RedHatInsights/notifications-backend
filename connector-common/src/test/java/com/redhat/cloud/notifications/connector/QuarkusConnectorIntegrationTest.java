package com.redhat.cloud.notifications.connector;

import com.redhat.cloud.notifications.connector.QuarkusConnectorBase.CloudEventData;
import com.redhat.cloud.notifications.connector.processors.CloudEventProcessor;
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
 * Integration test for Quarkus connector functionality.
 * Tests core connector logic without external service dependencies.
 */
@ExtendWith(MockitoExtension.class)
class QuarkusConnectorIntegrationTest {

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
    void testCloudEventDataExtraction() {
        // Given
        JsonObject payload = new JsonObject()
            .put("org_id", "test-org")
            .put("history_id", "test-history-id")
            .put("target_url", "http://localhost:8080/webhook")
            .put("connectorType", "webhook");

        when(message.getPayload()).thenReturn(payload);
        when(message.getMetadata(CloudEventMetadata.class)).thenReturn(Optional.of(cloudEventMetadata));
        when(cloudEventMetadata.getId()).thenReturn("test-cloud-event-id");
        when(cloudEventMetadata.getType()).thenReturn("com.redhat.console.notification.toCamel.webhook");

        // When
        CloudEventData result = cloudEventProcessor.extractCloudEventData(message);

        // Then
        assertNotNull(result);
        assertEquals("test-org", result.getOrgId());
        assertEquals("test-cloud-event-id", result.getHistoryId());
        assertEquals("webhook", result.getConnector());
        assertEquals(payload, result.getPayload());
    }

    @Test
    void testCloudEventDataExtractionWithoutMetadata() {
        // Given
        JsonObject payload = new JsonObject()
            .put("org_id", "test-org")
            .put("history_id", "test-history-id")
            .put("target_url", "http://localhost:8080/webhook")
            .put("connectorType", "webhook");

        when(message.getPayload()).thenReturn(payload);
        when(message.getMetadata(CloudEventMetadata.class)).thenReturn(Optional.empty());

        // When
        CloudEventData result = cloudEventProcessor.extractCloudEventData(message);

        // Then
        assertNotNull(result);
        assertEquals("test-org", result.getOrgId());
        assertEquals("unknown", result.getHistoryId());
        assertEquals("unknown", result.getConnector());
        assertEquals(payload, result.getPayload());
    }

    @Test
    void testCloudEventDataExtractionWithEmptyId() {
        // Given
        JsonObject payload = new JsonObject()
            .put("org_id", "test-org")
            .put("history_id", "test-history-id")
            .put("target_url", "http://localhost:8080/webhook")
            .put("connectorType", "webhook");

        when(message.getPayload()).thenReturn(payload);
        when(message.getMetadata(CloudEventMetadata.class)).thenReturn(Optional.of(cloudEventMetadata));
        when(cloudEventMetadata.getId()).thenReturn("");
        when(cloudEventMetadata.getType()).thenReturn("com.redhat.console.notification.toCamel.webhook");

        // When
        CloudEventData result = cloudEventProcessor.extractCloudEventData(message);

        // Then
        assertNotNull(result);
        assertEquals("test-org", result.getOrgId());
        assertEquals("unknown", result.getHistoryId());
        assertEquals("webhook", result.getConnector());
        assertEquals(payload, result.getPayload());
    }

    @Test
    void testCloudEventDataExtractionWithInvalidType() {
        // Given
        JsonObject payload = new JsonObject()
            .put("org_id", "test-org")
            .put("history_id", "test-history-id")
            .put("target_url", "http://localhost:8080/webhook")
            .put("connectorType", "webhook");

        when(message.getPayload()).thenReturn(payload);
        when(message.getMetadata(CloudEventMetadata.class)).thenReturn(Optional.of(cloudEventMetadata));
        when(cloudEventMetadata.getId()).thenReturn("test-cloud-event-id");
        when(cloudEventMetadata.getType()).thenReturn("invalid.type");

        // When
        CloudEventData result = cloudEventProcessor.extractCloudEventData(message);

        // Then
        assertNotNull(result);
        assertEquals("test-org", result.getOrgId());
        assertEquals("test-cloud-event-id", result.getHistoryId());
        assertEquals("unknown", result.getConnector());
        assertEquals(payload, result.getPayload());
    }

    @Test
    void testConnectorTypeExtraction() {
        // Test various connector types
        String[] testTypes = new String[] {"com.redhat.console.notification.toCamel.webhook",
            "com.redhat.console.notification.toCamel.email",
            "com.redhat.console.notification.toCamel.slack",
            "com.redhat.console.notification.toCamel.teams",
            "com.redhat.console.notification.toCamel.pagerduty",
            "com.redhat.console.notification.toCamel.servicenow",
            "com.redhat.console.notification.toCamel.splunk",
            "com.redhat.console.notification.toCamel.drawer"
        };

        String[] expectedConnectors = new String[] {"webhook", "email", "slack", "teams", "pagerduty", "servicenow", "splunk", "drawer"};
        for (int i = 0; i < testTypes.length; i++) {
            // Given
            JsonObject payload = new JsonObject().put("org_id", "test-org");
            when(message.getPayload()).thenReturn(payload);
            when(message.getMetadata(CloudEventMetadata.class)).thenReturn(Optional.of(cloudEventMetadata));
            when(cloudEventMetadata.getId()).thenReturn("test-id");
            when(cloudEventMetadata.getType()).thenReturn(testTypes[i]);

            // When
            CloudEventData result = cloudEventProcessor.extractCloudEventData(message);

            // Then
            assertEquals(expectedConnectors[i], result.getConnector(),
                "Failed for type: " + testTypes[i]);
        }
    }
}
