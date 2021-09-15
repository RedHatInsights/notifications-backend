package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;

import java.util.Objects;
import java.util.UUID;

public class RecipientResolverRequest {

    private boolean sendToAdmins;
    private boolean ignoreUserPreferences;
    private UUID groupId;

    private RecipientResolverRequest() {

    }

    public boolean isOnlyAdmins() {
        return this.sendToAdmins;
    }

    public boolean isIgnoreUserPreferences() {
        return this.ignoreUserPreferences;
    }

    public UUID getGroupId() {
        return this.groupId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof RecipientResolverRequest)) {
            return false;
        }

        RecipientResolverRequest that = (RecipientResolverRequest) o;
        return sendToAdmins == that.sendToAdmins && ignoreUserPreferences == that.ignoreUserPreferences &&
                Objects.equals(groupId, that.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sendToAdmins, ignoreUserPreferences, groupId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RecipientResolverRequest fromEmailSubscriptionEndpoint(Endpoint endpoint) {
        EmailSubscriptionProperties props = endpoint.getProperties(EmailSubscriptionProperties.class);
        return RecipientResolverRequest
                .builder()
                .ignoreUserPreferences(props.isIgnorePreferences())
                .onlyAdmins(props.isOnlyAdmins())
                .setGroupId(props.getGroupId())
                .build();
    }

    public static class Builder {

        private boolean onlyAdmins;
        private boolean ignoreUserPreferences;
        private UUID groupId;

        private Builder() {

        }

        public Builder onlyAdmins(boolean sendToAdmins) {
            this.onlyAdmins = sendToAdmins;
            return this;
        }

        public Builder ignoreUserPreferences(boolean forceSend) {
            this.ignoreUserPreferences = forceSend;
            return this;
        }

        public Builder setGroupId(UUID groupId) {
            this.groupId = groupId;
            return this;
        }

        public RecipientResolverRequest build() {
            RecipientResolverRequest request = new RecipientResolverRequest();
            request.sendToAdmins = this.onlyAdmins;
            request.ignoreUserPreferences = this.ignoreUserPreferences;
            request.groupId = this.groupId;
            return request;
        }
    }
}
