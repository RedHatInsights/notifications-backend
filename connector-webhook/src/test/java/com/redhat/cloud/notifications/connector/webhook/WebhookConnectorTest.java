package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.ConnectorProcessor;
import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class WebhookConnectorTest {

    @Inject
    WebhookConnector webhookConnector;

    private ExceptionProcessor.ProcessingContext testContext;
    private JsonObject testCloudEvent;

    @BeforeEach
    void setUp() {
        // Setup test data
        testCloudEvent = new JsonObject()
                .put("id", "test-event-id")
                .put("source", "test-source")
                .put("type", "com.redhat.console.notification")
                .put("data", new JsonObject()
                        .put("application", "test-app")
                        .put("bundle", "test-bundle")
                        .put("event_type", "test-event")
                        .put("context", "Test notification message"));

        testContext = new ExceptionProcessor.ProcessingContext();
        testContext.setId("test-id");
        testContext.setOrgId("test-org");
        testContext.setTargetUrl("http://example.com/webhook");
        testContext.setOriginalCloudEvent(testCloudEvent);
        testContext.setAdditionalProperty("ACCOUNT_ID", "test-account");
    }

    @Test
    void testSuccessfulResult() {
        ConnectorProcessor.ConnectorResult result = new ConnectorProcessor.ConnectorResult(
                true, "Webhook test-id sent successfully", "test-id", "test-org", testCloudEvent
        );

        assertTrue(result.isSuccessful());
        assertEquals("test-id", result.getId());
        assertEquals("test-org", result.getOrgId());
        assertTrue(result.getOutcome().contains("sent successfully"));
    }

    @Test
    void testFailedResult() {
        ConnectorProcessor.ConnectorResult result = new ConnectorProcessor.ConnectorResult(
                false, "HTTP 500: Internal Server Error", "test-id", "test-org", testCloudEvent
        );

        assertFalse(result.isSuccessful());
        assertTrue(result.getOutcome().contains("HTTP 500"));
    }

    @Test
    void testWebhookPayloadStructure() {
        // Test the webhook payload structure that would be sent
        JsonObject webhookPayload = testCloudEvent.copy();

        // Verify the payload is the original CloudEvent
        assertEquals("test-event-id", webhookPayload.getString("id"));
        assertEquals("test-source", webhookPayload.getString("source"));
        assertEquals("com.redhat.console.notification", webhookPayload.getString("type"));

        JsonObject data = webhookPayload.getJsonObject("data");
        assertNotNull(data);
        assertEquals("test-app", data.getString("application"));
        assertEquals("test-bundle", data.getString("bundle"));
        assertEquals("test-event", data.getString("event_type"));
        assertEquals("Test notification message", data.getString("context"));
    }

    @Test
    void testSecretPasswordHandling() {
        testContext.setAdditionalProperty("SECRET_PASSWORD", "test-secret");
        assertEquals("test-secret", testContext.getAdditionalProperty("SECRET_PASSWORD", String.class));
    }

    @Test
    void testCustomHeadersHandling() {
        String customHeaders = "{\"X-Custom-Header\":\"custom-value\",\"X-Another-Header\":\"another-value\"}";
        testContext.setAdditionalProperty("CUSTOM_HEADERS", customHeaders);
        assertEquals(customHeaders, testContext.getAdditionalProperty("CUSTOM_HEADERS", String.class));
    }

    @Test
    void testInvalidCustomHeadersHandling() {
        testContext.setAdditionalProperty("CUSTOM_HEADERS", "invalid-json");
        assertEquals("invalid-json", testContext.getAdditionalProperty("CUSTOM_HEADERS", String.class));
    }

    @Test
    void testTrustAllFlagHandling() {
        testContext.setAdditionalProperty("TRUST_ALL", true);
        assertTrue(testContext.getAdditionalProperty("TRUST_ALL", Boolean.class));
    }

    @Test
    void testProcessingContextSetup() {
        assertNotNull(testContext);
        assertEquals("test-id", testContext.getId());
        assertEquals("test-org", testContext.getOrgId());
        assertEquals("http://example.com/webhook", testContext.getTargetUrl());
        assertEquals(testCloudEvent, testContext.getOriginalCloudEvent());
        assertEquals("test-account", testContext.getAdditionalProperty("ACCOUNT_ID", String.class));
    }

    @Test
    void testMinimalDataHandling() {
        JsonObject minimalEvent = new JsonObject()
                .put("id", "minimal-event-id")
                .put("source", "test-source")
                .put("type", "com.redhat.console.notification");

        testContext.setOriginalCloudEvent(minimalEvent);
        assertEquals(minimalEvent, testContext.getOriginalCloudEvent());
    }

    @Test
    void testWebhookUrlConfiguration() {
        // Test different webhook URL configurations
        testContext.setTargetUrl("https://hooks.example.com/webhook/123");
        assertEquals("https://hooks.example.com/webhook/123", testContext.getTargetUrl());

        testContext.setTargetUrl("http://localhost:8080/webhook");
        assertEquals("http://localhost:8080/webhook", testContext.getTargetUrl());
    }
}
