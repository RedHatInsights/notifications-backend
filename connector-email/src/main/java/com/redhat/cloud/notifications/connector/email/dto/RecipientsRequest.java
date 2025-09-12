package com.redhat.cloud.notifications.connector.email.dto;

import java.util.Set;

/**
 * Request DTO for the recipients resolver service.
 */
public class RecipientsRequest {
    private String orgId;
    private String eventType;
    private Set<String> subscribers;
    private Set<String> unsubscribers;
    private boolean subscribedByDefault;

    public RecipientsRequest() {
    }

    public RecipientsRequest(String orgId, String eventType, Set<String> subscribers,
                           Set<String> unsubscribers, boolean subscribedByDefault) {
        this.orgId = orgId;
        this.eventType = eventType;
        this.subscribers = subscribers;
        this.unsubscribers = unsubscribers;
        this.subscribedByDefault = subscribedByDefault;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Set<String> getSubscribers() {
        return subscribers;
    }

    public void setSubscribers(Set<String> subscribers) {
        this.subscribers = subscribers;
    }

    public Set<String> getUnsubscribers() {
        return unsubscribers;
    }

    public void setUnsubscribers(Set<String> unsubscribers) {
        this.unsubscribers = unsubscribers;
    }

    public boolean isSubscribedByDefault() {
        return subscribedByDefault;
    }

    public void setSubscribedByDefault(boolean subscribedByDefault) {
        this.subscribedByDefault = subscribedByDefault;
    }

    public static class Builder {
        private String orgId;
        private String eventType;
        private Set<String> subscribers;
        private Set<String> unsubscribers;
        private boolean subscribedByDefault;

        public Builder orgId(String orgId) {
            this.orgId = orgId;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder subscribers(Set<String> subscribers) {
            this.subscribers = subscribers;
            return this;
        }

        public Builder unsubscribers(Set<String> unsubscribers) {
            this.unsubscribers = unsubscribers;
            return this;
        }

        public Builder subscribedByDefault(boolean subscribedByDefault) {
            this.subscribedByDefault = subscribedByDefault;
            return this;
        }

        public RecipientsRequest build() {
            return new RecipientsRequest(orgId, eventType, subscribers, unsubscribers, subscribedByDefault);
        }
    }
}


