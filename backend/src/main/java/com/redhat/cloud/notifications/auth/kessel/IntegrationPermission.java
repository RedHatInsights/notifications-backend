package com.redhat.cloud.notifications.auth.kessel;

public enum IntegrationPermission implements KesselPermission {
    DELETE("delete"),
    DISABLE("disable"),
    EDIT("edit"),
    ENABLE("enable"),
    TEST("test"),
    VIEW("view"),
    VIEW_HISTORY("view_history");

    /**
     * The permission's name in Kessel's schema.
     */
    private final String kesselPermission;

    /**
     * Builds an enum value for the integration permissions.
     * @param kesselPermission the permission's name in Kessel.
     */
    IntegrationPermission(final String kesselPermission) {
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
