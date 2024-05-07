package com.redhat.cloud.notifications.models.dto.v1.endpoint.properties;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public final class SystemSubscriptionPropertiesDTO extends EndpointPropertiesDTO {
    private UUID groupId;

    @NotNull
    private boolean ignorePreferences;

    @NotNull
    private boolean onlyAdmins;

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(final UUID groupId) {
        this.groupId = groupId;
    }

    public boolean isIgnorePreferences() {
        return ignorePreferences;
    }

    public void setIgnorePreferences(final boolean ignorePreferences) {
        this.ignorePreferences = ignorePreferences;
    }

    public boolean isOnlyAdmins() {
        return onlyAdmins;
    }

    public void setOnlyAdmins(final boolean onlyAdmins) {
        this.onlyAdmins = onlyAdmins;
    }
}
