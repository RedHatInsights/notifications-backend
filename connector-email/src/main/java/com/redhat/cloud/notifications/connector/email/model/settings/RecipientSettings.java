package com.redhat.cloud.notifications.connector.email.model.settings;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RecipientSettings {

    private boolean adminsOnly;
    private boolean ignoreUserPreferences;
    private UUID groupUUID;
    private Set<String> users;
    private Set<String> emails;

    /**
     * Default constructor for the automatic deserialization of objects.
     */
    public RecipientSettings() { }

    /**
     * Constructor used in the tests, mainly.
     * @param adminsOnly is the email just for administrators?
     * @param ignoreUserPreferences should we ignore user preferences?
     * @param groupUUID should we notify the entire group?
     * @param usernames the set of usernames we need to notify.
     * @param emails the set of emails we need to notify.
     */
    public RecipientSettings(final boolean adminsOnly, final boolean ignoreUserPreferences, final UUID groupUUID, final Set<String> usernames, final Set<String> emails) {
        this.adminsOnly = adminsOnly;
        this.ignoreUserPreferences = ignoreUserPreferences;
        this.groupUUID = groupUUID;
        this.users = usernames;
        this.emails = emails;
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
        return Objects.equals(this.adminsOnly, that.adminsOnly) &&
            Objects.equals(this.ignoreUserPreferences, that.ignoreUserPreferences) &&
            Objects.equals(this.groupUUID, that.groupUUID) &&
            Objects.equals(this.users, that.users) &&
            Objects.equals(this.emails, that.emails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(adminsOnly, ignoreUserPreferences, groupUUID, users, emails);
    }
}
