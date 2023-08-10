package com.redhat.cloud.notifications.processors.email.connector.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;

/**
 * Represents the data structure that the email connector is expecting.
 * @param subscribers the list of user {@link java.util.UUID}s which were
 *                    subscribed to the event.
 * @param emailBody the rendered body of the email to be sent
 * @param emailSubject the rendered subject of the email to be sent.
 */
public record EmailNotification(
        @JsonProperty("subscribers")        Collection<String> subscribers,
        @JsonProperty("recipient_settings") Collection<RecipientSettings> recipientSettings,
        @JsonProperty("email_body")         String emailBody,
        @JsonProperty("email_subject")      String emailSubject
)  { }
