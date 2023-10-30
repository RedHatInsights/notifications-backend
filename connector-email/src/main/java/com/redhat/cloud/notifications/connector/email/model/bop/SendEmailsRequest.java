package com.redhat.cloud.notifications.connector.email.model.bop;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.HashSet;
import java.util.Set;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

@JsonAutoDetect(fieldVisibility = ANY)
public class SendEmailsRequest {

    private final Set<Email> emails = new HashSet<>();
    private final boolean skipUsersResolution = true;

    public void addEmail(Email email) {
        emails.add(email);
    }
}
