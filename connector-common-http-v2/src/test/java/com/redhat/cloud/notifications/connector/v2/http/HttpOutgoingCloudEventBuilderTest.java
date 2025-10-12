package com.redhat.cloud.notifications.connector.v2.http;

import com.redhat.cloud.notifications.connector.v2.MessageContext;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.smallrye.reactive.messaging.ce.impl.DefaultIncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static com.redhat.cloud.notifications.connector.v2.CommonConstants.ENDPOINT_ID;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.ORG_ID;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.OUTCOME;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.RETURN_SOURCE;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.START_TIME;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.SUCCESSFUL;
import static com.redhat.cloud.notifications.connector.v2.CommonConstants.TARGET_URL;
import static com.redhat.cloud.notifications.connector.v2.http.CommonHttpConstants.HTTP_ERROR_TYPE;
import static com.redhat.cloud.notifications.connector.v2.http.CommonHttpConstants.HTTP_STATUS_CODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class HttpOutgoingCloudEventBuilderTest {

    @Inject
    HttpOutgoingCloudEventBuilder httpOutgoingCloudEventBuilder;

    private MessageContext context;

    @BeforeEach
    void setUp() {
        context = new MessageContext();

        // Set up incoming CloudEvent metadata
        context.setIncomingCloudEventMetadata(
            buildIncomingCloudEvent(
                "test-http-event-id",
                "com.redhat.console.notification.toCamel.http",
                new JsonObject().put("test", "data")
            )
        );

        // Set required properties
        context.setProperty(ORG_ID, "test-org-123");
        context.setProperty(ENDPOINT_ID, "endpoint-456");
        context.setProperty(RETURN_SOURCE, "notifications-connector-http");
        context.setProperty(TARGET_URL, "https://example.com/webhook");
        context.setProperty(START_TIME, System.currentTimeMillis());
        context.setProperty(SUCCESSFUL, false);
        context.setProperty(OUTCOME, "HTTP request failed");
    }

    @Test
    void testBuildWithHttpError() throws Exception {
        // Given - HTTP error context
        context.setProperty(HTTP_ERROR_TYPE, HttpErrorType.HTTP_5XX);
        context.setProperty(HTTP_STATUS_CODE, 500);

        // When
        Message<String> cloudEventMessage = httpOutgoingCloudEventBuilder.build(context);

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
    void testBuildWith4xxError() throws Exception {
        // Given - HTTP 4xx error
        context.setProperty(HTTP_ERROR_TYPE, HttpErrorType.HTTP_4XX);
        context.setProperty(HTTP_STATUS_CODE, 404);

        // When
        Message<String> cloudEventMessage = httpOutgoingCloudEventBuilder.build(context);

        // Then
        JsonObject data = new JsonObject(cloudEventMessage.getPayload());
        JsonObject error = data.getJsonObject("error");

        assertNotNull(error);
        assertEquals("HTTP_4XX", error.getString("error_type"));
        assertEquals(404, error.getInteger("http_status_code"));
    }

    @Test
    void testBuildWith3xxError() throws Exception {
        // Given - HTTP 3xx redirect treated as error
        context.setProperty(HTTP_ERROR_TYPE, HttpErrorType.HTTP_3XX);
        context.setProperty(HTTP_STATUS_CODE, 301);

        // When
        Message<String> cloudEventMessage = httpOutgoingCloudEventBuilder.build(context);

        // Then
        JsonObject data = new JsonObject(cloudEventMessage.getPayload());
        JsonObject error = data.getJsonObject("error");

        assertNotNull(error);
        assertEquals("HTTP_3XX", error.getString("error_type"));
        assertEquals(301, error.getInteger("http_status_code"));
    }

    @Test
    void testBuildWithConnectionError() throws Exception {
        // Given - Connection error (no HTTP status code)
        context.setProperty(HTTP_ERROR_TYPE, HttpErrorType.CONNECTION_REFUSED);

        // When
        Message<String> cloudEventMessage = httpOutgoingCloudEventBuilder.build(context);

        // Then
        JsonObject data = new JsonObject(cloudEventMessage.getPayload());
        JsonObject error = data.getJsonObject("error");

        assertNotNull(error);
        assertEquals("CONNECTION_REFUSED", error.getString("error_type"));
        // Should not have http_status_code for connection errors
        assertEquals(null, error.getInteger("http_status_code"));
    }

    @Test
    void testBuildWithoutHttpError() throws Exception {
        // Given - No HTTP error (should behave like base class)
        context.setProperty(SUCCESSFUL, true);
        context.setProperty(OUTCOME, "Success");
        // Don't set HTTP_ERROR_TYPE

        // When
        Message<String> cloudEventMessage = httpOutgoingCloudEventBuilder.build(context);

        // Then - should not have error object
        JsonObject data = new JsonObject(cloudEventMessage.getPayload());
        assertEquals(true, data.getBoolean("successful"));
        assertEquals(null, data.getJsonObject("error"));
    }

    @Test
    void testPreservesCloudEventMetadata() throws Exception {
        // Given - HTTP error
        context.setProperty(HTTP_ERROR_TYPE, HttpErrorType.HTTP_5XX);
        context.setProperty(HTTP_STATUS_CODE, 503);

        // When
        Message<String> cloudEventMessage = httpOutgoingCloudEventBuilder.build(context);

        // Then - CloudEvent metadata should be preserved
        OutgoingCloudEventMetadata<?> metadata = cloudEventMessage.getMetadata(OutgoingCloudEventMetadata.class)
            .orElseThrow(() -> new AssertionError("CloudEvent metadata not found"));

        assertEquals("test-http-event-id", metadata.getId());
        assertEquals("com.redhat.console.notifications.history", metadata.getType());
        assertNotNull(metadata.getSource());
        assertNotNull(metadata.getTimeStamp());
    }

    private static IncomingCloudEventMetadata<JsonObject> buildIncomingCloudEvent(String cloudEventId, String cloudEventType, JsonObject cloudEventData) {
        return new DefaultIncomingCloudEventMetadata(
            "1.0.0",
            cloudEventId,
            URI.create("notification"),
            cloudEventType,
            "application/json",
            null,
            null,
            null,
            null,
            cloudEventData);
    }
}
