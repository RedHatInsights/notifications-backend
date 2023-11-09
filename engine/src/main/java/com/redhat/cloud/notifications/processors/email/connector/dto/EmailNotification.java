package com.redhat.cloud.notifications.processors.email.connector.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;

/**
 * Represents the data structure that the email connector is expecting.
 * @param emailBody             the rendered body of the email to be sent.
 * @param emailSubject          the rendered subject of the email to be sent.
 * @param emailSender           the sender that will appear in the email when
 *                              the user receives it.
 * @param emailDefaultRecipient the recipient that will appear by default in
 *                              the email when the user receives it.
 * @param orgId                 the organization ID associated with the
 *                              triggered event.
 * @param recipientSettings     the collection of recipient settings extracted
 *                              from both the event and the related endpoints
 *                              to the event.
 * @param subscribers           the list of usernames who subscribed to the
 *                              event type.
 * @param unsubscribers         the list of usernames who unsubscribed from the
 *                              event type.
 * @param subscribedByDefault   true if the event type is subscribed by
 *                              default.
 */
public record EmailNotification(
    @JsonProperty("email_body")                 String emailBody,
    @JsonProperty("email_subject")              String emailSubject,
    @JsonProperty("email_sender")               String emailSender,
    @JsonProperty("email_default_recipient")    String emailDefaultRecipient,
    @JsonProperty("orgId")                      String orgId,
    @JsonProperty("recipient_settings")         Collection<RecipientSettings> recipientSettings,
    @JsonProperty("subscribers")                Collection<String> subscribers,
    @JsonProperty("unsubscribers")              Collection<String> unsubscribers,
    @JsonProperty("subscribed_by_default")      boolean subscribedByDefault
)  { }
