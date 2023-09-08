package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.IncomingCloudEventFilter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;

@ApplicationScoped
public class WebhookIncomingCloudEventFilter extends IncomingCloudEventFilter {
    @Inject
    WebhookConnectorConfig connectorConfig;

    @Override
    public boolean matches(Exchange exchange) {
        String connectorHeader = exchange.getIn().getHeader(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, String.class);
        return connectorConfig.getConnectorName().equals(connectorHeader) || connectorConfig.getAlternativeNames().contains(connectorHeader);
    }
}
