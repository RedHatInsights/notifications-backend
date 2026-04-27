package com.redhat.cloud.notifications.connector.servicenow;

import com.redhat.cloud.notifications.connector.v2.BaseConnectorIntegrationTest;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledExceptionDetails;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.connector.servicenow.ServiceNowMessageHandler.NOTIF_METADATA;
import static com.redhat.cloud.notifications.connector.servicenow.ServiceNowNotification.URL_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class ServiceNowExceptionHandlerTest {

    @Inject
    ServiceNowExceptionHandler serviceNowExceptionHandler;

    @Test
    void testProcessExtractsTargetUrlFromMetadata() {
        String expectedUrl = "https://snow.example.com/api/incident";
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent(expectedUrl);

        HandledExceptionDetails result = serviceNowExceptionHandler.process(
            new RuntimeException("test error"), incomingCloudEvent);

        assertInstanceOf(HandledHttpExceptionDetails.class, result);
        HandledHttpExceptionDetails httpDetails = (HandledHttpExceptionDetails) result;
        assertEquals(expectedUrl, httpDetails.targetUrl);
    }

    @Test
    void testProcessWithNullMetadata() {
        JsonObject payload = new JsonObject()
            .put("org_id", "12345")
            .put("account_id", "67890");
        // notif-metadata is intentionally not set

        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = BaseConnectorIntegrationTest.buildIncomingCloudEvent(
            "test-event-id", "com.redhat.console.notification.toCamel.servicenow", payload);

        HandledExceptionDetails result = serviceNowExceptionHandler.process(
            new RuntimeException("test error"), incomingCloudEvent);

        assertInstanceOf(HandledHttpExceptionDetails.class, result);
        HandledHttpExceptionDetails httpDetails = (HandledHttpExceptionDetails) result;
        assertNull(httpDetails.targetUrl);
    }

    @Test
    void testProcessWithMissingUrlInMetadata() {
        JsonObject metadata = new JsonObject();
        // url is intentionally not set

        JsonObject payload = new JsonObject()
            .put("org_id", "12345")
            .put("account_id", "67890")
            .put(NOTIF_METADATA, metadata);

        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = BaseConnectorIntegrationTest.buildIncomingCloudEvent(
            "test-event-id", "com.redhat.console.notification.toCamel.servicenow", payload);

        HandledExceptionDetails result = serviceNowExceptionHandler.process(
            new RuntimeException("test error"), incomingCloudEvent);

        assertInstanceOf(HandledHttpExceptionDetails.class, result);
        HandledHttpExceptionDetails httpDetails = (HandledHttpExceptionDetails) result;
        assertNull(httpDetails.targetUrl);
    }

    @Test
    void testProcessWithMetadataNotAJsonObject() {
        JsonObject payload = new JsonObject()
            .put("org_id", "12345")
            .put("account_id", "67890")
            .put(NOTIF_METADATA, "not-a-json-object");

        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = BaseConnectorIntegrationTest.buildIncomingCloudEvent(
            "test-event-id", "com.redhat.console.notification.toCamel.servicenow", payload);

        HandledExceptionDetails result = serviceNowExceptionHandler.process(
            new RuntimeException("test error"), incomingCloudEvent);

        assertInstanceOf(HandledHttpExceptionDetails.class, result);
        HandledHttpExceptionDetails httpDetails = (HandledHttpExceptionDetails) result;
        // Should not extract URL when metadata is not a JsonObject
        assertNull(httpDetails.targetUrl);
    }

    @Test
    void testProcessReturnsNonNullDetails() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://snow.example.com");

        HandledExceptionDetails result = serviceNowExceptionHandler.process(
            new RuntimeException("test error"), incomingCloudEvent);

        assertNotNull(result);
    }

    private IncomingCloudEventMetadata<JsonObject> buildIncomingCloudEvent(String targetUrl) {
        JsonObject metadata = new JsonObject()
            .put(URL_KEY, targetUrl);

        JsonObject payload = new JsonObject()
            .put("org_id", "12345")
            .put("account_id", "67890")
            .put(NOTIF_METADATA, metadata);

        return BaseConnectorIntegrationTest.buildIncomingCloudEvent(
            "test-event-id", "com.redhat.console.notification.toCamel.servicenow", payload);
    }
}
