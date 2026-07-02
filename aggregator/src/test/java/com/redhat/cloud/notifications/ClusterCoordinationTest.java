package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.AggregatorConfig;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Tests for cluster coordination functionality.
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class ClusterCoordinationTest {

    @Inject
    DailyEmailAggregationJob dailyEmailAggregationJob;

    @InjectMock
    AggregatorConfig aggregatorConfig;

    @Inject
    @Any
    InMemoryConnector connector;

    @Inject
    ResourceHelpers helpers;

    @AfterEach
    void tearDown() {
        connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL).clear();
        helpers.purgeEventAggregations();
    }

    @Test
    void shouldRunWhenClusterIdNotConfigured() {
        // When cluster ID is not configured, aggregator runs normally (backward compatibility)
        when(aggregatorConfig.getClusterId()).thenReturn(Optional.empty());

        helpers.purgeAggregationOrgConfig();
        addTestAggregation();
        dailyEmailAggregationJob.setDefaultDailyDigestTime(
            dailyEmailAggregationJob.computeScheduleExecutionTime().toLocalTime()
        );

        dailyEmailAggregationJob.processDailyEmail();

        assertEquals(1, connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL).received().size());
    }

    @Test
    void shouldRunWhenClusterIdMatchesUnleash() {
        // When cluster ID matches Unleash active cluster, aggregator runs
        when(aggregatorConfig.getClusterId()).thenReturn(Optional.of("test"));
        when(aggregatorConfig.getActiveCluster()).thenReturn(Optional.of("test"));

        helpers.purgeAggregationOrgConfig();
        addTestAggregation();
        dailyEmailAggregationJob.setDefaultDailyDigestTime(
            dailyEmailAggregationJob.computeScheduleExecutionTime().toLocalTime()
        );

        dailyEmailAggregationJob.processDailyEmail();

        assertEquals(1, connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL).received().size());
    }

    @Test
    void shouldSkipWhenClusterIdDoesNotMatch() {
        // When cluster ID does NOT match Unleash active cluster, aggregator skips
        when(aggregatorConfig.getClusterId()).thenReturn(Optional.of("test"));
        when(aggregatorConfig.getActiveCluster()).thenReturn(Optional.of("not-test"));

        helpers.purgeAggregationOrgConfig();
        addTestAggregation();
        dailyEmailAggregationJob.setDefaultDailyDigestTime(
            dailyEmailAggregationJob.computeScheduleExecutionTime().toLocalTime()
        );

        dailyEmailAggregationJob.processDailyEmail();

        assertEquals(0, connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL).received().size());
    }

    @Test
    void shouldSkipWhenUnleashReturnsEmpty() {
        // When Unleash is unreachable/disabled (returns empty), aggregator skips (fail-safe)
        when(aggregatorConfig.getClusterId()).thenReturn(Optional.of("test"));
        when(aggregatorConfig.getActiveCluster()).thenReturn(Optional.empty());

        helpers.purgeAggregationOrgConfig();
        addTestAggregation();
        dailyEmailAggregationJob.setDefaultDailyDigestTime(
            dailyEmailAggregationJob.computeScheduleExecutionTime().toLocalTime()
        );

        dailyEmailAggregationJob.processDailyEmail();

        assertEquals(0, connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL).received().size());
    }

    private void addTestAggregation() {
        helpers.purgeAggregationOrgConfig();
        helpers.addEventEmailAggregation(
            "testOrgId",
            "rhel",
            "policies",
            dailyEmailAggregationJob.computeScheduleExecutionTime().minusHours(5),
            "{\"org_id\":\"testOrgId\",\"bundle\":\"rhel\",\"application\":\"policies\"}",
            false
        );
    }
}
