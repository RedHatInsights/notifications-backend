package com.redhat.cloud.notifications.routers.internal.models;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BulkEventTypeBehaviorRequest {

    @NotNull
    public Set<UUID> eventTypeIdsToLink = Set.of();

    @NotNull
    public Set<UUID> eventTypeIdsToUnlink = Set.of();
}
