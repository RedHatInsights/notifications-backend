package com.redhat.cloud.notifications.models.event;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.ingress.Recipient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class TestEventHelper {

    /**
     * Creates a test action ready to be sent to the engine. It sets the endpoint's UUID and a flag in the context, so
     * that it can be easily identified.
     * @param endpointUuid the endpoint UUID that will be set in the context.
     * @param orgId the org ID for the action.
     * @return the created action.
     */
    public static Action createTestAction(final UUID endpointUuid, final String orgId) {
        Action testAction = new Action();

        final var context = new com.redhat.cloud.notifications.ingress.Context();
        context.setAdditionalProperty(TestEventConstants.TEST_ACTION_CONTEXT_TEST_EVENT, TestEventConstants.TEST_ACTION_CONTEXT_TEST_EVENT_VALUE);
        context.setAdditionalProperty(TestEventConstants.TEST_ACTION_CONTEXT_ENDPOINT_ID, endpointUuid);
        testAction.setContext(context);

        /*
         * Create a test event which will be sent along with the action.
         */
        Event testEvent = new Event();

        Metadata metadata = new Metadata();
        metadata.setAdditionalProperty(TestEventConstants.TEST_ACTION_METADATA_KEY, TestEventConstants.TEST_ACTION_METADATA_VALUE);

        Payload payload = new Payload();
        payload.setAdditionalProperty(TestEventConstants.TEST_ACTION_PAYLOAD_KEY, TestEventConstants.TEST_ACTION_PAYLOAD_VALUE);

        testEvent.setMetadata(metadata);
        testEvent.setPayload(payload);

        Recipient recipient = new Recipient();
        recipient.setUsers(List.of(TestEventConstants.TEST_ACTION_RECIPIENT));

        /*
         * Set the rest of the action's members.
         */
        testAction.setApplication(TestEventConstants.TEST_ACTION_APPLICATION);
        testAction.setBundle(TestEventConstants.TEST_ACTION_BUNDLE);
        testAction.setEvents(List.of(testEvent));
        testAction.setEventType(TestEventConstants.TEST_ACTION_EVENT_TYPE);
        testAction.setId(UUID.randomUUID());
        testAction.setRecipients(List.of(recipient));
        testAction.setOrgId(orgId);
        testAction.setTimestamp(LocalDateTime.now());
        testAction.setVersion(TestEventConstants.TEST_ACTION_VERSION);

        return testAction;
    }
}
