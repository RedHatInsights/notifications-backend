package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.auth.rhid.RhIdPrincipal;

import javax.ws.rs.core.SecurityContext;

public class SecurityContextUtil {

    public static String getAccountId(SecurityContext securityContext) {
        RhIdPrincipal principal = (RhIdPrincipal) securityContext.getUserPrincipal();
        return principal.getAccount();
    }
}
