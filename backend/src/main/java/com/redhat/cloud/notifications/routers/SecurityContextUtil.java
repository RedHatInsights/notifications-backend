package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.principal.ConsoleIdentity;
import com.redhat.cloud.notifications.auth.principal.ConsolePrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.InternalRoleAccessRepository;
import com.redhat.cloud.notifications.models.InternalRoleAccess;
import io.quarkus.security.ForbiddenException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.SecurityContext;

import java.security.Principal;
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

    public static String getUsername(SecurityContext securityContext) {
        RhIdPrincipal principal = (RhIdPrincipal) securityContext.getUserPrincipal();
        return principal.getName();
    }

    public static Boolean isServiceAccountAuthentication(SecurityContext securityContext) {
        return "ServiceAccount".equals(((RhIdPrincipal) securityContext.getUserPrincipal()).getType());
    }

    /**
     * Extracts the {@link RhIdentity} object from the security context.
     * @param securityContext the security context to extract the object from.
     * @return the extracted {@link RhIdentity} object.
     */
    public static RhIdentity extractRhIdentity(final SecurityContext securityContext) {
        final Principal genericPrincipal = securityContext.getUserPrincipal();
        if (!(genericPrincipal instanceof ConsolePrincipal<?> principal)) {
            throw new IllegalStateException(String.format("unable to extract RH Identity object from principal. Expected \"Console Principal\" object type, got \"%s\"", genericPrincipal.getClass().getName()));
        }

        final ConsoleIdentity genericIdentity = principal.getIdentity();
        if (!(genericIdentity instanceof RhIdentity identity)) {
            throw new IllegalStateException(String.format("unable to extract RH Identity object from principal. Expected \"RhIdentity\" object type, got \"%s\"", genericIdentity.getClass().getName()));
        }

        return identity;
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
