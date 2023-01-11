package com.redhat.cloud.notifications.routers.models.behaviorgroup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.apache.commons.lang3.StringUtils;

import javax.validation.constraints.AssertFalse;
import javax.validation.constraints.NotNull;
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
    @NotNull
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
     * @return true if the bundle ID is null and the bundle name is blank. False otherwise.
     */
    @AssertFalse(message = "either the bundle name or the bundle UUID are required")
    private boolean isBundleUuidNullOrBundleNameBlank() {
        return this.bundleId == null && StringUtils.isBlank(this.bundleName);
    }
}
