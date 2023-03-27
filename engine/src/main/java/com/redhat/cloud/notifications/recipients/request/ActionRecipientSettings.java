package com.redhat.cloud.notifications.recipients.request;

import com.redhat.cloud.notifications.events.EventWrapper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.ConsoleCloudEvent;
import com.redhat.cloud.notifications.recipients.RecipientSettings;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ActionRecipientSettings extends RecipientSettings {

    private final boolean adminsOnly;
    private final boolean ignorePreferences;
    private final Set<String> users;

    public ActionRecipientSettings(boolean adminsOnly, boolean ignorePreferences, Collection<String> users) {
        this.adminsOnly = adminsOnly;
        this.ignorePreferences = ignorePreferences;
        this.users = Set.copyOf(users);
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

    public static List<ActionRecipientSettings> fromEventWrapper(EventWrapper<?, ?> eventWrapper) {
        if (eventWrapper.getEvent() instanceof Action) {
            return ((Action) eventWrapper.getEvent())
                    .getRecipients()
                    .stream()
                    .map(r -> new ActionRecipientSettings(r.getOnlyAdmins(), r.getIgnoreUserPreferences(), r.getUsers()))
                    .collect(Collectors.toList());
        } else if (eventWrapper.getEvent() instanceof ConsoleCloudEvent) {
            ConsoleCloudEvent cloudEvent = (ConsoleCloudEvent) eventWrapper.getEvent();
            if (cloudEvent.getRecipients() != null) {
                return List.of(new ActionRecipientSettings(
                        cloudEvent.getRecipients().isOnlyAdmins(),
                        cloudEvent.getRecipients().isIgnoreUserPreferences(),
                        cloudEvent.getRecipients().getUsers()
                ));
            }
        }

        return Collections.emptyList();
    }
}
