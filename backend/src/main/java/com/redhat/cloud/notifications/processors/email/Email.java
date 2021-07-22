package com.redhat.cloud.notifications.processors.email;

import java.util.HashSet;
import java.util.Set;

/**
 * Input JSON format accepted by the BOP
 */
public class Email {
    private String subject;
    private String body;
    private Set<String> recipients;
    private Set<String> ccList;
    private Set<String> bccList;
    private String bodyType;

    public Email() {
        recipients = new HashSet<>();
        ccList = new HashSet<>();
        bccList = new HashSet<>();
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

    public Set<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(Set<String> recipients) {
        this.recipients = recipients;
    }

    public Set<String> getCcList() {
        return ccList;
    }

    public void setCcList(Set<String> ccList) {
        this.ccList = ccList;
    }

    public Set<String> getBccList() {
        return bccList;
    }

    public void setBccList(Set<String> bccList) {
        this.bccList = bccList;
    }

    public String getBodyType() {
        return bodyType;
    }

    public void setBodyType(String bodyType) {
        this.bodyType = bodyType;
    }
}
