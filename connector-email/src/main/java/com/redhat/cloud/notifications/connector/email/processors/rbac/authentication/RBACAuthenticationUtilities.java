package com.redhat.cloud.notifications.connector.email.processors.rbac.authentication;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.processors.rbac.RBACConstants;
import org.apache.camel.Exchange;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class RBACAuthenticationUtilities {
    @Inject
    EmailConnectorConfig emailConnectorConfig;

    /**
     * Sets the authentications headers for RBAC. In case we are in development
     * mode and the development key has been given, a basic authorization
     * header is set instead of the RBAC's PSK, which usually is taken from
     * a JSON secret set in Clowder.
     * @param exchange the exchange to set the authentication headers to.
     * @param orgId the tenant's organization ID required to set it if the
     *              authentication is made via a PSK secret.
     */
    public void setAuthenticationHeaders(final Exchange exchange, final String orgId) {
        if (this.emailConnectorConfig.isRbacDevelopmentAuthenticationKeyPresent()) {
            // Set a basic authentication header which is used for development
            // purposes.
            exchange.getMessage().setHeader("Authorization", String.format("Basic %s", this.emailConnectorConfig.getRbacDevelopmentAuthenticationKeyAuthInfo()));
        } else {
            // Set the PSK required for the request to be authenticated in RBAC.
            exchange.getMessage().setHeader(RBACConstants.HEADER_X_RH_RBAC_PSK, this.emailConnectorConfig.getRbacPSK());

            // Set the ORG ID required by the other end.
            exchange.getMessage().setHeader(RBACConstants.HEADER_X_RH_RBAC_ORG_ID, orgId);
        }
    }
}
