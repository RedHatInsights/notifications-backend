package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.engine.InternalEngine;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import com.redhat.cloud.notifications.connector.email.payload.PayloadDetails;
import com.redhat.cloud.notifications.qute.templates.mapping.Rhel;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.EMAIL_RECIPIENTS;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.EMAIL_SENDER;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.RECIPIENT_SETTINGS;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.RENDERED_BODY;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.RENDERED_SUBJECT;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.SUBSCRIBED_BY_DEFAULT;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.SUBSCRIBERS;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.UNSUBSCRIBERS;
import static org.apache.camel.test.junit5.TestSupport.createExchangeWithBody;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class EmailCloudEventDataExtractorTest extends CamelQuarkusTestSupport {
    @Inject
    EmailCloudEventDataExtractor emailCloudEventDataExtractor;

    @InjectMock
    @RestClient
    InternalEngine internalEngine;

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
            null
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

        final Set<String> subscribers = Set.of("a", "b", "c");
        final Set<String> unsubscribers = Set.of("d", "e", "f");
        final String emailBody = "fake email body";
        final String emailSubject = "fake email subject";

        final String emailSender = "\"Red Hat Insights\" noreply@redhat.com";

        // Prepare the JSON object.
        final JsonObject payload = new JsonObject();
        payload.put("recipient_settings", recipientSettingsList);
        payload.put("subscribers", subscribers);
        payload.put("unsubscribers", unsubscribers);
        payload.put("email_body", emailBody);
        payload.put("email_subject", emailSubject);
        payload.put("email_sender", emailSender);
        payload.put("subscribed_by_default", true);
        payload.put("event_data", generateDefaultPatchEventData());

        final Exchange exchange = createExchangeWithBody(context, "");

        // Call the extractor under test. In order for the JSON object not to
        // contain the Java data structures, we encode it to a string and wrap
        // it back to a new JSON Object.
        this.emailCloudEventDataExtractor.extract(exchange, new JsonObject(payload.encode()));

        // Assert that the extracted data is correct.
        assertTrue(exchange.getProperty(RENDERED_BODY, String.class).startsWith("<!DOCTYPE html PUBLIC"));
        assertTrue(exchange.getProperty(RENDERED_SUBJECT, String.class).startsWith("Instant notification"));
        assertIterableEquals(recipientSettingsList, exchange.getProperty(RECIPIENT_SETTINGS, List.class));
        assertEquals(subscribers, exchange.getProperty(SUBSCRIBERS, Set.class));
        assertEquals(unsubscribers, exchange.getProperty(UNSUBSCRIBERS, Set.class));
        assertEquals(Set.of("foo@bar.com", "bar@foo.com", "john@doe.com"), exchange.getProperty(EMAIL_RECIPIENTS, Set.class));
        assertEquals(emailSender, exchange.getProperty(EMAIL_SENDER, String.class));
        assertTrue(exchange.getProperty(SUBSCRIBED_BY_DEFAULT, boolean.class));
    }

    /**
     * Tests that when a payload identifier is present in the Cloud Event's
     * payload, the engine gets called to fetch the payload. It also tests that
     * the exchange property containing the payload's identifier gets also set.
     */
    @Test
    void testPayloadFetchedFromEngine() {

        String payloadId = "123";
        String emailSubject = "fake email subject from engine";
        String emailBody = "fake email body from engine";

        JsonObject mockedPayload = JsonObject.of(
            "email_subject", emailSubject,
            "email_body", emailBody,
            "recipient_settings", new ArrayList<>(),
            "event_data", generateDefaultPatchEventData()
        );
        when(internalEngine.getPayloadDetails(payloadId)).thenReturn(new PayloadDetails(mockedPayload.encode()));

        Exchange exchange = createExchangeWithBody(context, "");

        JsonObject cloudEventData = new JsonObject();
        cloudEventData.put(PayloadDetails.PAYLOAD_DETAILS_ID_KEY, "123");

        emailCloudEventDataExtractor.extract(exchange, cloudEventData);

        verify(internalEngine, times(1)).getPayloadDetails(payloadId);

        assertEquals(payloadId, exchange.getProperty(ExchangeProperty.PAYLOAD_ID, String.class));
        assertTrue(exchange.getProperty(RENDERED_BODY, String.class).startsWith("<!DOCTYPE html PUBLIC"));
        assertTrue(exchange.getProperty(RENDERED_SUBJECT, String.class).startsWith("Instant notification"));
    }

    public static Map<String, Object> generateDefaultPatchEventData() {
        Map<String, Object> source = new HashMap<>();
        source.put("event_type", Map.of("display_name", Rhel.PATCH_NEW_ADVISORY));
        source.put("application", Map.of("display_name", Rhel.PATCH_APP_NAME));
        source.put("bundle", Map.of("display_name", Rhel.BUNDLE_NAME));

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("bundle", Rhel.BUNDLE_NAME);
        eventData.put("application", Rhel.PATCH_APP_NAME);
        eventData.put("event_type", Rhel.PATCH_NEW_ADVISORY);
        eventData.put("events", new ArrayList<>());
        eventData.put("environment", Map.of("url", new ArrayList<>()));
        eventData.put("orgId", TestConstants.DEFAULT_ORG_ID);
        eventData.put("source", source);
        return eventData;
    }
}
