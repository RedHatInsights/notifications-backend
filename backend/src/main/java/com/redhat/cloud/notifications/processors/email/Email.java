package com.redhat.cloud.notifications.processors.email;

import java.util.HashSet;
import java.util.Set;

/**
 * Input JSON format accepted by the BOP
 */
class Email {
    private String subject;
    private String body;
    private Set<String> recipients;
    private Set<String> ccList;
    private Set<String> bccList;
    private String bodyType;

    Email() {
        recipients = new HashSet<>();
        ccList = new HashSet<>();
        bccList = new HashSet<>();
    }

    String getSubject() {
        return subject;
    }

    void setSubject(String subject) {
        this.subject = subject;
    }

    String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    Set<String> getRecipients() {
        return recipients;
    }

    void setRecipients(Set<String> recipients) {
        this.recipients = recipients;
    }

    Set<String> getCcList() {
        return ccList;
    }

    void setCcList(Set<String> ccList) {
        this.ccList = ccList;
    }

    Set<String> getBccList() {
        return bccList;
    }

    void setBccList(Set<String> bccList) {
        this.bccList = bccList;
    }

    String getBodyType() {
        return bodyType;
    }

    void setBodyType(String bodyType) {
        this.bodyType = bodyType;
    }
}
