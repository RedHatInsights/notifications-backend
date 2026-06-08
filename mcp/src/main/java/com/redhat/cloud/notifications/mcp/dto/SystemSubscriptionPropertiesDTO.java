package com.redhat.cloud.notifications.mcp.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Set;
import java.util.UUID;

/**
 * Properties for system subscription endpoint types (drawer, email_subscription).
 *
 * Based on: backend/src/main/java/com/redhat/cloud/notifications/models/dto/v1/endpoint/properties/SystemSubscriptionPropertiesDTO.java
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@RegisterForReflection
public class SystemSubscriptionPropertiesDTO extends EndpointPropertiesDTO {

    private UUID groupId;

    private Set<UUID> groupIds;

    private boolean ignorePreferences;

    private boolean onlyAdmins;

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public Set<UUID> getGroupIds() {
        return groupIds;
    }

    public void setGroupIds(Set<UUID> groupIds) {
        this.groupIds = groupIds;
    }

    public Boolean isIgnorePreferences() {
        return ignorePreferences;
    }

    public void setIgnorePreferences(boolean ignorePreferences) {
        this.ignorePreferences = ignorePreferences;
    }

    public Boolean isOnlyAdmins() {
        return onlyAdmins;
    }

    public void setOnlyAdmins(boolean onlyAdmins) {
        this.onlyAdmins = onlyAdmins;
    }
}
