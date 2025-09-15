package com.redhat.cloud.notifications.connector;

import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;

import static java.time.ZoneOffset.UTC;

/**
 * Builds outgoing CloudEvents for sending results back to the engine.
 * This is the new version that replaces the Camel-based OutgoingCloudEventBuilder.
 */
@DefaultBean
@ApplicationScoped
public class OutgoingCloudEventBuilder {

    public static final String CE_SPEC_VERSION = "1.0";
    public static final String CE_TYPE = "com.redhat.console.notifications.history";

    public JsonObject build(ConnectorProcessor.ConnectorResult result) {
        return build(
                result.getId(),
                result.getOrgId(),
                result.isSuccessful(),
                result.getOutcome(),
                extractDetails(result)
        );
    }

    public JsonObject build(
            String id,
            String orgId,
            boolean successful,
            String outcome,
            JsonObject details) {

        JsonObject data = new JsonObject();
        data.put("successful", successful);
        data.put("duration", calculateDuration());
        data.put("details", details);

        Log.infof("Notification sent [orgId=%s, historyId=%s, duration=%d, successful=%b]",
                orgId,
                id,
                data.getLong("duration"),
                successful);

        JsonObject outgoingCloudEvent = new JsonObject();
        outgoingCloudEvent.put("type", CE_TYPE);
        outgoingCloudEvent.put("specversion", CE_SPEC_VERSION);
        outgoingCloudEvent.put("source", determineSource(orgId));
        outgoingCloudEvent.put("id", id);
        outgoingCloudEvent.put("time", LocalDateTime.now(UTC).toString());
        outgoingCloudEvent.put("data", data.encode());

        return outgoingCloudEvent;
    }

    private JsonObject extractDetails(ConnectorProcessor.ConnectorResult result) {
        JsonObject details = new JsonObject();

        // Extract details from the original cloud event if available
        JsonObject originalCloudEvent = result.getOriginalCloudEvent();
        if (originalCloudEvent != null) {
            JsonObject data = originalCloudEvent.getJsonObject("data");
            if (data != null) {
                details.put("type", data.getString("type"));
                details.put("target", data.getString("target"));
            }
        }

        details.put("outcome", result.getOutcome());
        return details;
    }

    private long calculateDuration() {
        // For now, return 0. In a full implementation, you'd track start time.
        // This could be enhanced by storing start time in the processing context.
        return 0L;
    }

    private String determineSource(String orgId) {
        // In the original implementation, this was stored as RETURN_SOURCE
        // For now, we'll use a default format. This should be enhanced based on actual requirements.
        return String.format("notifications-connector-%s", orgId);
    }
}
