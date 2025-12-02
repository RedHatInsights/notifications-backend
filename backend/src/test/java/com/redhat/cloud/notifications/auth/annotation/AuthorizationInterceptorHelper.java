package com.redhat.cloud.notifications.auth.annotation;

import jakarta.ws.rs.core.SecurityContext;

import java.util.UUID;

import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.NOTIFICATIONS_VIEW;

/**
 * A simple helper class which defines a few methods for the {@link AuthorizationInterceptorTest}
 * class. For some reason, defining them there made the method's annotations
 * disappear when trying to use reflection to get them.
 */
public class AuthorizationInterceptorHelper {

    @Authorization(legacyRBACRole = AuthorizationInterceptorTest.LEGACY_RBAC_ROLE)
    public void testMethodWithoutSecurityContext(final String ignored, final UUID ignoredTwo) { }

    @Authorization(legacyRBACRole = AuthorizationInterceptorTest.LEGACY_RBAC_ROLE)
    public void testMethodWithRBACRole(final SecurityContext ignored, final String ignoredTwo, final UUID ignoredThree) { }

    @Authorization(
        legacyRBACRole = AuthorizationInterceptorTest.LEGACY_RBAC_ROLE,
        workspacePermissions = NOTIFICATIONS_VIEW
    )
    public void testMethodWithWorkspacePermissions(final SecurityContext ignored, final String ignoredTwo, final UUID ignoredThree) { }
}
