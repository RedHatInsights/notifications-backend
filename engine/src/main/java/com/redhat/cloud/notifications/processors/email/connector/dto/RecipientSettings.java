package com.redhat.cloud.notifications.processors.email.connector.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Objects;
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
    private final Set<String> emails;

    public RecipientSettings(final boolean adminsOnly, final boolean ignoreUserPreferences, final UUID groupUUID, final Set<String> users, final Set<String> emails) {
        this.adminsOnly = adminsOnly;
        this.ignoreUserPreferences = ignoreUserPreferences;
        this.groupUUID = groupUUID;
        this.users = users;
        this.emails = emails;
    }

    public RecipientSettings(final com.redhat.cloud.notifications.recipients.RecipientSettings recipientSettings) {
        this.adminsOnly = recipientSettings.isOnlyAdmins();
        this.ignoreUserPreferences = recipientSettings.isIgnoreUserPreferences();
        this.groupUUID = recipientSettings.getGroupId();
        this.users = recipientSettings.getUsers();
        this.emails = recipientSettings.getEmails();
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

    public Set<String> getEmails() {
        return emails;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }

        final RecipientSettings that = (RecipientSettings) o;

        return this.adminsOnly == that.adminsOnly
            && this.ignoreUserPreferences == that.ignoreUserPreferences
            && Objects.equals(this.groupUUID, that.groupUUID)
            && Objects.equals(this.users, that.users)
            && Objects.equals(this.emails, that.emails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(adminsOnly, ignoreUserPreferences, groupUUID, users, emails);
    }
}
