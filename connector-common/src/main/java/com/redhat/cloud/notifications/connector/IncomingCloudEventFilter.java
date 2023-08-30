package com.redhat.cloud.notifications.connector;

import io.quarkus.arc.DefaultBean;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@DefaultBean
@ApplicationScoped
public class IncomingCloudEventFilter implements Predicate {

    public static final String X_RH_NOTIFICATIONS_CONNECTOR_HEADER = "x-rh-notifications-connector";

    @Inject
    ConnectorConfig connectorConfig;

    @Override
    public boolean matches(Exchange exchange) {
        String connectorHeader = exchange.getIn().getHeader(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, String.class);
        return connectorConfig.getConnectorName().equals(connectorHeader);
    }
}
