package com.redhat.cloud.notifications.processors.camel.teams;

import com.redhat.cloud.notifications.processors.camel.CamelRoutesTest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.BeforeEach;

import static com.redhat.cloud.notifications.events.EndpointProcessor.TEAMS_ENDPOINT_SUBTYPE;
import static com.redhat.cloud.notifications.processors.camel.RetryCounterProcessor.CAMEL_TEAMS_RETRY_COUNTER;
import static com.redhat.cloud.notifications.processors.camel.teams.TeamsRouteBuilder.TEAMS_ROUTE;

@QuarkusTest
@TestProfile(TeamsTestProfile.class)
public class TeamsRoutesTest extends CamelRoutesTest {

    @BeforeEach
    @Override
    protected void beforeEach() {
        routeEndpoint = "https://foo.com";
        mockRouteEndpoint = "mock:https:foo.com";
        super.beforeEach();
    }

    @Override
    protected String getIncomingRoute() {
        return TEAMS_ROUTE;
    }

    @Override
    protected String getEndpointSubtype() {
        return TEAMS_ENDPOINT_SUBTYPE;
    }

    @Override
    protected String getRetryCounterName() {
        return CAMEL_TEAMS_RETRY_COUNTER;
    }
}
