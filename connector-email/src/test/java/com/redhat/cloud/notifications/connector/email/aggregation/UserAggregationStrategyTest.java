package com.redhat.cloud.notifications.connector.email.aggregation;

import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.redhat.cloud.notifications.connector.email.TestUtils.createUsers;

@QuarkusTest
public class UserAggregationStrategyTest extends CamelQuarkusTestSupport {
    @Inject
    UserAggregationStrategy userAggregationStrategy;

    /**
     * Tests that the aggregation works as expected.
     */
    @Test
    void testAggregation() {
        // Prepare the list of users to simulate an incoming exchange with a
        // few first ones.
        final Set<User> users = createUsers("foo", "bar", "baz");

        // Prepare the list of users to simulate a second incoming exchange
        // with more users.
        final Set<User> users2 = createUsers("johndoe", "janedoe", "jimmydoe");

        // Prepare the list of users that we are expecting to find at the end
        // of the test.
        final Set<User> finalExpectedUsers = new HashSet<>();
        finalExpectedUsers.addAll(users);
        finalExpectedUsers.addAll(users2);

        // Create just a new exchange to simulate the first split iteration.
        final Exchange newExchange = this.createExchangeWithBody("");

        // Call the aggregator under test.
        final Exchange resultExchange = this.userAggregationStrategy.aggregate(null, newExchange);
        Assertions.assertEquals(newExchange, resultExchange, "when the old exchange is null, which means that it might be the first iteration of the split, the new exchange should be returned");

        // Prepare an old exchange to simulate the aggregation process.
        final Exchange oldExchange = this.createExchangeWithBody("");
        oldExchange.setProperty(ExchangeProperty.FILTERED_USERS, users);

        // Call the aggregator under test.
        final Exchange resultExchangeTwo = this.userAggregationStrategy.aggregate(oldExchange, newExchange);
        Assertions.assertEquals(oldExchange, resultExchangeTwo, "on following split iterations, the old exchange should be returned along with the aggregated users");

        // Check that since the new exchange didn't have any new users, the old
        // exchange should contain the original ones.
        final Set<User> resultUsers = resultExchangeTwo.getProperty(ExchangeProperty.FILTERED_USERS, Set.class);
        Assertions.assertEquals(users, resultUsers);

        // Prepare a new exchange with a new list of users

        final Exchange yetAnotherExchange = this.createExchangeWithBody("");
        yetAnotherExchange.setProperty(ExchangeProperty.FILTERED_USERS, users2);

        // Call the exchange under test.
        final Exchange finalExchange = this.userAggregationStrategy.aggregate(oldExchange, yetAnotherExchange);
        Assertions.assertEquals(oldExchange, finalExchange, "on following split iterations, the old exchange should be returned along with the aggregated users");

        // Assert that the old exchange contains all the aggregated users.
        final Set<User> finalUsersList = finalExchange.getProperty(ExchangeProperty.FILTERED_USERS, Set.class);
        Assertions.assertEquals(finalExpectedUsers, finalUsersList);
    }
}
