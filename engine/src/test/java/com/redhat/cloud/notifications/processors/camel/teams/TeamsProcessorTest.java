package com.redhat.cloud.notifications.processors.camel.teams;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.processors.camel.CamelProcessor;
import com.redhat.cloud.notifications.processors.camel.CamelProcessorTest;
import com.redhat.cloud.notifications.templates.models.EnvironmentTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import static com.redhat.cloud.notifications.events.EndpointProcessor.TEAMS_ENDPOINT_SUBTYPE;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TeamsProcessorTest extends CamelProcessorTest {

    private static final String TEAMS_TEMPLATE = "{\"text\":\"{#if data.context.display_name??}" +
            "[{data.context.display_name}]({data.inventory_url}) " +
            "triggered {data.events.size()} event{#if data.events.size() > 1}s{/if}" +
            "{#else}{data.events.size()} event{#if data.events.size() > 1}s{/if} triggered{/if} " +
            "from {data.source.application.display_name} - {data.source.bundle.display_name}. " +
            "[Open {data.source.application.display_name}]({data.application_url})\"}";

    private static final String TEAMS_EXPECTED_MSG = "{\"text\":\"[my-computer](" + EnvironmentTest.expectedTestEnvUrlValue + "/insights/inventory/6ad30f3e-0497-4e74-99f1-b3f9a6120a6f?from=notification_teams) " +
            "triggered 1 event from Policies - Red Hat Enterprise Linux. [Open Policies](" + EnvironmentTest.expectedTestEnvUrlValue + "/insights/policies?from=notification_teams)\"}";

    private static final String TEAMS_EXPECTED_MSG_WITH_HOST_URL = "{\"text\":\"[my-computer](" + CONTEXT_HOST_URL + "?from=notification_teams) " +
            "triggered 1 event from Policies - Red Hat Enterprise Linux. [Open Policies](" + EnvironmentTest.expectedTestEnvUrlValue + "/insights/policies?from=notification_teams)\"}";

    @Inject
    TeamsProcessor teamsProcessor;

    @Override
    protected String getQuteTemplate() {
        return TEAMS_TEMPLATE;
    }

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
}
