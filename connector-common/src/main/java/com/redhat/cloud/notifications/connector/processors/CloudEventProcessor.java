package com.redhat.cloud.notifications.connector.processors;

import com.redhat.cloud.notifications.connector.QuarkusConnectorBase.CloudEventData;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Message;
import java.util.Optional;

/**
 * Processor for extracting CloudEvent data from incoming messages.
 * Replaces the Camel-based CloudEvent data extraction logic.
 */
@ApplicationScoped
public class CloudEventProcessor {

    /**
     * Extract CloudEvent data from a message.
     */
    public CloudEventData extractCloudEventData(Message<JsonObject> message) {
        JsonObject payload = message.getPayload();
        Optional<CloudEventMetadata> cloudEventMetadata = message.getMetadata(CloudEventMetadata.class);

        String orgId = payload.getString("org_id");
        String historyId = cloudEventMetadata.map(CloudEventMetadata::getId)
            .filter(id -> id != null && !id.trim().isEmpty())
            .orElse(payload.getString("id", "unknown"));
        String connector = extractConnectorFromType(cloudEventMetadata.map(CloudEventMetadata::getType).orElse("unknown"));

        return new CloudEventData(orgId, historyId, connector, payload);
    }

    /**
     * Extract connector name from CloudEvent type.
     */
    private String extractConnectorFromType(String cloudEventType) {
        if (cloudEventType == null || cloudEventType.isEmpty()) {
            return "unknown";
        }

        // Extract connector name from type like "com.redhat.console.notification.toCamel.webhook"
        if (cloudEventType.contains("toCamel.")) {
            return cloudEventType.substring(cloudEventType.lastIndexOf(".") + 1);
        }

        return "unknown";
    }
}

