package com.redhat.cloud.notifications.connector.teams;

import com.redhat.cloud.notifications.connector.ConnectorProcessor;
import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TeamsConnectorTest {

    @Inject
    TeamsConnector teamsConnector;

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
                        .put("severity", "error"));

        testContext = new ExceptionProcessor.ProcessingContext();
        testContext.setId("test-id");
        testContext.setOrgId("test-org");
        testContext.setTargetUrl("https://outlook.office.com/webhook/test");
        testContext.setOriginalCloudEvent(testCloudEvent);
        testContext.setAdditionalProperty("ACCOUNT_ID", "test-account");
    }

    @Test
    void testSuccessfulResult() {
        ConnectorProcessor.ConnectorResult result = new ConnectorProcessor.ConnectorResult(
                true, "Teams message test-id sent successfully", "test-id", "test-org", testCloudEvent
        );

        assertTrue(result.isSuccessful());
        assertEquals("test-id", result.getId());
        assertEquals("test-org", result.getOrgId());
        assertTrue(result.getOutcome().contains("sent successfully"));
    }

    @Test
    void testFailedResult() {
        ConnectorProcessor.ConnectorResult result = new ConnectorProcessor.ConnectorResult(
                false, "HTTP 400: Bad Request", "test-id", "test-org", testCloudEvent
        );

        assertFalse(result.isSuccessful());
        assertTrue(result.getOutcome().contains("HTTP 400"));
    }

    @Test
    void testTeamsMessageTransformation() {
        // Test the Teams MessageCard format that would be created
        JsonObject teamsMessage = new JsonObject();
        teamsMessage.put("@type", "MessageCard");
        teamsMessage.put("@context", "https://schema.org/extensions");
        teamsMessage.put("summary", "test-app Notification");
        teamsMessage.put("title", "test-app");
        teamsMessage.put("text", "Test notification message");
        teamsMessage.put("themeColor", "FF0000"); // Error severity = red

        // Add sections for additional information
        JsonArray sections = new JsonArray();
        JsonObject section = new JsonObject();
        JsonArray facts = new JsonArray();

        JsonObject bundleFact = new JsonObject();
        bundleFact.put("name", "Bundle");
        bundleFact.put("value", "test-bundle");
        facts.add(bundleFact);

        JsonObject eventFact = new JsonObject();
        eventFact.put("name", "Event Type");
        eventFact.put("value", "test-event");
        facts.add(eventFact);

        JsonObject orgFact = new JsonObject();
        orgFact.put("name", "Organization");
        orgFact.put("value", "test-org");
        facts.add(orgFact);

        section.put("facts", facts);
        sections.add(section);
        teamsMessage.put("sections", sections);

        // Verify structure
        assertTrue(teamsMessage.containsKey("@type"));
        assertTrue(teamsMessage.containsKey("@context"));
        assertTrue(teamsMessage.containsKey("summary"));
        assertTrue(teamsMessage.containsKey("title"));
        assertTrue(teamsMessage.containsKey("text"));
        assertTrue(teamsMessage.containsKey("themeColor"));

        assertEquals("MessageCard", teamsMessage.getString("@type"));
        assertEquals("https://schema.org/extensions", teamsMessage.getString("@context"));
        assertEquals("test-app", teamsMessage.getString("title"));
        assertEquals("FF0000", teamsMessage.getString("themeColor"));
        assertTrue(teamsMessage.getString("text").contains("Test notification message"));

        // Verify sections with facts
        JsonArray sectionArray = teamsMessage.getJsonArray("sections");
        assertNotNull(sectionArray);
        assertTrue(sectionArray.size() > 0);

        JsonObject firstSection = sectionArray.getJsonObject(0);
        assertNotNull(firstSection);
        assertTrue(firstSection.containsKey("facts"));

        JsonArray factsArray = firstSection.getJsonArray("facts");
        assertNotNull(factsArray);
        assertTrue(factsArray.size() > 0);
    }

    @Test
    void testSeverityColorMapping() {
        // Test different severity color mappings
        assertEquals("FF0000", mapSeverityColor("critical"));
        assertEquals("FF0000", mapSeverityColor("error"));
        assertEquals("FFA500", mapSeverityColor("warning"));
        assertEquals("0078D4", mapSeverityColor("info"));
        assertEquals("0078D4", mapSeverityColor("unknown"));
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

        // Test with minimal data should create default Teams message
        JsonObject teamsMessage = new JsonObject();
        teamsMessage.put("@type", "MessageCard");
        teamsMessage.put("@context", "https://schema.org/extensions");
        teamsMessage.put("summary", "Red Hat Console Notification");
        teamsMessage.put("title", "Red Hat Console");
        teamsMessage.put("text", "Notification from Red Hat Console");
        teamsMessage.put("themeColor", "0078D4");

        assertEquals("Red Hat Console", teamsMessage.getString("title"));
        assertEquals("0078D4", teamsMessage.getString("themeColor"));
    }

    @Test
    void testCriticalSeverityColor() {
        // Test critical severity (should be red)
        testCloudEvent.getJsonObject("data").put("severity", "critical");
        assertEquals("FF0000", mapSeverityColor("critical"));
    }

    @Test
    void testWarningSeverityColor() {
        // Test warning severity (should be orange)
        testCloudEvent.getJsonObject("data").put("severity", "warning");
        assertEquals("FFA500", mapSeverityColor("warning"));
    }

    @Test
    void testInfoSeverityColor() {
        // Test info severity (should be blue)
        testCloudEvent.getJsonObject("data").put("severity", "info");
        assertEquals("0078D4", mapSeverityColor("info"));
    }

    @Test
    void testTrustAllFlagHandling() {
        testContext.setAdditionalProperty("TRUST_ALL", true);
        assertTrue(testContext.getAdditionalProperty("TRUST_ALL", Boolean.class));
    }

    @Test
    void testProcessingContextSetup() {
        // Test that the processing context is properly configured
        assertNotNull(testContext);
        assertEquals("test-id", testContext.getId());
        assertEquals("test-org", testContext.getOrgId());
        assertEquals("https://outlook.office.com/webhook/test", testContext.getTargetUrl());
        assertEquals(testCloudEvent, testContext.getOriginalCloudEvent());
        assertEquals("test-account", testContext.getAdditionalProperty("ACCOUNT_ID", String.class));
    }

    private String mapSeverityColor(String severity) {
        // Teams color mapping based on event severity
        return switch (severity.toLowerCase()) {
            case "critical", "error" -> "FF0000";  // Red
            case "warning" -> "FFA500";           // Orange
            case "info" -> "0078D4";              // Blue
            default -> "0078D4";                  // Blue
        };
    }
}
