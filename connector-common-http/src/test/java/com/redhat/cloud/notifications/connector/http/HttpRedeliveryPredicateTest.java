package com.redhat.cloud.notifications.connector.http;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.SocketTimeoutException;
import java.util.List;

@QuarkusTest
public class HttpRedeliveryPredicateTest extends CamelQuarkusTestSupport {
    @Inject
    HttpRedeliveryPredicate httpRedeliveryPredicate;

    /**
     * Tests that the redelivery predicate returns {@code true} for the status
     * codes that we have defined that should trigger a redelivery.
     */
    @Test
    void testPositiveMatchStatusCodes() {
        // List of statuses that should generate a positive match.
        final List<Integer> positiveStatuses = List.of(
            HttpStatus.SC_TOO_MANY_REQUESTS,
            HttpStatus.SC_INTERNAL_SERVER_ERROR,
            HttpStatus.SC_NOT_IMPLEMENTED,
            HttpStatus.SC_BAD_GATEWAY,
            HttpStatus.SC_SERVICE_UNAVAILABLE,
            HttpStatus.SC_GATEWAY_TIMEOUT,
            HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED,
            HttpStatus.SC_INSUFFICIENT_STORAGE
        );

        // For each status that should positively match, mock the exception and
        // assert that the predicate gives the correct result.
        for (final int status : positiveStatuses) {
            final Exchange exchange = this.createExchangeWithBody("");

            final HttpOperationFailedException exception = Mockito.mock(HttpOperationFailedException.class);
            Mockito.when(exception.getStatusCode()).thenReturn(status);

            exchange.setProperty(Exchange.EXCEPTION_CAUGHT, exception);

            // Call the predicate under test.
            Assertions.assertTrue(
                this.httpRedeliveryPredicate.matches(exchange),
                String.format("the HTTP Redelivery Predicate should positively match for the status code '%s'", status)
            );
        }
    }

    /**
     * Tests that the redelivery predicate returns {@code true} for when an
     * IOException is thrown.
     */
    @Test
    void testPositiveMatchIOException() {
        // Simulate a timeout exception.
        final Exchange exchange = this.createExchangeWithBody("");

        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, new SocketTimeoutException());

        // Call the predicate under test.
        Assertions.assertTrue(
            this.httpRedeliveryPredicate.matches(exchange),
            "the HTTP Redelivery Predicate should positively match for a timeout"
        );
    }

    /**
     * Tests that the redelivery predicate returns {@code false} for the status
     * codes that we have identified as the ones that we do not need to retry
     * our requests for.
     */
    @Test
    void testNegativeMatches() {
        final List<Integer> negativeStatuses = List.of(
            HttpStatus.SC_CONTINUE,
            HttpStatus.SC_SWITCHING_PROTOCOLS,
            HttpStatus.SC_PROCESSING,
            HttpStatus.SC_OK,
            HttpStatus.SC_CREATED,
            HttpStatus.SC_ACCEPTED,
            HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION,
            HttpStatus.SC_NO_CONTENT,
            HttpStatus.SC_RESET_CONTENT,
            HttpStatus.SC_PARTIAL_CONTENT,
            HttpStatus.SC_MULTI_STATUS,
            HttpStatus.SC_MULTIPLE_CHOICES,
            HttpStatus.SC_MOVED_PERMANENTLY,
            HttpStatus.SC_MOVED_TEMPORARILY,
            HttpStatus.SC_SEE_OTHER,
            HttpStatus.SC_NOT_MODIFIED,
            HttpStatus.SC_USE_PROXY,
            HttpStatus.SC_TEMPORARY_REDIRECT,
            HttpStatus.SC_BAD_REQUEST,
            HttpStatus.SC_UNAUTHORIZED,
            HttpStatus.SC_PAYMENT_REQUIRED,
            HttpStatus.SC_FORBIDDEN,
            HttpStatus.SC_NOT_FOUND,
            HttpStatus.SC_METHOD_NOT_ALLOWED,
            HttpStatus.SC_NOT_ACCEPTABLE,
            HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED,
            HttpStatus.SC_REQUEST_TIMEOUT,
            HttpStatus.SC_CONFLICT,
            HttpStatus.SC_GONE,
            HttpStatus.SC_LENGTH_REQUIRED,
            HttpStatus.SC_PRECONDITION_FAILED,
            HttpStatus.SC_REQUEST_TOO_LONG,
            HttpStatus.SC_REQUEST_URI_TOO_LONG,
            HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE,
            HttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE,
            HttpStatus.SC_EXPECTATION_FAILED,
            HttpStatus.SC_INSUFFICIENT_SPACE_ON_RESOURCE,
            HttpStatus.SC_METHOD_FAILURE,
            HttpStatus.SC_UNPROCESSABLE_ENTITY,
            HttpStatus.SC_LOCKED,
            HttpStatus.SC_FAILED_DEPENDENCY
        );

        // For each status that should negatively match, mock the exception and
        // assert that the predicate gives the correct result.
        for (final int status : negativeStatuses) {
            final Exchange exchange = this.createExchangeWithBody("");

            final HttpOperationFailedException exception = Mockito.mock(HttpOperationFailedException.class);
            Mockito.when(exception.getStatusCode()).thenReturn(status);

            exchange.setProperty(Exchange.EXCEPTION_CAUGHT, exception);

            // Call the predicate under test.
            Assertions.assertFalse(
                this.httpRedeliveryPredicate.matches(exchange),
                String.format("the HTTP Redelivery Predicate should negatively match for the status code '%s'", status)
            );
        }
    }

    /**
     * Tests that for any exception that isn't the ones that we have identified
     * that should be potential positive matches, the predicate under test
     * returns {@code false}.
     */
    @Test
    void testDifferentExceptionNegativeMatches() {
        final List<Exception> exceptions = List.of(
            Mockito.mock(ArithmeticException.class),
            Mockito.mock(RuntimeException.class),
            Mockito.mock(IllegalStateException.class)
        );

        // For each exception make sure that the predicate under test returns
        // a negative match.
        for (final Exception exception : exceptions) {
            final Exchange exchange = this.createExchangeWithBody("");
            exchange.setProperty(Exchange.EXCEPTION_CAUGHT, exception);

            // Call the predicate under test.
            Assertions.assertFalse(
                this.httpRedeliveryPredicate.matches(exchange),
                String.format("the HTTP Redelivery Predicate should negatively match for the exception '%s'", exception)
            );
        }
    }
}
