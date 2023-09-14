package com.redhat.cloud.notifications.connector.email.processors.rbac;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import com.redhat.cloud.notifications.connector.email.processors.rbac.authentication.RBACAuthenticationUtilities;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;

@ApplicationScoped
public class RBACPrincipalsRequestPreparerProcessor implements Processor {
    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    RBACAuthenticationUtilities rbacAuthenticationUtilities;

    @Override
    public void process(final Exchange exchange) {
        final String orgId = exchange.getProperty(ORG_ID, String.class);
        final RecipientSettings recipientSettings = exchange.getProperty(ExchangeProperty.CURRENT_RECIPIENT_SETTINGS, RecipientSettings.class);

        // Grab the current offset that will be used to fetch different pages
        // from RBAC.
        final int offset = exchange.getProperty(ExchangeProperty.OFFSET, Integer.class);

        // Set the accept header for the response's payload.
        exchange.getMessage().setHeader("Accept", "application/json");

        // Set the authentication headers for RBAC.
        this.rbacAuthenticationUtilities.setAuthenticationHeaders(exchange, orgId);

        // Set the path of the request's call. Beware that RBAC is expecting
        // the trailing slash.
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/api/rbac/v1/principals/");

        // Set the query parameters for the call.
        exchange.getMessage().setHeader(
            Exchange.HTTP_QUERY,
            String.format(
                "admin_only=%s" +
                "&offset=%s" +
                "&limit=%s",
                recipientSettings.isAdminsOnly(),
                offset,
                this.emailConnectorConfig.getRbacElementsPerPage()
            )
        );
    }
}
