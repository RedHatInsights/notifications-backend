package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.auth.rhid.RhIdPrincipal;
import io.smallrye.mutiny.Uni;

import javax.ws.rs.core.SecurityContext;

public class SecurityContextUtil {

    public static Uni<String> getAccountId(SecurityContext securityContext) {
        return Uni.createFrom().item(() -> {
            RhIdPrincipal principal = (RhIdPrincipal) securityContext.getUserPrincipal();
            return principal.getAccount();
        });
    }
}
