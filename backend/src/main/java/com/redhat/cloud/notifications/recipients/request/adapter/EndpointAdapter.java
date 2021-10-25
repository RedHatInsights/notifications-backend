package com.redhat.cloud.notifications.recipients.request;

import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.recipients.RecipientSettings;

import java.util.UUID;

public class EndpointRecipientSettings extends RecipientSettings {

    private final Endpoint endpoint;

    public EndpointAdapter(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public boolean isOnlyAdmins() {
        return endpoint.getProperties(EmailSubscriptionProperties.class).isOnlyAdmins();
    }

    @Override
    public boolean isIgnoreUserPreferences() {
        return endpoint.getProperties(EmailSubscriptionProperties.class).isIgnorePreferences();
    }

    @Override
    public UUID getGroupId() {
        return endpoint.getProperties(EmailSubscriptionProperties.class).getGroupId();
    }
}
