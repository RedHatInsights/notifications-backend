package com.redhat.cloud.notifications.connector;

import io.quarkus.test.Mock;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Test utilities for Quarkus connector testing.
 * Provides mock implementations and helper methods.
 */
@ApplicationScoped
public class QuarkusConnectorTestUtils {

    /**
     * Mock REST client for testing.
     */
    @Mock
    @RestClient
    public static class MockRestClient {
        public JsonObject sendRequest(JsonObject request) {
            return new JsonObject().put("status", "success");
        }
    }

    /**
     * Helper method to create test CloudEvent data.
     */
    public static QuarkusConnectorBase.CloudEventData createTestCloudEventData(String orgId, String historyId, String connector) {
        JsonObject payload = new JsonObject()
            .put("org_id", orgId)
            .put("history_id", historyId)
            .put("connector", connector)
            .put("test", true);

        return new QuarkusConnectorBase.CloudEventData(orgId, historyId, connector, payload);
    }

    /**
     * Helper method to create test message.
     */
    public static Message<JsonObject> createTestMessage(JsonObject payload) {
        return Message.of(payload);
    }

    /**
     * Helper method to simulate async processing.
     */
    public static CompletionStage<Message<JsonObject>> simulateAsyncProcessing(Message<JsonObject> message, boolean shouldSucceed) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Simulate processing time
                Thread.sleep(100);

                if (shouldSucceed) {
                    JsonObject response = new JsonObject()
                        .put("type", "com.redhat.console.notification.fromCamel.test")
                        .put("specversion", "1.0")
                        .put("id", message.getPayload().getString("id"))
                        .put("source", "test-connector")
                        .put("time", java.time.Instant.now().toString())
                        .put("data", new JsonObject()
                            .put("successful", true)
                            .put("duration", "100ms")
                            .put("details", new JsonObject()
                                .put("outcome", "SUCCESS")
                                .put("type", "test")));

                    return Message.of(response);
                } else {
                    throw new RuntimeException("Simulated processing error");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Processing interrupted", e);
            }
        });
    }

    /**
     * Helper method to create error response.
     */
    public static JsonObject createErrorResponse(String errorMessage) {
        return new JsonObject()
            .put("successful", false)
            .put("error", errorMessage)
            .put("details", new JsonObject()
                .put("outcome", "FAILED")
                .put("error", errorMessage));
    }

    /**
     * Helper method to create success response.
     */
    public static JsonObject createSuccessResponse() {
        return new JsonObject()
            .put("successful", true)
            .put("duration", "100ms")
            .put("details", new JsonObject()
                .put("outcome", "SUCCESS")
                .put("type", "test"));
    }

    /**
     * Helper method to verify message structure.
     */
    public static void verifyMessageStructure(Message<JsonObject> message) {
        assert message != null : "Message should not be null";
        assert message.getPayload() != null : "Message payload should not be null";

        JsonObject payload = message.getPayload();
        assert payload.containsKey("type") : "Message should contain 'type' field";
        assert payload.containsKey("specversion") : "Message should contain 'specversion' field";
        assert payload.containsKey("id") : "Message should contain 'id' field";
        assert payload.containsKey("source") : "Message should contain 'source' field";
        assert payload.containsKey("time") : "Message should contain 'time' field";
        assert payload.containsKey("data") : "Message should contain 'data' field";
    }
}

