package com.redhat.cloud.notifications.recipients.request;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.recipients.RecipientSettings;

import java.util.Set;
import java.util.UUID;

public class EndpointRecipientSettings extends RecipientSettings {

    private final Endpoint endpoint;

    public EndpointRecipientSettings(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public boolean isOnlyAdmins() {
        return endpoint.getProperties(SystemSubscriptionProperties.class).isOnlyAdmins();
    }

    @Override
    public boolean isIgnoreUserPreferences() {
        return endpoint.getProperties(SystemSubscriptionProperties.class).isIgnorePreferences();
    }

    @Override
    public UUID getGroupId() {
        return endpoint.getProperties(SystemSubscriptionProperties.class).getGroupId();
    }

    @Override
    public Set<String> getUsers() {
        return Set.of();
    }
}
