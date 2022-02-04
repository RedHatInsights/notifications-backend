package com.redhat.cloud.notifications.auth.rhid;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

import static com.redhat.cloud.notifications.Constants.X_RH_IDENTITY_HEADER;

public class RhIdentityAuthenticationRequest extends BaseAuthenticationRequest {

    public RhIdentityAuthenticationRequest(String xRhIdentity) {
        setAttribute(X_RH_IDENTITY_HEADER, xRhIdentity);
    }
}
