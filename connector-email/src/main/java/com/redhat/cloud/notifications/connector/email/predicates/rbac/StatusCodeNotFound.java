package com.redhat.cloud.notifications.connector.email.predicates.rbac;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.http.HttpStatus;

@ApplicationScoped
public class StatusCodeNotFound implements Predicate {
    /**
     * Predicate which indicates if the received status code is a "Not Found"
     * one.
     * @param exchange the exchange to check the status code from.
     * @return {@code true} if the exchange contains an
     * {@link HttpOperationFailedException} with a {@link HttpStatus#SC_NOT_FOUND}
     * status code.
     */
    @Override
    public boolean matches(final Exchange exchange) {
        if (exchange.getException() instanceof HttpOperationFailedException e) {
            return e.getStatusCode() == HttpStatus.SC_NOT_FOUND;
        }

        return false;
    }

}
