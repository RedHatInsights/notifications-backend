package com.redhat.cloud.notifications.auth.kessel;

import org.project_kessel.api.inventory.v1beta1.authz.ObjectType;

/**
 * Enumerates the resource types that are present in Kessel.
 */
public enum ResourceType {
    INTEGRATION(buildObjectType("notifications", "integration")),
    WORKSPACE(buildObjectType("rbac", "workspace"));

    /**
     * The resource's object type for the Kessel calls.
     */
    private final ObjectType kesselObjectType;

    ResourceType(final ObjectType kesselObjectType) {
        this.kesselObjectType = kesselObjectType;
    }

    /**
     * Builds the object type as defined in the Kessel's schema. Useful to be
     * able to directly use it in the gRPC calls.
     * @param namespace the namespace of the resource.
     * @param resourceName the resource type's name.
     * @return an {@link ObjectType} object ready to be used in a gRPC call.
     */
    private static ObjectType buildObjectType(final String namespace, final String resourceName) {
        return ObjectType
            .newBuilder()
            .setNamespace(namespace)
            .setName(resourceName)
            .build();
    }

    /**
     * Get the resource type's name as seen in Kessel.
     * @return the resource type's name as seen in Kesel.
     */
    public ObjectType getKesselObjectType() {
        return this.kesselObjectType;
    }

    /**
     * Represents the resource type as defined in the Kessel schema.
     * @return the Kessel representation of the resource type.
     */
    public String getKesselRepresentation() {
        return this.kesselObjectType.getNamespace() + "/" + this.kesselObjectType.getName();
    }
}
