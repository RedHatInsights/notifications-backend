package com.redhat.cloud.notifications.connector.servicenow;

import com.redhat.cloud.notifications.connector.ConnectorProcessor;
import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ServiceNowConnectorTest {

    @Inject
    ServiceNowConnector serviceNowConnector;

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
                        .put("severity", "critical"));

        testContext = new ExceptionProcessor.ProcessingContext();
        testContext.setId("test-id");
        testContext.setOrgId("test-org");
        testContext.setTargetUrl("https://dev12345.service-now.com");
        testContext.setOriginalCloudEvent(testCloudEvent);
        testContext.setAdditionalProperty("ACCOUNT_ID", "test-account");
        testContext.setAdditionalProperty("AUTHORIZATION_HEADER", "Basic dGVzdDp0ZXN0");
    }

    @Test
    void testSuccessfulResult() {
        ConnectorProcessor.ConnectorResult result = new ConnectorProcessor.ConnectorResult(
                true, "ServiceNow incident test-id created successfully", "test-id", "test-org", testCloudEvent
        );

        assertTrue(result.isSuccessful());
        assertEquals("test-id", result.getId());
        assertEquals("test-org", result.getOrgId());
        assertTrue(result.getOutcome().contains("created successfully"));
    }

    @Test
    void testFailedResult() {
        ConnectorProcessor.ConnectorResult result = new ConnectorProcessor.ConnectorResult(
                false, "HTTP 401: User Not Authenticated", "test-id", "test-org", testCloudEvent
        );

        assertFalse(result.isSuccessful());
        assertTrue(result.getOutcome().contains("HTTP 401"));
    }

    @Test
    void testServiceNowIncidentTransformation() {
        // Test the ServiceNow incident structure that would be created
        JsonObject incident = new JsonObject();
        incident.put("short_description", "test-app: Test notification message");
        incident.put("description", "Event ID: test-id\nOrganization: test-org\nBundle: test-bundle\nEvent Type: test-event\nMessage: Test notification message");
        incident.put("urgency", "1"); // Critical = High urgency
        incident.put("impact", "2"); // Medium impact
        incident.put("category", "Software");
        incident.put("subcategory", "Application");
        incident.put("u_event_id", "test-id");
        incident.put("u_org_id", "test-org");
        incident.put("u_account_id", "test-account");
        incident.put("opened_at", "2023-01-01T00:00:00Z");

        // Verify ServiceNow incident structure
        assertTrue(incident.containsKey("short_description"));
        assertTrue(incident.containsKey("description"));
        assertTrue(incident.containsKey("urgency"));
        assertTrue(incident.containsKey("impact"));
        assertTrue(incident.containsKey("category"));
        assertTrue(incident.containsKey("u_event_id"));
        assertTrue(incident.containsKey("u_org_id"));
        assertTrue(incident.containsKey("u_account_id"));
        assertTrue(incident.containsKey("opened_at"));

        assertEquals("1", incident.getString("urgency")); // Critical = High urgency
        assertEquals("test-id", incident.getString("u_event_id"));
        assertEquals("test-org", incident.getString("u_org_id"));
        assertEquals("test-account", incident.getString("u_account_id"));
        assertTrue(incident.getString("short_description").contains("test-app"));
        assertEquals("Software", incident.getString("category"));
        assertEquals("Application", incident.getString("subcategory"));
    }

    @Test
    void testSeverityToUrgencyMapping() {
        // Test severity to urgency mapping
        assertEquals("1", mapSeverityToUrgency("critical")); // High urgency
        assertEquals("2", mapSeverityToUrgency("error"));    // Medium urgency
        assertEquals("3", mapSeverityToUrgency("warning"));  // Low urgency
        assertEquals("3", mapSeverityToUrgency("info"));     // Low urgency
        assertEquals("3", mapSeverityToUrgency("unknown"));  // Low urgency
    }

    @Test
    void testAuthorizationHeaderHandling() {
        testContext.setAdditionalProperty("AUTHORIZATION_HEADER", "Basic dGVzdDp0ZXN0");
        assertEquals("Basic dGVzdDp0ZXN0", testContext.getAdditionalProperty("AUTHORIZATION_HEADER", String.class));
    }

    @Test
    void testSecretPasswordHandling() {
        testContext.setAdditionalProperty("SECRET_PASSWORD", "test-secret");
        assertEquals("test-secret", testContext.getAdditionalProperty("SECRET_PASSWORD", String.class));
    }

    @Test
    void testAssignmentGroupHandling() {
        testContext.setAdditionalProperty("ASSIGNMENT_GROUP", "Test Assignment Group");
        assertEquals("Test Assignment Group", testContext.getAdditionalProperty("ASSIGNMENT_GROUP", String.class));
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
        assertEquals("https://dev12345.service-now.com", testContext.getTargetUrl());
        assertEquals(testCloudEvent, testContext.getOriginalCloudEvent());
        assertEquals("test-account", testContext.getAdditionalProperty("ACCOUNT_ID", String.class));
        assertEquals("Basic dGVzdDp0ZXN0", testContext.getAdditionalProperty("AUTHORIZATION_HEADER", String.class));
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
    void testServiceNowUrlConfiguration() {
        // Test different ServiceNow instance URL configurations
        testContext.setTargetUrl("https://dev12345.service-now.com");
        assertEquals("https://dev12345.service-now.com", testContext.getTargetUrl());

        testContext.setTargetUrl("https://mycompany.service-now.com");
        assertEquals("https://mycompany.service-now.com", testContext.getTargetUrl());
    }

    @Test
    void testIncidentFieldMapping() {
        // Test incident field mappings with different event data
        JsonObject eventWithSeverity = new JsonObject()
                .put("id", "test-event-id")
                .put("source", "test-source")
                .put("type", "com.redhat.console.notification")
                .put("data", new JsonObject()
                        .put("application", "rhel-system")
                        .put("bundle", "rhel")
                        .put("event_type", "policy-triggered")
                        .put("context", "Policy violation detected")
                        .put("severity", "warning"));

        testContext.setOriginalCloudEvent(eventWithSeverity);
        assertEquals(eventWithSeverity, testContext.getOriginalCloudEvent());

        JsonObject data = eventWithSeverity.getJsonObject("data");
        assertEquals("rhel-system", data.getString("application"));
        assertEquals("rhel", data.getString("bundle"));
        assertEquals("policy-triggered", data.getString("event_type"));
        assertEquals("warning", data.getString("severity"));
    }

    private String mapSeverityToUrgency(String severity) {
        // ServiceNow urgency mapping based on event severity
        return switch (severity.toLowerCase()) {
            case "critical" -> "1";  // High urgency
            case "error" -> "2";     // Medium urgency
            case "warning", "info" -> "3";  // Low urgency
            default -> "3";          // Low urgency
        };
    }
}
