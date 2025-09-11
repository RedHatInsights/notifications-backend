package com.redhat.cloud.notifications.connector.webhook.processors;

import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Processor for webhook-specific logic.
 * Replaces the Camel-based webhook processing.
 */
@ApplicationScoped
public class WebhookProcessor {

    /**
     * Process webhook payload and prepare it for delivery.
     */
    public JsonObject processWebhookPayload(JsonObject payload) {
        try {
            Log.debugf("Processing webhook payload: %s", payload.encode());

            // Remove internal properties that shouldn't be sent to external webhooks
            JsonObject processedPayload = payload.copy();
            processedPayload.remove("org_id");
            processedPayload.remove("history_id");
            processedPayload.remove("trust_all");

            // Add webhook-specific metadata
            processedPayload.put("webhook_timestamp", System.currentTimeMillis());
            processedPayload.put("webhook_version", "1.0");

            Log.debugf("Processed webhook payload: %s", processedPayload.encode());
            return processedPayload;

        } catch (Exception e) {
            Log.errorf(e, "Error processing webhook payload: %s", payload.encode());
            throw new RuntimeException("Failed to process webhook payload", e);
        }
    }

    /**
     * Validate webhook target URL.
     */
    public boolean isValidTargetUrl(String targetUrl) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            return false;
        }

        // Basic URL validation
        return targetUrl.startsWith("http://") || targetUrl.startsWith("https://");
    }

    /**
     * Extract target URL from payload.
     */
    public String extractTargetUrl(JsonObject payload) {
        return payload.getString("target_url");
    }
}


