package com.redhat.cloud.notifications.connector.email.processors.rbac.authentication;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.processors.rbac.RBACConstants;
import com.redhat.cloud.notifications.connector.email.processors.rbac.authentication.testprofiles.PSKsTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

@QuarkusTest
@TestProfile(PSKsTestProfile.class)
public class RBACAuthenticationUtilitiesPSKTest extends CamelQuarkusTestSupport {
    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    RBACAuthenticationUtilities rbacAuthenticationUtilities;

    /**
     * Tests that the correct PSK and organization ID headers are set after
     * calling the authentication utilities. The PSK configuration properties
     * are set via a test profile for this to test to work.
     */
    @Test
    void testPSKs() {
        // Prepare the required elements for the test to work.
        final Exchange exchange = this.createExchangeWithBody("");
        final String orgId = UUID.randomUUID().toString();

        // Call the function under test.
        this.rbacAuthenticationUtilities.setAuthenticationHeaders(exchange, orgId);

        // Assert that the PSK and organization ID headers were set.
        final Map<String, Object> headers = exchange.getMessage().getHeaders();

        Assertions.assertEquals(PSKsTestProfile.NOTIFICATIONS_PSK_SECRET, headers.get(RBACConstants.HEADER_X_RH_RBAC_PSK), "incorrect PSK header value found after calling the RBAC authentication utilities");
        Assertions.assertEquals(this.emailConnectorConfig.getRbacApplicationKey(), headers.get(RBACConstants.HEADER_X_RH_RBAC_CLIENT_ID));
        Assertions.assertEquals(orgId, headers.get(RBACConstants.HEADER_X_RH_RBAC_ORG_ID), "incorrect organization ID header value found after calling the RBAC authentication utilities");
    }
}
