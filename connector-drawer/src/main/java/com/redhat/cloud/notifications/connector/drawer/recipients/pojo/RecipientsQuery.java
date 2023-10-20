package com.redhat.cloud.notifications.connector.drawer.recipients.pojo;

import com.redhat.cloud.notifications.connector.drawer.model.RecipientSettings;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.Set;

public class RecipientsQuery {
    @NotNull
    String orgId;
    @NotNull
    Set<RecipientSettings> recipientSettings;
    Set<String> subscribers;
    boolean isOptIn;

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public void setRecipientSettings(Set<RecipientSettings> recipientSettings) {
        this.recipientSettings = recipientSettings;
    }

    public void setSubscribers(Set<String> subscribers) {
        this.subscribers = subscribers;
    }

    public void setOptIn(boolean optIn) {
        isOptIn = optIn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RecipientsQuery that = (RecipientsQuery) o;
        return isOptIn == that.isOptIn && orgId.equals(that.orgId) && recipientSettings.equals(that.recipientSettings) && Objects.equals(subscribers, that.subscribers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, recipientSettings, subscribers, isOptIn);
    }
}
