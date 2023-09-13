package com.redhat.cloud.notifications.connector.email.processors.rbac.group;

import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.processors.rbac.authentication.RBACAuthenticationUtilities;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http.HttpMethods;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;

@ApplicationScoped
public class RBACGroupPrincipalsRequestPreparerProcessor implements Processor {
    @Inject
    RBACAuthenticationUtilities rbacAuthenticationUtilities;

    @Override
    public void process(final Exchange exchange) {
        // Set the "Accept" header for the incoming payload.
        exchange.getMessage().setHeader("Accept", "application/json");

        // Set the authentication headers for RBAC.
        this.rbacAuthenticationUtilities.setAuthenticationHeaders(exchange, exchange.getProperty(ORG_ID, String.class));

        // Set the path and methods of the request. Beware that RBAC is
        // expecting the trailing slash in the path.
        final String groupUUID = exchange.getProperty(ExchangeProperty.GROUP_UUID, String.class);

        exchange.getMessage().setHeader(Exchange.HTTP_PATH, String.format("/api/rbac/v1/groups/%s/principals/", groupUUID));
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, HttpMethods.GET);
    }
}
