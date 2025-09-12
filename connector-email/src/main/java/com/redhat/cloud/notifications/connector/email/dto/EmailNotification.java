package com.redhat.cloud.notifications.connector.email.dto;

import java.util.Set;

/**
 * DTO for email notification data.
 */
public class EmailNotification {

    private String subject;
    private String body;
    private String sender;
    private String orgId;
    private Set<String> recipients;
    private Set<String> subscribers;
    private Set<String> unsubscribers;
    private boolean subscribedByDefault;
    private String eventType;

    public EmailNotification() {
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public Set<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(Set<String> recipients) {
        this.recipients = recipients;
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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
}


