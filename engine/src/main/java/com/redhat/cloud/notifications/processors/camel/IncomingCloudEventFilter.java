package com.redhat.cloud.notifications.processors.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;

import java.util.Objects;

import static com.redhat.cloud.notifications.processors.camel.CamelNotificationProcessor.CLOUD_EVENT_TYPE_HEADER;

public class IncomingCloudEventFilter implements Predicate {

    public static final String EXCEPTION_MSG = "The 'allowedCeType' argument cannot be null";

    private final String allowedCeType;

    public IncomingCloudEventFilter(String allowedCeType) {
        this.allowedCeType = Objects.requireNonNull(allowedCeType, EXCEPTION_MSG);
    }

    @Override
    public boolean matches(Exchange exchange) {
        String actualCeType = exchange.getIn().getHeader(CLOUD_EVENT_TYPE_HEADER, String.class);
        return allowedCeType.equals(actualCeType);
    }
}
