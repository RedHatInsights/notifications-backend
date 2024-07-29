package com.redhat.cloud.notifications.auth.kessel;

public enum WorkspacePermission implements KesselPermission {
    CREATE_DRAWER_INTEGRATION("notifications_integration_create_drawer"),
    CREATE_EMAIL_SUBSCRIPTION_INTEGRATION("notifications_integration_create_email_subscription"),
    EVENT_LOG_VIEW("notifications_event_log_view"),
    INTEGRATIONS_CREATE("notifications_integration_create"),
    DAILY_DIGEST_PREFERENCE_EDIT("notifications_daily_digest_preference_edit"),
    DAILY_DIGEST_PREFERENCE_VIEW("notifications_daily_digest_preference_view");

    /**
     * The permission's name in Kessel's schema.
     */
    private final String kesselPermission;

    /**
     * Builds an enum value for the workspace permissions.
     * @param kesselPermission the permission's name in Kessel.
     */
    WorkspacePermission(final String kesselPermission) {
        this.kesselPermission = kesselPermission;
    }

    /**
     * Get the permission's name as defined Kessel.
     *
     * @return the String representation of the permission's name as defined in
     * Kessel.
     */
    @Override
    public String getKesselPermissionName() {
        return this.kesselPermission;
    }
}
