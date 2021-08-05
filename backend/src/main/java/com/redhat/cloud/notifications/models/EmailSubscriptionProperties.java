package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "email_properties")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EmailSubscriptionProperties extends EndpointProperties {

    @NotNull
    @Schema(name = "only_admins")
    private boolean onlyAdmins;

    @NotNull
    @Schema(name = "ignore_preferences")
    private boolean ignorePreferences;

    @Schema(name = "group_id")
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

    public boolean hasSameProperties(EmailSubscriptionProperties otherProps) {
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
