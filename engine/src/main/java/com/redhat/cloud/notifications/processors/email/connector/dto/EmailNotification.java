package com.redhat.cloud.notifications.processors.email.connector.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;

/**
 * Represents the data structure that the email connector is expecting.
 * @param emailBody         the rendered body of the email to be sent.
 * @param emailSubject      the rendered subject of the email to be sent.
 * @param orgId             the organization ID associated with the triggered
 *                          event.
 * @param recipientSettings the collection of recipient settings extracted from
 *                          both the event and the related endpoints to the
 *                          event.
 * @param subscribers       the list of user {@link java.util.UUID}s which were
 *                          subscribed to the event.
 */
public record EmailNotification(
    @JsonProperty("email_body")         String emailBody,
    @JsonProperty("email_subject")      String emailSubject,
    @JsonProperty("orgId")              String orgId,
    @JsonProperty("recipient_settings") Collection<RecipientSettings> recipientSettings,
    @JsonProperty("subscribers")        Collection<String> subscribers
)  { }
