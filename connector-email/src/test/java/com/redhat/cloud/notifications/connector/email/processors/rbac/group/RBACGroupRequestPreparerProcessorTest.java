package com.redhat.cloud.notifications.connector.email.processors.rbac.group;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.processors.rbac.RBACConstants;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.Exchange;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Map;
import java.util.UUID;

@QuarkusTest
public class RBACGroupRequestPreparerProcessorTest extends CamelQuarkusTestSupport {
    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    RBACGroupRequestPreparerProcessor rbacGroupRequestPreparerProcessor;

    /**
     * Tests that the processor correctly prepares the request for the "get
     * RBAC group" call.
     */
    @Test
    void testProcess() {
        // Set up the properties the processor expects.
        final String orgId = UUID.randomUUID().toString();
        final String groupUUID = UUID.randomUUID().toString();

        final Exchange exchange = this.createExchangeWithBody("");
        exchange.setProperty(com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID, orgId);
        exchange.setProperty(ExchangeProperty.GROUP_UUID, groupUUID);

        // Process the exchange with the processor under test.
        this.rbacGroupRequestPreparerProcessor.process(exchange);

        // Assert that the correct headers were set in the processor.
        final Map<String, Object> headers = exchange.getMessage().getHeaders();
        Assertions.assertEquals("application/json", headers.get("Accept"), "the \"Accept\" header has an incorrect value");
        Assertions.assertEquals(this.emailConnectorConfig.getRbacPSK(), headers.get(RBACConstants.HEADER_X_RH_RBAC_PSK));
        Assertions.assertEquals(orgId, headers.get(RBACConstants.HEADER_X_RH_RBAC_ORG_ID), "the RBAC's ORG ID header has an incorrect value");
        Assertions.assertEquals(String.format("/api/rbac/v1/groups/%s/", groupUUID), headers.get(Exchange.HTTP_PATH), "the wrong path was set in the processor");
        Assertions.assertEquals(HttpMethods.GET, headers.get(Exchange.HTTP_METHOD), "the wrong HTTP method was set in the processor");
    }
}
