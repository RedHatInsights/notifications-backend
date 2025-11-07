package com.redhat.cloud.notifications.connector.v2.http;

import com.redhat.cloud.notifications.connector.v2.BaseConnectorIntegrationTest;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class HttpOutgoingCloudEventBuilderTest {

    @Inject
    HttpOutgoingCloudEventBuilder httpOutgoingCloudEventBuilder;

    @Test
    void testBuildWithHttpError() {
        // Given - HTTP error context
        Message<String> cloudEventMessage = getOutgoingCloudEventMessage(HttpErrorType.HTTP_5XX, 500);

        // Then - verify message exists
        assertNotNull(cloudEventMessage);

        // Verify CloudEvent metadata
        OutgoingCloudEventMetadata<?> metadata = cloudEventMessage.getMetadata(OutgoingCloudEventMetadata.class)
            .orElseThrow(() -> new AssertionError("CloudEvent metadata not found"));

        assertEquals("test-http-event-id", metadata.getId());

        // Verify payload includes error information
        JsonObject data = new JsonObject(cloudEventMessage.getPayload());
        assertEquals(false, data.getBoolean("successful"));

        JsonObject error = data.getJsonObject("error");
        assertNotNull(error, "Error object should be present");
        assertEquals("HTTP_5XX", error.getString("error_type"));
        assertEquals(500, error.getInteger("http_status_code"));
    }

    @Test
    void testBuildWith4xxError() {
        // Given - HTTP 4xx error
        Message<String> cloudEventMessage = getOutgoingCloudEventMessage(HttpErrorType.HTTP_4XX, 404);

        // Then
        JsonObject data = new JsonObject(cloudEventMessage.getPayload());
        JsonObject error = data.getJsonObject("error");

        assertNotNull(error);
        assertEquals("HTTP_4XX", error.getString("error_type"));
        assertEquals(404, error.getInteger("http_status_code"));
    }

    @Test
    void testBuildWith3xxError() {
        // Given - HTTP 3xx error
        Message<String> cloudEventMessage = getOutgoingCloudEventMessage(HttpErrorType.HTTP_3XX, 301);
        // {"successful":false,"duration":1,"details":{"type":"com.redhat.console.notification.toCamel.test","outcome":null}}

        // Then
        JsonObject data = new JsonObject(cloudEventMessage.getPayload());
        JsonObject error = data.getJsonObject("error");

        assertNotNull(error);
        assertEquals("HTTP_3XX", error.getString("error_type"));
        assertEquals(301, error.getInteger("http_status_code"));
    }

    @Test
    void testBuildWithConnectionError() {
        // Given - Connection error (no HTTP status code)
        Message<String> cloudEventMessage = getOutgoingCloudEventMessage(HttpErrorType.CONNECTION_REFUSED, null);

        // Then
        JsonObject data = new JsonObject(cloudEventMessage.getPayload());
        JsonObject error = data.getJsonObject("error");

        assertNotNull(error);
        assertEquals("CONNECTION_REFUSED", error.getString("error_type"));
        // Should not have http_status_code for connection errors
        assertEquals(null, error.getInteger("http_status_code"));
    }

    @Test
    void testBuildWithoutHttpError() {
        // Given - No HTTP error (should behave like base class)

        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = BaseConnectorIntegrationTest.buildIncomingCloudEvent(
            "test-http-event-id",
            "com.redhat.console.notification.toCamel.http",
            new JsonObject().put("test", "data").put("target_url", "https://example.com/webhook")
        );

        HandledMessageDetails processedMessageDetails = new HandledMessageDetails("Success");

        // When
        Message<String> cloudEventMessage = httpOutgoingCloudEventBuilder.buildSuccess(incomingCloudEvent, processedMessageDetails, System.currentTimeMillis());

        // Then - should not have error object
        JsonObject data = new JsonObject(cloudEventMessage.getPayload());
        assertEquals(true, data.getBoolean("successful"));
        assertEquals(null, data.getJsonObject("error"));
    }

    @Test
    void testPreservesCloudEventMetadata() {
        // Given - HTTP error
        Message<String> cloudEventMessage = getOutgoingCloudEventMessage(HttpErrorType.HTTP_5XX, 503);

        // Then - CloudEvent metadata should be preserved
        OutgoingCloudEventMetadata<?> metadata = cloudEventMessage.getMetadata(OutgoingCloudEventMetadata.class)
            .orElseThrow(() -> new AssertionError("CloudEvent metadata not found"));

        assertEquals("test-http-event-id", metadata.getId());
        assertEquals("com.redhat.console.notifications.history", metadata.getType());
        assertNotNull(metadata.getSource());
        assertNotNull(metadata.getTimeStamp());
    }

    private Message<String> getOutgoingCloudEventMessage(HttpErrorType httpErrorType, Integer httpStatusCode) {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = BaseConnectorIntegrationTest.buildIncomingCloudEvent(
            "test-http-event-id",
            "com.redhat.console.notification.toCamel.http",
            new JsonObject().put("test", "data").put("target_url", "https://example.com/webhook")
        );

        HandledHttpExceptionDetails processedExceptionDetailsHttp = new HandledHttpExceptionDetails();
        processedExceptionDetailsHttp.httpErrorType = httpErrorType;
        processedExceptionDetailsHttp.httpStatusCode = httpStatusCode;

        // When
        return httpOutgoingCloudEventBuilder.buildFailure(incomingCloudEvent, processedExceptionDetailsHttp, System.currentTimeMillis());
    }
}
