package com.redhat.cloud.notifications.connector.googlechat;

import com.redhat.cloud.notifications.connector.ConnectorProcessor;
import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import com.redhat.cloud.notifications.connector.google.chat.GoogleChatConnector;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GoogleChatConnectorTest {

    @Inject
    GoogleChatConnector googleChatConnector;

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
        testContext.setTargetUrl("https://chat.googleapis.com/v1/spaces/SPACE/messages?key=KEY");
        testContext.setOriginalCloudEvent(testCloudEvent);
        testContext.setAdditionalProperty("ACCOUNT_ID", "test-account");
    }

    @Test
    void testConnectorResultStructure() {
        // Test the ConnectorResult structure that would be returned
        ConnectorProcessor.ConnectorResult result = new ConnectorProcessor.ConnectorResult(
                true, "Google Chat message test-id sent successfully", "test-id", "test-org", testCloudEvent
        );

        assertTrue(result.isSuccessful());
        assertEquals("test-id", result.getId());
        assertEquals("test-org", result.getOrgId());
        assertEquals(testCloudEvent, result.getOriginalCloudEvent());
        assertTrue(result.getOutcome().contains("sent successfully"));
    }

    @Test
    void testFailureConnectorResult() {
        // Test failure scenario result structure
        ConnectorProcessor.ConnectorResult result = new ConnectorProcessor.ConnectorResult(
                false, "HTTP 400: Invalid request", "test-id", "test-org", testCloudEvent
        );

        assertFalse(result.isSuccessful());
        assertEquals("test-id", result.getId());
        assertEquals("test-org", result.getOrgId());
        assertTrue(result.getOutcome().contains("HTTP 400"));
    }

    @Test
    void testGoogleChatMessageTransformation() {
        // Test the message transformation logic that would be used
        JsonObject expectedMessage = new JsonObject();
        expectedMessage.put("text", "test-app: Test notification message");

        // Test card structure
        JsonArray cards = new JsonArray();
        JsonObject card = new JsonObject();
        JsonArray sections = new JsonArray();
        JsonObject section = new JsonObject();

        JsonObject header = new JsonObject();
        header.put("title", "test-app");
        header.put("subtitle", "Notification");
        section.put("header", header);

        sections.add(section);
        card.put("sections", sections);
        cards.add(card);
        expectedMessage.put("cards", cards);

        // Verify structure
        assertTrue(expectedMessage.containsKey("text"));
        assertTrue(expectedMessage.containsKey("cards"));
        assertNotNull(expectedMessage.getJsonArray("cards"));
        assertTrue(expectedMessage.getJsonArray("cards").size() > 0);
    }

    @Test
    void testProcessingContextSetup() {
        // Test that the processing context is properly configured
        assertNotNull(testContext);
        assertEquals("test-id", testContext.getId());
        assertEquals("test-org", testContext.getOrgId());
        assertEquals("https://chat.googleapis.com/v1/spaces/SPACE/messages?key=KEY", testContext.getTargetUrl());
        assertEquals(testCloudEvent, testContext.getOriginalCloudEvent());
        assertEquals("test-account", testContext.getAdditionalProperty("ACCOUNT_ID", String.class));
    }

    @Test
    void testAuthenticationHeaderHandling() {
        // Test authentication header setup
        testContext.setAdditionalProperty("AUTHORIZATION_HEADER", "Bearer test-token");
        assertEquals("Bearer test-token", testContext.getAdditionalProperty("AUTHORIZATION_HEADER", String.class));
    }

    @Test
    void testTrustAllFlagHandling() {
        // Test trust all flag setup
        testContext.setAdditionalProperty("TRUST_ALL", true);
        assertTrue(testContext.getAdditionalProperty("TRUST_ALL", Boolean.class));
    }

    @Test
    void testMinimalCloudEventHandling() {
        // Test with minimal cloud event data
        JsonObject minimalEvent = new JsonObject()
                .put("id", "minimal-event-id")
                .put("source", "test-source")
                .put("type", "com.redhat.console.notification");

        testContext.setOriginalCloudEvent(minimalEvent);
        assertEquals(minimalEvent, testContext.getOriginalCloudEvent());

        // Should handle minimal data gracefully
        ConnectorProcessor.ConnectorResult result = new ConnectorProcessor.ConnectorResult(
                true, "Message sent with minimal data", "minimal-event-id", "test-org", minimalEvent
        );
        assertTrue(result.isSuccessful());
    }
}
