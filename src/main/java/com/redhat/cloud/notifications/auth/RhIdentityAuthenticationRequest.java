package com.redhat.cloud.notifications.auth;

import io.quarkus.security.identity.request.AuthenticationRequest;

public class RhIdentityAuthenticationRequest implements AuthenticationRequest {
    private final String xRhIdentity;

    public RhIdentityAuthenticationRequest(String xRhIdentity) {
        this.xRhIdentity = xRhIdentity;
    }

    public String getxRhIdentity() {
        return xRhIdentity;
    }
}
