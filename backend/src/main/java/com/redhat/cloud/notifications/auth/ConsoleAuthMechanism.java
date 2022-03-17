package com.redhat.cloud.notifications.auth;

import com.redhat.cloud.notifications.auth.principal.ConsolePrincipal;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import java.util.Collections;
import java.util.Set;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.Constants.X_RH_IDENTITY_HEADER;

/**
 * Implements Jakarta EE JSR-375 (Security API) HttpAuthenticationMechanism for the insight's
 * x-rh-identity header and RBAC
 */
@ApplicationScoped
public class ConsoleAuthMechanism implements HttpAuthenticationMechanism {

    @ConfigProperty(name = "internal-rbac.enabled", defaultValue = "true")
    boolean isInternalRbacEnabled;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext routingContext, IdentityProviderManager identityProviderManager) {
        String xRhIdentityHeaderValue = routingContext.request().getHeader(X_RH_IDENTITY_HEADER);
        String path = routingContext.normalizedPath();

        if (path.startsWith(API_INTERNAL) && !isInternalRbacEnabled) {
            // Disable internal auth - could be useful for ephemeral environments
            return Uni.createFrom().item(() -> QuarkusSecurityIdentity.builder()
                    .setPrincipal(ConsolePrincipal.noIdentity())
                    .addRole(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
                    .addRole(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
                    .build());
        } else if (xRhIdentityHeaderValue == null) { // Access that did not go through 3Scale or turnpike
            boolean good = false;

            // We block access unless the openapi file is requested.
            if (path.startsWith("/api/notifications") || path.startsWith("/api/integrations") || path.startsWith("/api/private")
                || path.startsWith(API_INTERNAL)) {
                if (path.endsWith("openapi.json")) {
                    good = true;
                }
            }

            if (path.startsWith("/openapi.json") || path.startsWith(API_INTERNAL + "/validation") || path.startsWith(API_INTERNAL + "/version")
                    || path.startsWith("/health") || path.startsWith("/metrics")) {
                good = true;
            }

            if (!good) {
                return Uni.createFrom().failure(new AuthenticationFailedException("No " + X_RH_IDENTITY_HEADER + " provided"));
            } else {
                return Uni.createFrom().item(QuarkusSecurityIdentity.builder()
                        // Set a dummy principal, but add no roles.
                        .setPrincipal(ConsolePrincipal.noIdentity())
                        .build());
            }
        }

        ConsoleAuthenticationRequest authReq = new ConsoleAuthenticationRequest(xRhIdentityHeaderValue);
        return identityProviderManager.authenticate(authReq);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext routingContext) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Collections.singleton(ConsoleAuthenticationRequest.class);
    }

    @Override
    public HttpCredentialTransport getCredentialTransport() {
        return null;
    }
}
