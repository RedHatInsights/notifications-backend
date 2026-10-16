package com.redhat.cloud.notifications.recipients.request;

import com.redhat.cloud.event.core.v1.Recipients;
import com.redhat.cloud.notifications.events.EventWrapper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.NotificationsConsoleCloudEvent;
import com.redhat.cloud.notifications.recipients.RecipientSettings;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    public static List<ActionRecipientSettings> fromEventWrapper(EventWrapper<?, ?> eventWrapper) {
        if (eventWrapper.getEvent() instanceof Action) {
            return ((Action) eventWrapper.getEvent())
                    .getRecipients()
                    .stream()
                    .map(r -> new ActionRecipientSettings(r.getOnlyAdmins(), r.getIgnoreUserPreferences(), r.getUsers(), r.getEmails()))
                    .collect(Collectors.toList());
        } else if (eventWrapper.getEvent() instanceof NotificationsConsoleCloudEvent cloudEvent) {
            Optional<Recipients> recipients = cloudEvent.getRecipients();
            if (recipients.isPresent()) {
                return List.of(new ActionRecipientSettings(
                        recipients.get().getOnlyAdmins(),
                        recipients.get().getIgnoreUserPreferences(),
                        Arrays.asList(recipients.get().getUsers()),
                        Arrays.asList(recipients.get().getEmails())
                ));
            }
        }

        return Collections.emptyList();
    }
}
