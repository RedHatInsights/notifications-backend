package com.redhat.cloud.notifications.connector.email.model.bop;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents the payload to be sent to BOP/MBOP.
 */
@Deprecated(forRemoval = true)
public class Emails {

    @JsonProperty("emails")
    private final Set<Email> emails;

    public Emails() {
        this.emails = new HashSet<>();
    }

    public void addEmail(final Email email) {
        this.emails.add(email);
    }

    public Set<Email> getEmails() {
        return this.emails;
    }
}
