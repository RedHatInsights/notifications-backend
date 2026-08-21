package com.redhat.cloud.notifications.connector.email.model.bop;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTest {

    @Test
    void testBccListIsSetFromConstructor() {
        Set<String> bcc = Set.of("a@example.com", "b@example.com");
        Email email = new Email("Subject", "Body", bcc);
        assertEquals(bcc, email.getBccList());
    }

    @Test
    void testRecipientsAndCcAreAlwaysEmptyForPrivacy() {
        Email email = new Email("Subject", "Body", Set.of("user@example.com"));
        assertTrue(email.getRecipients().isEmpty());
        assertTrue(email.getCcList().isEmpty());
    }

    @Test
    void testBodyTypeIsAlwaysHtml() {
        Email email = new Email("Subject", "Body", Set.of());
        assertEquals("html", email.getBodyType());
    }

    @Test
    void testSubjectAndBodyAreSet() {
        Email email = new Email("My Subject", "My Body", Set.of());
        assertEquals("My Subject", email.getSubject());
        assertEquals("My Body", email.getBody());
    }
}
