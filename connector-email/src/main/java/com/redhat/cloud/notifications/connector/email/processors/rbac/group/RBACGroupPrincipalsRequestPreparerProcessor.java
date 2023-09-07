package com.redhat.cloud.notifications.connector.email.processors.rbac.group;

import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.processors.rbac.RBACConstants;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http.HttpMethods;

import javax.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;

@ApplicationScoped
public class RBACGroupPrincipalsRequestPreparerProcessor implements Processor {
    @Override
    public void process(final Exchange exchange) {
        // Set the "Accept" header for the incoming payload.
        exchange.getMessage().setHeader("Accept", "application/json");

        // Set the required Org ID header expected by RBAC.
        final String orgId = exchange.getProperty(ORG_ID, String.class);
        exchange.getMessage().setHeader(RBACConstants.HEADER_X_RH_RBAC_ORG_ID, orgId);

        // Set the path and methods of the request. Beware that RBAC is
        // expecting the trailing slash in the path.
        final String groupUUID = exchange.getProperty(ExchangeProperty.GROUP_UUID, String.class);

        exchange.getMessage().setHeader(Exchange.HTTP_PATH, String.format("/api/rbac/v1/groups/%s/principals/", groupUUID));
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, HttpMethods.GET);
    }
}
