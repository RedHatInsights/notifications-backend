package com.redhat.cloud.notifications.auth.annotation;

import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the annotation to be able to perform permission checks with Kessel
 * at the method level.
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Authorization {

    /**
     * The legacy RBAC role that will be checked when Kessel is disabled.
     * @return the legacy RBAC role set for the method.
     */
    @Nonbinding String legacyRBACRole();

    /**
     * The type of resource being guarded by this authorization check.
     * Used in security audit logs when an RBAC authorization denial occurs.
     * @return the resource type for audit logging (e.g. "integration", "behavior_group", "event").
     */
    @Nonbinding String resourceType() default "integration";

    /**
     * The Kessel workspace permissions defined by the developer.
     * @return an array of defined workspace permissions.
     */
    @Nonbinding WorkspacePermission[] workspacePermissions() default {};
}
