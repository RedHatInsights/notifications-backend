package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.events.GeneralCommunicationsHelper;
import com.redhat.cloud.notifications.events.IntegrationDisabledNotifier;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.apache.http.HttpStatus;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.events.ConnectorReceiver.EGRESS_CHANNEL;
import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class GeneralCommunicationsResourceTest {
    @Any
    @Inject
    InMemoryConnector inMemoryConnector;

    @Inject
    ResourceHelpers resourceHelpers;

    @BeforeEach
    void beforeEach() {
        this.inMemoryConnector.sink(EGRESS_CHANNEL).clear();
    }

    /**
     * Tests that a "general communication" action is sent to the ingress
     * channel for the engine to process it.
     */
    @Test
    void testSendGeneralCommunication() {
        // Create some integrations for an organization.
        final String orgId = DEFAULT_ORG_ID + "test-send-gen-com";
        final List<Endpoint> createdEndpoints = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(orgId, EndpointType.CAMEL, "teams", true, 0);

            createdEndpoints.add(createdEndpoint);
        }

        this.inMemoryConnector.sink(EGRESS_CHANNEL).clear();

        // Call the endpoint under test.
        given()
            .when()
            .post("/internal/general-communications/send")
            .then()
            .statusCode(HttpStatus.SC_OK);

        // We should receive the action triggered by the REST call.
        final InMemorySink<String> actionsOut = this.inMemoryConnector.sink(EGRESS_CHANNEL);

        // Make sure that the message was received before continuing.
        Awaitility.await().until(
            () -> !actionsOut.received().isEmpty()
        );

        // Find the action that was sent on this particular test. When running
        // this test alone, everything works fine. However, when it is run
        // along with some other tests from the engine, for some reason the
        // Kafka sink does not get properly cleared out, so multiple messages
        // end up getting piled up here. Therefore, in order to avoid that,
        // since clearing the sink does not work for some reason, we need to
        // just get the action that belongs to the custom organization ID
        // we created for this test.
        final List<? extends Message<String>> actionList = actionsOut.received();

        String kafkaActionRaw = null;
        for (final Message<String> action : actionList) {
            final String payload = action.getPayload();

            if (payload.contains(orgId)) {
                kafkaActionRaw = payload;
                break;
            }
        }

        if (kafkaActionRaw == null) {
            Assertions.fail(String.format("Unable to find the sent action for organization id \"%s\"", orgId));
        }

        // Decode the action.
        final Action kafkaAction = Parser.decode(kafkaActionRaw);

        // Check that the top level values coincide.
        Assertions.assertEquals(GeneralCommunicationsHelper.GENERAL_COMMUNICATIONS_BUNDLE, kafkaAction.getBundle(), "unexpected bundle in the \"general communications\" action");
        Assertions.assertEquals(GeneralCommunicationsHelper.GENERAL_COMMUNICATIONS_APPLICATION, kafkaAction.getApplication(), "unexpected application in the \"general communications\" action");
        Assertions.assertEquals(GeneralCommunicationsHelper.GENERAL_COMMUNICATIONS_EVENT_TYPE, kafkaAction.getEventType(), "unexpected event type in the \"general communications\" action");
        Assertions.assertEquals(orgId, kafkaAction.getOrgId(), "unexpected org id in the \"general communications\" action");

        // Assert that a recipients' override exists.
        final List<Recipient> recipients = kafkaAction.getRecipients();
        Assertions.assertEquals(1, recipients.size(), "there should only be one recipient setting for the \"general communications\" action");

        // Make sure that the user preferences are ignored.
        final Recipient recipient = recipients.getFirst();
        Assertions.assertTrue(recipient.getIgnoreUserPreferences());

        final Context context = kafkaAction.getContext();
        final Map<String, Object> contextProperties = context.getAdditionalProperties();
        Assertions.assertEquals(IntegrationDisabledNotifier.getFrontendCategory(createdEndpoints.getFirst()), contextProperties.get(GeneralCommunicationsHelper.GENERAL_COMMUNICATION_CONTEXT_INTEGRATION_CATEGORY), "unexpected integration category received in the action's context");

        // Check the events, their metadata and their payload.
        final List<Event> events = kafkaAction.getEvents();
        Assertions.assertEquals(1, events.size(), "unexpected number of \"general communications\" action events");

        final Event event = events.getFirst();

        final Metadata metadata = event.getMetadata();
        Assertions.assertEquals(1, metadata.getAdditionalProperties().size(), "unexpected number of metadata additional properties");
        Assertions.assertEquals(GeneralCommunicationsHelper.GENERAL_COMMUNICATION_METADATA_VALUE, metadata.getAdditionalProperties().get(GeneralCommunicationsHelper.GENERAL_COMMUNICATION_METADATA_KEY), "unexpected event metadata value");

        final Payload payload = event.getPayload();
        final Map<String, Object> payloadAdditionalProperties = payload.getAdditionalProperties();
        Assertions.assertEquals(1, payloadAdditionalProperties.size(), "unexpected number of payload additional properties");

        final List<String> expectedIntegrationNames = createdEndpoints
            .stream()
            .map(Endpoint::getName)
            .toList();
        final List<String> actionIntegrationNames = (List<String>) payloadAdditionalProperties.get(GeneralCommunicationsHelper.GENERAL_COMMUNICATION_PAYLOAD_INTEGRATION_NAMES);

        for (final String actionIntegrationName : actionIntegrationNames) {
            Assertions.assertTrue(expectedIntegrationNames.contains(actionIntegrationName), String.format("the event's payload contains an integration name \"%s\" that was not found in the ones created in the database", actionIntegrationName));
        }
    }
}
