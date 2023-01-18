package com.redhat.cloud.notifications.models.event;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.ingress.Recipient;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    public static final String TEST_ACTION_CONTEXT_ENDPOINT_ID = "integration-uuid";
    public static final String TEST_ACTION_PAYLOAD_KEY = "message";
    public static final String TEST_ACTION_PAYLOAD_VALUE = "Congratulations! The integration you created on https://console.redhat.com was successfully tested!";
    public static final String TEST_ACTION_RECIPIENT = "test-recipient-1";
    /**
     * The test action or event will be sent with a defined "console" bundle, the "notifications" application and
     * a new event type that has been inserted in the V1.71.0 migration.
     */
    public static final String TEST_ACTION_BUNDLE = "console";
    public static final String TEST_ACTION_APPLICATION = "integrations";
    public static final String TEST_ACTION_EVENT_TYPE = "integration-test";

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
        context.setAdditionalProperty(TEST_ACTION_CONTEXT_ENDPOINT_ID, endpointUuid);
        testAction.setContext(context);

        /*
         * Create a test event which will be sent along with the action.
         */
        Event testEvent = new Event();

        Payload payload = new Payload();
        payload.setAdditionalProperty(TEST_ACTION_PAYLOAD_KEY, TEST_ACTION_PAYLOAD_VALUE);

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

        return testAction;
    }

    /**
     * Checks if the provided event is part of an integration test requested by
     * the client to test their endpoint.
     * @param event the event to check.
     * @return true if the event is an integration test, false otherwise.
     */
    public static boolean isIntegrationTestEvent(final com.redhat.cloud.notifications.models.Event event) {
        return TestEventHelper.TEST_ACTION_BUNDLE.equals(event.getAction().getBundle()) &&
                TestEventHelper.TEST_ACTION_APPLICATION.equals(event.getAction().getApplication()) &&
                TestEventHelper.TEST_ACTION_EVENT_TYPE.equals(event.getAction().getEventType());
    }

    /**
     * Extracts the UUID from the given test event. Probably best used after
     * {@link #isIntegrationTestEvent(com.redhat.cloud.notifications.models.Event)}.
     * @param event the event to extract the endpoint's UUID from.
     * @return the extracted UUID.
     */
    public static UUID extractEndpointUuidFromTestEvent(final com.redhat.cloud.notifications.models.Event event) {
        final Context context = event.getAction().getContext();
        final Map<String, Object> contextProperties = context.getAdditionalProperties();

        return UUID.fromString((String) contextProperties.get(TestEventHelper.TEST_ACTION_CONTEXT_ENDPOINT_ID));
    }
}
