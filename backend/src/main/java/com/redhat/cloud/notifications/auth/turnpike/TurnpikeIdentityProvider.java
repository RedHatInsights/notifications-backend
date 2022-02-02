package com.redhat.cloud.notifications.auth.turnpike;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;

public class TurnpikeIdentityProvider implements IdentityProvider<TurnpikeAuthenticationRequest> {


    @Override
    public Class<TurnpikeAuthenticationRequest> getRequestType() {
        return TurnpikeAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(TurnpikeAuthenticationRequest turnpikeAuthenticationRequest, AuthenticationRequestContext authenticationRequestContext) {
        return null;
    }
}
