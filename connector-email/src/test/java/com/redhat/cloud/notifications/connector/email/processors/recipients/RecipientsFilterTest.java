package com.redhat.cloud.notifications.connector.email.processors.recipients;

import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
        // The set needs to be defined this way in order for it to be modified.
        final Set<String> usernames = new HashSet<>();
        usernames.add("a");
        usernames.add("b");
        usernames.add("c");
        usernames.add("d");
        usernames.add("e");

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
        exchange.setProperty(ExchangeProperty.USERNAMES, usernames);

        // Call the processor under test.
        this.recipientsFilter.process(exchange);

        // Assert that the list contains the expected elements. The
        // "expectedResult" set is manually created because "Set.of" doesn't
        // respect the order of the specified elements.
        final Set<String> expectedResult = new HashSet<>();
        expectedResult.add("a");
        expectedResult.add("c");
        expectedResult.add("e");
        final Set<String> result = exchange.getProperty(ExchangeProperty.USERNAMES, Set.class);

        Assertions.assertIterableEquals(expectedResult, result);
    }

    /**
     * Tests that when ignoring the user preferences, the original username
     * set is retained.
     */
    @Test
    void testIgnoreUserPreferences() {
        final Set<String> subscribers = Set.of("b");
        // The set needs to be defined this way in order for it to be modified.
        final Set<String> usernames = Set.of("a", "b", "c", "d", "e");

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
        exchange.setProperty(ExchangeProperty.USERNAMES, usernames);

        // Call the processor under test.
        this.recipientsFilter.process(exchange);

        // Assert that the list contains the expected elements.
        final Set<String> result = exchange.getProperty(ExchangeProperty.USERNAMES, Set.class);

        Assertions.assertIterableEquals(usernames, result);
    }

    /**
     * Tests that when "ignore user preferences" is false, then the
     * unsubscribed usernames are removed from the final list.
     */
    @Test
    void testFilterUnsubscribedUsernames() {
        final Set<String> subscribers = Set.of("b", "c", "e");
        // The set needs to be defined this way in order for it to be modified.
        final Set<String> usernames = new HashSet<>();
        usernames.add("a");
        usernames.add("b");
        usernames.add("c");
        usernames.add("d");
        usernames.add("e");

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
        exchange.setProperty(ExchangeProperty.USERNAMES, usernames);

        // Call the processor under test.
        this.recipientsFilter.process(exchange);

        // Assert that the list contains the expected elements. The
        // "expectedResult" set is manually created because "Set.of" doesn't
        // respect the order of the specified elements.
        final Set<String> expectedResult = new HashSet<>();
        expectedResult.add("b");
        expectedResult.add("c");
        expectedResult.add("e");
        final Set<String> result = exchange.getProperty(ExchangeProperty.USERNAMES, Set.class);

        Assertions.assertIterableEquals(expectedResult, result);
    }
}
