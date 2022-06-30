package com.redhat.cloud.notifications.routers.models.behaviorgroup;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Response to the create behavior group request.
 * Has information about how the behavior group was created.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateBehaviorGroupResponse {
    /**
     * Id of the newly created behavior group.
     */
    @NotNull
    public UUID id;

    /**
     * Account id the behavior group belongs to.
     */
    @Deprecated
    @NotNull
    public String accountId;

    /**
     * Org id the behavior group belongs to.
     */
    public String orgId; // Should add @NotNull when have fully migrated to org id.

    /**
     * Bundle if of the newly created behavior group.
     */
    @NotNull
    public UUID bundleId;

    /**
     * Display name of the newly created behavior group.
     */
    @NotNull
    public String displayName;

    /**
     * Endpoints linked to the newly created behavior group.
     */
    @NotNull
    public List<UUID> endpoints;

    /**
     * Event types linked to the newly created behavior group.
     */
    @NotNull
    public Set<UUID> eventTypes;

    /**
     * Creation time of the behavior group
     */
    @NotNull
    public LocalDateTime created;
}
