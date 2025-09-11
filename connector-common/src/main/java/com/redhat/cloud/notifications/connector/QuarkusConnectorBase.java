package com.redhat.cloud.notifications.connector;

import com.redhat.cloud.notifications.connector.processors.CloudEventProcessor;
import com.redhat.cloud.notifications.connector.processors.ErrorProcessor;
import com.redhat.cloud.notifications.connector.processors.RedeliveryProcessor;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Base class for Quarkus-based connectors that replaces the Camel-based EngineToConnectorRouteBuilder.
 * This class provides common functionality for processing notifications from Kafka and sending responses back.
 */
@Default
@ApplicationScoped
public abstract class QuarkusConnectorBase {

    @Inject
    protected ConnectorConfig connectorConfig;

    @Inject
    protected CloudEventProcessor cloudEventProcessor;

    @Inject
    protected ErrorProcessor errorProcessor;

    @Inject
    protected RedeliveryProcessor redeliveryProcessor;

    @Inject
    @Channel("outgoing-notifications")
    Emitter<JsonObject> outgoingEmitter;

    /**
     * Process incoming notification messages from Kafka.
     * This replaces the Camel route configuration.
     */
    public CompletionStage<Message<JsonObject>> processNotification(Message<JsonObject> message) {
        try {
            Log.debugf("Processing notification: %s", message.getPayload());

            // Extract CloudEvent data
            CloudEventData cloudEventData = cloudEventProcessor.extractCloudEventData(message);

            // Process the notification using the connector-specific logic
            ProcessingResult result = processConnectorSpecificLogic(cloudEventData, message.getPayload());

            if (result.isSuccess()) {
                // Send success response back to engine
                sendSuccessResponse(cloudEventData, result.getResponseData());
                return CompletableFuture.completedFuture(message);
            } else {
                // Handle error case
                handleError(cloudEventData, result.getError(), message);
                return CompletableFuture.completedFuture(message);
            }

        } catch (Exception e) {
            Log.errorf(e, "Error processing notification: %s", e.getMessage());
            // Handle unexpected errors
            try {
                CloudEventData cloudEventData = cloudEventProcessor.extractCloudEventData(message);
                handleError(cloudEventData, e, message);
            } catch (Exception ex) {
                Log.errorf(ex, "Failed to extract CloudEvent data for error handling");
            }
            return CompletableFuture.completedFuture(message);
        }
    }

    /**
     * Send success response back to the engine.
     */
    private void sendSuccessResponse(CloudEventData cloudEventData, JsonObject responseData) {
        try {
            JsonObject successMessage = new JsonObject()
                .put("orgId", cloudEventData.getOrgId())
                .put("historyId", cloudEventData.getHistoryId())
                .put("status", "SUCCESS")
                .put("response", responseData);

            Headers kafkaHeaders = new RecordHeaders();
            kafkaHeaders.add("x-rh-notifications-connector", connectorConfig.getConnectorName().getBytes());
            kafkaHeaders.add("x-rh-notifications-history-id", cloudEventData.getHistoryId().getBytes());

            OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String>builder()
                .withTopic(connectorConfig.getOutgoingKafkaTopic())
                .withHeaders(kafkaHeaders)
                .build();

            outgoingEmitter.send(Message.of(successMessage).addMetadata(metadata));

            Log.infof("Success response sent for orgId=%s, historyId=%s",
                cloudEventData.getOrgId(), cloudEventData.getHistoryId());

        } catch (Exception e) {
            Log.errorf(e, "Failed to send success response for orgId=%s, historyId=%s",
                cloudEventData.getOrgId(), cloudEventData.getHistoryId());
        }
    }

    /**
     * Handle error cases and potentially retry or send error response.
     */
    private void handleError(CloudEventData cloudEventData, Throwable error, Message<JsonObject> message) {
        try {
            // Process the error
            errorProcessor.processError(cloudEventData, error, message);

            // Check if we should retry
            if (redeliveryProcessor.shouldRetry(cloudEventData, error, connectorConfig.getRedeliveryMaxAttempts())) {
                Log.infof("Scheduling retry for orgId=%s, historyId=%s", cloudEventData.getOrgId(), cloudEventData.getHistoryId());
                redeliveryProcessor.scheduleRetry(cloudEventData, message, 1000L);
            } else {
                // Send error response back to engine
                sendErrorResponse(cloudEventData, error);
            }

        } catch (Exception e) {
            Log.errorf(e, "Error in error handling for orgId=%s, historyId=%s",
                cloudEventData.getOrgId(), cloudEventData.getHistoryId());
        }
    }

    /**
     * Send error response back to the engine.
     */
    private void sendErrorResponse(CloudEventData cloudEventData, Throwable error) {
        try {
            JsonObject errorMessage = new JsonObject()
                .put("orgId", cloudEventData.getOrgId())
                .put("historyId", cloudEventData.getHistoryId())
                .put("status", "FAILED")
                .put("error", error.getMessage());

            Headers kafkaHeaders = new RecordHeaders();
            kafkaHeaders.add("x-rh-notifications-connector", connectorConfig.getConnectorName().getBytes());
            kafkaHeaders.add("x-rh-notifications-history-id", cloudEventData.getHistoryId().getBytes());

            OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String>builder()
                .withTopic(connectorConfig.getOutgoingKafkaTopic())
                .withHeaders(kafkaHeaders)
                .build();

            outgoingEmitter.send(Message.of(errorMessage).addMetadata(metadata));

            Log.infof("Error response sent for orgId=%s, historyId=%s",
                cloudEventData.getOrgId(), cloudEventData.getHistoryId());

        } catch (Exception e) {
            Log.errorf(e, "Failed to send error response for orgId=%s, historyId=%s",
                cloudEventData.getOrgId(), cloudEventData.getHistoryId());
        }
    }

    /**
     * Abstract method to be implemented by specific connectors.
     * This contains the connector-specific business logic.
     */
    protected abstract ProcessingResult processConnectorSpecificLogic(CloudEventData cloudEventData, JsonObject payload);

    /**
     * Data class for CloudEvent information.
     */
    public static class CloudEventData {
        private final String orgId;
        private final String historyId;
        private final String connector;
        private final JsonObject payload;

        public CloudEventData(String orgId, String historyId, String connector, JsonObject payload) {
            this.orgId = orgId;
            this.historyId = historyId;
            this.connector = connector;
            this.payload = payload;
        }

        public String getOrgId() {
            return orgId;
        }

        public String getHistoryId() {
            return historyId;
        }

        public String getConnector() {
            return connector;
        }

        public JsonObject getPayload() {
            return payload;
        }
    }

    /**
     * Result class for processing operations.
     */
    public static class ProcessingResult {
        private final boolean success;
        private final JsonObject responseData;
        private final Throwable error;

        private ProcessingResult(boolean success, JsonObject responseData, Throwable error) {
            this.success = success;
            this.responseData = responseData;
            this.error = error;
        }

        public static ProcessingResult success(JsonObject responseData) {
            return new ProcessingResult(true, responseData, null);
        }

        public static ProcessingResult failure(Throwable error) {
            return new ProcessingResult(false, null, error);
        }

        public boolean isSuccess() {
            return success;
        }

        public JsonObject getResponseData() {
            return responseData;
        }

        public Throwable getError() {
            return error;
        }
    }
}

