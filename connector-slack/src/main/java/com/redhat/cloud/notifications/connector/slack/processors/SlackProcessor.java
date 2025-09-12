package com.redhat.cloud.notifications.connector.slack.processors;

import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Processor for Slack-specific logic.
 * Replaces the Camel-based Slack processing.
 */
@ApplicationScoped
public class SlackProcessor {

    /**
     * Process Slack payload and prepare it for delivery.
     */
    public JsonObject processSlackPayload(JsonObject payload) {
        try {
            Log.debugf("Processing Slack payload: %s", payload.encode());

            // Remove internal properties that shouldn't be sent to Slack
            JsonObject processedPayload = payload.copy();
            processedPayload.remove("org_id");
            processedPayload.remove("history_id");
            processedPayload.remove("target_url");

            // Add Slack-specific metadata
            processedPayload.put("slack_timestamp", System.currentTimeMillis());
            processedPayload.put("slack_version", "1.0");

            // Ensure proper Slack message format
            if (!processedPayload.containsKey("text") && !processedPayload.containsKey("blocks")) {
                // If no text or blocks, add a default text field
                processedPayload.put("text", "Notification from Red Hat");
            }

            Log.debugf("Processed Slack payload: %s", processedPayload.encode());
            return processedPayload;

        } catch (Exception e) {
            Log.errorf(e, "Error processing Slack payload: %s", payload.encode());
            throw new RuntimeException("Failed to process Slack payload", e);
        }
    }

    /**
     * Validate Slack target URL.
     */
    public boolean isValidSlackUrl(String targetUrl) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            return false;
        }

        // Slack webhook URLs typically start with https://hooks.slack.com
        return targetUrl.startsWith("https://hooks.slack.com/");
    }

    /**
     * Extract target URL from payload.
     */
    public String extractTargetUrl(JsonObject payload) {
        return payload.getString("target_url");
    }
}


