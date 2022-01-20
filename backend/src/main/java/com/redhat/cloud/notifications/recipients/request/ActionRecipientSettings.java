package com.redhat.cloud.notifications.recipients.request;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.recipients.RecipientSettings;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ActionRecipientSettings extends RecipientSettings {

    private final Recipient recipient;
    private final Set<String> users;

    public ActionRecipientSettings(Recipient recipient) {
        this.recipient = recipient;
        this.users = Set.copyOf(this.recipient.getUsers());
    }

    @Override
    public boolean isOnlyAdmins() {
        return this.recipient.getOnlyAdmins();
    }

    @Override
    public boolean isIgnoreUserPreferences() {
        return this.recipient.getIgnoreUserPreferences();
    }

    @Override
    public UUID getGroupId() {
        return null;
    }

    @Override
    public Set<String> getUsers() {
        return users;
    }

    public static List<ActionRecipientSettings> fromAction(Action action) {
        return action
                .getRecipients()
                .stream()
                .map(ActionRecipientSettings::new)
                .collect(Collectors.toList());
    }
}
