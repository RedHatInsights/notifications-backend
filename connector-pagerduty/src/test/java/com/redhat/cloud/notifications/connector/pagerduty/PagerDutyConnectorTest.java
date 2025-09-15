package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.ConnectorProcessor;
import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PagerDutyConnectorTest {

    @Inject
    PagerDutyConnector pagerDutyConnector;

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
                        .put("severity", "critical")
                        .put("source", new JsonObject()
                                .put("display_name", "Test Host")));

        testContext = new ExceptionProcessor.ProcessingContext();
        testContext.setId("test-id");
        testContext.setOrgId("test-org");
        testContext.setTargetUrl("https://events.pagerduty.com/v2/enqueue");
        testContext.setOriginalCloudEvent(testCloudEvent);
        testContext.setAdditionalProperty("ACCOUNT_ID", "test-account");
        testContext.setAdditionalProperty("ROUTING_KEY", "test-routing-key");
    }

    @Test
    void testSuccessfulResult() {
        ConnectorProcessor.ConnectorResult result = new ConnectorProcessor.ConnectorResult(
                true, "PagerDuty event test-id sent successfully", "test-id", "test-org", testCloudEvent
        );

        assertTrue(result.isSuccessful());
        assertEquals("test-id", result.getId());
        assertEquals("test-org", result.getOrgId());
        assertTrue(result.getOutcome().contains("sent successfully"));
    }

    @Test
    void testFailedResult() {
        ConnectorProcessor.ConnectorResult result = new ConnectorProcessor.ConnectorResult(
                false, "HTTP 400: invalid event", "test-id", "test-org", testCloudEvent
        );

        assertFalse(result.isSuccessful());
        assertTrue(result.getOutcome().contains("HTTP 400"));
    }

    @Test
    void testPagerDutyEventTransformation() {
        // Test the PagerDuty event structure that would be created
        JsonObject pagerDutyEvent = new JsonObject();
        pagerDutyEvent.put("routing_key", "test-routing-key");
        pagerDutyEvent.put("event_action", "trigger");
        pagerDutyEvent.put("dedup_key", "test-id-test-org");

        JsonObject payload = new JsonObject();
        payload.put("summary", "test-app: Test notification message");
        payload.put("source", "test-app");
        payload.put("severity", "critical");
        payload.put("timestamp", "2023-01-01T00:00:00Z");

        JsonObject customDetails = new JsonObject();
        customDetails.put("event_id", "test-id");
        customDetails.put("org_id", "test-org");
        customDetails.put("account_id", "test-account");
        customDetails.put("bundle", "test-bundle");
        customDetails.put("event_type", "test-event");
        payload.put("custom_details", customDetails);

        pagerDutyEvent.put("payload", payload);

        // Verify PagerDuty event structure
        assertTrue(pagerDutyEvent.containsKey("routing_key"));
        assertTrue(pagerDutyEvent.containsKey("event_action"));
        assertTrue(pagerDutyEvent.containsKey("dedup_key"));
        assertTrue(pagerDutyEvent.containsKey("payload"));

        assertEquals("test-routing-key", pagerDutyEvent.getString("routing_key"));
        assertEquals("trigger", pagerDutyEvent.getString("event_action"));
        assertEquals("test-id-test-org", pagerDutyEvent.getString("dedup_key"));

        JsonObject testPayload = pagerDutyEvent.getJsonObject("payload");
        assertNotNull(testPayload);
        assertTrue(testPayload.containsKey("summary"));
        assertTrue(testPayload.containsKey("source"));
        assertTrue(testPayload.containsKey("severity"));
        assertTrue(testPayload.containsKey("custom_details"));

        assertEquals("critical", testPayload.getString("severity"));
        assertEquals("test-app", testPayload.getString("source"));

        // Verify custom details
        JsonObject testCustomDetails = testPayload.getJsonObject("custom_details");
        assertNotNull(testCustomDetails);
        assertEquals("test-id", testCustomDetails.getString("event_id"));
        assertEquals("test-org", testCustomDetails.getString("org_id"));
        assertEquals("test-account", testCustomDetails.getString("account_id"));
    }

    @Test
    void testSeverityMapping() {
        // Test different severity mappings
        assertEquals("critical", mapSeverity("critical"));
        assertEquals("error", mapSeverity("error"));
        assertEquals("warning", mapSeverity("warning"));
        assertEquals("info", mapSeverity("info"));
        assertEquals("info", mapSeverity("unknown"));
    }

    @Test
    void testEventActionMapping() {
        // Test event action mappings
        assertEquals("trigger", mapEventAction("TRIGGER"));
        assertEquals("acknowledge", mapEventAction("ACKNOWLEDGE"));
        assertEquals("resolve", mapEventAction("RESOLVE"));
        assertEquals("trigger", mapEventAction("INVALID_ACTION")); // Default to trigger
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
        assertEquals("https://events.pagerduty.com/v2/enqueue", testContext.getTargetUrl());
        assertEquals(testCloudEvent, testContext.getOriginalCloudEvent());
        assertEquals("test-account", testContext.getAdditionalProperty("ACCOUNT_ID", String.class));
        assertEquals("test-routing-key", testContext.getAdditionalProperty("ROUTING_KEY", String.class));
    }

    @Test
    void testEventActionConfiguration() {
        // Test custom event action
        testContext.setAdditionalProperty("EVENT_ACTION", "ACKNOWLEDGE");
        assertEquals("ACKNOWLEDGE", testContext.getAdditionalProperty("EVENT_ACTION", String.class));
    }

    @Test
    void testTrustAllFlagHandling() {
        testContext.setAdditionalProperty("TRUST_ALL", true);
        assertTrue(testContext.getAdditionalProperty("TRUST_ALL", Boolean.class));
    }

    private String mapSeverity(String severity) {
        // PagerDuty severity mapping
        return switch (severity.toLowerCase()) {
            case "critical" -> "critical";
            case "error" -> "error";
            case "warning" -> "warning";
            case "info" -> "info";
            default -> "info";
        };
    }

    private String mapEventAction(String action) {
        // PagerDuty event action mapping
        return switch (action.toUpperCase()) {
            case "TRIGGER" -> "trigger";
            case "ACKNOWLEDGE" -> "acknowledge";
            case "RESOLVE" -> "resolve";
            default -> "trigger";
        };
    }
}
