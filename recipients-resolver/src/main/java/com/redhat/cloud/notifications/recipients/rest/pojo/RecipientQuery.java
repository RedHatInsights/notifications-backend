package com.redhat.cloud.notifications.recipients.rest.pojo;

import com.redhat.cloud.notifications.recipients.model.RecipientSettings;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public class RecipientQuery {
    @NotNull
    String orgId;
    @NotNull
    Set<RecipientSettings> recipientSettings;
    Set<String> subscribers;
    boolean isOptIn;

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public Set<RecipientSettings> getRecipientSettings() {
        return recipientSettings;
    }

    public void setRecipientSettings(Set<RecipientSettings> recipientSettings) {
        this.recipientSettings = recipientSettings;
    }

    public Set<String> getSubscribers() {
        return subscribers;
    }

    public void setSubscribers(Set<String> subscribers) {
        this.subscribers = subscribers;
    }

    public boolean isOptIn() {
        return isOptIn;
    }

    public void setOptIn(boolean optIn) {
        isOptIn = optIn;
    }
}
