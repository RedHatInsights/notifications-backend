package com.redhat.cloud.notifications.auth.rbac;

import com.redhat.cloud.notifications.auth.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.rhid.RhIdentity;
import com.redhat.cloud.notifications.auth.rhid.RhIdentityAuthenticationRequest;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.Json;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Base64;
import java.util.logging.Logger;

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
    private final Logger log = Logger.getLogger(this.getClass().getSimpleName());

    @Inject
    @RestClient
    RbacServer rbacServer;

    @ConfigProperty(name = "rbac.enabled", defaultValue = "true")
    Boolean isRbacEnabled;

    @Override
    public Class<RhIdentityAuthenticationRequest> getRequestType() {
        return RhIdentityAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RhIdentityAuthenticationRequest rhAuthReq, AuthenticationRequestContext authenticationRequestContext) {
        if (!isRbacEnabled) {
            return Uni.createFrom().item(() -> QuarkusSecurityIdentity.builder()
                    .setPrincipal(new RhIdPrincipal("-noauth-", "-1"))
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
                                                // An RBAC server call failure will cause an authentication failure
                                                // TODO Add retry?
                                                .onFailure().transform(failure -> {
                                                    log.warning("RBAC call failed: " + failure.getMessage());
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
