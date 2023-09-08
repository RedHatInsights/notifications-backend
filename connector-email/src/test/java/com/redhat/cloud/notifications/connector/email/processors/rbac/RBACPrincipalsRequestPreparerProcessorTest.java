package com.redhat.cloud.notifications.connector.email.processors.rbac;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

@QuarkusTest
public class RBACPrincipalsRequestPreparerProcessorTest extends CamelQuarkusTestSupport {
    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    RBACPrincipalsRequestPreparerProcessor rbacPrincipalsRequestPreparerProcessor;

    /**
     * Tests that the processor prepares the request as expected.
     */
    @Test
    void testProcessor() {
        // Prepare the properties that the processor expects.
        final String orgId = "65050842-4723-11ee-8b73-271566e07622";
        final RecipientSettings recipientSettings = new RecipientSettings(
            true,
            true,
            null,
            null
        );
        final int offset = 25;

        final Exchange exchange = this.createExchangeWithBody("");
        exchange.setProperty(com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID, orgId);
        exchange.setProperty(ExchangeProperty.CURRENT_RECIPIENT_SETTINGS, recipientSettings);
        exchange.setProperty(ExchangeProperty.OFFSET, offset);

        // Call the processor under test.
        this.rbacPrincipalsRequestPreparerProcessor.process(exchange);

        // Assert that the headers are correct.
        final Map<String, Object> headers = exchange.getMessage().getHeaders();
        Assertions.assertEquals("application/json", headers.get("Accept"));
        Assertions.assertEquals(this.emailConnectorConfig.getRbacApplicationKey(), headers.get(RBACConstants.HEADER_X_RH_RBAC_CLIENT_ID));
        Assertions.assertEquals(this.emailConnectorConfig.getRbacPSK(), headers.get(RBACConstants.HEADER_X_RH_RBAC_PSK));
        Assertions.assertEquals(orgId, headers.get(RBACConstants.HEADER_X_RH_RBAC_ORG_ID));
        Assertions.assertEquals("/api/rbac/v1/principals/", headers.get(Exchange.HTTP_PATH));
        Assertions.assertEquals(
            String.format(
                "admin_only=%s" +
                "&offset=%s" +
                "&limit=%s",
                recipientSettings.isAdminsOnly(),
                offset,
                this.emailConnectorConfig.getRbacElementsPerPage()
            ),
            headers.get(Exchange.HTTP_QUERY)
        );
    }
}
