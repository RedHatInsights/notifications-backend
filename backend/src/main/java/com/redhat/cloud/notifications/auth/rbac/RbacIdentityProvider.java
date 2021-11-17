package com.redhat.cloud.notifications.auth.rbac;

import com.redhat.cloud.notifications.auth.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.rhid.RhIdentity;
import com.redhat.cloud.notifications.auth.rhid.RhIdentityAuthenticationRequest;
import io.netty.channel.ConnectTimeoutException;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.Json;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;

import static com.redhat.cloud.notifications.auth.rhid.RHIdentityAuthMechanism.IDENTITY_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Authorizes the data from the insight's RBAC-server and adds the appropriate roles
 */
@ApplicationScoped
public class RbacIdentityProvider implements IdentityProvider<RhIdentityAuthenticationRequest> {

    public static final String RBAC_READ_NOTIFICATIONS = "read:notifications";
    public static final String RBAC_WRITE_NOTIFICATIONS = "write:notifications";
    public static final String RBAC_READ_INTEGRATIONS_ENDPOINTS = "read:integrations_ep";
    public static final String RBAC_WRITE_INTEGRATIONS_ENDPOINTS = "write:integrations_ep";
    private static final Logger log = Logger.getLogger(RbacIdentityProvider.class);

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
    public Class<RhIdentityAuthenticationRequest> getRequestType() {
        return RhIdentityAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RhIdentityAuthenticationRequest rhAuthReq, AuthenticationRequestContext authenticationRequestContext) {
        if (!isRbacEnabled) {
            RhIdPrincipal principal;
            String xH = rhAuthReq.getAttribute(IDENTITY_HEADER);
            if (xH != null) {
                RhIdentity rhid = getRhIdentityFromString(xH);
                principal = new RhIdPrincipal(rhid.getIdentity().getUser().getUsername(), rhid.getIdentity().getAccountNumber());
            } else {
                principal = new RhIdPrincipal("-noauth-", "-1");
            }
            return Uni.createFrom().item(() -> QuarkusSecurityIdentity.builder()
                    .setPrincipal(principal)
                    .addRole(RBAC_READ_NOTIFICATIONS)
                    .addRole(RBAC_WRITE_NOTIFICATIONS)
                    .addRole(RBAC_READ_INTEGRATIONS_ENDPOINTS)
                    .addRole(RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
                    .build());
        }
        // Retrieve the identity header from the authentication request
        return Uni.createFrom().item(() -> (String) rhAuthReq.getAttribute(IDENTITY_HEADER))
                .onItem().transformToUni(xRhIdHeader ->
                        // Start building a QuarkusSecurityIdentity
                        Uni.createFrom().item(QuarkusSecurityIdentity.builder())
                                .onItem().transform(builder -> {
                                    // Decode the header and deserialize the resulting JSON
                                    RhIdentity rhid = getRhIdentityFromString(xRhIdHeader);
                                    RhIdPrincipal principal = new RhIdPrincipal(rhid.getIdentity().getUser().getUsername(), rhid.getIdentity().getAccountNumber());
                                    return builder.setPrincipal(principal);
                                })
                                // A decoding or a deserialization failure will cause an authentication failure
                                .onFailure().transform(AuthenticationFailedException::new)
                                // Otherwise, we can call the RBAC server
                                .onItem().transformToUni(builder ->
                                        rbacServer.getRbacInfo("notifications,integrations", xRhIdHeader)
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
                                                    log.warnf("RBAC authentication call failed: %s", failure.getMessage());
                                                    throw new AuthenticationFailedException(failure.getMessage());
                                                })
                                                // Otherwise, we can finish building the QuarkusSecurityIdentity and return the result
                                                .onItem().transform(rbacRaw -> {
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
                                                })
                                )
                );
    }

    private static RhIdentity getRhIdentityFromString(String xRhIdHeader) {
        String xRhDecoded = new String(Base64.getDecoder().decode(xRhIdHeader.getBytes(UTF_8)), UTF_8);
        return Json.decodeValue(xRhDecoded, RhIdentity.class);
    }
}
