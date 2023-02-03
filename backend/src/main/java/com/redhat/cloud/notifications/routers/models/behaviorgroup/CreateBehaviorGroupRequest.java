package com.redhat.cloud.notifications.routers.models.behaviorgroup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateBehaviorGroupRequest {
    /**
     * BundleId the behavior group will be created for.
     */
    public UUID bundleId;

    /**
     * The name of the bundle.
     */
    public String bundleName;

    /**
     * Display name the behavior group will have.
     */
    @NotBlank
    @Size(max = 150, message = "the display name cannot exceed {max} characters")
    public String displayName;

    /**
     * If not null, the behavior group will have the relations to the endpoints (i.e. actions within a behavior group).
     */
    // Allow this list to be null to be backwards compatible with the previous CreateBehaviorGroupRequest
    public List<UUID> endpointIds;

    /**
     * If not null, the behavior group will have the relations to the event types (i.e. the event type is linked with
     * the behavior group).
     */
    public Set<UUID> eventTypeIds;

    /**
     * Validates that the bundle is identifiable.
     * @return true if the bundle UUID is not null, or if the bundle name is
     * not blank.
     */
    @AssertTrue(message = "either the bundle name or the bundle UUID are required")
    private boolean isBundleUuidOrBundleNameValid() {
        return this.bundleId != null || (
            this.bundleName != null && !this.bundleName.isBlank()
            );
    }
}
