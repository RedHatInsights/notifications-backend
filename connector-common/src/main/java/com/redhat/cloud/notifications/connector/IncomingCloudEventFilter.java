package com.redhat.cloud.notifications.connector;

import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Filters incoming CloudEvents based on connector-specific criteria.
 * This is the new version that replaces the Camel-based IncomingCloudEventFilter.
 */
@ApplicationScoped
public class IncomingCloudEventFilter {

    public static final String X_RH_NOTIFICATIONS_CONNECTOR_HEADER = "x-rh-notifications-connector";

    @Inject
    ConnectorConfig connectorConfig;

    public boolean accept(JsonObject cloudEvent) {
        // In CloudEvents, headers might be stored differently
        // Check both the data section and top-level properties
        String connectorHeader = extractConnectorHeader(cloudEvent);
        return connectorConfig.getSupportedConnectorHeaders().contains(connectorHeader);
    }

    private String extractConnectorHeader(JsonObject cloudEvent) {
        // First check if it's in the data section
        JsonObject data = cloudEvent.getJsonObject("data");
        if (data != null) {
            String header = data.getString(X_RH_NOTIFICATIONS_CONNECTOR_HEADER);
            if (header != null) {
                return header;
            }
        }

        // Then check top-level CloudEvent properties
        String header = cloudEvent.getString(X_RH_NOTIFICATIONS_CONNECTOR_HEADER);
        if (header != null) {
            return header;
        }

        // Check if it's in a headers object
        JsonObject headers = cloudEvent.getJsonObject("headers");
        if (headers != null) {
            return headers.getString(X_RH_NOTIFICATIONS_CONNECTOR_HEADER);
        }

        return null;
    }
}
