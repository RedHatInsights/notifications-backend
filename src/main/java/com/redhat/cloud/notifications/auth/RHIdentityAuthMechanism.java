package com.redhat.cloud.notifications.auth;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import javax.enterprise.context.ApplicationScoped;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Implements Jakarta EE JSR-375 (Security API) HttpAuthenticationMechanism for the insight's
 * x-rh-identity header and RBAC
 */
@ApplicationScoped
public class RHIdentityAuthMechanism implements HttpAuthenticationMechanism {

    private static final String IDENTITY_HEADER = "x-rh-identity";

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext routingContext, IdentityProviderManager identityProviderManager) {
        String xRhIdentityHeaderValue = routingContext.request().getHeader(IDENTITY_HEADER);
        if(xRhIdentityHeaderValue == null) {
            return Uni.createFrom().nullItem();
        }
        Uni<Set<String>> rbacRoles = getRbacRoles(xRhIdentityHeaderValue);

        Uni<QuarkusSecurityIdentity.Builder> identityBuilderUni = Uni.createFrom().item(getRhIdentityFromString(xRhIdentityHeaderValue))
                .onItem().transform(rhid -> new RhIdPrincipal(rhid.getIdentity().getUser().getUsername(), rhid.getIdentity().getAccountNumber()))
                .onItem().transform(principal -> QuarkusSecurityIdentity.builder().setPrincipal(principal));

        // Do RBAC query and then do (addRoles etc to the SecurityIdentity)
        return rbacRoles
                .onItem().transformToUni((Function<Set<String>, Uni<QuarkusSecurityIdentity.Builder>>) roles -> identityBuilderUni.onItem().transform(b -> b.addRoles(roles)))
                .onItem().transform(QuarkusSecurityIdentity.Builder::build);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext routingContext) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Collections.emptySet();
    }

    @Override
    public HttpCredentialTransport getCredentialTransport() {
        return null;
    }

    private Uni<Set<String>> getRbacRoles(String xRhIdentity) {
        // TODO Add RBAC call and mapping to Set<String>
        Set<String> rights = new HashSet<>();
        rights.add("read");
        rights.add("write");
        rights.add("execute");
        return Uni.createFrom().item(rights);
    }

    private static RhIdentity getRhIdentityFromString(String xRhIdHeader) {
        String xRhDecoded = new String(Base64.getDecoder().decode(xRhIdHeader));
        JsonObject json = new JsonObject(xRhDecoded);
        return json.mapTo(RhIdentity.class);
    }
}
