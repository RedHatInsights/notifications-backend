package com.redhat.cloud.notifications.processors.slack;

import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.processors.camel.CamelProcessor;
import com.redhat.cloud.notifications.processors.camel.CamelProcessorTest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.mockito.ArgumentCaptor;
import javax.inject.Inject;
import java.util.Map;


import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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

    @InjectMock
    @RestClient
    InternalTemporarySlackService internalTemporarySlackService;

    @Override
    protected String getCuteTemplate() {
        return SLACK_TEMPLATE;
    }

    @Override
    protected String getExpectedMessage() {
        return SLACK_EXPECTED_MSG;
    }

    @Override
    protected String getSubType() {
        return "slack";
    }

    @Override
    protected CamelProcessor getCamelProcessor() {
        return slackProcessor;
    }

    @Override
    protected void argumentCaptorChecks() {
        ArgumentCaptor<SlackNotification> argumentCaptor = ArgumentCaptor.forClass(SlackNotification.class);
        verify(internalTemporarySlackService, times(1)).send(argumentCaptor.capture());
        assertEquals(DEFAULT_ORG_ID, argumentCaptor.getValue().orgId);
        assertNotNull(argumentCaptor.getValue().historyId);
        assertEquals(WEBHOOK_URL, argumentCaptor.getValue().webhookUrl);
        assertEquals(CHANNEL, argumentCaptor.getValue().channel);
        assertEquals(SLACK_EXPECTED_MSG, argumentCaptor.getValue().message);
    }

    @Override
    protected void addExtraEndpointProperties(CamelProperties properties) {
        properties.setExtras(Map.of("channel", CHANNEL));
    }

}
