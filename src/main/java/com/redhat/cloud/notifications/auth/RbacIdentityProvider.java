package com.redhat.cloud.notifications.auth;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;

import javax.enterprise.context.ApplicationScoped;

/**
 * Authorizes the data from the insight's RBAC-server and adds the appropriate roles
 */
@ApplicationScoped
public class RbacIdentityProvider implements IdentityProvider<RhIdentityAuthenticationRequest> {
    @Override
    public Class<RhIdentityAuthenticationRequest> getRequestType() {
        return RhIdentityAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RhIdentityAuthenticationRequest rhAuthReq, AuthenticationRequestContext authenticationRequestContext) {
        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder()
                .addRole("read")
                .addRole("write")
                .addRole("execute");

        // If Rbac authentication fails
//        return Uni.createFrom().failure(new AuthenticationFailedException(e));
        return Uni.createFrom().item(builder.build());
    }
}
