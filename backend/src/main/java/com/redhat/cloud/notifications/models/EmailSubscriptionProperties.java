package com.redhat.cloud.notifications.models;

import java.util.Objects;
import java.util.UUID;

public class EmailSubscriptionProperties extends EndpointProperties {

    private boolean onlyAdmins = false;
    private boolean ignorePreferences = false;
    private UUID groupId;

    public boolean getOnlyAdmins() {
        return onlyAdmins;
    }

    public void setOnlyAdmins(boolean onlyAdmins) {
        this.onlyAdmins = onlyAdmins;
    }

    public boolean getIgnorePreferences() {
        return ignorePreferences;
    }

    public void setIgnorePreferences(boolean ignorePreferences) {
        this.ignorePreferences = ignorePreferences;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof EmailSubscriptionProperties)) {
            return false;
        }

        EmailSubscriptionProperties props = (EmailSubscriptionProperties) o;

        return Objects.equals(onlyAdmins, props.onlyAdmins) && Objects.equals(ignorePreferences, props.ignorePreferences)
                && Objects.equals(groupId, props.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(onlyAdmins, ignorePreferences, groupId);
    }
}
