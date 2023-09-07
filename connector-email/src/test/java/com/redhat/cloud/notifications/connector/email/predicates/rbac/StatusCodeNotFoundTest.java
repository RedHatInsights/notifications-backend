package com.redhat.cloud.notifications.connector.email.predicates.rbac;

import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.HashMap;

@QuarkusTest
public class StatusCodeNotFoundTest extends CamelQuarkusTestSupport {
    @Inject
    StatusCodeNotFound statusCodeNotFound;

    /**
     * Tests that the predicate returns {@code true} when the exception is a
     * {@link HttpOperationFailedException} and its status code is a
     * {@link HttpStatus#SC_NOT_FOUND} status code.
     */
    @Test
    void testNotFound() {
        final Exchange exchange = this.createExchangeWithBody("");

        exchange.setException(
            new HttpOperationFailedException(
                "Uri!",
                HttpStatus.SC_NOT_FOUND,
                "Not Found!",
                "Location!",
                new HashMap<>(),
                "Response body!"
            )
        );

        Assertions.assertTrue(this.statusCodeNotFound.matches(exchange), "the predicate should have returned \"true\" for an \"HttpOperationFailedException\" with a \"not found\" status code");
    }

    /**
     * Tests that the predicate returns {@code false} when the exception is a
     * {@link HttpOperationFailedException} and its status code is different
     * from a {@link HttpStatus#SC_NOT_FOUND} status code.
     */
    @Test
    void testDifferentStatusCode() {
        final Exchange exchange = this.createExchangeWithBody("");

        exchange.setException(
            new HttpOperationFailedException(
                "Uri!",
                HttpStatus.SC_INTERNAL_SERVER_ERROR,
                "Not Found!",
                "Location!",
                new HashMap<>(),
                "Response body!"
            )
        );

        Assertions.assertFalse(this.statusCodeNotFound.matches(exchange), "the predicate should have returned \"false\" for an \"HttpOperationFailedException\" with a status code different from a \"not found\"");
    }

    /**
     * Tests that the predicate returns {@code false} when the exception is not
     * a {@link HttpOperationFailedException} exception.
     */
    @Test
    void testDifferentExceptionType() {
        final Exchange exchange = this.createExchangeWithBody("");

        exchange.setException(
            new UnknownError()
        );

        Assertions.assertFalse(this.statusCodeNotFound.matches(exchange), "the predicate should have returned \"false\" for an exception which is not of a \"HttpOperationFailedException\" type");
    }
}
