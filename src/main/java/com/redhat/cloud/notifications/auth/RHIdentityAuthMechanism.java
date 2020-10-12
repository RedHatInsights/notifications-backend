package com.redhat.cloud.notifications.auth;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import javax.enterprise.context.ApplicationScoped;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;

/**
 * Implements Jakarta EE JSR-375 (Security API) HttpAuthenticationMechanism for the insight's
 * x-rh-identity header and RBAC
 */
@ApplicationScoped
public class RHIdentityAuthMechanism implements HttpAuthenticationMechanism {

    public static final String IDENTITY_HEADER = "x-rh-identity";

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext routingContext, IdentityProviderManager identityProviderManager) {
        String xRhIdentityHeaderValue = routingContext.request().getHeader(IDENTITY_HEADER);

        // Access from outside the 3Scale
        if (xRhIdentityHeaderValue == null) {
            return Uni.createFrom().item(QuarkusSecurityIdentity.builder().build());
        }

        RhIdentityAuthenticationRequest authReq = new RhIdentityAuthenticationRequest(xRhIdentityHeaderValue);
        Uni<SecurityIdentity> identityUni = identityProviderManager.authenticate(authReq);

        Uni<QuarkusSecurityIdentity.Builder> identityBuilderUni = Uni.createFrom().item(() -> getRhIdentityFromString(xRhIdentityHeaderValue))
                .onFailure().transform(AuthenticationFailedException::new)
                .onItem().transform(rhid -> new RhIdPrincipal(rhid.getIdentity().getUser().getUsername(), rhid.getIdentity().getAccountNumber()))
                .onItem().transform(principal -> QuarkusSecurityIdentity.builder().setPrincipal(principal));

        return identityBuilderUni.onItem().transformToUni(builder -> identityUni.onItem().transform(ide -> builder.addRoles(ide.getRoles())))
                .onItem().transform(QuarkusSecurityIdentity.Builder::build);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext routingContext) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Collections.singleton(RhIdentityAuthenticationRequest.class);
    }

    @Override
    public HttpCredentialTransport getCredentialTransport() {
        return null;
    }

    private static RhIdentity getRhIdentityFromString(String xRhIdHeader) {
        String xRhDecoded = new String(Base64.getDecoder().decode(xRhIdHeader));
        JsonObject json = new JsonObject(xRhDecoded);
        return json.mapTo(RhIdentity.class);
    }
}
