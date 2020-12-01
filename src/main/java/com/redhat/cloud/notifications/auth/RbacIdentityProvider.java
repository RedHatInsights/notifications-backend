package com.redhat.cloud.notifications.auth;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.logging.Logger;

/**
 * Authorizes the data from the insight's RBAC-server and adds the appropriate roles
 */
@ApplicationScoped
public class RbacIdentityProvider implements IdentityProvider<RhIdentityAuthenticationRequest> {

    public static final String RBAC_READ_NOTIFICATIONS = "read:notifications";
    public static final String RBAC_WRITE_NOTIFICATIONS = "write:notifications";
    public static final String RBAC_READ_INTEGRATIONS = "read:integrations";
    public static final String RBAC_WRITE_INTEGRATIONS = "write:integrations";
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
                    .addRole("read")
                    .addRole("write")
                    .build());
        }
        return rbacServer.getRbacInfo("notifications,integrations", rhAuthReq.getxRhIdentity())
                .onItem()
                .transform(raw -> {
                    QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder();
                    if (raw.canRead("notifications")) {
                        builder.addRole(RBAC_READ_NOTIFICATIONS);
                    }
                    if (raw.canWrite("notifications")) {
                        builder.addRole(RBAC_WRITE_NOTIFICATIONS);
                    }
                    if (raw.canRead("integrations")) {
                        builder.addRole(RBAC_READ_INTEGRATIONS);
                    }
                    if (raw.canWrite("integrations")) {
                        builder.addRole(RBAC_WRITE_INTEGRATIONS);
                    }

                    return (SecurityIdentity) builder.build();
                })
                .onFailure()
                .transform(raw -> {
                    log.warning("RBAC call failed: " + raw.getMessage());
                    throw new AuthenticationFailedException(raw.getMessage());
                });
    }
}
