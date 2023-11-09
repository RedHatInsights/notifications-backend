package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.email.connector.dto.EmailSenderDefaultRecipientDTO;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EmailActorsResolver {
    /**
     * Determines which sender and default recipients should be set in the
     * email from the given event.
     * @param event the event to determine the sender and the default
     *              recipients from.
     * @return the pair of sender and default recipients to be used in the
     * email.
     */
    public EmailSenderDefaultRecipientDTO getEmailSenderAndDefaultRecipient(final Event event) {
        return new EmailSenderDefaultRecipientDTO(EmailSenderDefaultRecipientDTO.RH_INSIGHTS_SENDER, EmailSenderDefaultRecipientDTO.RH_INSIGHTS_DEFAULT_RECIPIENT);
    }
}
