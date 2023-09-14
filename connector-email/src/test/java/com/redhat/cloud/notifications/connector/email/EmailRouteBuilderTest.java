package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.constants.Routes;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

@QuarkusTest
@TestProfile(EmailRouteBuilderTest.class)
public class EmailRouteBuilderTest extends CamelQuarkusTestSupport {
    @Inject
    ProducerTemplate producerTemplate;

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

    @Test
    void testIndividualEmailPerUser() throws Exception {
        AdviceWith.adviceWith(this.context, Routes.SEND_EMAIL_BOP_SINGLE_PER_USER, a -> {
            a.mockEndpointsAndSkip(String.format("direct:%s", Routes.SEND_EMAIL_BOP));
        });

        final Set<String> usernames = Set.of("a", "b", "c", "d", "e");

        final Exchange exchange = this.createExchangeWithBody("");
        exchange.setProperty(ExchangeProperty.USERNAMES, usernames);

        final MockEndpoint sendEmailBopEndpoint = this.getMockEndpoint(String.format("mock:direct:%s", Routes.SEND_EMAIL_BOP), false);
        sendEmailBopEndpoint.expectedMessageCount(5);

        this.producerTemplate.send(String.format("direct:%s", Routes.SEND_EMAIL_BOP_SINGLE_PER_USER), exchange);

        sendEmailBopEndpoint.assertIsSatisfied();

        final List<Exchange> splittedExchanges = sendEmailBopEndpoint.getExchanges();
        for (final Exchange splittedExchange : splittedExchanges) {
            Assertions.assertTrue(splittedExchange.getProperty(ExchangeProperty.SINGLE_EMAIL_PER_USER, Boolean.class), "after splitting the usernames' list, the resulting exchanges did not contain the 'single email per user' flag defined as a property");
        }
    }
}
