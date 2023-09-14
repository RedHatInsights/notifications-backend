package com.redhat.cloud.notifications.connector.email.predicates;

import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;

@ApplicationScoped
public class NotFinishedFetchingAllPages implements Predicate {
    /**
     * Predicate which indicates if we are done fetching all the result pages.
     * @param exchange the exchange of the pipeline.
     * @return {@code true} if the fetched number of elements coincides with
     * the specified limit. {@code false} otherwise.
     */
    @Override
    public boolean matches(final Exchange exchange) {
        final Object fetchedCount = exchange.getProperty(ExchangeProperty.ELEMENTS_COUNT);

        // If we didn't fetch any count that means this is the first time we
        // enter the loop, so we can simply let the first iteration run.
        if (fetchedCount == null) {
            return true;
        }

        // Determine if we should keep looping.
        final int limit = exchange.getProperty(ExchangeProperty.LIMIT, Integer.class);

        return fetchedCount.equals(limit);
    }
}
