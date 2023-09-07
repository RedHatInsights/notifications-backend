package com.redhat.cloud.notifications.processors.email.connector.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Set;
import java.util.UUID;

/**
 * Represents the data structure for the recipient settings which will be used
 * in the connector to fetch data from RBAC and filter it out.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RecipientSettings {

    private final boolean adminsOnly;
    private final boolean ignoreUserPreferences;
    private final UUID groupUUID;
    private final Set<String> users;

    public RecipientSettings(final com.redhat.cloud.notifications.recipients.RecipientSettings recipientSettings) {
        this.adminsOnly = recipientSettings.isOnlyAdmins();
        this.ignoreUserPreferences = recipientSettings.isIgnoreUserPreferences();
        this.groupUUID = recipientSettings.getGroupId();
        this.users = recipientSettings.getUsers();
    }

    public boolean isAdminsOnly() {
        return this.adminsOnly;
    }

    public boolean isIgnoreUserPreferences() {
        return this.ignoreUserPreferences;
    }

    public UUID getGroupUUID() {
        return this.groupUUID;
    }

    public Set<String> getUsers() {
        return this.users;
    }
}
