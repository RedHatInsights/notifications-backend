package com.redhat.cloud.notifications.models;

import java.util.Set;

public class EmailAttributes extends Attributes {
    private Set<String> recipients;

    public EmailAttributes() {

    }

    public Set<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(Set<String> recipients) {
        this.recipients = recipients;
    }
}
