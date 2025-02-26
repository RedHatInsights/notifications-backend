package com.redhat.cloud.notifications.auth;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

import static com.redhat.cloud.notifications.Constants.X_RH_IDENTITY_HEADER;

public class ConsoleAuthenticationRequest extends BaseAuthenticationRequest {

    public ConsoleAuthenticationRequest(String xRhIdentity) {
        setAttribute(X_RH_IDENTITY_HEADER, xRhIdentity);
    }

    /**
     * Returns the original "x-rh-identity" header's value.
     * @return the unprocessed "x-rh-identity" header's value.
     */
    public String getXRhIdentityHeaderValue() {
        return getAttribute(X_RH_IDENTITY_HEADER);
    }
}
