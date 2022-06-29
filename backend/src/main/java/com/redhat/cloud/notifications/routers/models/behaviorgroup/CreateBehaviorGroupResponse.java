package com.redhat.cloud.notifications.routers.models.behaviorgroup;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

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
    public UUID id;

    /**
     * Account id the behavior group belongs to.
     */
    @Deprecated
    public String accountId;

    /**
     * Org id the behavior group belongs to.
     */
    public String orgId;

    /**
     * Bundle if of the newly created behavior group.
     */
    public UUID bundleId;

    /**
     * Display name of the newly created behavior group.
     */
    public String displayName;

    /**
     * Endpoints linked to the newly created behavior group.
     */
    public List<UUID> endpoints;

    /**
     * Event types linked to the newly created behavior group.
     */
    public Set<UUID> eventTypes;

    /**
     * Creation time of the behavior group
     */
    public LocalDateTime created;
}
