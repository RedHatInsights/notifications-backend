package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.Set;

public class Emails {

    @JsonProperty("emails")
    private final Set<Email> emails;

    Emails() {
        this.emails = new HashSet<>();
    }

    void addEmail(final Email email) {
        this.emails.add(email);
    }

    Set<Email> getEmails() {
        return emails;
    }
}
