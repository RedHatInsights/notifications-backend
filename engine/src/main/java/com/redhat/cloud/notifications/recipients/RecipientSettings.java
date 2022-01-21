package com.redhat.cloud.notifications.recipients;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public abstract class RecipientSettings {

    public abstract boolean isOnlyAdmins();

    public abstract boolean isIgnoreUserPreferences();

    public abstract UUID getGroupId();

    public abstract Set<String> getUsers();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof RecipientSettings)) {
            return false;
        }

        RecipientSettings that = (RecipientSettings) o;
        return this.isOnlyAdmins() == that.isOnlyAdmins() && this.isIgnoreUserPreferences() == that.isIgnoreUserPreferences() &&
                Objects.equals(this.getGroupId(), that.getGroupId()) && Objects.equals(this.getUsers(), that.getUsers());
    }

    @Override
    public int hashCode() {
        return Objects.hash(isOnlyAdmins(), isIgnoreUserPreferences(), getGroupId(), getUsers());
    }

}
