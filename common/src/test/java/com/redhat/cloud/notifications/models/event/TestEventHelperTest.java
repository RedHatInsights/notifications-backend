package com.redhat.cloud.notifications.models.event;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
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
        Assertions.assertEquals(TestEventHelper.TEST_ACTION_VERSION, testAction.getVersion(), "unexpected version in the test action");
        Assertions.assertEquals(TestEventHelper.TEST_ACTION_BUNDLE, testAction.getBundle(), "unexpected bundle in the test action");
        Assertions.assertEquals(TestEventHelper.TEST_ACTION_APPLICATION, testAction.getApplication(), "unexpected application in the test action");
        Assertions.assertEquals(TestEventHelper.TEST_ACTION_EVENT_TYPE, testAction.getEventType(), "unexpected event type in the test action");
        Assertions.assertEquals(orgId, testAction.getOrgId(), "unexpected org id in the test action");

        final Context context = testAction.getContext();
        Map<String, Object> contextProperties = context.getAdditionalProperties();
        Assertions.assertTrue((boolean) contextProperties.get(TestEventHelper.TEST_ACTION_CONTEXT_TEST_EVENT), "unexpected test action flag value received in the action's context");
        Assertions.assertEquals(endpointUuid, contextProperties.get(TestEventHelper.TEST_ACTION_CONTEXT_ENDPOINT_ID), "unexpected endpoint ID received in the action's context");

        // Check the recipients and its users.
        final var expectedRecipientsCount = 1;
        final var recipients = testAction.getRecipients();

        Assertions.assertEquals(expectedRecipientsCount, recipients.size(), "unexpected number of recipients in the test action");

        final var recipient = recipients.get(0);
        final var users = recipient.getUsers();

        Assertions.assertEquals(expectedRecipientsCount, users.size(), "unexpected number of test action users");

        final var user = users.get(0);

        Assertions.assertEquals(TestEventHelper.TEST_ACTION_RECIPIENT, user, "unexpected user in the test action");

        // Check the events, their metadata and their payload.
        final var events = testAction.getEvents();

        final var expectedEventsCount = 1;
        Assertions.assertEquals(expectedEventsCount, events.size(), "unexpected number of test action events");

        final Event event = events.get(0);
        final Metadata metadata = event.getMetadata();
        final Map<String, Object> metaAdditionalProperties = metadata.getAdditionalProperties();

        final var expectedMetadataAdditionalPropertiesCount = 1;
        Assertions.assertEquals(expectedMetadataAdditionalPropertiesCount, metaAdditionalProperties.size(), "unexpected number of metadata additional properties");

        final String metadataValue = (String) metaAdditionalProperties.get(TestEventHelper.TEST_ACTION_METADATA_KEY);
        Assertions.assertEquals(TestEventHelper.TEST_ACTION_METADATA_VALUE, metadataValue, "unexpected event metadata value");

        final Payload payload = event.getPayload();
        final Map<String, Object> payloadAdditionalProperties = payload.getAdditionalProperties();

        final var expectedPayloadAdditionalPropertiesCount = 1;
        Assertions.assertEquals(expectedPayloadAdditionalPropertiesCount, payloadAdditionalProperties.size(), "unexpected number of payload additional properties");

        final String payloadValue = (String) payload.getAdditionalProperties().get(TestEventHelper.TEST_ACTION_PAYLOAD_KEY);

        Assertions.assertEquals(TestEventHelper.TEST_ACTION_PAYLOAD_VALUE, payloadValue, "unexpected event payload value");
    }
}
