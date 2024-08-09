package com.redhat.cloud.notifications.auth.kessel;

/**
 * Enumerates the resource types that are present in Kessel.
 */
public enum ResourceType {
    INTEGRATION("notifications/integration"),
    WORKSPACE("workspace");

    /**
     * The resource type's name in Kessel.
     */
    private final String kesselName;

    ResourceType(final String kesselName) {
        this.kesselName = kesselName;
    }

    /**
     * Get the resource type's name as seen in Kessel.
     * @return the resource type's name as seen in Kesel.
     */
    public String getKesselName() {
        return this.kesselName;
    }
}
