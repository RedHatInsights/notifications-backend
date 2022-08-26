package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.InternalRoleAccessRepository;
import com.redhat.cloud.notifications.models.InternalRoleAccess;
import io.quarkus.security.ForbiddenException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SecurityContextUtil {

    @Inject
    InternalRoleAccessRepository internalRoleAccessRepository;

    @Inject
    ApplicationRepository applicationRepository;

    public static String getAccountId(SecurityContext securityContext) {
        RhIdPrincipal principal = (RhIdPrincipal) securityContext.getUserPrincipal();
        return principal.getAccount();
    }

    public static String getOrgId(SecurityContext securityContext) {
        RhIdPrincipal principal = (RhIdPrincipal) securityContext.getUserPrincipal();
        return principal.getOrgId();
    }

    public void hasPermissionForRole(SecurityContext securityContext, String role) {
        if (securityContext.isUserInRole(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)) {
            return;
        }

        if (role == null || !securityContext.isUserInRole(InternalRoleAccess.getInternalRole(role))) {
            throw new ForbiddenException("You don't have access to the role: " + role);
        }
    }

    public void hasPermissionForEventType(SecurityContext securityContext, UUID eventTypeId) {
        if (securityContext.isUserInRole(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)) {
            return;
        }

        UUID applicationId = applicationRepository.getApplicationIdOfEventType(eventTypeId);
        hasPermissionForApplication(securityContext, applicationId);
    }

    public void hasPermissionForApplication(SecurityContext securityContext, UUID applicationId) {
        if (securityContext.isUserInRole(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)) {
            return;
        }

        List<InternalRoleAccess> internalRoleAccessList = internalRoleAccessRepository.getByApplication(applicationId);

        boolean hasAccess = internalRoleAccessList
                .stream()
                .anyMatch(internalRoleAccess -> securityContext.isUserInRole(internalRoleAccess.getInternalRole()));

        if (!hasAccess) {
            throw new ForbiddenException();
        }
    }
}
