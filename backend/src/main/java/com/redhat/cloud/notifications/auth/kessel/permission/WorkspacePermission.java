package com.redhat.cloud.notifications.auth.kessel.permission;

public enum WorkspacePermission implements KesselPermission {

    EVENTS_VIEW("notifications_events_view"),
    INTEGRATIONS_EDIT("integrations_endpoints_edit"),
    INTEGRATIONS_VIEW("integrations_endpoints_view"),
    NOTIFICATIONS_EDIT("notifications_notifications_edit"),
    NOTIFICATIONS_VIEW("notifications_notifications_view");

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
