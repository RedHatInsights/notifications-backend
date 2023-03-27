package com.redhat.cloud.notifications.models.event;

import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

public class TestEventHelperTest {

    /**
     * Tests that the action gets properly created with the expected values.
     */
    @Test
    public void createTestActionTest() {
        final var endpointUuid = UUID.randomUUID();
        final var orgId = UUID.randomUUID().toString();

        final Action testAction = TestEventHelper.createTestAction(endpointUuid, orgId);

        // Check that the top level values coincide.
        Assertions.assertEquals(TestEventHelper.TEST_ACTION_BUNDLE, testAction.getBundle(), "unexpected bundle in the test action");
        Assertions.assertEquals(TestEventHelper.TEST_ACTION_APPLICATION, testAction.getApplication(), "unexpected application in the test action");
        Assertions.assertEquals(TestEventHelper.TEST_ACTION_EVENT_TYPE, testAction.getEventType(), "unexpected event type in the test action");
        Assertions.assertEquals(orgId, testAction.getOrgId(), "unexpected org id in the test action");

        final Context context = testAction.getContext();
        Map<String, Object> contextProperties = context.getAdditionalProperties();
        Assertions.assertEquals(endpointUuid, contextProperties.get(TestEventHelper.TEST_ACTION_CONTEXT_ENDPOINT_ID), "unexpected endpoint ID received in the action's context");

        // Check the events, their metadata and their payload.
        final var events = testAction.getEvents();

        final var expectedEventsCount = 1;
        Assertions.assertEquals(expectedEventsCount, events.size(), "unexpected number of test action events");

        final Event event = events.get(0);

        final Payload payload = event.getPayload();
        final Map<String, Object> payloadAdditionalProperties = payload.getAdditionalProperties();

        final var expectedPayloadAdditionalPropertiesCount = 1;
        Assertions.assertEquals(expectedPayloadAdditionalPropertiesCount, payloadAdditionalProperties.size(), "unexpected number of payload additional properties");

        final String payloadValue = (String) payload.getAdditionalProperties().get(TestEventHelper.TEST_ACTION_PAYLOAD_KEY);

        Assertions.assertEquals(TestEventHelper.TEST_ACTION_PAYLOAD_VALUE, payloadValue, "unexpected event payload value");
    }

    /**
     * Tests that a test integration event is properly identified as such by
     * the function under test.
     */
    @Test
    public void testIsIntegrationEvent() {
        final Action testAction = TestEventHelper.createTestAction(UUID.randomUUID(), "random-org-id");

        final var testEvent = new com.redhat.cloud.notifications.models.Event();
        testEvent.setEventWrapper(new EventWrapperAction(testAction));

        Assertions.assertTrue(TestEventHelper.isIntegrationTestEvent(testEvent), "the test event was not identified as such");
    }

    /**
     * Tests that when the action doesn't have a bundle specified, the function
     * under test identifies the event as a normal event.
     */
    @Test
    public void testIsIntegrationEventNoBundle() {
        final Action nonTestAction = TestEventHelper.createTestAction(UUID.randomUUID(), "random-org-id");
        nonTestAction.setBundle(null);

        final var nonTestEvent = new com.redhat.cloud.notifications.models.Event();
        nonTestEvent.setEventWrapper(new EventWrapperAction(nonTestAction));

        Assertions.assertFalse(TestEventHelper.isIntegrationTestEvent(nonTestEvent), "the event should not have been identified as a test event");
    }

    /**
     * Tests that when the action doesn't have an application specified, the
     * function under test identifies the event as a normal event.
     */
    @Test
    public void testIsIntegrationEventNoApplication() {
        final Action nonTestAction = TestEventHelper.createTestAction(UUID.randomUUID(), "random-org-id");
        nonTestAction.setApplication(null);

        final var nonTestEvent = new com.redhat.cloud.notifications.models.Event();
        nonTestEvent.setEventWrapper(new EventWrapperAction(nonTestAction));

        Assertions.assertFalse(TestEventHelper.isIntegrationTestEvent(nonTestEvent), "the event should not have been identified as a test event");
    }

    /**
     * Tests that when the action doesn't have an event type specified, the
     * function under test identifies the event as a normal event.
     */
    @Test
    public void testIsIntegrationEventNoEventType() {
        final Action nonTestAction = TestEventHelper.createTestAction(UUID.randomUUID(), "random-org-id");
        nonTestAction.setEventType(null);

        final var nonTestEvent = new com.redhat.cloud.notifications.models.Event();
        nonTestEvent.setEventWrapper(new EventWrapperAction(nonTestAction));

        Assertions.assertFalse(TestEventHelper.isIntegrationTestEvent(nonTestEvent), "the event should not have been identified as a test event");
    }

    /**
     * Tests that the function under test correctly extracts the UUID from the
     * context property from a test event.
     */
    @Test
    public void testExtractEndpointUuidFromTestEvent() {
        final UUID endpointUuid = UUID.randomUUID();

        final Action testAction = TestEventHelper.createTestAction(endpointUuid, "random-org-id");

        // Convert the action to JSON and back to simulate the event going
        // through Kafka. If not, some additional properties of the context are
        // not serialized as String, and won't match all the types and the way
        // they get serialized when sent via Kafka and received via Kafka as
        // well.
        final String jsonAction = Parser.encode(testAction);
        final Action rawAction = Parser.decode(jsonAction);

        final var testEvent = new com.redhat.cloud.notifications.models.Event();
        testEvent.setEventWrapper(new EventWrapperAction(rawAction));

        final UUID extractedUuid = TestEventHelper.extractEndpointUuidFromTestEvent(testEvent);

        Assertions.assertEquals(endpointUuid, extractedUuid, "the function under test did not properly extract the expected UUID");
    }
}
