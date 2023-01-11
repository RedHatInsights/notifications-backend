package com.redhat.cloud.notifications.models.event;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.ingress.Recipient;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The purpose of this class is to help creating test events for testing the
 * integrations that the clients have set up. The used constants are also
 * exposed for any required checks or tests that might need them. For more
 * information, take a look at <a href="https://issues.redhat.com/browse/RHCLOUD-22470">RHCLOUD-22470</a>.
 */
public class TestEventHelper {
    /**
     * Regular action test data.
     */
    public static final String TEST_ACTION_CONTEXT_TEST_EVENT = "test-action-context-test-event";
    public static final boolean TEST_ACTION_CONTEXT_TEST_EVENT_VALUE = true;
    public static final String TEST_ACTION_CONTEXT_ENDPOINT_ID = "test-action-context-endpoint-id";
    public static final String TEST_ACTION_METADATA_KEY = "test-metadata-key";
    public static final String TEST_ACTION_METADATA_VALUE = "test-metadata-value";
    public static final String TEST_ACTION_PAYLOAD_KEY = "test-payload-key";
    public static final String TEST_ACTION_PAYLOAD_VALUE = "test-payload-value";
    public static final String TEST_ACTION_RECIPIENT = "test-recipient-1";
    public static final String TEST_ACTION_VERSION = "0.0.0";
    /**
     * The test action or event will be sent with a defined bundle, application and event types, that have been
     * specifically inserted via a migration into the database. Please check the migration "V1.70.0" to see more
     * details about this.
     */
    public static final String TEST_ACTION_BUNDLE = "test-actions-events-bundle";
    public static final String TEST_ACTION_APPLICATION = "test-actions-events-application";
    public static final String TEST_ACTION_EVENT_TYPE = "test-event-type";

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
        context.setAdditionalProperty(TEST_ACTION_CONTEXT_TEST_EVENT, TEST_ACTION_CONTEXT_TEST_EVENT_VALUE);
        context.setAdditionalProperty(TEST_ACTION_CONTEXT_ENDPOINT_ID, endpointUuid);
        testAction.setContext(context);

        /*
         * Create a test event which will be sent along with the action.
         */
        Event testEvent = new Event();

        Metadata metadata = new Metadata();
        metadata.setAdditionalProperty(TEST_ACTION_METADATA_KEY, TEST_ACTION_METADATA_VALUE);

        Payload payload = new Payload();
        payload.setAdditionalProperty(TEST_ACTION_PAYLOAD_KEY, TEST_ACTION_PAYLOAD_VALUE);

        testEvent.setMetadata(metadata);
        testEvent.setPayload(payload);

        Recipient recipient = new Recipient();
        recipient.setUsers(List.of(TEST_ACTION_RECIPIENT));

        /*
         * Set the rest of the action's members.
         */
        testAction.setApplication(TEST_ACTION_APPLICATION);
        testAction.setBundle(TEST_ACTION_BUNDLE);
        testAction.setEvents(List.of(testEvent));
        testAction.setEventType(TEST_ACTION_EVENT_TYPE);
        testAction.setId(UUID.randomUUID());
        testAction.setRecipients(List.of(recipient));
        testAction.setOrgId(orgId);
        testAction.setTimestamp(LocalDateTime.now(Clock.systemUTC()));
        testAction.setVersion(TEST_ACTION_VERSION);

        return testAction;
    }
}
