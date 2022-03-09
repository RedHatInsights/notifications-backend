package com.redhat.cloud.notifications.auth;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

import static com.redhat.cloud.notifications.Constants.X_RH_IDENTITY_HEADER;

public class ConsoleAuthenticationRequest extends BaseAuthenticationRequest {

    public ConsoleAuthenticationRequest(String xRhIdentity) {
        setAttribute(X_RH_IDENTITY_HEADER, xRhIdentity);
    }
}
