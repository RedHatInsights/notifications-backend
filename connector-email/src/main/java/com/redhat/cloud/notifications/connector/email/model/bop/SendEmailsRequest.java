package com.redhat.cloud.notifications.connector.email.model.bop;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashSet;
import java.util.Set;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

@JsonAutoDetect(fieldVisibility = ANY)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SendEmailsRequest {

    private final Set<Email> emails = new HashSet<>();
    private final boolean skipUsersResolution = true;
    private final String emailSender;
    private final String defaultRecipient;

    public SendEmailsRequest(final Set<Email> emails, final String emailSender, final String defaultRecipient) {
        this.emails.addAll(emails);
        this.emailSender = emailSender;
        this.defaultRecipient = defaultRecipient;
    }
}
