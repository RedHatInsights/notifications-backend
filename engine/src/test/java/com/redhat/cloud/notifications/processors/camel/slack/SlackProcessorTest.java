package com.redhat.cloud.notifications.processors.camel.slack;

import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.camel.CamelProcessor;
import com.redhat.cloud.notifications.processors.camel.CamelProcessorTest;
import com.redhat.cloud.notifications.templates.models.EnvironmentTest;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.events.EndpointProcessor.SLACK_ENDPOINT_SUBTYPE;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
public class SlackProcessorTest extends CamelProcessorTest {

    private static final String WEBHOOK_URL = "https://foo.bar";
    private static final String CHANNEL = "#notifications";
    private static final String SLACK_TEMPLATE = "{#if data.context.display_name??}" +
            "<{data.inventory_url}|{data.context.display_name}> " +
            "triggered {data.events.size()} event{#if data.events.size() > 1}s{/if}" +
            "{#else}{data.events.size()} event{#if data.events.size() > 1}s{/if} triggered{/if} " +
            "from {data.source.application.display_name} - {data.source.bundle.display_name}. " +
            "<{data.application_url}|Open {data.source.application.display_name}>";
    private static final String SLACK_EXPECTED_MSG = "<" + EnvironmentTest.expectedTestEnvUrlValue + "/insights/inventory/6ad30f3e-0497-4e74-99f1-b3f9a6120a6f?from=notifications&integration=slack|my-computer> " +
            "triggered 1 event from Policies - Red Hat Enterprise Linux. <" + EnvironmentTest.expectedTestEnvUrlValue + "/insights/policies?from=notifications&integration=slack|Open Policies>";
    private static final String SLACK_EXPECTED_MSG_WITH_HOST_URL = "<" + CONTEXT_HOST_URL + "?from=notifications&integration=slack|my-computer> " +
            "triggered 1 event from Policies - Red Hat Enterprise Linux. <" + EnvironmentTest.expectedTestEnvUrlValue + "/insights/policies?from=notifications&integration=slack|Open Policies>";

    @Inject
    SlackProcessor slackProcessor;

    @Override
    protected String getQuteTemplate() {
        return SLACK_TEMPLATE;
    }

    @Override
    protected String getExpectedMessage(boolean withHostUrl) {
        return withHostUrl ? SLACK_EXPECTED_MSG_WITH_HOST_URL : SLACK_EXPECTED_MSG;
    }

    @Override
    protected String getSubType() {
        return SLACK_ENDPOINT_SUBTYPE;
    }

    @Override
    protected CamelProcessor getCamelProcessor() {
        return slackProcessor;
    }

    @Override
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
        assertEquals(CHANNEL, notification.getString("channel"));
        assertEquals(getExpectedMessage(withHostUrl), notification.getString("message"));
    }

    @Override
    protected void addExtraEndpointProperties(CamelProperties properties) {
        properties.setExtras(Map.of("channel", CHANNEL));
    }

    @Override
    protected String getExpectedConnectorHeader() {
        return SLACK_ENDPOINT_SUBTYPE;
    }

    /**
     * Tests that when the endpoint's {@link CamelProperties} does not contain
     * an {@link CamelProperties#extras} property, the Slack message gets sent
     * to the connector without the Slack channel. It's a regression test for
     * <a href="https://issues.redhat.com/browse/RHCLOUD-33871">RHCLOUD-33871</a>.
     */
    @Test
    void testEmptyExtrasSendsSlackMessage() {
        // Create a default template for the test.
        this.mockTemplate();

        // Build the required data.
        final Event event = buildEvent(false);
        final Endpoint endpoint = this.buildEndpoint();
        // Remove the "extras" object from the endpoint.
        final CamelProperties camelProperties = endpoint.getProperties(CamelProperties.class);
        camelProperties.setExtras(null);

        // Call the processor under test.
        this.slackProcessor.process(event, List.of(endpoint));

        await().until(() -> inMemorySink.received().size() == 1);
        Message<JsonObject> message = inMemorySink.received().get(0);

        // Make sure the message was sent with the expected headers and payload.
        assertNotificationsConnectorHeader(message);

        CloudEventMetadata cloudEventMetadata = message.getMetadata(CloudEventMetadata.class).get();
        assertNotNull(cloudEventMetadata.getId());
        assertEquals(getExpectedCloudEventType(), cloudEventMetadata.getType());

        JsonObject notification = message.getPayload();

        assertEquals(DEFAULT_ORG_ID, notification.getString("org_id"));
        assertEquals(WEBHOOK_URL, notification.getString("webhookUrl"));
        // The channel should be null in this case, since we removed the
        // "extras" object.
        assertNull(notification.getString("channel"), "the channel should be null since the endpoint's camel properties did not contain an \"extras\" object");
        assertEquals(SLACK_EXPECTED_MSG, notification.getString("message"));
    }
}
