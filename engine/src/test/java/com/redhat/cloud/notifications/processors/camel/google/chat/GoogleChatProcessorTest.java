package com.redhat.cloud.notifications.processors.camel.google.chat;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.processors.camel.CamelProcessor;
import com.redhat.cloud.notifications.processors.camel.CamelProcessorTest;
import com.redhat.cloud.notifications.templates.models.EnvironmentTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import static com.redhat.cloud.notifications.events.EndpointProcessor.GOOGLE_CHAT_ENDPOINT_SUBTYPE;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class GoogleChatProcessorTest extends CamelProcessorTest {

    private static final String GOOGLE_CHAT_TEMPLATE = "{\"text\":\"{#if data.context.display_name??}" +
            "<{data.environment_url}/insights/inventory/{data.context.inventory_id}|{data.context.display_name}> " +
            "triggered {data.events.size()} event{#if data.events.size() > 1}s{/if}" +
            "{#else}{data.events.size()} event{#if data.events.size() > 1}s{/if} triggered{/if} " +
            "from {data.bundle}/{data.application}. " +
            "<{data.environment_url}/insights/{data.application}|Open {data.application}>\"}";

    private static final String GOOGLE_CHAT_EXPECTED_MSG = "{\"text\":\"<" + EnvironmentTest.expectedTestEnvUrlValue + "/insights/inventory/6ad30f3e-0497-4e74-99f1-b3f9a6120a6f|my-computer> " +
            "triggered 1 event from rhel/policies. <" + EnvironmentTest.expectedTestEnvUrlValue + "/insights/policies|Open policies>\"}";

    @Inject
    GoogleChatProcessor googleSpacesProcessor;

    @Override
    protected String getQuteTemplate() {
        return GOOGLE_CHAT_TEMPLATE;
    }

    @Override
    protected String getExpectedMessage() {
        return GOOGLE_CHAT_EXPECTED_MSG;
    }

    @Override
    protected String getSubType() {
        return GOOGLE_CHAT_ENDPOINT_SUBTYPE;
    }

    @Override
    protected CamelProcessor getCamelProcessor() {
        return googleSpacesProcessor;
    }

    @Override
    protected String getExpectedConnectorHeader() {
        return GOOGLE_CHAT_ENDPOINT_SUBTYPE;
    }
}
