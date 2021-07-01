package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.Set;

public class Emails {

    @JsonProperty("emails")
    private Set<Email> emails;

    public Emails() {
        emails = new HashSet<>();
    }

    public void addEmail(Email email) {
        emails.add(email);
    }

    public Set<Email> getEmails() {
        return emails;
    }

}
