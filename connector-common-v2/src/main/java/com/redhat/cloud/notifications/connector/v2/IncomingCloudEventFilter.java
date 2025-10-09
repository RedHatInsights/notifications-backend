package com.redhat.cloud.notifications.connector.v2;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class IncomingCloudEventFilter {

    public static final String X_RH_NOTIFICATIONS_CONNECTOR_HEADER = "x-rh-notifications-connector";

    @Inject
    ConnectorConfig connectorConfig;

    public boolean accept(MessageContext context) {
        return connectorConfig.getSupportedConnectorHeaders().contains(context.getHeaders().get(X_RH_NOTIFICATIONS_CONNECTOR_HEADER).orElse(null));
    }
}
