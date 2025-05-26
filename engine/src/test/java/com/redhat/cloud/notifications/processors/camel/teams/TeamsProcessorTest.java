package com.redhat.cloud.notifications.processors.camel.teams;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.processors.camel.CamelProcessor;
import com.redhat.cloud.notifications.processors.camel.CamelProcessorTest;
import com.redhat.cloud.notifications.templates.models.EnvironmentTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.events.EndpointProcessor.TEAMS_ENDPOINT_SUBTYPE;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TeamsProcessorTest extends CamelProcessorTest {

    private static final String TEAMS_EXPECTED_MSG = "\"text\": \"[my-computer](" + EnvironmentTest.expectedTestEnvUrlValue + "/insights/inventory/6ad30f3e-0497-4e74-99f1-b3f9a6120a6f?from=notifications&integration=teams) " +
            "triggered 1 event from Policies - Red Hat Enterprise Linux. [Open Policies](" + EnvironmentTest.expectedTestEnvUrlValue + "/insights/policies?from=notifications&integration=teams)\"";

    private static final String TEAMS_EXPECTED_MSG_WITH_HOST_URL = "\"text\": \"[my-computer](" + CONTEXT_HOST_URL + "?from=notifications&integration=teams) " +
            "triggered 1 event from Policies - Red Hat Enterprise Linux. [Open Policies](" + EnvironmentTest.expectedTestEnvUrlValue + "/insights/policies?from=notifications&integration=teams)\"";

    private static final String TEAMS_EXPECTED_OCM_MSG_WITH_SUBSCRIPTION_ID = "\"text\": \"1 event triggered from Cluster Manager - OpenShift. [Open Cluster Manager](https://cloud.redhat.com/openshift/details/s/64503ec1-a365-4a1b-8c8b-0a6c519ec5fb?from=notifications&integration=teams)\"";

    @Inject
    TeamsProcessor teamsProcessor;

    @Override
    protected String getExpectedMessage(boolean withHostUrl) {
        return withHostUrl ? TEAMS_EXPECTED_MSG_WITH_HOST_URL : TEAMS_EXPECTED_MSG;
    }

    @Override
    protected String getSubType() {
        return TEAMS_ENDPOINT_SUBTYPE;
    }

    @Override
    protected CamelProcessor getCamelProcessor() {
        return teamsProcessor;
    }

    @Override
    protected String getExpectedConnectorHeader() {
        return TEAMS_ENDPOINT_SUBTYPE;
    }

    protected void verifyKafkaMessage(boolean withHostUrl) {

        await().until(() -> inMemorySink.received().size() == 1);
        Message<JsonObject> message = inMemorySink.received().get(0);

        assertNotificationsConnectorHeader(message);

        CloudEventMetadata cloudEventMetadata = message.getMetadata(CloudEventMetadata.class).get();
        assertNotNull(cloudEventMetadata.getId());
        assertEquals(getExpectedCloudEventType(), cloudEventMetadata.getType());

        JsonObject notification = message.getPayload();

        assertEquals(DEFAULT_ORG_ID, notification.getString("org_id"));
        assertEquals(WEBHOOK_URL, notification.getString("webhookUrl"));
        assertTrue(notification.getString("message").contains(getExpectedMessage(withHostUrl)));
    }


    /**
     * An additional test to validate that a {@code application_url} will use the provided {@code subscription_id}, if
     * present.
     */
    @Test
    void testProcessWithOCMPayload() {
        // Build the required data.
        final Event event = buildOCMEvent();
        final Endpoint endpoint = this.buildEndpoint();

        // Send the event.
        getCamelProcessor().process(event, List.of(endpoint));

        // Retrieve the message to be sent, and run basic validation.
        await().until(() -> inMemorySink.received().size() == 1);
        Message<JsonObject> message = inMemorySink.received().getFirst();

        CloudEventMetadata cloudEventMetadata = message.getMetadata(CloudEventMetadata.class).get();
        assertNotNull(cloudEventMetadata.getId());
        assertEquals(getExpectedCloudEventType(), cloudEventMetadata.getType());

        // Retrieve the OCM notification and validate that the correct URL is provided.
        JsonObject notification = message.getPayload();
        System.out.println(notification.getString("message"));
        assertTrue(notification.getString("message").contains(TEAMS_EXPECTED_OCM_MSG_WITH_SUBSCRIPTION_ID));
    }

    private static Event buildOCMEvent() {
        Event event = buildEvent(false);
        Action action = ((EventWrapperAction) event.getEventWrapper()).getEvent();
        action.setContext(null);
        action.setBundle("openshift");
        action.setApplication("cluster-manager");
        action.setEventType("cluster-update");
        action.setEvents(
                List.of(
                        new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                                .withMetadata(new Metadata.MetadataBuilder().build())
                                .withPayload(new Payload.PayloadBuilder()
                                        .withAdditionalProperty(
                                                "global_vars",
                                                Map.of("subscription_id", "64503ec1-a365-4a1b-8c8b-0a6c519ec5fb"))
                                        .withAdditionalProperty("foo", "bar")
                                        .build()
                                ).build()
                )
        );

        event.setEventWrapper(new EventWrapperAction(action));
        event.setBundleDisplayName("OpenShift");
        event.setApplicationDisplayName("Cluster Manager");
        Bundle bundle = new Bundle("openshift", "OpenShift");
        Application application = new Application();
        application.setBundle(bundle);
        application.setName("cluster-manager");
        application.setDisplayName("Cluster Manager");
        EventType eventType = new EventType();
        eventType.setApplication(application);
        eventType.setName("cluster-update");
        eventType.setDisplayName("Cluster Update");
        event.setEventType(eventType);

        return event;
    }
}
