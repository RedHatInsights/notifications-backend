package com.redhat.cloud.notifications.connector.email.predicates;

import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class NotFinishedFetchingAllPagesTest extends CamelQuarkusTestSupport {

    /**
     * Tests that when no elements count is set in the exchange's property,
     * then the predicate returns {@code true}, since that way we make the
     * condition start the first iteration on the loops.
     */
    @Test
    void testNullElementsCountReturnsTrue() {
        final Exchange exchange = createExchangeWithBody("");

        final NotFinishedFetchingAllPages predicate = new NotFinishedFetchingAllPages();

        Assertions.assertTrue(predicate.matches(exchange), "when no element count is specified, true must be returned so that the first loop iteration is performed");
    }

    /**
     * Tests that when the elements count is different from the specified
     * limit, then the predicate returns {@code false}, which implies that not
     * a full page was fetched, which means that there are no more elements
     * left to fetch.
     */
    @Test
    void testDifferentElementsCountLimitReturnsFalse() {
        final Exchange exchange = createExchangeWithBody("");
        exchange.setProperty(ExchangeProperty.ELEMENTS_COUNT, 5);
        exchange.setProperty(ExchangeProperty.LIMIT, 12);

        final NotFinishedFetchingAllPages predicate = new NotFinishedFetchingAllPages();

        Assertions.assertFalse(predicate.matches(exchange), "when the received elements count and the limit differ, that means that we might have not fetched an entire page, which might imply that there are no more elements left to fetch");
    }

    /**
     * Tests that when the elements count is the same as the specified limit,
     * {@code true} is returned, which implies that we should keep looping and
     * fetching user pages.
     */
    @Test
    void testSameElementsCountLimitReturnsTrue() {
        final Exchange exchange = createExchangeWithBody("");
        exchange.setProperty(ExchangeProperty.ELEMENTS_COUNT, 12);
        exchange.setProperty(ExchangeProperty.LIMIT, 12);

        final NotFinishedFetchingAllPages predicate = new NotFinishedFetchingAllPages();

        Assertions.assertTrue(predicate.matches(exchange), "when the received elements count and the limit are the same, that implies that there might be more pages yet to fetch, and therefore we should keep looping");
    }
}
