package com.redhat.cloud.notifications.auth;

import com.redhat.cloud.notifications.Base64Utils;
import com.redhat.cloud.notifications.auth.principal.ConsoleIdentity;
import com.redhat.cloud.notifications.auth.principal.ConsoleIdentityWrapper;
import com.redhat.cloud.notifications.auth.principal.ConsolePrincipal;
import com.redhat.cloud.notifications.auth.principal.ConsolePrincipalFactory;
import com.redhat.cloud.notifications.auth.principal.IllegalIdentityHeaderException;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.auth.principal.turnpike.TurnpikeSamlIdentity;
import com.redhat.cloud.notifications.auth.rbac.RbacServer;
import com.redhat.cloud.notifications.config.BackendConfig;
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
import io.vertx.core.json.Json;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.util.Optional;

import static com.redhat.cloud.notifications.Constants.X_RH_IDENTITY_HEADER;

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
    @RestClient
    RbacServer rbacServer;

    @ConfigProperty(name = "rbac.enabled", defaultValue = "true")
    Boolean isRbacEnabled;

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
    public Uni<SecurityIdentity> authenticate(ConsoleAuthenticationRequest rhAuthReq, AuthenticationRequestContext authenticationRequestContext) {
        // The Kessel back end takes priority over any other authentication, in
        // order to be able to have both the RBAC and Kessel back ends enabled
        // at the same time. That way we can jump from using RBAC to Kessel and
        // if we see that something is not working properly, we can instantly
        // switch back to RBAC by disabling Kessel.
        try {
            String orgId = "-none-";
            // Build the principal from the incoming "x-rh-identity" header.
            Optional<Principal> principal = this.buildPrincipalFromIdentityHeader(rhAuthReq);
            if (principal.get() instanceof RhIdPrincipal rhIdPrincipal) {
                orgId = rhIdPrincipal.getOrgId();
            }
            if (this.backendConfig.isKesselRelationsEnabled(orgId)) {

                final QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder();

                // Set the principal to the one we decoded from the header,
                // unless no header was present. In that case we build an empty
                // principal.
                builder.setPrincipal(principal.orElse(ConsolePrincipal.noIdentity()));

                // Build the security identity for Quarkus.
                return Uni.createFrom().item(builder.build());
            }
        } catch (final IllegalIdentityHeaderException | IllegalArgumentException e) {
            return Uni.createFrom().failure(() -> new AuthenticationFailedException(e));
        }

        if (!isRbacEnabled) {
            final Principal principal;
            try {
                final Optional<Principal> optionalPrincipal = this.buildPrincipalFromIdentityHeader(rhAuthReq);

                // Set the principal to the one we decoded from the header,
                // unless no header was present. In that case we set an empty
                // principal.
                principal = optionalPrincipal.orElse(ConsolePrincipal.noIdentity());
            } catch (final IllegalIdentityHeaderException e) {
                return Uni.createFrom().failure(() -> new AuthenticationFailedException(e));
            }

            return Uni.createFrom().item(() -> QuarkusSecurityIdentity.builder()
                    .setPrincipal(principal)
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
        // Retrieve the identity header from the authentication request
        return Uni.createFrom().item(() -> (String) rhAuthReq.getAttribute(X_RH_IDENTITY_HEADER))
                .onItem().transformToUni(xRhIdHeader -> buildQuarkusUni(xRhIdHeader));
    }

    @CacheResult(cacheName = "rbac-cache")
    protected Uni<QuarkusSecurityIdentity> buildQuarkusUni(String xRhIdHeader) {
        // Start building a QuarkusSecurityIdentity
        return Uni.createFrom().item(QuarkusSecurityIdentity.builder())
            .onItem().transformToUni(builder -> {
                // Decode the header and deserialize the resulting JSON
                ConsoleIdentity identity = getRhIdentityFromString(xRhIdHeader);
                try {
                    ConsolePrincipal<?> principal = ConsolePrincipalFactory.fromIdentity(identity);
                    builder.setPrincipal(principal);
                } catch (IllegalIdentityHeaderException e) {
                    return Uni.createFrom().failure(() -> new AuthenticationFailedException(e));
                }
                if (identity instanceof RhIdentity rhIdentity) {
                    return rbacServer.getRbacInfo("notifications,integrations", xRhIdHeader)
                        /*
                         * RBAC server calls fail regularly because of RBAC instability so we need to retry.
                         * IOException is thrown when the connection between us and RBAC is reset during an RBAC call execution.
                         * ConnectTimeoutException is thrown when RBAC does not respond at all to our call.
                         */
                        .onFailure(failure -> failure.getClass() == IOException.class || failure.getClass() == ConnectTimeoutException.class)
                        .retry()
                        .withBackOff(initialBackOff, maxBackOff)
                        .atMost(maxRetryAttempts)
                        // After we're done retrying, an RBAC server call failure will cause an authentication failure
                        .onFailure().transform(failure -> {
                            throw new AuthenticationFailedException("RBAC authentication call failed", failure);
                        })
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
                } else if (identity instanceof TurnpikeSamlIdentity) {
                    builder.addRole(RBAC_INTERNAL_USER);
                    for (String role : ((TurnpikeSamlIdentity) identity).associate.roles) {
                        if (role.equals(adminRole)) {
                            builder.addRole(RBAC_INTERNAL_ADMIN);
                        }

                        String internalRole = InternalRoleAccess.getInternalRole(role);
                        builder.addRole(internalRole);
                    }

                    return Uni.createFrom().item(builder.build());
                } else {
                    Log.warnf("Unprocessed identity found. type: %s and name: %s", identity.type, identity.getName());
                    return Uni.createFrom().failure(new AuthenticationFailedException());
                }
            })
            // A failure will cause an authentication failure
            .onFailure().transform(throwable -> {
                Log.error("Error while processing identity", throwable);
                return new AuthenticationFailedException(throwable);
            });
    }

    /**
     * Builds the {@link Principal} object from the incoming "x-rh-identity"
     * header.
     * @param request the incoming request.
     * @return the built Principal from the request's header.
     * @throws IllegalIdentityHeaderException in case the header does not
     * contain the mandatory fields.
     */
    public Optional<Principal> buildPrincipalFromIdentityHeader(final ConsoleAuthenticationRequest request) throws IllegalIdentityHeaderException {
        final String xRhIdentityHeaderValue = request.getAttribute(X_RH_IDENTITY_HEADER);
        if (xRhIdentityHeaderValue == null) {
            return Optional.empty();
        }

        final ConsoleIdentity identity = getRhIdentityFromString(xRhIdentityHeaderValue);

        return Optional.of(ConsolePrincipalFactory.fromIdentity(identity));
    }

    public static ConsoleIdentity getRhIdentityFromString(String xRhIdHeader) {
        String xRhDecoded = Base64Utils.decode(xRhIdHeader);
        ConsoleIdentity identity = Json.decodeValue(xRhDecoded, ConsoleIdentityWrapper.class).getIdentity();
        identity.rawIdentity = xRhIdHeader;
        return identity;
    }
}
