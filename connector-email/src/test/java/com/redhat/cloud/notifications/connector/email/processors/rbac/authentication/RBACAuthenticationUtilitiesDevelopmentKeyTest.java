package com.redhat.cloud.notifications.connector.email.processors.rbac.authentication;

import com.redhat.cloud.notifications.connector.email.processors.rbac.authentication.testprofiles.DevelopmentKeyTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@QuarkusTest
@TestProfile(DevelopmentKeyTestProfile.class)
public class RBACAuthenticationUtilitiesDevelopmentKeyTest extends CamelQuarkusTestSupport {
    @Inject
    RBACAuthenticationUtilities rbacAuthenticationUtilities;

    /**
     * Tests that the correct basic authentication header is set after calling
     * the authentication utilities. The development key configuration property
     * is set via a test profile for this to test to work.
     */
    @Test
    void testDevelopmentKey() {
        // Prepare the required elements for the test to work.
        final Exchange exchange = this.createExchangeWithBody("");
        final String orgId = UUID.randomUUID().toString();

        // Call the function under test.
        this.rbacAuthenticationUtilities.setAuthenticationHeaders(exchange, orgId);

        // Assert that the basic authentication header was set. Since we are at
        // it, we will also compute the Base64 contents of the header, to make
        // sure we are doing it right in the connector's configuration class.
        final Map<String, Object> headers = exchange.getMessage().getHeaders();

        final String base64EncodedAuthentication = new String(
            Base64.getEncoder().encode(DevelopmentKeyTestProfile.DEVELOPMENT_AUTHENTICATION_KEY.getBytes(StandardCharsets.UTF_8)),
            StandardCharsets.UTF_8
        );

        final String expectedValue = String.format("Basic %s", base64EncodedAuthentication);

        Assertions.assertEquals(expectedValue, headers.get("Authorization"), "incorrect authorization header value found after calling the RBAC authentication utilities");
    }
}
