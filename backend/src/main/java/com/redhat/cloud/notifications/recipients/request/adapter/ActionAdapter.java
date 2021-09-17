package com.redhat.cloud.notifications.recipients.request.adapter;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.recipients.RecipientResolverRequest;

import java.util.UUID;

public class ActionAdapter extends RecipientResolverRequest {

    private final Action action;

    public ActionAdapter(Action action) {
        this.action = action;
    }

    @Override
    public boolean isOnlyAdmins() {
        if (this.action.getRecipients() != null) {
            return this.action.getRecipients().getOnlyAdmins();
        }

        return false;
    }

    @Override
    public boolean isIgnoreUserPreferences() {
        if (this.action.getRecipients() != null) {
            return this.action.getRecipients().getIgnoreUserPreferences();
        }

        return false;
    }

    @Override
    public UUID getGroupId() {
        return null;
    }
}
