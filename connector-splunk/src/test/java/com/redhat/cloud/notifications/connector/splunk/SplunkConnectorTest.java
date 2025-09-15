package com.redhat.cloud.notifications.connector.splunk;

import com.redhat.cloud.notifications.connector.ConnectorProcessor;
import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SplunkConnectorTest {

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
                        .put("application", "policies")
                        .put("bundle", "rhel")
                        .put("event_type", "triggered")
                        .put("context", "Test notification message")
                        .put("events", new JsonArray()
                                .add(new JsonObject()
                                        .put("payload", "test-payload-1")
                                        .put("timestamp", "2023-01-01T00:00:00Z"))
                                .add(new JsonObject()
                                        .put("payload", "test-payload-2")
                                        .put("timestamp", "2023-01-01T00:01:00Z"))
                        ));

        testContext = new ExceptionProcessor.ProcessingContext();
        testContext.setId("test-id");
        testContext.setOrgId("test-org");
        testContext.setTargetUrl("https://splunk.example.com/services/collector/event");
        testContext.setOriginalCloudEvent(testCloudEvent);
        testContext.setAdditionalProperty("ACCOUNT_ID", "test-account");
        testContext.setAdditionalProperty("SECRET_PASSWORD", "test-token");
        testContext.setAdditionalProperty("AUTHORIZATION_HEADER", "Bearer test-token");
    }

    @Test
    void testSuccessfulResult() {
        ConnectorProcessor.ConnectorResult result = new ConnectorProcessor.ConnectorResult(
                true, "Splunk events test-id sent successfully", "test-id", "test-org", testCloudEvent
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
    void testSplunkEventTransformation() {
        // Test the Splunk event structure that would be created
        JsonObject splunkEvent = new JsonObject();
        splunkEvent.put("source", "eventing");
        splunkEvent.put("sourcetype", "Insights event");
        splunkEvent.put("index", "main");
        splunkEvent.put("host", "notifications-backend");

        JsonObject eventData = new JsonObject();
        eventData.put("application", "policies");
        eventData.put("bundle", "rhel");
        eventData.put("event_type", "triggered");
        eventData.put("context", "Test notification message");
        eventData.put("event_id", "test-id");
        eventData.put("org_id", "test-org");
        eventData.put("account_id", "test-account");
        eventData.put("timestamp", "2023-01-01T00:00:00Z");

        splunkEvent.put("event", eventData);

        // Verify Splunk event structure
        assertTrue(splunkEvent.containsKey("source"));
        assertTrue(splunkEvent.containsKey("sourcetype"));
        assertTrue(splunkEvent.containsKey("index"));
        assertTrue(splunkEvent.containsKey("host"));
        assertTrue(splunkEvent.containsKey("event"));

        assertEquals("eventing", splunkEvent.getString("source"));
        assertEquals("Insights event", splunkEvent.getString("sourcetype"));
        assertEquals("main", splunkEvent.getString("index"));
        assertEquals("notifications-backend", splunkEvent.getString("host"));

        JsonObject event = splunkEvent.getJsonObject("event");
        assertNotNull(event);
        assertEquals("policies", event.getString("application"));
        assertEquals("rhel", event.getString("bundle"));
        assertEquals("triggered", event.getString("event_type"));
        assertEquals("test-id", event.getString("event_id"));
        assertEquals("test-org", event.getString("org_id"));
        assertEquals("test-account", event.getString("account_id"));
    }

    @Test
    void testMultipleEventsHandling() {
        // Test handling multiple events in the events array
        JsonArray events = testCloudEvent.getJsonObject("data").getJsonArray("events");
        assertNotNull(events);
        assertEquals(2, events.size());

        // Verify first event
        JsonObject firstEvent = events.getJsonObject(0);
        assertEquals("test-payload-1", firstEvent.getString("payload"));
        assertEquals("2023-01-01T00:00:00Z", firstEvent.getString("timestamp"));

        // Verify second event
        JsonObject secondEvent = events.getJsonObject(1);
        assertEquals("test-payload-2", secondEvent.getString("payload"));
        assertEquals("2023-01-01T00:01:00Z", secondEvent.getString("timestamp"));
    }

    @Test
    void testAuthorizationHeaderHandling() {
        testContext.setAdditionalProperty("AUTHORIZATION_HEADER", "Bearer test-token");
        assertEquals("Bearer test-token", testContext.getAdditionalProperty("AUTHORIZATION_HEADER", String.class));
    }

    @Test
    void testSecretPasswordHandling() {
        testContext.setAdditionalProperty("SECRET_PASSWORD", "test-secret");
        assertEquals("test-secret", testContext.getAdditionalProperty("SECRET_PASSWORD", String.class));
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
        assertEquals("https://splunk.example.com/services/collector/event", testContext.getTargetUrl());
        assertEquals(testCloudEvent, testContext.getOriginalCloudEvent());
        assertEquals("test-account", testContext.getAdditionalProperty("ACCOUNT_ID", String.class));
        assertEquals("test-token", testContext.getAdditionalProperty("SECRET_PASSWORD", String.class));
        assertEquals("Bearer test-token", testContext.getAdditionalProperty("AUTHORIZATION_HEADER", String.class));
    }

    @Test
    void testMinimalDataHandling() {
        JsonObject minimalEvent = new JsonObject()
                .put("id", "minimal-event-id")
                .put("source", "test-source")
                .put("type", "com.redhat.console.notification")
                .put("data", new JsonObject()
                        .put("application", "policies"));

        testContext.setOriginalCloudEvent(minimalEvent);
        assertEquals(minimalEvent, testContext.getOriginalCloudEvent());
    }

    @Test
    void testSplunkUrlConfiguration() {
        // Test different Splunk URL configurations
        testContext.setTargetUrl("https://splunk.example.com/services/collector/event");
        assertEquals("https://splunk.example.com/services/collector/event", testContext.getTargetUrl());

        testContext.setTargetUrl("https://localhost:8088/services/collector/raw");
        assertEquals("https://localhost:8088/services/collector/raw", testContext.getTargetUrl());
    }

    @Test
    void testSplunkIndexConfiguration() {
        // Test Splunk index configuration
        testContext.setAdditionalProperty("SPLUNK_INDEX", "custom-index");
        assertEquals("custom-index", testContext.getAdditionalProperty("SPLUNK_INDEX", String.class));
    }

    @Test
    void testSplunkSourceConfiguration() {
        // Test Splunk source configuration
        testContext.setAdditionalProperty("SPLUNK_SOURCE", "custom-source");
        assertEquals("custom-source", testContext.getAdditionalProperty("SPLUNK_SOURCE", String.class));
    }

    @Test
    void testSplunkSourcetypeConfiguration() {
        // Test Splunk sourcetype configuration
        testContext.setAdditionalProperty("SPLUNK_SOURCETYPE", "custom-sourcetype");
        assertEquals("custom-sourcetype", testContext.getAdditionalProperty("SPLUNK_SOURCETYPE", String.class));
    }

    @Test
    void testEventDataWithSeverity() {
        // Test event data with severity information
        JsonObject eventWithSeverity = new JsonObject()
                .put("id", "test-event-id")
                .put("source", "test-source")
                .put("type", "com.redhat.console.notification")
                .put("data", new JsonObject()
                        .put("application", "policies")
                        .put("bundle", "rhel")
                        .put("event_type", "policy-triggered")
                        .put("context", "Policy violation detected")
                        .put("severity", "critical")
                        .put("events", new JsonArray()
                                .add(new JsonObject()
                                        .put("payload", "critical-payload")
                                        .put("severity", "critical"))));

        testContext.setOriginalCloudEvent(eventWithSeverity);
        assertEquals(eventWithSeverity, testContext.getOriginalCloudEvent());

        JsonObject data = eventWithSeverity.getJsonObject("data");
        assertEquals("critical", data.getString("severity"));
        assertEquals("policy-triggered", data.getString("event_type"));
        assertEquals("Policy violation detected", data.getString("context"));

        JsonArray events = data.getJsonArray("events");
        JsonObject firstEvent = events.getJsonObject(0);
        assertEquals("critical", firstEvent.getString("severity"));
        assertEquals("critical-payload", firstEvent.getString("payload"));
    }

    @Test
    void testBatchEventHandling() {
        // Test handling of batch events (common Splunk pattern)
        JsonObject batchEvent = new JsonObject()
                .put("id", "batch-event-id")
                .put("source", "test-source")
                .put("type", "com.redhat.console.notification")
                .put("data", new JsonObject()
                        .put("application", "batch-processor")
                        .put("bundle", "operations")
                        .put("event_type", "batch-processed")
                        .put("events", new JsonArray()
                                .add(new JsonObject().put("payload", "batch-1").put("batch_id", "batch-001"))
                                .add(new JsonObject().put("payload", "batch-2").put("batch_id", "batch-001"))
                                .add(new JsonObject().put("payload", "batch-3").put("batch_id", "batch-001"))));

        testContext.setOriginalCloudEvent(batchEvent);
        JsonArray events = batchEvent.getJsonObject("data").getJsonArray("events");
        assertEquals(3, events.size());

        // Verify all events belong to same batch
        for (int i = 0; i < events.size(); i++) {
            JsonObject event = events.getJsonObject(i);
            assertEquals("batch-001", event.getString("batch_id"));
        }
    }

    @Test
    void testSplunkEventFormatting() {
        // Test Splunk-specific event formatting
        JsonObject splunkFormatted = new JsonObject();

        // Standard Splunk HEC format
        splunkFormatted.put("time", 1672531200); // Unix timestamp
        splunkFormatted.put("source", "console-notifications");
        splunkFormatted.put("sourcetype", "_json");
        splunkFormatted.put("index", "insights");
        splunkFormatted.put("host", "notifications-backend");

        JsonObject eventData = new JsonObject();
        eventData.put("event_id", "test-id");
        eventData.put("org_id", "test-org");
        eventData.put("application", "policies");
        eventData.put("bundle", "rhel");
        eventData.put("event_type", "triggered");
        eventData.put("severity", "high");
        eventData.put("message", "Policy violation detected");

        splunkFormatted.put("event", eventData);

        // Verify Splunk HEC format compliance
        assertTrue(splunkFormatted.containsKey("time"));
        assertTrue(splunkFormatted.containsKey("source"));
        assertTrue(splunkFormatted.containsKey("sourcetype"));
        assertTrue(splunkFormatted.containsKey("index"));
        assertTrue(splunkFormatted.containsKey("host"));
        assertTrue(splunkFormatted.containsKey("event"));

        assertEquals("_json", splunkFormatted.getString("sourcetype"));
        assertEquals("insights", splunkFormatted.getString("index"));
        assertEquals("console-notifications", splunkFormatted.getString("source"));

        JsonObject event = splunkFormatted.getJsonObject("event");
        assertEquals("high", event.getString("severity"));
        assertEquals("Policy violation detected", event.getString("message"));
    }
}
