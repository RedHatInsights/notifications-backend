package com.redhat.cloud.notifications.auth.kessel;

/**
 * Represents the permissions an individual resource might have.
 */
public enum ResourcePermission implements KesselPermission {
    VIEW,
    WRITE,
    DELETE;

    /**
     * Get the permission's name as defined Kessel.
     *
     * @return the String representation of the permission's name as defined in
     * Kessel.
     */
    @Override
    public String getKesselPermissionName() {
        return this.name().toLowerCase();
    }
}
