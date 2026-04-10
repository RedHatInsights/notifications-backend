package com.redhat.cloud.notifications.mcp;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Identity provider for MCP endpoints that validates x-rh-identity headers.
 * Per ADR-084, MCP servers must strictly inherit the user's identity with no
 * service-account elevation for user-initiated operations.
 */
@ApplicationScoped
public class McpIdentityProvider implements IdentityProvider<McpAuthenticationRequest> {

    @Inject
    MeterRegistry registry;

    @Override
    public Class<McpAuthenticationRequest> getRequestType() {
        return McpAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(McpAuthenticationRequest request, AuthenticationRequestContext context) {
        try {
            RhIdentity rhIdentity = parseIdentity(request.getXRhIdentityHeaderValue());

            validateRequiredField(rhIdentity.orgId(), "org_id");
            validateRequiredField(rhIdentity.userId(), "user_id");
            validateRequiredField(rhIdentity.username(), "username");

            Log.debugf("MCP authentication successful: org_id=%s, user_id=%s, username=%s, type=%s",
                    rhIdentity.orgId(), rhIdentity.userId(), rhIdentity.username(), rhIdentity.type());

            McpPrincipal principal = new McpPrincipal(rhIdentity);

            registry.counter("notifications.mcp.auth.success").increment();
            return Uni.createFrom().item(QuarkusSecurityIdentity.builder()
                    .setPrincipal(principal)
                    .build());

        } catch (MissingFieldException e) {
            registry.counter("notifications.mcp.auth.failure", "reason", "missing_" + e.fieldName).increment();
            Log.warnf("MCP authentication rejected: missing %s in x-rh-identity header", e.fieldName);
            return Uni.createFrom().failure(new AuthenticationFailedException("Missing " + e.fieldName + " in identity header"));
        } catch (IllegalArgumentException | DecodeException e) {
            registry.counter("notifications.mcp.auth.failure", "reason", "invalid_header").increment();
            Log.warnf(e, "MCP authentication failed: invalid x-rh-identity header");
            return Uni.createFrom().failure(new AuthenticationFailedException("Invalid x-rh-identity header", e));
        }
    }

    private static void validateRequiredField(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new MissingFieldException(fieldName);
        }
    }

    private static class MissingFieldException extends RuntimeException {
        final String fieldName;

        MissingFieldException(String fieldName) {
            super("Missing " + fieldName);
            this.fieldName = fieldName;
        }
    }

    /**
     * Parse x-rh-identity header into RhIdentity record.
     * Only User identities are accepted — service accounts and other types are rejected.
     *
     * SECURITY: This method trusts the x-rh-identity header without signature verification.
     * The HCC gateway (3Scale/Turnpike) is responsible for OIDC token validation and injects
     * this header into forwarded requests. The MCP service must never be exposed to untrusted
     * networks — the ClowdApp deployment enforces this with {@code public: false}.
     */
    private RhIdentity parseIdentity(String xRhIdentityHeader) {
        String decoded = new String(Base64.getDecoder().decode(xRhIdentityHeader.getBytes(UTF_8)), UTF_8);
        JsonObject identity = new JsonObject(decoded).getJsonObject("identity");

        if (identity == null) {
            throw new IllegalArgumentException("Missing 'identity' field in x-rh-identity header");
        }

        String type = identity.getString("type");
        if (!"User".equals(type)) {
            throw new IllegalArgumentException("Unsupported identity type: " + type + ". Only User identities are allowed.");
        }

        JsonObject user = identity.getJsonObject("user");

        return new RhIdentity(
            type,
            identity.getString("org_id"),
            identity.getString("account_number"),
            user != null ? user.getString("user_id") : null,
            user != null ? user.getString("username") : null,
            xRhIdentityHeader
        );
    }
}
