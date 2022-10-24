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

        // Assert the values.
        assertEquals(action.getAccountId(), result.getString(BaseTransformer.ACCOUNT_ID), "the account id isn't the same");
        assertEquals(action.getApplication(), result.getString(BaseTransformer.APPLICATION), "the application isn't the same");
        assertEquals(action.getBundle(), result.getString(BaseTransformer.BUNDLE), "the bundle isn't the same");
        assertEquals(action.getEventType(), result.getString(BaseTransformer.EVENT_TYPE), "the event type isn't the same");
        assertEquals(action.getOrgId(), result.getString(BaseTransformer.ORG_ID), "the org id isn't the same");
        assertEquals(action.getTimestamp(), LocalDateTime.parse(result.getString(BaseTransformer.TIMESTAMP)), "the timestamp isn't the same");

        // Assert that the expected context is present in the JSON output.
        if (!result.containsKey(BaseTransformer.CONTEXT)) {
            fail("the context is missing from the resulting JSON object");
        }

        // Assert that the expected context and the context in the JSON output are the same.
        final JsonObject context = result.getJsonObject(BaseTransformer.CONTEXT);
        for (final var contextProperty : action.getContext().getAdditionalProperties().entrySet()) {
            assertEquals(
                contextProperty.getValue(),
                context.getString(contextProperty.getKey()),
                String.format(
                    "the resulting JSON does not contain the expected key value pair for the context. Want \"%s\" and \"%s\", got value \"%s\" in the JSON file",
                    contextProperty.getKey(),
                    contextProperty.getValue(),
                    context.getString(contextProperty.getKey())
                )
            );
        }

        // Check that the events are present in the JSON object.
        if (!result.containsKey(BaseTransformer.EVENTS)) {
            fail("the events field is missing from the resulting JSON object");
        }

        // Grab the expected events and the events from the JSON output.
        final List<Event> actionEvents = action.getEvents();
        final JsonArray events = result.getJsonArray(BaseTransformer.EVENTS);

        // The size should be the same for both!
        assertEquals(actionEvents.size(), events.size(), "the number of events in the JSON file is different from the test data");

        // Loop through the expected events.
        for (int i = 0; i < actionEvents.size(); i++) {
            final Event expectedEvent = actionEvents.get(i);
            final JsonObject jsonEvent = events.getJsonObject(i);

            // Start by checking the expected metadata object, and the one from the JSON output.
            final Metadata expectedMetadata = expectedEvent.getMetadata();
            final JsonObject metadata = jsonEvent.getJsonObject(BaseTransformer.METADATA);

            for (final var property : expectedMetadata.getAdditionalProperties().entrySet()) {
                assertEquals(
                    property.getValue(),
                    metadata.getString(property.getKey()),
                    String.format(
                        "the resulting JSON does not contain the expected key value pair for the metadata. Want \"%s\" and \"%s\", got value \"%s\" in the JSON file",
                        property.getKey(),
                        property.getValue(),
                        metadata.getString(property.getKey())
                    )
                );
            }

            // After that check that both payloads are also the same!
            final Payload expectedPayload = expectedEvent.getPayload();
            final JsonObject payload = jsonEvent.getJsonObject(BaseTransformer.PAYLOAD);
            for (final var property : expectedPayload.getAdditionalProperties().entrySet()) {
                assertEquals(
                    property.getValue(),
                    payload.getString(property.getKey()),
                    String.format(
                        "the resulting JSON does not contain the expected key value pair for the payload. Want \"%s\" and \"%s\", got value \"%s\" in the JSON file",
                        property.getKey(),
                        property.getValue(),
                        payload.getString(property.getKey())
                    )
                );
            }
        }
    }
}
