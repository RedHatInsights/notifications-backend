package com.redhat.cloud.notifications.auth;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * Authorizes the data from the insight's RBAC-server and adds the appropriate roles
 */
@ApplicationScoped
public class RbacIdentityProvider implements IdentityProvider<RhIdentityAuthenticationRequest> {
    @Inject
    @RestClient
    RbacServer rbacServer;

    @Override
    public Class<RhIdentityAuthenticationRequest> getRequestType() {
        return RhIdentityAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RhIdentityAuthenticationRequest rhAuthReq, AuthenticationRequestContext authenticationRequestContext) {
        return rbacServer.getRbacInfo("notifications", rhAuthReq.getxRhIdentity())
                .onItem()
                .transform(raw -> {
                    QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder();
                    if (raw.canReadAll()) {
                        builder.addRole("read");
                    }
                    if (raw.canWriteAll()) {
                        builder.addRole("write");
                    }

                    return (SecurityIdentity) builder.build();
                })
                .onFailure()
                .transform(AuthenticationFailedException::new);
    }
}
