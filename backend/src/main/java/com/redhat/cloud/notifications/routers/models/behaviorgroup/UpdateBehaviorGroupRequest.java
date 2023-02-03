package com.redhat.cloud.notifications.routers.models.behaviorgroup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.constraints.AssertFalse;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UpdateBehaviorGroupRequest {

    /**
     * If not null, changes the display name of the behavior group to this value.
     */
    @Size(max = 150, message = "the display name cannot exceed {max} characters")
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

    /**
     * Validates that the display name is not blank when it is provided. Since
     * this is a potential payload for the "update" operation, clients might
     * not even send the display name if they don't want to update it. That is
     * why we let "null" display names go through.
     * @return true if the display name is not null, but it is blank.
     */
    @AssertFalse(message = "the display name cannot be empty")
    private boolean isDisplayNameNotNullAndBlank() {
        return this.displayName != null && this.displayName.isBlank();
    }
}
