package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.auth.rbac.RbacIdentityProvider;
import com.redhat.cloud.notifications.auth.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.InternalRoleAccessResources;
import com.redhat.cloud.notifications.models.EventType;
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
    InternalRoleAccessResources internalRoleAccessResources;

    @Inject
    ApplicationResources applicationResources;

    public static String getAccountId(SecurityContext securityContext) {
        RhIdPrincipal principal = (RhIdPrincipal) securityContext.getUserPrincipal();
        return principal.getAccount();
    }

    public void hasPermissionForEventType(SecurityContext securityContext, UUID eventTypeId) {
        if (securityContext.isUserInRole(RbacIdentityProvider.RBAC_INTERNAL_UI_ADMIN)) {
            return;
        }

        EventType eventType = applicationResources.getEventType(eventTypeId);
        hasPermissionForApplication(securityContext, eventType.getApplicationId());
    }

    public void hasPermissionForApplication(SecurityContext securityContext, UUID applicationId) {
        if (securityContext.isUserInRole(RbacIdentityProvider.RBAC_INTERNAL_UI_ADMIN)) {
            return;
        }

        List<InternalRoleAccess> internalRoleAccessList = internalRoleAccessResources.getByApplication(applicationId);

        boolean hasAccess = internalRoleAccessList
                .stream()
                .anyMatch(internalRoleAccess -> securityContext.isUserInRole(internalRoleAccess.getPrivateRole()));

        if (!hasAccess) {
            throw new ForbiddenException();
        }
    }
}
