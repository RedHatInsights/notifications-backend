package com.redhat.cloud.notifications.processors.camel.teams;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.processors.camel.CamelProcessor;
import com.redhat.cloud.notifications.processors.camel.CamelProcessorTest;
import com.redhat.cloud.notifications.processors.camel.InternalCamelTemporaryService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import javax.inject.Inject;


@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TeamsProcessorTest extends CamelProcessorTest {

    private static final String GOOGLE_SPACES_TEMPLATE = "{#if data.context.display_name??}" +
            "<{data.environment_url}/insights/inventory/{data.context.inventory_id}|{data.context.display_name}> " +
            "triggered {data.events.size()} event{#if data.events.size() > 1}s{/if}" +
            "{#else}{data.events.size()} event{#if data.events.size() > 1}s{/if} triggered{/if} " +
            "from {data.bundle}/{data.application}. " +
            "<{data.environment_url}/insights/{data.application}|Open {data.application}>";

    private static final String GOOGLE_SPACES_EXPECTED_MSG = "<//insights/inventory/6ad30f3e-0497-4e74-99f1-b3f9a6120a6f|my-computer> " +
            "triggered 1 event from rhel/policies. <//insights/policies|Open policies>";


    @Inject
    TeamsProcessor teamsProcessor;

    @InjectMock
    @RestClient
    InternalTemporaryTeamsService internalTemporaryTeamsService;

    @Override
    protected String getCuteTemplate() {
        return GOOGLE_SPACES_TEMPLATE;
    }

    @Override
    protected String getExpectedMessage() {
        return GOOGLE_SPACES_EXPECTED_MSG;
    }

    @Override
    protected String getSubType() {
        return "teams";
    }

    @Override
    protected CamelProcessor getCamelProcessor() {
        return teamsProcessor;
    }

    @Override
    protected InternalCamelTemporaryService getInternalClient() {
        return internalTemporaryTeamsService;
    }
}
