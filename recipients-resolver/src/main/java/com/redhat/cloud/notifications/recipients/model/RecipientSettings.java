package com.redhat.cloud.notifications.recipients.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecipientSettings {

    private boolean adminsOnly;
    private boolean ignoreUserPreferences;
    private UUID groupUUID;
    private Set<String> users;

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
     */
    public RecipientSettings(final boolean adminsOnly, final boolean ignoreUserPreferences, final UUID groupUUID, final Set<String> usernames) {
        this.adminsOnly = adminsOnly;
        this.ignoreUserPreferences = ignoreUserPreferences;
        this.groupUUID = groupUUID;
        this.users = usernames;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RecipientSettings that = (RecipientSettings) o;
        return adminsOnly == that.adminsOnly && ignoreUserPreferences == that.ignoreUserPreferences && Objects.equals(groupUUID, that.groupUUID) && Objects.equals(users, that.users);
    }

    @Override
    public int hashCode() {
        return Objects.hash(adminsOnly, ignoreUserPreferences, groupUUID, users);
    }
}
