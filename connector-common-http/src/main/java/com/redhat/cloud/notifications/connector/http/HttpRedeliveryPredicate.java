package com.redhat.cloud.notifications.connector.http;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import com.redhat.cloud.notifications.connector.RedeliveryPredicate;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.http.HttpStatus;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

import java.io.IOException;

/**
 * HTTP-specific redelivery predicate that determines whether HTTP failures
 * should trigger retry attempts.
 * This is the new version that replaces the Camel-based HttpRedeliveryPredicate.
 */
@ApplicationScoped
public class HttpRedeliveryPredicate extends RedeliveryPredicate {

    /**
     * Check if we should attempt a redelivery.
     * @param throwable the exception that was thrown.
     * @param context the processing context.
     * @return {@code true} if we received a "429 Too Many Requests" or a 5xx
     * status code, or if the delivery failed because of a timeout.
     */
    @Override
    public boolean shouldRetry(Throwable throwable, ExceptionProcessor.ProcessingContext context) {

        if (throwable instanceof ClientWebApplicationException e) {
            int statusCode = e.getResponse().getStatus();
            String responseBody = null;
            try {
                responseBody = e.getResponse().readEntity(String.class);
            } catch (Exception ex) {
                responseBody = "Unable to read response body";
            }

            final boolean shouldRetry = statusCode >= 500 || statusCode == HttpStatus.SC_TOO_MANY_REQUESTS;

            Log.debugf("The HTTP request failed with status code '%s' and body '%s'. %s",
                statusCode, responseBody, (shouldRetry) ? "Retrying..." : "Not retrying");

            return shouldRetry;
        }

        // Handle other HTTP-related exceptions from various clients
        if (throwable.getCause() instanceof ClientWebApplicationException e) {
            return shouldRetry(e, context);
        }

        // Default to retrying on IO exceptions (network issues, timeouts, etc.)
        return throwable instanceof IOException;
    }
}
