package com.redhat.cloud.notifications.routers.internal.kessel;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the expected request's structure when requesting a migration for
 * the Kessel's assets.
 * @param orgId the organization ID for which the assets want to be migrated.
 */
public record KesselAssetsMigrationRequest(
    @JsonProperty("org_id") String orgId
) {
}
