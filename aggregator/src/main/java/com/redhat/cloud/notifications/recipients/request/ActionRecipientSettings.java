package com.redhat.cloud.notifications.recipients.request;

import com.redhat.cloud.notifications.recipients.RecipientSettings;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public class ActionRecipientSettings extends RecipientSettings {

    private final boolean adminsOnly;
    private final boolean ignorePreferences;
    private final Set<String> users;
    private final Set<String> emails;

    public ActionRecipientSettings(boolean adminsOnly, boolean ignorePreferences, Collection<String> users, Collection<String> emails) {
        this.adminsOnly = adminsOnly;
        this.ignorePreferences = ignorePreferences;
        this.users = Set.copyOf(users);
        this.emails = Set.copyOf(emails);
    }

    @Override
    public boolean isOnlyAdmins() {
        return this.adminsOnly;
    }

    @Override
    public boolean isIgnoreUserPreferences() {
        return this.ignorePreferences;
    }

    @Override
    public UUID getGroupId() {
        return null;
    }

    @Override
    public Set<String> getUsers() {
        return users;
    }

    @Override
    public Set<String> getEmails() {
        return emails;
    }

}
