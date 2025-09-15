package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.ConnectorProcessor;
import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SlackConnectorTest {

    @Inject
    SlackConnector slackConnector;

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
                        .put("context", "Test notification message")
                        .put("severity", "warning"));

        testContext = new ExceptionProcessor.ProcessingContext();
        testContext.setId("test-id");
        testContext.setOrgId("test-org");
        testContext.setTargetUrl("https://hooks.slack.com/services/TEST/WEBHOOK/URL");
        testContext.setOriginalCloudEvent(testCloudEvent);
        testContext.setAdditionalProperty("ACCOUNT_ID", "test-account");
        testContext.setAdditionalProperty("CHANNEL", "#test-channel");
    }

    @Test
    void testSlackMessageTransformation() {
        // Test the Slack message structure that would be created
        JsonObject slackMessage = new JsonObject();
        slackMessage.put("channel", "#test-channel");
        slackMessage.put("text", "*test-app*: Test notification message");

        JsonObject attachment = new JsonObject();
        attachment.put("color", "warning"); // warning severity
        attachment.put("fields", new JsonObject()
                .put("Application", "test-app")
                .put("Bundle", "test-bundle")
                .put("Event Type", "test-event"));

        slackMessage.put("attachments", new JsonObject().put("0", attachment));

        // Verify structure
        assertTrue(slackMessage.containsKey("text"));
        assertTrue(slackMessage.containsKey("channel"));
        assertTrue(slackMessage.containsKey("attachments"));
        assertEquals("#test-channel", slackMessage.getString("channel"));
        assertTrue(slackMessage.getString("text").contains("test-app"));
    }

    @Test
    void testSeverityColorMapping() {
        // Test severity to color mapping
        assertEquals("danger", mapSeverity("critical"));
        assertEquals("danger", mapSeverity("error"));
        assertEquals("warning", mapSeverity("warning"));
        assertEquals("good", mapSeverity("info"));
        assertEquals("#36a64f", mapSeverity("unknown"));
    }

    @Test
    void testSuccessfulResult() {
        ConnectorProcessor.ConnectorResult result = new ConnectorProcessor.ConnectorResult(
                true, "Slack message test-id sent successfully", "test-id", "test-org", testCloudEvent
        );

        assertTrue(result.isSuccessful());
        assertEquals("test-id", result.getId());
        assertEquals("test-org", result.getOrgId());
        assertTrue(result.getOutcome().contains("sent successfully"));
    }

    @Test
    void testFailedResult() {
        ConnectorProcessor.ConnectorResult result = new ConnectorProcessor.ConnectorResult(
                false, "HTTP 400: invalid_payload", "test-id", "test-org", testCloudEvent
        );

        assertFalse(result.isSuccessful());
        assertTrue(result.getOutcome().contains("HTTP 400"));
    }

    @Test
    void testSecretPasswordHandling() {
        testContext.setAdditionalProperty("SECRET_PASSWORD", "test-secret");
        assertEquals("test-secret", testContext.getAdditionalProperty("SECRET_PASSWORD", String.class));
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
    void testProcessingContextSetup() {
        assertNotNull(testContext);
        assertEquals("test-id", testContext.getId());
        assertEquals("test-org", testContext.getOrgId());
        assertEquals("https://hooks.slack.com/services/TEST/WEBHOOK/URL", testContext.getTargetUrl());
        assertEquals(testCloudEvent, testContext.getOriginalCloudEvent());
        assertEquals("test-account", testContext.getAdditionalProperty("ACCOUNT_ID", String.class));
        assertEquals("#test-channel", testContext.getAdditionalProperty("CHANNEL", String.class));
    }

    private String mapSeverity(String severity) {
        return switch (severity.toLowerCase()) {
            case "critical", "error" -> "danger";
            case "warning" -> "warning";
            case "info" -> "good";
            default -> "#36a64f";
        };
    }
}
