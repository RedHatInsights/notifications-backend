package com.redhat.cloud.notifications.connector.http;

import com.redhat.cloud.notifications.connector.RedeliveryPredicate;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.http.HttpStatus;

import java.io.IOException;

import static org.apache.camel.Exchange.EXCEPTION_CAUGHT;

@ApplicationScoped
public class HttpRedeliveryPredicate extends RedeliveryPredicate {
    /**
     * Check if we should attempt a redelivery.
     * @param exchange the exchange that contains the generated exception.
     * @return {@code true} if we received a "429 Too Many Requests" or a 5xx
     * status code, or if the delivery failed because of a timeout.
     */
    @Override
    public boolean matches(final Exchange exchange) {
        final Throwable t = exchange.getProperty(EXCEPTION_CAUGHT, Throwable.class);

        if (t instanceof HttpOperationFailedException e) {
            final boolean shouldRetry = e.getStatusCode() >= 500 || e.getStatusCode() == HttpStatus.SC_TOO_MANY_REQUESTS;

            Log.debugf("The HTTP request failed with status code '%s' and body '%s'. %s", e.getStatusCode(), e.getResponseBody(), (shouldRetry) ? "Retrying..." : "Not retrying");

            return shouldRetry;
        }

        return t instanceof IOException;
    }

}
