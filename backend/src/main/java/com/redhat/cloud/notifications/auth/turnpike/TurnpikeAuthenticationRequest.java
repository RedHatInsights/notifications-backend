package com.redhat.cloud.notifications.auth.turnpike;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

import static com.redhat.cloud.notifications.auth.rhid.RHIdentityAuthMechanism.IDENTITY_HEADER;

public class TurnpikeAuthenticationRequest extends BaseAuthenticationRequest {

    public TurnpikeAuthenticationRequest(String xRhIdentity) {
        setAttribute(IDENTITY_HEADER, xRhIdentity);
    }

}
