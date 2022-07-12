package com.redhat.cloud.notifications.routers.models.behaviorgroup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UpdateBehaviorGroupRequest {

    /**
     * Remove: Only used to make QA tests happy
     */
    @Deprecated
    public UUID bundleId;

    /**
     * If not null, changes the display name of the behavior group to this value.
     */
    public String displayName;

    /**
     * If not null, changes the list of associated endpoints.
     * This will effectively set the linked endpoints..
     */
    // Allow this list to be null to be backwards compatible with the previous UpdateBehaviorGroupRequest
    public List<UUID> endpointIds;

    /**
     * If not null, changes the list of associated event types..
     * This will effectively set the linked event types..
     */
    public Set<UUID> eventTypeIds;
}
