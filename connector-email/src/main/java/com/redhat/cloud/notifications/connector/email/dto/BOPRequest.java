package com.redhat.cloud.notifications.connector.email.dto;

import java.util.List;

/**
 * Request DTO for the BOP service.
 */
public class BOPRequest {
    private String subject;
    private String body;
    private String sender;
    private String orgId;
    private List<String> recipients;
    private String eventType;

    public BOPRequest() {
    }

    public BOPRequest(String subject, String body, String sender, String orgId,
                     List<String> recipients, String eventType) {
        this.subject = subject;
        this.body = body;
        this.sender = sender;
        this.orgId = orgId;
        this.recipients = recipients;
        this.eventType = eventType;
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

    public List<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
}


