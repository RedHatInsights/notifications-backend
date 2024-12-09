package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.http.SslTrustAllManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import org.apache.camel.Endpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.when;

@QuarkusTest
public class BuildBopEndpointTest extends CamelQuarkusTestSupport {

    @InjectSpy
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    EmailRouteBuilder emailRouteBuilder;

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    /**
     * Disables the route builder to ensure that the Camel Context does not get
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
     * Tests that the function under test creates the BOP endpoint with the
     * {@link SslTrustAllManager} class as the SSL context parameters, and that
     * that class is essentially a NOOP class.
     * @throws Exception if the endpoint could not be created.
     */
    @Test
    void testBuildBOPEndpoint() throws Exception {
        String initialBopUrl = emailConnectorConfig.getBopURL();
        when(emailConnectorConfig.getBopURL()).thenReturn("https://test.com");

        Endpoint bopEndpoint = this.emailRouteBuilder.setUpBOPEndpointV1().resolve(this.context);
        Assertions.assertEquals(this.emailConnectorConfig.getBopURL(), bopEndpoint.getEndpointBaseUri(), "the base URI of the endpoint is not the same as the one set through the properties");

        final String bopEndpointURI = bopEndpoint.getEndpointUri();
        Assertions.assertTrue(bopEndpointURI.contains("trustManager%3Dcom.redhat.cloud.notifications.connector.http.SslTrustAllManager"), "the endpoint does not contain a reference to the SslTrustAllManager");
        Assertions.assertTrue(bopEndpointURI.contains("x509HostnameVerifier=NO_OP"), "the base URI does not contain a mention to the NO_OP hostname verifier");
    }
}
