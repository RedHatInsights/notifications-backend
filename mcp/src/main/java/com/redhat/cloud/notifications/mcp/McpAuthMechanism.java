package com.redhat.cloud.notifications.mcp;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;

/**
 * HTTP authentication mechanism for MCP endpoints that validates the x-rh-identity header.
 * Per ADR-084, MCP servers within HCC must authenticate using x-rh-identity rather than
 * standard MCP auth headers, as the HCC gateway strips and transforms authentication headers.
 *
 * All endpoints require authentication by default. Only paths listed in
 * {@link #ANONYMOUS_PATHS} are allowed without an x-rh-identity header.
 */
@ApplicationScoped
public class McpAuthMechanism implements HttpAuthenticationMechanism {

    static final String X_RH_IDENTITY_HEADER = "x-rh-identity";
    static final List<String> ANONYMOUS_PATHS = List.of("/q/health", "/q/metrics");

    private static final Set<Class<? extends AuthenticationRequest>> CREDENTIAL_TYPES = Set.of(McpAuthenticationRequest.class);

    @Inject
    MeterRegistry registry;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext routingContext, IdentityProviderManager identityProviderManager) {
        String path = routingContext.normalizedPath();

        for (String anonymousPath : ANONYMOUS_PATHS) {
            if (path.equals(anonymousPath) || path.startsWith(anonymousPath + "/")) {
                return Uni.createFrom().item(QuarkusSecurityIdentity.builder()
                        .setPrincipal(() -> "anonymous")
                        .setAnonymous(true)
                        .build());
            }
        }

        String xRhIdentityHeaderValue = routingContext.request().getHeader(X_RH_IDENTITY_HEADER);

        Log.debugf("MCP authentication: path=%s, x-rh-identity present=%s", path, xRhIdentityHeaderValue != null);

        if (xRhIdentityHeaderValue == null) {
            registry.counter("notifications.mcp.auth.failure", "reason", "missing_header").increment();
            Log.warnf("MCP authentication failed: missing %s header for path %s", X_RH_IDENTITY_HEADER, path);
            return Uni.createFrom().failure(new AuthenticationFailedException("Missing " + X_RH_IDENTITY_HEADER + " header"));
        }

        McpAuthenticationRequest authRequest = new McpAuthenticationRequest(xRhIdentityHeaderValue);
        return identityProviderManager.authenticate(authRequest);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext routingContext) {
        // No challenge data for header-based auth
        return Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return CREDENTIAL_TYPES;
    }
}
