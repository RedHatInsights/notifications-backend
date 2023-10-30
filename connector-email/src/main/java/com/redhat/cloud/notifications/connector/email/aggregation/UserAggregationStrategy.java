package com.redhat.cloud.notifications.connector.email.aggregation;

import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
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

        final Set<User> oldFilteredUsers = oldExchange.getProperty(ExchangeProperty.FILTERED_USERS, Set.class);
        final Set<User> newFilteredUsers = newExchange.getProperty(ExchangeProperty.FILTERED_USERS, Set.class);

        if (newFilteredUsers != null) {
            oldFilteredUsers.addAll(newFilteredUsers);
        }

        return oldExchange;
    }
}
