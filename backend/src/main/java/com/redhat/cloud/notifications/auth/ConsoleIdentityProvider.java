package com.redhat.cloud.notifications.auth;

import com.redhat.cloud.notifications.Base64Utils;
import com.redhat.cloud.notifications.auth.principal.ConsoleIdentity;
import com.redhat.cloud.notifications.auth.principal.ConsoleIdentityWrapper;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.auth.principal.turnpike.TurnpikePrincipal;
import com.redhat.cloud.notifications.auth.principal.turnpike.TurnpikeSamlIdentity;
import com.redhat.cloud.notifications.auth.rbac.RbacServer;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.InternalRoleAccess;
import io.netty.channel.ConnectTimeoutException;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.unchecked.Unchecked;
import io.vertx.core.json.Json;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.IOException;
import java.time.Duration;

/**
 * Authorizes the data from the insight's RBAC-server and adds the appropriate roles
 */
@ApplicationScoped
public class ConsoleIdentityProvider implements IdentityProvider<ConsoleAuthenticationRequest> {

    public static final String RBAC_READ_NOTIFICATIONS_EVENTS = "read:events";
    public static final String RBAC_READ_NOTIFICATIONS = "read:notifications";
    public static final String RBAC_WRITE_NOTIFICATIONS = "write:notifications";
    public static final String RBAC_READ_INTEGRATIONS_ENDPOINTS = "read:integrations_ep";
    public static final String RBAC_WRITE_INTEGRATIONS_ENDPOINTS = "write:integrations_ep";

    // This permission is added to users using turnpike to access the internal API
    public static final String RBAC_INTERNAL_USER = "read:internal";

    // This permission is added to users of the ${internal.admin-role} group
    public static final String RBAC_INTERNAL_ADMIN = "write:internal";

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    BackendConfig backendConfig;

    @Inject
    Environment environment;

    @Inject
    @RestClient
    RbacServer rbacServer;

    @ConfigProperty(name = "rbac.retry.max-attempts", defaultValue = "3")
    long maxRetryAttempts;

    @ConfigProperty(name = "rbac.retry.back-off.initial-value", defaultValue = "0.1S")
    Duration initialBackOff;

    @ConfigProperty(name = "rbac.retry.back-off.max-value", defaultValue = "1S")
    Duration maxBackOff;

    @Override
    public Class<ConsoleAuthenticationRequest> getRequestType() {
        return ConsoleAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(final ConsoleAuthenticationRequest request, final AuthenticationRequestContext ignored) {
        // Protect ourselves from malformed "x-rh-identity" headers before
        // proceeding.
        final ConsoleIdentity consoleIdentity;
        try {
            consoleIdentity = getRhIdentityFromString(request.getXRhIdentityHeaderValue());
        } catch (final IllegalArgumentException e) {
            Log.warnf(e, "[x-rh-identity: %s] Unable to decode identity header", request.getXRhIdentityHeaderValue());

            throw new AuthenticationFailedException();
        }

        // Identify which kind of identity we are dealing with.
        switch (consoleIdentity) {
            case RhIdentity rhIdentity -> {
                // The "user" and "service account" identities need to come
                // with an "org_id", since it is crucial for our application
                // to work and make the proper relations.
                if (rhIdentity.getOrgId() == null || rhIdentity.getOrgId().isBlank()) {
                    Log.warnf("[x-rh-identity: %s] Rejected request header because the \"org_id\" field is missing in the identity header");

                    return Uni.createFrom().failure(new AuthenticationFailedException());
                }

                // Extract the principal.
                final RhIdPrincipal rhIdPrincipal = new RhIdPrincipal(rhIdentity);

                // The Kessel back end takes priority over any other authentication, in
                // order to be able to have both the RBAC and Kessel back ends enabled
                // at the same time. That way we can jump from using RBAC to Kessel and
                // if we see that something is not working properly, we can instantly
                // switch back to RBAC by disabling Kessel.
                if (this.backendConfig.isKesselRelationsEnabled(rhIdPrincipal.getOrgId())) {
                    return this.buildKesselSecurityIdentity(rhIdentity, rhIdPrincipal);
                }

                // At this point we know Kessel is not enabled for the
                // specified organization, so we call RBAC as usual...
                //
                // ... unless it is disabled. In that case, if neither Kessel
                // nor RBAC are enabled we can assume that we are on a
                // development environment, and that we can simply return a
                // security identity with full privileges.
                if (this.backendConfig.isRBACEnabled()) {
                    return this.buildRBACSecurityIdentity(request.getXRhIdentityHeaderValue(), rhIdPrincipal);
                } else if (!this.environment.isLocal()) {
                    Log.errorf("Kessel and RBAC are disabled in a non development environment");

                    return Uni.createFrom().failure(new AuthenticationFailedException());
                } else {
                    return this.buildDevelopmentSecurityIdentity(rhIdPrincipal);
                }
            }

            // For Turnpike we just want to add the "internal user" role to the
            // list of roles that the identity might come with.
            case TurnpikeSamlIdentity turnpikeSamlIdentity -> {
                return this.buildTurnpikeSecurityIdentity(turnpikeSamlIdentity);
            }

            // We do not support any other authentication methods for the
            // moment.
            default -> {
                Log.warnf("[identity_type: %s][identity_name: %s] Unprocessable identity", consoleIdentity.type, consoleIdentity.getName());

                return Uni.createFrom().failure(new AuthenticationFailedException());
            }
        }
    }

    /**
     * Builds a development security identity, with all the available roles
     * appended to it, so that the user has all the privileges.
     * @param rhIdPrincipal the principal that will be attached to the security
     *                      identity.
     * @return a development security identity.
     */
    public Uni<SecurityIdentity> buildDevelopmentSecurityIdentity(final RhIdPrincipal rhIdPrincipal) {
        return Uni.createFrom().item(() -> QuarkusSecurityIdentity.builder()
            .setPrincipal(rhIdPrincipal)
            .addRole(RBAC_READ_NOTIFICATIONS_EVENTS)
            .addRole(RBAC_READ_NOTIFICATIONS)
            .addRole(RBAC_WRITE_NOTIFICATIONS)
            .addRole(RBAC_READ_INTEGRATIONS_ENDPOINTS)
            .addRole(RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
            .addRole(RBAC_INTERNAL_USER)
            .addRole(RBAC_INTERNAL_ADMIN)
            .addRole(adminRole)
            .build());
    }

    /**
     * <p>Builds an empty security identity with just the principal and no
     * roles. The reason for not adding any roles is that the authorization
     * checks will be performed by calling Kessel either in the {@link com.redhat.cloud.notifications.auth.annotation.AuthorizationInterceptor}
     * or in the endpoint handlers themselves.</p>
     *
     * <p>The function checks that the {@code user_id} field is present and not
     * blank, because it is required to use it in the "subject" part of Kessel
     * requests</p>
     * @param rhIdentity the identity to get the user ID from, which is
     *                   required by Kessel to perform relation checks for the
     *                   principal.
     * @param rhIdPrincipal the principal to be attached to the security
     *                      identity.
     * @return an empty security identity with just the provided principal
     * attached to it.
     */
    public Uni<SecurityIdentity> buildKesselSecurityIdentity(final RhIdentity rhIdentity, final RhIdPrincipal rhIdPrincipal) {
        // Kessel uses the "user_id" from the "user" and "service account
        // identities, so it needs to be present for the authorization to work.
        if (rhIdentity.getUserId() == null || rhIdentity.getUserId().isBlank()) {
            Log.warnf("[x-rh-identity: %s] Rejected identity header due to the \"user_id\" field missing on a Kessel context");

            return Uni.createFrom().failure(new AuthenticationFailedException());
        } else {
            return Uni.createFrom().item(
                QuarkusSecurityIdentity
                    .builder()
                    .setPrincipal(rhIdPrincipal)
                    .build()
            );
        }
    }

    /**
     * Build a security identity by calling RBAC and using the provided
     * "x-rh-identity" header. The roles are extracted from the RBAC response
     * and transformed or translated accordingly for our application to
     * understand them.
     * @param xRhIdentityHeaderValue the original "x-rh-identity" header's
     *                               value.
     * @param principal the principal to attach to the security identity.
     * @return a security identity with the roles that were fetched and mapped
     * from RBAC.
     */
    @CacheResult(cacheName = "rbac-cache")
    public Uni<SecurityIdentity> buildRBACSecurityIdentity(final String xRhIdentityHeaderValue, final RhIdPrincipal principal) {
        final QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder();
        builder.setPrincipal(principal);

        return rbacServer.getRbacInfo("notifications,integrations", xRhIdentityHeaderValue)
            /*
             * RBAC server calls fail regularly because of RBAC instability so we need to retry.
             * IOException is thrown when the connection between us and RBAC is reset during an RBAC call execution.
             * ConnectTimeoutException is thrown when RBAC does not respond at all to our call.
             */
            .onFailure(failure -> failure.getClass() == IOException.class || failure.getClass() == ConnectTimeoutException.class)
            .retry()
            .withBackOff(this.initialBackOff, this.maxBackOff)
            .atMost(this.maxRetryAttempts)
            // After we're done retrying, an RBAC server call failure will cause an authentication failure
            .onFailure().transform(Unchecked.function(failure -> {
                throw new AuthenticationFailedException("RBAC authentication call failed", failure);
            }))
            // Otherwise, we can finish building the QuarkusSecurityIdentity and return the result
            .onItem().transform(rbacRaw -> {
                if (rbacRaw.canRead("notifications", "events")) {
                    builder.addRole(RBAC_READ_NOTIFICATIONS_EVENTS);
                }
                if (rbacRaw.canRead("notifications", "notifications")) {
                    builder.addRole(RBAC_READ_NOTIFICATIONS);
                }
                if (rbacRaw.canWrite("notifications", "notifications")) {
                    builder.addRole(RBAC_WRITE_NOTIFICATIONS);
                }
                if (rbacRaw.canRead("integrations", "endpoints")) {
                    builder.addRole(RBAC_READ_INTEGRATIONS_ENDPOINTS);
                }
                if (rbacRaw.canWrite("integrations", "endpoints")) {
                    builder.addRole(RBAC_WRITE_INTEGRATIONS_ENDPOINTS);
                }
                return builder.build();
            });
    }

    /**
     * Build a security identity from an incoming Turnpike SAML "associate"
     * identity. Includes all the specified roles in the "associate" identity
     * plus a predefined {@link #RBAC_INTERNAL_USER} that we append.
     * @param turnpikeSamlIdentity the "associate" identity.
     * @return a security identity with all the specified roles in the incoming
     * identity plus the {@link #RBAC_INTERNAL_USER} role too.
     */
    public Uni<SecurityIdentity> buildTurnpikeSecurityIdentity(final TurnpikeSamlIdentity turnpikeSamlIdentity) {
        final QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder();
        builder.setPrincipal(new TurnpikePrincipal(turnpikeSamlIdentity));

        builder.addRole(RBAC_INTERNAL_USER);
        for (String role : turnpikeSamlIdentity.associate.roles) {
            if (role.equals(adminRole)) {
                builder.addRole(RBAC_INTERNAL_ADMIN);
            }

            String internalRole = InternalRoleAccess.getInternalRole(role);
            builder.addRole(internalRole);
        }

        return Uni.createFrom().item(builder.build());
    }

    public static ConsoleIdentity getRhIdentityFromString(String xRhIdHeader) {
        String xRhDecoded = Base64Utils.decode(xRhIdHeader);
        ConsoleIdentity identity = Json.decodeValue(xRhDecoded, ConsoleIdentityWrapper.class).getIdentity();
        identity.rawIdentity = xRhIdHeader;
        return identity;
    }
}
