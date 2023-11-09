package com.redhat.cloud.notifications.processors.email.connector.dto;

/**
 * Represents the pair of "sender" and "default" recipient for an email. These
 * are the ones that will appear in the email the user will receive.
 * @param emailSender the "sender" that will appear in the "from" field.
 * @param defaultRecipient the recipient that will appear in the "to" field.
 */
public record EmailSenderDefaultRecipientDTO(String emailSender, String defaultRecipient) {
    public static final String RH_INSIGHTS_DEFAULT_RECIPIENT = "\"Red Hat Insights\" noreply@redhat.com";
    public static final String RH_INSIGHTS_SENDER = "\"Red Hat Insights\" noreply@redhat.com";
}
