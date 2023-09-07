package com.redhat.cloud.notifications.connector.email.processors.rbac;

public class RBACConstants {
    /**
     * Property to check if the group is the platform's default one.
     */
    public static final String RBAC_GROUP_IS_PLATFORM_DEFAULT = "rbac-group-is-platform-default";
    /**
     * RBAC's specific ORG ID header which the service is expecting.
     */
    public static final String HEADER_X_RH_RBAC_ORG_ID = "x-rh-rbac-org-id";
}
