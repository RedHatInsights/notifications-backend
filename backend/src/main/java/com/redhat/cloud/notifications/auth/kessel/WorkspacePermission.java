package com.redhat.cloud.notifications.auth.kessel;

public enum WorkspacePermission implements KesselPermission {
    EVENTS_READ("notifications_events_read"),
    INTEGRATIONS_READ("notifications_integrations_view"),
    INTEGRATIONS_WRITE("notifications_integrations_write"),
    INTERNAL_ADMINISTRATOR("notifications_internal_administrator_access"),
    INTERNAL_USER("notifications_internal_user_access"),
    NOTIFICATIONS_READ("notifications_notifications_view"),
    NOTIFICATIONS_WRITE("notifications_notifications_write");

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
