package com.redhat.cloud.notifications.processors.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;

import java.util.Objects;

import static com.redhat.cloud.notifications.processors.ConnectorSender.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;

public class IncomingCloudEventFilter implements Predicate {

    public static final String EXCEPTION_MSG = "The 'allowedConnectorHeader' argument cannot be null";

    private final String allowedConnectorHeader;

    public IncomingCloudEventFilter(String allowedConnectorHeader) {
        this.allowedConnectorHeader = Objects.requireNonNull(allowedConnectorHeader, EXCEPTION_MSG);
    }

    @Override
    public boolean matches(Exchange exchange) {
        String actualConnectorHeader = exchange.getIn().getHeader(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, String.class);
        return allowedConnectorHeader.equals(actualConnectorHeader);
    }
}
