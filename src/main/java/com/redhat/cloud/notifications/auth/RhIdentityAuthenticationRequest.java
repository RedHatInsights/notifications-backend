package com.redhat.cloud.notifications.auth;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

import static com.redhat.cloud.notifications.auth.RHIdentityAuthMechanism.IDENTITY_HEADER;

public class RhIdentityAuthenticationRequest extends BaseAuthenticationRequest {

    public RhIdentityAuthenticationRequest(String xRhIdentity) {
        setAttribute(IDENTITY_HEADER, xRhIdentity);
    }
}
