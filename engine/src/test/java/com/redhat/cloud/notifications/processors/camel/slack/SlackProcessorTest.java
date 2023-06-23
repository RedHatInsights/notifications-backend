package com.redhat.cloud.notifications.processors.camel.slack;

import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.processors.camel.CamelProcessor;
import com.redhat.cloud.notifications.processors.camel.CamelProcessorTest;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.reactive.messaging.Message;

import javax.inject.Inject;
import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.events.EndpointProcessor.SLACK_ENDPOINT_SUBTYPE;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
public class SlackProcessorTest extends CamelProcessorTest {

    private static final String WEBHOOK_URL = "https://foo.bar";
    private static final String CHANNEL = "#notifications";
    private static final String SLACK_TEMPLATE = "{#if data.context.display_name??}" +
            "<{data.environment_url}/insights/inventory/{data.context.inventory_id}|{data.context.display_name}> " +
            "triggered {data.events.size()} event{#if data.events.size() > 1}s{/if}" +
            "{#else}{data.events.size()} event{#if data.events.size() > 1}s{/if} triggered{/if} " +
            "from {data.bundle}/{data.application}. " +
            "<{data.environment_url}/insights/{data.application}|Open {data.application}>";
    private static final String SLACK_EXPECTED_MSG = "<//insights/inventory/6ad30f3e-0497-4e74-99f1-b3f9a6120a6f|my-computer> " +
            "triggered 1 event from rhel/policies. <//insights/policies|Open policies>";

    @Inject
    SlackProcessor slackProcessor;

    @Override
    protected String getQuteTemplate() {
        return SLACK_TEMPLATE;
    }

    @Override
    protected String getExpectedMessage() {
        return SLACK_EXPECTED_MSG;
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
    protected void verifyKafkaMessage() {

        await().until(() -> inMemorySink.received().size() == 1);
        Message<JsonObject> message = inMemorySink.received().get(0);

        assertNotificationsConnectorHeader(message);

        CloudEventMetadata cloudEventMetadata = message.getMetadata(CloudEventMetadata.class).get();
        assertNotNull(cloudEventMetadata.getId());
        assertEquals(getExpectedCloudEventType(), cloudEventMetadata.getType());

        SlackNotification notification = message.getPayload().mapTo(SlackNotification.class);

        assertEquals(DEFAULT_ORG_ID, notification.orgId);
        assertEquals(WEBHOOK_URL, notification.webhookUrl);
        assertEquals(CHANNEL, notification.channel);
        assertEquals(SLACK_EXPECTED_MSG, notification.message);
    }

    @Override
    protected void addExtraEndpointProperties(CamelProperties properties) {
        properties.setExtras(Map.of("channel", CHANNEL));
    }

    @Override
    protected String getExpectedConnectorHeader() {
        return SLACK_ENDPOINT_SUBTYPE;
    }
}
