package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.Set;

class Emails {

    @JsonProperty("emails")
    private final Set<Email> emails;

    Emails() {
        emails = new HashSet<>();
    }

    void addEmail(Email email) {
        emails.add(email);
    }

    Set<Email> getEmails() {
        return emails;
    }

}
