package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.EMAIL_RECIPIENTS;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.RECIPIENT_SETTINGS;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.RENDERED_BODY;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.RENDERED_SUBJECT;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.SUBSCRIBERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

@QuarkusTest
public class EmailCloudEventDataExtractorTest extends CamelQuarkusTestSupport {
    @Inject
    EmailCloudEventDataExtractor emailCloudEventDataExtractor;

    /**
     * Tests that the incoming JSON payload's extraction works as intended.
     */
    @Test
    void testExtract() {
        // Prepare the payload that we will simulate that we receive on the
        // connector.
        final Set<String> users = Set.of("a", "b", "c");
        final RecipientSettings recipientSettings = new RecipientSettings(
            true,
            true,
            UUID.randomUUID(),
            users,
            Collections.emptySet()
        );

        final Set<String> users2 = Set.of("d", "e", "f");
        final RecipientSettings recipientSettings2 = new RecipientSettings(
            true,
            true,
            UUID.randomUUID(),
            users2,
            Set.of("foo@bar.com", "bar@foo.com")
        );

        final Set<String> users3 = Set.of("g", "h", "i");
        final RecipientSettings recipientSettings3 = new RecipientSettings(
            true,
            true,
            UUID.randomUUID(),
            users3,
            Set.of("john@doe.com")
        );

        final List<RecipientSettings> recipientSettingsList = new ArrayList<>();
        recipientSettingsList.add(recipientSettings);
        recipientSettingsList.add(recipientSettings2);
        recipientSettingsList.add(recipientSettings3);

        final List<String> subscribers = List.of("a", "b", "c");
        final String emailBody = "fake email body";
        final String emailSubject = "fake email subject";

        // Prepare the JSON object.
        final JsonObject payload = new JsonObject();
        payload.put("recipient_settings", recipientSettingsList);
        payload.put("subscribers", subscribers);
        payload.put("email_body", emailBody);
        payload.put("email_subject", emailSubject);

        final Exchange exchange = this.createExchangeWithBody("");

        // Call the extractor under test. In order for the JSON object not to
        // contain the Java data structures, we encode it to a string and wrap
        // it back to a new JSON Object.
        this.emailCloudEventDataExtractor.extract(exchange, new JsonObject(payload.encode()));

        // Assert that the extracted data is correct.
        assertEquals(emailBody, exchange.getProperty(RENDERED_BODY, String.class));
        assertEquals(emailSubject, exchange.getProperty(RENDERED_SUBJECT, String.class));
        assertIterableEquals(recipientSettingsList, exchange.getProperty(RECIPIENT_SETTINGS, List.class));
        assertIterableEquals(subscribers, exchange.getProperty(SUBSCRIBERS, List.class));
        assertEquals(Set.of("foo@bar.com", "bar@foo.com", "john@doe.com"), exchange.getProperty(EMAIL_RECIPIENTS, Set.class));
    }
}
