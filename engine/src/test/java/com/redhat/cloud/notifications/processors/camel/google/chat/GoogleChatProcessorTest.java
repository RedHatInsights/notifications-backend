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

    private static final String GOOGLE_CHAT_EXPECTED_MSG = "{\"text\":\"<" + EnvironmentTest.expectedTestEnvUrlValue + "/insights/inventory/6ad30f3e-0497-4e74-99f1-b3f9a6120a6f?from=notifications&integration=google_chat|my-computer> " +
            "triggered 1 event from Policies - Red Hat Enterprise Linux. <" + EnvironmentTest.expectedTestEnvUrlValue + "/insights/policies?from=notifications&integration=google_chat|Open Policies>\"}";

    private static final String GOOGLE_CHAT_EXPECTED_MSG_WITH_HOST_URL = "{\"text\":\"<" + CONTEXT_HOST_URL + "?from=notifications&integration=google_chat|my-computer> " +
            "triggered 1 event from Policies - Red Hat Enterprise Linux. <" + EnvironmentTest.expectedTestEnvUrlValue + "/insights/policies?from=notifications&integration=google_chat|Open Policies>\"}";

    @Inject
    GoogleChatProcessor googleSpacesProcessor;

    @Override
    protected String getExpectedMessage(boolean withHostUrl) {
        return withHostUrl ? GOOGLE_CHAT_EXPECTED_MSG_WITH_HOST_URL : GOOGLE_CHAT_EXPECTED_MSG;
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
