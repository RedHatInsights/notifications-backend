package com.redhat.cloud.notifications.connector.email.aggregation;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import java.util.HashSet;
import java.util.Set;

import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.RECIPIENTS_WITH_EMAIL_ERROR;

@ApplicationScoped
public class ErrorAggregationStrategy implements AggregationStrategy {

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

        Set<String> recipientsWithError = oldExchange.getProperty(RECIPIENTS_WITH_EMAIL_ERROR, Set.class);
        if (recipientsWithError == null) {
            recipientsWithError = new HashSet<>();
        }
        if (newExchange.getProperty(RECIPIENTS_WITH_EMAIL_ERROR, Set.class) != null) {
            recipientsWithError.addAll(newExchange.getProperty(RECIPIENTS_WITH_EMAIL_ERROR, Set.class));
        }
        oldExchange.setProperty(RECIPIENTS_WITH_EMAIL_ERROR, recipientsWithError);

        return oldExchange;
    }
}
