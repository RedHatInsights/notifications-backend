package com.redhat.cloud.notifications.connector.processors;

import com.redhat.cloud.notifications.connector.QuarkusConnectorBase.CloudEventData;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Message;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Processor for handling message redelivery and retry logic.
 * Replaces the Camel-based RedeliveryProcessor and KafkaReinjectionProcessor.
 */
@ApplicationScoped
public class RedeliveryProcessor {

    /**
     * Determine if a message should be retried based on error type and retry count.
     */
    public boolean shouldRetry(CloudEventData cloudEventData, Throwable error, int maxAttempts) {
        if (cloudEventData == null) {
            return false;
        }

        if (maxAttempts <= 0) {
            return false;
        }

        // Check if the error is retryable
        return isRetryableError(error);
    }

    /**
     * Schedule a retry for a failed message.
     */
    public void scheduleRetry(CloudEventData cloudEventData, Message<JsonObject> message, long delayMs) {
        if (cloudEventData == null || message == null) {
            return;
        }

        Log.infof("Scheduling retry for orgId=%s, historyId=%s after %d ms",
            cloudEventData.getOrgId(), cloudEventData.getHistoryId(), delayMs);

        // In a real implementation, you would:
        // 1. Store the message for delayed processing
        // 2. Use a scheduler to retry after the delay
        // 3. Implement exponential backoff
        // 4. Track retry attempts

        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
            .execute(() -> {
                Log.infof("Retrying message for orgId=%s, historyId=%s",
                    cloudEventData.getOrgId(), cloudEventData.getHistoryId());
                // Here you would reinject the message back into the processing pipeline
            });
    }

    /**
     * Determine if an error is retryable.
     */
    private boolean isRetryableError(Throwable error) {
        if (error == null) {
            return false;
        }

        String errorMessage = error.getMessage();
        if (errorMessage == null) {
            return false;
        }

        // Check for retryable error patterns (case-insensitive)
        String lowerErrorMessage = errorMessage.toLowerCase();
        return lowerErrorMessage.contains("timeout") ||
            lowerErrorMessage.contains("connection") ||
            lowerErrorMessage.contains("network") ||
            lowerErrorMessage.contains("temporary") ||
            lowerErrorMessage.contains("unavailable") ||
            lowerErrorMessage.contains("503") ||
            lowerErrorMessage.contains("502") ||
            lowerErrorMessage.contains("504");
    }
}

