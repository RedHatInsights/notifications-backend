package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
import com.redhat.cloud.notifications.connector.slack.clients.SlackClient;
import com.redhat.cloud.notifications.connector.slack.dto.SlackRequest;
import com.redhat.cloud.notifications.connector.slack.dto.SlackResponse;
import com.redhat.cloud.notifications.connector.slack.processors.SlackProcessor;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

// import static com.redhat.cloud.notifications.TestConstants."default-org-id";
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test class for SlackQuarkusConnector.
 * Replaces the Camel-based Slack connector tests.
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class SlackQuarkusConnectorTest {

    @Inject
    HttpConnectorConfig connectorConfig;

    @Inject
    SlackProcessor slackProcessor;

    @Inject
    @RestClient
    @InjectMock
    SlackClient slackClient;

    @Test
    void testSlackConnectorConfiguration() {
        // Test that the connector configuration is properly injected
        assertNotNull(connectorConfig);
        assertNotNull(slackProcessor);
        assertNotNull(slackClient);
    }

    @Test
    void testSlackResponseStructure() {
        // Test the SlackResponse DTO structure
        SlackResponse response = new SlackResponse(true, 200, null, "OK");

        assertTrue(response.isSuccess());
        assertEquals(200, response.getStatusCode());
        assertNull(response.getErrorMessage());
        assertEquals("OK", response.getResponseBody());
    }

    @Test
    void testSlackRequestStructure() {
        // Test the SlackRequest DTO structure
        SlackRequest request = new SlackRequest();
        request.setTargetUrl("https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX");
        JsonObject payload = new JsonObject();
        payload.put("text", "Test message");
        payload.put("channel", "#test-channel");
        payload.put("username", "Test Bot");
        request.setPayload(payload);
        request.setContentType("application/json");

        assertEquals("https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX", request.getTargetUrl());
        assertNotNull(request.getPayload());
        assertEquals("Test message", request.getPayload().getString("text"));
        assertEquals("#test-channel", request.getPayload().getString("channel"));
        assertEquals("Test Bot", request.getPayload().getString("username"));
        assertEquals("application/json", request.getContentType());
    }

    @Test
    void testSlackResponseFailure() {
        // Test the SlackResponse DTO for failure cases
        SlackResponse response = new SlackResponse(false, 400, "Bad Request", "Invalid payload");

        assertFalse(response.isSuccess());
        assertEquals(400, response.getStatusCode());
        assertEquals("Bad Request", response.getErrorMessage());
        assertEquals("Invalid payload", response.getResponseBody());
    }

    @Test
    void testSlackProcessorException() {
        // Test exception handling in SlackProcessor
        JsonObject testPayload = new JsonObject()
            .put("org_id", "default-org-id")
            .put("target_url", "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX")
            .put("text", "Test message")
            .put("channel", "#test-channel");

        // This should not throw an exception
        assertDoesNotThrow(() -> {
            JsonObject processedPayload = slackProcessor.processSlackPayload(testPayload);
            assertNotNull(processedPayload);
        });
    }

    @Test
    void testSlackProcessorWithDefaultText() {
        // Given
        JsonObject testPayload = new JsonObject()
            .put("org_id", "default-org-id")
            .put("target_url", "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX")
            .put("channel", "#general");

        // When
        JsonObject processedPayload = slackProcessor.processSlackPayload(testPayload);

        // Then
        assertNotNull(processedPayload);
        assertTrue(processedPayload.containsKey("text"));
        assertEquals("Notification from Red Hat", processedPayload.getString("text"));
        assertTrue(processedPayload.containsKey("slack_timestamp"));
        assertTrue(processedPayload.containsKey("slack_version"));
    }

    @Test
    void testSlackUrlValidation() {
        // Test valid Slack webhook URLs
        assertTrue(slackProcessor.isValidSlackUrl("https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX"));
        assertTrue(slackProcessor.isValidSlackUrl("https://hooks.slack.com/workflows/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX"));

        // Test invalid URLs
        assertFalse(slackProcessor.isValidSlackUrl(null));
        assertFalse(slackProcessor.isValidSlackUrl(""));
        assertFalse(slackProcessor.isValidSlackUrl("https://example.com/webhook"));
        assertFalse(slackProcessor.isValidSlackUrl("http://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX"));
        assertFalse(slackProcessor.isValidSlackUrl("invalid-url"));
    }

    @Test
    void testSlackTargetUrlExtraction() {
        // Test target URL extraction from payload
        JsonObject testPayload = new JsonObject()
            .put("org_id", "default-org-id")
            .put("target_url", "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX")
            .put("text", "Test message");

        String extractedUrl = slackProcessor.extractTargetUrl(testPayload);
        assertEquals("https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX", extractedUrl);
    }

    @Test
    void testSlackClientMock() {
        // Test that the SlackClient mock is properly configured
        SlackResponse mockResponse = new SlackResponse(true, 200, null, "OK");
        when(slackClient.sendMessage(any(SlackRequest.class))).thenReturn(mockResponse);

        SlackRequest request = new SlackRequest();
        request.setTargetUrl("https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX");
        JsonObject payload = new JsonObject();
        payload.put("text", "Test message");
        payload.put("channel", "#test-channel");
        request.setPayload(payload);

        SlackResponse response = slackClient.sendMessage(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(200, response.getStatusCode());
        verify(slackClient).sendMessage(any(SlackRequest.class));
    }

    @Test
    void testSlackProcessorWithCustomText() {
        // Given
        JsonObject testPayload = new JsonObject()
            .put("org_id", "default-org-id")
            .put("target_url", "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX")
            .put("text", "Custom notification message")
            .put("channel", "#alerts");

        // When
        JsonObject processedPayload = slackProcessor.processSlackPayload(testPayload);

        // Then
        assertNotNull(processedPayload);
        assertTrue(processedPayload.containsKey("text"));
        assertEquals("Custom notification message", processedPayload.getString("text"));
        assertTrue(processedPayload.containsKey("channel"));
        assertEquals("#alerts", processedPayload.getString("channel"));
        assertTrue(processedPayload.containsKey("slack_timestamp"));
        assertTrue(processedPayload.containsKey("slack_version"));
    }

    @Test
    void testSlackProcessorWithMissingFields() {
        // Given
        JsonObject testPayload = new JsonObject()
            .put("org_id", "default-org-id")
            .put("target_url", "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX");

        // When
        JsonObject processedPayload = slackProcessor.processSlackPayload(testPayload);

        // Then
        assertNotNull(processedPayload);
        assertTrue(processedPayload.containsKey("text"));
        assertEquals("Notification from Red Hat", processedPayload.getString("text"));
        assertTrue(processedPayload.containsKey("slack_timestamp"));
        assertTrue(processedPayload.containsKey("slack_version"));
    }
}
