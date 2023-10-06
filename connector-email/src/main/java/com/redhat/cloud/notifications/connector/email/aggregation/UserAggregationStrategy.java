package com.redhat.cloud.notifications.connector.email.aggregation;

import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;

import java.util.Set;

@ApplicationScoped
public class UserAggregationStrategy implements AggregationStrategy {

    /**
     * Aggregates the users from the split exchanges into a single exchange.
     * @param oldExchange the old exchange of a previous split iteration.
     * @param newExchange the new incoming exchange from a new user provider
     *                    request.
     * @return the new exchange on the first iteration, the old iteration with
     * the aggregated users for the rest.
     */
    @Override
    public Exchange aggregate(final Exchange oldExchange, final Exchange newExchange) {
        if (oldExchange == null) {
            return newExchange;
        }

        final Set<String> oldFilteredUsers = oldExchange.getProperty(ExchangeProperty.FILTERED_USERNAMES, Set.class);
        final Set<String> newFilteredUsers = newExchange.getProperty(ExchangeProperty.FILTERED_USERNAMES, Set.class);

        if (newFilteredUsers != null) {
            oldFilteredUsers.addAll(newFilteredUsers);
        }

        return oldExchange;
    }
}
