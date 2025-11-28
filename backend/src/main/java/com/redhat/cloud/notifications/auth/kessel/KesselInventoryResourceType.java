package com.redhat.cloud.notifications.auth.kessel;

import org.project_kessel.api.inventory.v1beta2.ReporterReference;

/**
 * Enumerates the resource types that are present in Kessel.
 */
public enum KesselInventoryResourceType {

    WORKSPACE("rbac", "workspace");

    private final ReporterReference reporter;
    private final String resourceType;

    KesselInventoryResourceType(String reporter, String resourceType) {
        this.reporter = ReporterReference.newBuilder()
            .setType(reporter)
            .build();
        this.resourceType = resourceType;
    }

    public ReporterReference getReporter() {
        return reporter;
    }

    public String getResourceType() {
        return resourceType;
    }
}
