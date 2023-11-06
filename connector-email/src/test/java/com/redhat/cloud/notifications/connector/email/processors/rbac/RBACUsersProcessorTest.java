package com.redhat.cloud.notifications.connector.email.processors.rbac;

import com.google.common.io.Resources;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.connector.email.TestUtils.createUsers;

@QuarkusTest
public class RBACUsersProcessorTest extends CamelQuarkusTestSupport {
    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    RBACUsersProcessor rbacUsersProcessor;

    /**
     * Tests that a regular call that processes users works as expected.
     * @throws IOException if the expected response's JSON file could not be
     * loaded.
     */
    @Test
    void testProcessRegularCall() throws IOException {
        // Prepare the body of the response as we would receive it from RBAC.
        final URL url = Resources.getResource("processors/rbac/rbacUsersResponse.json");
        final String body = Resources.toString(url, StandardCharsets.UTF_8);
        final Exchange exchange = this.createExchangeWithBody(body);

        // Set the rest of the properties.
        final RecipientSettings recipientSettings = new RecipientSettings(
            true,
            true,
            null,
            null,
            null
        );
        exchange.setProperty(ExchangeProperty.CURRENT_RECIPIENT_SETTINGS, recipientSettings);
        exchange.setProperty(ExchangeProperty.LIMIT, this.emailConnectorConfig.getRbacElementsPerPage());
        exchange.setProperty(ExchangeProperty.USERS, new HashSet<User>());

        // Call the processor under test.
        this.rbacUsersProcessor.process(exchange);

        // Assert that the elements count is correct.
        Assertions.assertEquals(5, exchange.getProperty(ExchangeProperty.ELEMENTS_COUNT));

        // Assert that the users are the expected ones.
        final Set<User> expectedUsers = createUsers("foouser", "baruser", "bazuser", "johndoe", "janedoe");

        final Set<User> result = exchange.getProperty(ExchangeProperty.USERS, Set.class);
        Assertions.assertEquals(expectedUsers, result);
    }

    /**
     * Tests that a regular call that processes users works as expected. In
     * this case we simulate that the specified limit is the same as the
     * received number of elements, which should update the offset.
     * @throws IOException if the expected response's JSON file could not be
     * loaded.
     */
    @Test
    void testProcessRegularCallCountMatchesLimit() throws IOException {
        // Prepare the body of the response as we would receive it from RBAC.
        final URL url = Resources.getResource("processors/rbac/rbacUsersResponse.json");
        final String body = Resources.toString(url, StandardCharsets.UTF_8);
        final Exchange exchange = this.createExchangeWithBody(body);

        // Set the rest of the properties.
        final RecipientSettings recipientSettings = new RecipientSettings(
            true,
            true,
            null,
            null,
            null
        );
        exchange.setProperty(ExchangeProperty.CURRENT_RECIPIENT_SETTINGS, recipientSettings);

        final int offset = 0;
        final int limit = 5;
        exchange.setProperty(ExchangeProperty.OFFSET, offset);
        exchange.setProperty(ExchangeProperty.LIMIT, limit);
        exchange.setProperty(ExchangeProperty.USERS, new HashSet<User>());

        // Call the processor under test.
        this.rbacUsersProcessor.process(exchange);

        // Assert that the elements count is correct.
        Assertions.assertEquals(5, exchange.getProperty(ExchangeProperty.ELEMENTS_COUNT));

        // Assert that the users are the expected ones.
        final Set<User> expectedUsers = createUsers("foouser", "baruser", "bazuser", "johndoe", "janedoe");

        final Set<User> result = exchange.getProperty(ExchangeProperty.USERS, Set.class);
        Assertions.assertEquals(expectedUsers, result);

        // Assert that the offset got updated.
        Assertions.assertEquals(offset + limit, exchange.getProperty(ExchangeProperty.OFFSET));
    }

    /**
     * Tests that a group call that processes users works as expected. We also
     * test the "isAdmin" option for the group, which should only pick up
     * admins from the response.
     * @throws IOException if the expected response's JSON file could not be
     * loaded.
     */
    @Test
    void testProcessGroupCall() throws IOException {
        // Prepare the body of the response as we would receive it from RBAC.
        final URL url = Resources.getResource("processors/rbac/rbacGroupResponse.json");
        final String body = Resources.toString(url, StandardCharsets.UTF_8);
        final Exchange exchange = this.createExchangeWithBody(body);

        // Set the rest of the properties.
        final RecipientSettings recipientSettings = new RecipientSettings(
            true,
            true,
            UUID.randomUUID(),
            null,
            null
        );
        exchange.setProperty(ExchangeProperty.CURRENT_RECIPIENT_SETTINGS, recipientSettings);
        exchange.setProperty(ExchangeProperty.LIMIT, this.emailConnectorConfig.getRbacElementsPerPage());
        exchange.setProperty(ExchangeProperty.USERS, new HashSet<User>());

        // Call the processor under test.
        this.rbacUsersProcessor.process(exchange);

        // Assert that the elements count is correct.
        Assertions.assertEquals(5, exchange.getProperty(ExchangeProperty.ELEMENTS_COUNT));

        // Assert that the users are the expected ones.
        final Set<User> expectedUsers = createUsers("foouser", "baruser");

        final Set<User> result = exchange.getProperty(ExchangeProperty.USERS, Set.class);
        Assertions.assertIterableEquals(expectedUsers, result);
    }
}
