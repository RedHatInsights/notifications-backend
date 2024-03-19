package com.redhat.cloud.notifications.models.dto.v1.endpoint.properties;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public final class SystemSubscriptionPropertiesDTO extends EndpointPropertiesDTO {
    private UUID groupId;

    @NotNull
    @JsonProperty(required = true)
    private boolean ignorePreferences;

    @NotNull
    @JsonProperty(required = true)
    private boolean onlyAdmins;

    public SystemSubscriptionPropertiesDTO() { }

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
