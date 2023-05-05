package com.redhat.cloud.notifications.processors.teams;

import com.redhat.cloud.notifications.processors.camel.CamelMetricsTest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;

import static com.redhat.cloud.notifications.processors.camel.RetryCounterProcessor.CAMEL_TEAMS_RETRY_COUNTER;
import static com.redhat.cloud.notifications.processors.teams.TeamsRouteBuilder.TEAMS_INCOMING_ROUTE;
import static com.redhat.cloud.notifications.processors.teams.TeamsRouteBuilder.TEAMS_OUTGOING_ROUTE;

@QuarkusTest
public class TeamsMetricsTest extends CamelMetricsTest {

    @BeforeEach
    void beforeTest() {
        restPath = TeamsRouteBuilder.REST_PATH;
        mockPath = "/camel/teams";
        mockPathKo = "/camel/teams_ko";
        camelIncomingRouteName = TEAMS_INCOMING_ROUTE;
        camelOutgoingRouteName = TEAMS_OUTGOING_ROUTE;
        retryCounterName = CAMEL_TEAMS_RETRY_COUNTER;
    }
}
