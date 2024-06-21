package com.redhat.cloud.notifications.auth.kessel;

/**
 * Defines common methods that the Kessel enumerations will hold, since our
 * internal permission representation is not the same as the
 */
public interface KesselPermission {
    /**
     * Get the permission's name as defined Kessel.
     * @return the String representation of the permission's name as defined in
     * Kessel.
     */
    String getKesselPermissionName();
}
