package com.redhat.cloud.notifications.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "email_properties")
public class SystemSubscriptionProperties extends EndpointProperties {

    private boolean onlyAdmins;

    private boolean ignorePreferences;

    private UUID groupId;

    public boolean isOnlyAdmins() {
        return onlyAdmins;
    }

    public void setOnlyAdmins(boolean onlyAdmins) {
        this.onlyAdmins = onlyAdmins;
    }

    public boolean isIgnorePreferences() {
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

    public boolean hasSameProperties(SystemSubscriptionProperties otherProps) {
        if (otherProps == null) {
            return false;
        }

        if (otherProps == this) {
            return true;
        }

        return onlyAdmins == otherProps.onlyAdmins && ignorePreferences == otherProps.ignorePreferences
                && Objects.equals(groupId, otherProps.groupId);
    }
}
