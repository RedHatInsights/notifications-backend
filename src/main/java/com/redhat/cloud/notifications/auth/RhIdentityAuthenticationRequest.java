package com.redhat.cloud.notifications.auth;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

public class RhIdentityAuthenticationRequest extends BaseAuthenticationRequest {
    private final String xRhIdentity;

    public RhIdentityAuthenticationRequest(String xRhIdentity) {
        this.xRhIdentity = xRhIdentity;
    }

    public String getxRhIdentity() {
        return xRhIdentity;
    }
}
