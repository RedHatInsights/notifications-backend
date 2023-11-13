package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.http.SslTrustAllManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.component.caffeine.CaffeineConfiguration;
import org.apache.camel.component.caffeine.EvictionType;
import org.apache.camel.component.caffeine.cache.CaffeineCacheComponent;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

@QuarkusTest
@TestProfile(EmailRouteBuilderTest.class)
public class EmailRouteBuilderTest extends CamelQuarkusTestSupport {
    @Inject
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
        try (Endpoint bopEndpoint = this.emailRouteBuilder.setUpBOPEndpoint().resolve(this.context)) {
            Assertions.assertEquals(this.emailConnectorConfig.getBopURL(), bopEndpoint.getEndpointBaseUri(), "the base URI of the endpoint is not the same as the one set through the properties");

            final String bopEndpointURI = bopEndpoint.getEndpointUri();
            Assertions.assertTrue(bopEndpointURI.contains("trustManager%3Dcom.redhat.cloud.notifications.connector.http.SslTrustAllManager"), "the endpoint does not contain a reference to the BOPTrustManager");
            Assertions.assertTrue(bopEndpointURI.contains("x509HostnameVerifier=NO_OP"), "the base URI does not contain a mention to the NO_OP hostname verifier");
        }
    }

    /**
     * Tests that the function under test creates the IT endpoint with custom
     * key store, the custom trust manager and the custom SSL context
     * parameters.
     *
     * The test cannot be run unless you modify the properties of the
     * application and specify a JKS container with a certificate inside of it.
     * @throws Exception if the endpoint could not be created.
     */
    @Disabled
    @Test
    void testBuildITEndpoint() throws Exception {
        try (Endpoint itEndpoint = this.emailRouteBuilder.setUpITEndpoint().resolve(this.context)) {
            Assertions.assertEquals(this.emailConnectorConfig.getItUserServiceURL(), itEndpoint.getEndpointBaseUri(), "the base URI of the endpoint is not the same as the one set through the properties");

            final String itEndpointUri = itEndpoint.getEndpointUri();
            Assertions.assertTrue(itEndpointUri.contains("cookieSpec=ignoreCookies"), "the cookie policy was not set to 'ignoreCookies' as expected");
            Assertions.assertTrue(itEndpointUri.contains("keyManagers%3DKeyManagersParameters"), "the key manager was not set in the endpoint");
            Assertions.assertTrue(itEndpointUri.contains("keyStore%3DKeyStoreParameters"), "the key store parameters were not set in the endpoint");
            Assertions.assertTrue(itEndpointUri.contains("sslContextParameters=SSLContextParameters"), "the SSL context parameters were not set in the endpoint");
            Assertions.assertTrue(itEndpointUri.contains("password%3D********"), "the URI does not contain a reference to the key store's password");
        }
    }

    /**
     * Tests that the function under test creates the IT endpoint in
     * development mode, which means that no custom key managers, key stores or
     * SSL context parameters are set.
     * @throws Exception if the endpoint could not be created.
     */
    @Test
    void testBuildITEndpointDevelopment() throws Exception {
        try (Endpoint itEndpoint = this.emailRouteBuilder.setUpITEndpointDevelopment().resolve(this.context)) {
            Assertions.assertEquals(this.emailConnectorConfig.getItUserServiceURL(), itEndpoint.getEndpointBaseUri(), "the base URI of the endpoint is not the same as the one set through the properties");
            Assertions.assertEquals(this.emailConnectorConfig.getItUserServiceURL(), itEndpoint.getEndpointUri(), "the URI of the endpoint contains unexpected configuration elements.");
        }
    }

    /**
     * Tests that the correct cache format is computed for the Caffeine cache
     * keys.
     */
    @Test
    void testComputeCacheKey() {
        final Expression cacheKey = this.emailRouteBuilder.computeCacheKey();

        Assertions.assertEquals("simple{${exchangeProperty.orgId}-adminsOnly=${exchangeProperty.current_recipient_settings.adminsOnly}}", cacheKey.toString(), "unexpected cache key generated");
    }

    /**
     * Tests that the correct cache format is computed for the Caffeine cache
     * keys.
     */
    @Test
    void testComputeGroupsPrincipalCacheKey() {
        final Expression cacheKey = this.emailRouteBuilder.computeGroupPrincipalsCacheKey();

        Assertions.assertEquals("simple{${exchangeProperty.orgId}-${exchangeProperty.group_uuid}}", cacheKey.toString(), "unexpected cache key generated");
    }

    /**
     * Tests that the Caffeine cache component has the configurations that we
     * expect.
     * @throws IOException if an unexpected error occurs when fetching the
     * Caffeine cache component.
     */
    @Test
    void testCaffeineCacheConfiguration() throws IOException {
        this.emailRouteBuilder.configureCaffeineCache();

        try (CaffeineCacheComponent caffeineCacheComponent = this.context().getComponent("caffeine-cache", CaffeineCacheComponent.class)) {
            final CaffeineConfiguration caffeineConfiguration = caffeineCacheComponent.getConfiguration();

            Assertions.assertEquals(EvictionType.TIME_BASED, caffeineConfiguration.getEvictionType(), "unexpected eviction type set in the configuration");
            Assertions.assertEquals(600, caffeineConfiguration.getExpireAfterAccessTime(), "unexpected expiration after access time set in the configuration");
            Assertions.assertEquals(600, caffeineConfiguration.getExpireAfterWriteTime(), "unexpected expiration after write time set in the configuration");
        }
    }
}
