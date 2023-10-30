package com.redhat.cloud.notifications.processors.email.connector.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;
import java.util.UUID;

/**
 * Represents the data structure that the email connector is expecting.
 * @param emailBody         the rendered body of the email to be sent.
 * @param emailSubject      the rendered subject of the email to be sent.
 * @param orgId             the organization ID associated with the triggered
 *                          event.
 * @param eventTypeId       the event type ID associated with the triggered event
 * @param subscriptionType  the subscription type
 * @param recipientSettings the collection of recipient settings extracted from
 *                          both the event and the related endpoints to the
 *                          event.
 */
public record EmailNotification(
    @JsonProperty("email_body")            String emailBody,
    @JsonProperty("email_subject")         String emailSubject,
    @JsonProperty("orgId")                 String orgId,
    @JsonProperty("event_type_id")         UUID eventTypeId,
    @JsonProperty("subscription_type")     String subscriptionType,
    @JsonProperty("recipient_settings")    Collection<RecipientSettings> recipientSettings
)  { }
