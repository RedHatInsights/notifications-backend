package com.redhat.cloud.notifications.connector.email.model.bop;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents the payload accepted by BOP/MBOP.
 */
public class Email {
    private final String subject;
    private final String body;
    private final Set<String> recipients;
    private final Set<String> ccList;
    private final Set<String> bccList;
    private final String bodyType = "html";

    public Email(final String subject, final String body, final Set<String> bbcList) {
        this.subject = subject;
        this.body = body;
        this.bccList = bbcList;

        // Recipients and the carbon copies will never be used for privacy
        // reasons.
        this.recipients = new HashSet<>();
        this.ccList = new HashSet<>();
    }

    public String getSubject() {
        return this.subject;
    }

    public String getBody() {
        return this.body;
    }

    public Set<String> getRecipients() {
        return this.recipients;
    }

    public Set<String> getCcList() {
        return this.ccList;
    }

    public Set<String> getBccList() {
        return this.bccList;
    }

    public String getBodyType() {
        return this.bodyType;
    }
}
