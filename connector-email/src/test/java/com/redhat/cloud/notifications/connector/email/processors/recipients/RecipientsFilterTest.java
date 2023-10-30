package com.redhat.cloud.notifications.connector.email.processors.recipients;

import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.connector.email.TestUtils.createUsers;

@QuarkusTest
public class RecipientsFilterTest extends CamelQuarkusTestSupport {

    /**
     * The class under test.
     */
    @Inject
    RecipientsFilter recipientsFilter;

    /**
     * Tests that when the recipient settings have usernames, then they are
     * used to filter the received users from the user providers.
     */
    @Test
    void testRecipientSettingsUsersKept() {
        final Set<String> recipientUsers = Set.of("a", "c", "e");
        final Set<String> subscribers = Set.of("b");
        final Set<User> users = createUsers("a", "b", "c", "d", "e");

        final RecipientSettings recipientSettings = new RecipientSettings(
            true,
            // It is important for the test to have the "ignoreUserPreferences"
            // as true, in order for the logic not to further modify the set.
            true,
            UUID.randomUUID(),
            recipientUsers
        );

        // Prepare the exchange.
        final Exchange exchange = this.createExchangeWithBody("");
        exchange.setProperty(ExchangeProperty.CURRENT_RECIPIENT_SETTINGS, recipientSettings);
        exchange.setProperty(ExchangeProperty.SUBSCRIBERS, subscribers);
        exchange.setProperty(ExchangeProperty.USERS, users);

        // Call the processor under test.
        this.recipientsFilter.process(exchange);

        // Assert that the list contains the expected elements.
        final Set<User> expectedResult = createUsers("a", "c", "e");
        final Set<User> result = exchange.getProperty(ExchangeProperty.FILTERED_USERS, Set.class);

        Assertions.assertEquals(expectedResult, result);
    }

    /**
     * Tests that when ignoring the user preferences, the original username
     * set is retained.
     */
    @Test
    void testIgnoreUserPreferences() {
        final Set<String> subscribers = Set.of("b");
        final Set<User> users = createUsers("a", "b", "c", "d", "e");

        final RecipientSettings recipientSettings = new RecipientSettings(
            true,
            // It is important for the test to have the "ignoreUserPreferences"
            // as true, in order for the logic not to further modify the set.
            true,
            UUID.randomUUID(),
            null
        );

        // Prepare the exchange.
        final Exchange exchange = this.createExchangeWithBody("");
        exchange.setProperty(ExchangeProperty.CURRENT_RECIPIENT_SETTINGS, recipientSettings);
        exchange.setProperty(ExchangeProperty.SUBSCRIBERS, subscribers);
        exchange.setProperty(ExchangeProperty.USERS, users);

        // Call the processor under test.
        this.recipientsFilter.process(exchange);

        // Assert that the list contains the expected elements.
        final Set<User> result = exchange.getProperty(ExchangeProperty.FILTERED_USERS, Set.class);

        Assertions.assertEquals(users, result);
    }

    /**
     * Tests that when "ignore user preferences" is false, then the
     * unsubscribed usernames are removed from the final list.
     */
    @Test
    void testFilterUnsubscribedUsernames() {
        final Set<String> subscribers = Set.of("b", "c", "e");
        final Set<User> users = createUsers("a", "b", "c", "d", "e");

        final RecipientSettings recipientSettings = new RecipientSettings(
            true,
            // It is important for the test to have the "ignoreUserPreferences"
            // as false, in order for the logic to filter the unsubscribed
            // users.
            false,
            UUID.randomUUID(),
            null
        );

        // Prepare the exchange.
        final Exchange exchange = this.createExchangeWithBody("");
        exchange.setProperty(ExchangeProperty.CURRENT_RECIPIENT_SETTINGS, recipientSettings);
        exchange.setProperty(ExchangeProperty.SUBSCRIBERS, subscribers);
        exchange.setProperty(ExchangeProperty.USERS, users);

        // Call the processor under test.
        this.recipientsFilter.process(exchange);

        // Assert that the list contains the expected elements. The
        // "expectedResult" set is manually created because "Set.of" doesn't
        // respect the order of the specified elements.
        final Set<User> expectedResult = createUsers("b", "c", "e");
        final Set<User> result = exchange.getProperty(ExchangeProperty.FILTERED_USERS, Set.class);

        Assertions.assertEquals(expectedResult, result);
    }

    /**
     * Asserts that the username collections contain the usernames, regardless
     * of the order. We use this function instead of the {@link Assertions#assertIterableEquals(Iterable, Iterable)}
     * one because that one does check that the order is correct.
     * @param expected expected username collection to end up with.
     * @param actual the actual result from the test.
     */
    public static void assertUsernameCollectionsEqualsIgnoreOrder(final Collection<String> expected, final Collection<String> actual) {
        Assertions.assertEquals(expected.size(), actual.size(), String.format("the given username collections differ in size. Expected collection: %s . Actual collection: %s", expected, actual));

        for (final String expectedUsername : expected) {
            Assertions.assertTrue(actual.contains(expectedUsername), String.format("expected username '%s' not found in collection %s", expectedUsername, actual));
        }
    }
}
