package com.redhat.cloud.notifications.transformers;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BaseTransformerTest {

    private final BaseTransformer baseTransformer = new BaseTransformer();

    // Below are the fixtures defined for the tests.
    private static final String FIXTURE_ACCOUNT_ID = "account-id-test";
    private static final String FIXTURE_APPLICATION = "application-test";
    private static final String FIXTURE_BUNDLE = "bundle-test";
    private static Context FIXTURE_CONTEXT;
    private static final String FIXTURE_CONTEXT_ADDITIONAL_PROPERTY = "context-additional-property";
    private static final String FIXTURE_CONTEXT_ADDITIONAL_PROPERTY_VALUE = "context-additional-property-value";
    private static final String FIXTURE_EVENT_TYPE = "event-type-test";
    private static List<Event> FIXTURE_EVENTS;
    private static final String FIXTURE_METADATA_ADDITIONAL_PROPERTY = "event-metadata-additional-property";
    private static final String FIXTURE_METADATA_ADDITIONAL_PROPERTY_VALUE = "event-metadata-additional-property-value";
    private static final String FIXTURE_ORG_ID = "org-id-test";
    private static final String FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY = "event-payload-additional-property";
    private static final String FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_VALUE = "event-payload-additional-property-value";
    private static final LocalDateTime FIXTURE_TIMESTAMP = LocalDateTime.of(2022, 1, 1, 0, 0, 0);

    // JSON property names' definition.
    private static final String ACCOUNT_ID = "account_id";
    private static final String APPLICATION = "application";
    private static final String BUNDLE = "bundle";
    private static final String CONTEXT = "context";
    private static final String EVENT_TYPE = "event_type";
    private static final String EVENTS = "events";
    private static final String METADATA = "metadata";
    private static final String ORG_ID = "org_id";
    private static final String PAYLOAD = "payload";
    private static final String TIMESTAMP = "timestamp";

    /**
     * Sets up the context and a list with one event for the tests.
     */
    @BeforeAll
    static void setUp() {
        final Context context = new Context();

        context.setAdditionalProperty(FIXTURE_CONTEXT_ADDITIONAL_PROPERTY, FIXTURE_CONTEXT_ADDITIONAL_PROPERTY_VALUE);

        FIXTURE_CONTEXT = context;

        final List<Event> events = new ArrayList<>();
        final Event event = new Event();

        final Metadata metadata = new Metadata();
        metadata.setAdditionalProperty(FIXTURE_METADATA_ADDITIONAL_PROPERTY, FIXTURE_METADATA_ADDITIONAL_PROPERTY_VALUE);
        event.setMetadata(metadata);

        final Payload payload = new Payload();
        payload.setAdditionalProperty(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY, FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_VALUE);
        event.setPayload(payload);

        events.add(event);
        FIXTURE_EVENTS = events;
    }

    /**
     * Tests that a proper JSON payload is generated from a fully populated {@link Action}.
     */
    @Test
    void toJsonObjectTest() {
        Action action = new Action();

        action.setAccountId(FIXTURE_ACCOUNT_ID);
        action.setApplication(FIXTURE_APPLICATION);
        action.setBundle(FIXTURE_BUNDLE);
        action.setContext(FIXTURE_CONTEXT);
        action.setEventType(FIXTURE_EVENT_TYPE);
        action.setEvents(FIXTURE_EVENTS);
        action.setOrgId(FIXTURE_ORG_ID);
        action.setTimestamp(FIXTURE_TIMESTAMP);

        // Call the function under test.
        JsonObject result = this.baseTransformer.toJsonObject(action);

        // Assert that all the properties from the Action are present in the JSON object.
        assertTrue(result.containsKey(ACCOUNT_ID), "the account id is missing from the resulting JSON object");
        assertTrue(result.containsKey(APPLICATION), "the application is missing from the resulting JSON object");
        assertTrue(result.containsKey(BUNDLE), "the bundle is missing from the resulting JSON object");
        assertTrue(result.containsKey(CONTEXT), "the context is missing from the resulting JSON object");
        assertTrue(result.containsKey(EVENT_TYPE), "the event type is missing from the resulting JSON object");
        assertTrue(result.containsKey(EVENTS), "the events is missing from the resulting JSON object");
        assertTrue(result.containsKey(ORG_ID), "the org id is missing from the resulting JSON object");
        assertTrue(result.containsKey(TIMESTAMP), "the timestamp is missing from the resulting JSON object");

        // Assert the values.
        assertEquals(FIXTURE_ACCOUNT_ID, result.getString(ACCOUNT_ID), "the account id isn't the same");
        assertEquals(FIXTURE_APPLICATION, result.getString(APPLICATION), "the application isn't the same");
        assertEquals(FIXTURE_BUNDLE, result.getString(BUNDLE), "the bundle isn't the same");
        assertEquals(FIXTURE_EVENT_TYPE, result.getString(EVENT_TYPE), "the event type isn't the same");

        final JsonArray events = result.getJsonArray(EVENTS);
        if (events.size() != FIXTURE_EVENTS.size()) {
            fail("the number of events in the JSON file is different from the test data");
        }

        final JsonObject event = events.getJsonObject(0);
        final JsonObject metadata = event.getJsonObject(METADATA);
        assertTrue(metadata.containsKey(FIXTURE_METADATA_ADDITIONAL_PROPERTY), "the metadata object doesn't contain the additional property's key");
        assertEquals(FIXTURE_METADATA_ADDITIONAL_PROPERTY_VALUE, metadata.getString(FIXTURE_METADATA_ADDITIONAL_PROPERTY), "the metadata's additional property's value isn't the same");

        final JsonObject payload = event.getJsonObject(PAYLOAD);
        assertTrue(payload.containsKey(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY));
        assertEquals(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY_VALUE, payload.getString(FIXTURE_PAYLOAD_ADDITIONAL_PROPERTY), "the payload's additional property's value isn't the same");

        assertEquals(FIXTURE_ORG_ID, result.getString(ORG_ID), "the org id isn't the same");
        assertEquals(FIXTURE_TIMESTAMP, LocalDateTime.parse(result.getString(TIMESTAMP)), "the timestamp isn't the same");
    }
}
