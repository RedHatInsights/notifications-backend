package com.redhat.cloud.notifications.connector;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;

/**
 * Extend this class in an {@link ApplicationScoped} bean from a connector Maven module to change the
 * condition that determines if a failure while calling an external service (e.g. Slack, Splunk...)
 * should trigger a redelivery attempt. In addition to the exception class, the exception message may
 * also be parsed and used to build the new redelivery condition. If this class is not extended, then
 * the default implementation below will be used.
 *
 * This is the new version that replaces the Camel-based RedeliveryPredicate.
 */
@DefaultBean
@ApplicationScoped
public class RedeliveryPredicate {

    public boolean shouldRetry(Throwable throwable, ExceptionProcessor.ProcessingContext context) {
        return throwable instanceof IOException;
    }

    public boolean shouldNotRetry(Throwable throwable, ExceptionProcessor.ProcessingContext context) {
        return !shouldRetry(throwable, context);
    }
}
