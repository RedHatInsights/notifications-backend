package com.redhat.cloud.notifications.models.dto.v1.endpoint.properties;

import java.util.Set;
import java.util.UUID;

public final class SystemSubscriptionPropertiesDTO extends EndpointPropertiesDTO {
    private UUID groupId;

    private Set<UUID> groupIds;

    private boolean ignorePreferences;

    private boolean onlyAdmins;

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(final UUID groupId) {
        this.groupId = groupId;
    }

    public Boolean isIgnorePreferences() {
        return ignorePreferences;
    }

    public void setIgnorePreferences(final boolean ignorePreferences) {
        this.ignorePreferences = ignorePreferences;
    }

    public Boolean isOnlyAdmins() {
        return onlyAdmins;
    }

    public void setOnlyAdmins(final boolean onlyAdmins) {
        this.onlyAdmins = onlyAdmins;
    }

    public Set<UUID> getGroupIds() {
        return groupIds;
    }

    public void setGroupIds(Set<UUID> groupIds) {
        this.groupIds = groupIds;
    }
}
