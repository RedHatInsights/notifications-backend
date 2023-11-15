package com.redhat.cloud.notifications.connector;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;

@ApplicationScoped
public class IncomingCloudEventFilter implements Predicate {

    public static final String X_RH_NOTIFICATIONS_CONNECTOR_HEADER = "x-rh-notifications-connector";

    @Inject
    ConnectorConfig connectorConfig;

    @Override
    public boolean matches(Exchange exchange) {
        String connectorHeader = exchange.getIn().getHeader(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, String.class);
        return connectorConfig.getSupportedConnectorHeaders().contains(connectorHeader);
    }
}
