package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.constants.Routes;
import com.redhat.cloud.notifications.connector.email.processors.bop.ssl.BOPTrustManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.camel.Endpoint;
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
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    EmailRouteBuilder emailRouteBuilder;

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

    /**
     * Tests that the function under test creates the BOP endpoint with the
     * {@link BOPTrustManager} class as the SSL context parameters, and that
     * that class is essentially a NOOP class.
     * @throws Exception if the endpoint could not be created.
     */
    @Test
    void testBuildBOPEndpoint() throws Exception {
        try (Endpoint bopEndpoint = this.emailRouteBuilder.setUpBOPEndpoint().resolve(this.context)) {
            Assertions.assertEquals(this.emailConnectorConfig.getBopURL(), bopEndpoint.getEndpointBaseUri(), "the base URI of the endpoint is not the same as the one set through the properties");

            final String bopEndpointURI = bopEndpoint.getEndpointUri();
            Assertions.assertTrue(bopEndpointURI.contains("trustManager%3Dcom.redhat.cloud.notifications.connector.email.processors.bop.ssl.BOPTrustManager"), "the endpoint does not contain a reference to the BOPTrustManager");
            Assertions.assertTrue(bopEndpointURI.contains("x509HostnameVerifier=NO_OP"), "the base URI does not contain a mention to the NO_OP hostname verifier");
        }
    }

    /**
     * Tests that the function under test creates the IT endpoint with custom
     * key store, the custom trust manager and the custom SSL context
     * parameters.
     * @throws Exception if the endpoint could not be created.
     */
    @Test
    void testBuildITEndpoint() throws Exception {
        try (Endpoint bopEndpoint = this.emailRouteBuilder.setUpITEndpoint().resolve(this.context)) {
            Assertions.assertEquals(this.emailConnectorConfig.getItUserServiceURL(), bopEndpoint.getEndpointBaseUri(), "the base URI of the endpoint is not the same as the one set through the properties");

            final String itEndpointUri = bopEndpoint.getEndpointUri();
            Assertions.assertTrue(itEndpointUri.contains("TrustManagerType%5BkeyStore"), "the trust manager type for the IT endpoint is not set to \"keyStore\"");
            Assertions.assertTrue(itEndpointUri.contains("password%3D********"), "the URI does not contain a reference to the key store's password");
        }
    }
}
