package com.redhat.cloud.notifications.recipients.request.adapter;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.recipients.RecipientResolverRequest;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ActionRecipientAdapter extends RecipientResolverRequest {

    private final Recipient recipient;

    public ActionRecipientAdapter(Recipient recipient) {
        this.recipient = recipient;
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

    public static List<ActionRecipientAdapter> fromAction(Action action) {
        return action
                .getRecipients()
                .stream()
                .map(ActionRecipientAdapter::new)
                .collect(Collectors.toList());
    }
}
