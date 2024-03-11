package com.redhat.cloud.notifications.connector;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;

import java.io.IOException;

import static org.apache.camel.Exchange.EXCEPTION_CAUGHT;

/**
 * Extend this class in an {@link ApplicationScoped} bean from a connector Maven module to change the
 * condition that determines if a failure while calling an external service (e.g. Slack, Splunk...)
 * should trigger a redelivery attempt. In addition to the exception class, the exception message may
 * also be parsed and used to build the new redelivery condition. If this class is not extended, then
 * the default implementation below will be used.
 */
@DefaultBean
@ApplicationScoped
public class RedeliveryPredicate implements Predicate {

    @Override
    public boolean matches(Exchange exchange) {
        Throwable t = exchange.getProperty(EXCEPTION_CAUGHT, Throwable.class);
        return t instanceof IOException;
    }

}
