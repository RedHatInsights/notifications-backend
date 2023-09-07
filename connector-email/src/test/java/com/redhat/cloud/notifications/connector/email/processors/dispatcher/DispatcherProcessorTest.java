package com.redhat.cloud.notifications.connector.email.processors.dispatcher;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.constants.Routes;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@QuarkusTest
@TestProfile(DispatcherProcessorTest.class)
public class DispatcherProcessorTest extends CamelQuarkusTestSupport {

    @Inject
    DispatcherProcessor dispatcherProcessor;

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    /**
     * Disables the rout builder to ensure that the Camel Context does not get
     * started before the routes have been advised. More information is
     * available at the <a href="https://people.apache.org/~dkulp/camel/camel-test.html">dkulp's Apache Camel Test documentation page</a>.
     * @return {@code false} in order to stop the Camel Context from booting
     * before the routes have been advised.
     */
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    /**
     * Tests that when a "recipient setting" doesn't contain an RBAC group's
     * UUID, the exchange is sent to the {@link Routes#FETCH_USERS} route.
     */
    @Test
    void testSendFetchUsers() throws Exception {
        // Create an exchange with an empty body.
        final Exchange exchange = this.createExchangeWithBody("");

        // Create a recipient settings object without the group's UUID.
        final RecipientSettings recipientSettings = new RecipientSettings(
            true,
            true,
            null,
            new HashSet<>()
        );

        // Set the property that will be grabbed in the processor.
        exchange.setProperty(ExchangeProperty.RECIPIENT_SETTINGS, List.of(recipientSettings));

        // Assert that the exchange was sent to the correct route.
        // Assert that the exchange was sent to the correct route.
        AdviceWith.adviceWith(this.context(), EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR, a -> {
            a.mockEndpointsAndSkip(String.format("direct:%s", Routes.FETCH_USERS));
            a.mockEndpointsAndSkip(String.format("direct:%s", Routes.FETCH_GROUP));
        });

        final MockEndpoint fetchUsersEndpoint = this.getMockEndpoint(String.format("mock:direct:%s", Routes.FETCH_USERS));
        final MockEndpoint fetchGroupEndpoint = this.getMockEndpoint(String.format("mock:direct:%s", Routes.FETCH_GROUP));

        fetchUsersEndpoint.expectedMessageCount(1);
        fetchGroupEndpoint.expectedMessageCount(0);

        this.dispatcherProcessor.process(exchange);

        fetchUsersEndpoint.assertIsSatisfied();
        fetchGroupEndpoint.assertIsSatisfied();

        // Make sure that the exchange contains the "RecipientSettings" object
        final List<Exchange> exchanges = fetchUsersEndpoint.getExchanges();
        final Exchange sentExchange = exchanges.get(0);
        final RecipientSettings sentRecipientSettings = sentExchange.getProperty(ExchangeProperty.CURRENT_RECIPIENT_SETTINGS, RecipientSettings.class);

        Assertions.assertEquals(recipientSettings, sentRecipientSettings, "the recipient settings object was not properly set in the dispatcher");
    }

    /**
     * Tests that when a "recipient setting" contains an RBAC group's UUID,
     * the exchange is sent to the {@link Routes#FETCH_GROUP} route.
     */
    @Test
    void testSendFetchGroup() throws InterruptedException, Exception {
        // Create an exchange with an empty body.
        final Exchange exchange = this.createExchangeWithBody("");

        // Create a recipient settings object with the group's UUID.
        final RecipientSettings recipientSettings = new RecipientSettings(
            true,
            true,
            UUID.randomUUID(),
            new HashSet<>()
        );

        // Set the property that will be grabbed in the processor.
        exchange.setProperty(ExchangeProperty.RECIPIENT_SETTINGS, List.of(recipientSettings));

        // Assert that the exchange was sent to the correct route.
        AdviceWith.adviceWith(this.context(), EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR, a -> {
            a.mockEndpointsAndSkip(String.format("direct:%s", Routes.FETCH_USERS));
            a.mockEndpointsAndSkip(String.format("direct:%s", Routes.FETCH_GROUP));
        });

        final MockEndpoint fetchUsersEndpoint = this.getMockEndpoint(String.format("mock:direct:%s", Routes.FETCH_USERS));
        final MockEndpoint fetchGroupEndpoint = this.getMockEndpoint(String.format("mock:direct:%s", Routes.FETCH_GROUP));

        fetchUsersEndpoint.expectedMessageCount(0);
        fetchGroupEndpoint.expectedMessageCount(1);

        this.dispatcherProcessor.process(exchange);

        fetchUsersEndpoint.assertIsSatisfied();
        fetchGroupEndpoint.assertIsSatisfied();

        // Make sure that the exchange contains the "RecipientSettings" object
        final List<Exchange> exchanges = fetchGroupEndpoint.getExchanges();
        final Exchange sentExchange = exchanges.get(0);
        final RecipientSettings sentRecipientSettings = sentExchange.getProperty(ExchangeProperty.CURRENT_RECIPIENT_SETTINGS, RecipientSettings.class);

        Assertions.assertEquals(recipientSettings, sentRecipientSettings, "the recipient settings object was not properly set in the dispatcher");
    }


}
