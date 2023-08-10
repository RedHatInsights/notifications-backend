package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
            users
        );

        final Set<String> users2 = Set.of("d", "e", "f");
        final RecipientSettings recipientSettings2 = new RecipientSettings(
            true,
            true,
            UUID.randomUUID(),
            users2
        );

        final Set<String> users3 = Set.of("g", "h", "i");
        final RecipientSettings recipientSettings3 = new RecipientSettings(
            true,
            true,
            UUID.randomUUID(),
            users3
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
        Assertions.assertEquals(emailBody, exchange.getProperty(ExchangeProperty.RENDERED_BODY, String.class));
        Assertions.assertEquals(emailSubject, exchange.getProperty(ExchangeProperty.RENDERED_SUBJECT, String.class));
        Assertions.assertIterableEquals(recipientSettingsList, exchange.getProperty(ExchangeProperty.RECIPIENT_SETTINGS, List.class));
        Assertions.assertIterableEquals(subscribers, exchange.getProperty(ExchangeProperty.SUBSCRIBERS, List.class));
    }
}
