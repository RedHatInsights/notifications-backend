package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.helpers.AggregationTimeHelper;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.helpers.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.models.DigestTriggerOrgConfig;
import com.redhat.cloud.notifications.models.DigestTriggerOrgConfigId;
import io.prometheus.client.CollectorRegistry;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.models.SubscriptionType.WEEKLY;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the shape of the trigger the weekly job emits for an org WITHOUT the new aggregation pipeline (see {@link DailyTriggerTest}).
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class WeeklyTriggerTest {

    private static final String DEFAULT_WEEKLY_CRON = "30 0 * * 1";

    @Inject
    ResourceHelpers helpers;

    @Inject
    WeeklyEmailAggregationJob weeklyEmailAggregationJob;

    @Inject
    @Any
    InMemoryConnector connector;

    LocalDateTime now;
    LocalDateTime executionTime;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(UTC);
        executionTime = AggregationTimeHelper.computeReferenceTime(now);
        helpers.purgeEventAggregations();
        helpers.purgeDigestTriggerOrgConfig();
        connector.sink(WeeklyEmailAggregationJob.EGRESS_CHANNEL).clear();
    }

    @AfterEach
    void tearDown() {
        helpers.purgeEventAggregations();
        helpers.purgeDigestTriggerOrgConfig();
        connector.sink(WeeklyEmailAggregationJob.EGRESS_CHANNEL).clear();
    }

    @Test
    void weeklyTriggerHasThePlainShape() {
        prepareScenario();

        weeklyEmailAggregationJob.searchBuildAndSendAggregationCommands(executionTime, new CollectorRegistry());

        assertPlainShape();
    }

    private void prepareScenario() {
        for (String orgId : List.of("weeklyBaselineOrg1", "weeklyBaselineOrg2")) {
            DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(new DigestTriggerOrgConfigId(orgId, WEEKLY), DEFAULT_WEEKLY_CRON);
            config.setLastRun(now.minusWeeks(1));
            config.setNextRun(executionTime);
            helpers.addDigestTriggerOrgConfig(config);
        }
        addEvent("weeklyBaselineOrg1", "rhel", "policies");
        addEvent("weeklyBaselineOrg1", "rhel", "advisor");
        addEvent("weeklyBaselineOrg2", "rhel", "compliance");
    }

    private void assertPlainShape() {
        List<Action> actions = connector.sink(WeeklyEmailAggregationJob.EGRESS_CHANNEL).received().stream()
            .map(message -> Parser.decode(String.valueOf(message.getPayload())))
            .toList();
        assertEquals(2, actions.size(), "One trigger per org and bundle");
        actions.forEach(action -> PlainTriggerAssertions.assertPlainTrigger(action, WeeklyEmailAggregationJob.EVENT_TYPE_NAME, WEEKLY));
        assertEquals(Map.of(
            "weeklyBaselineOrg1", Map.of("rhel", List.of("advisor", "policies")),
            "weeklyBaselineOrg2", Map.of("rhel", List.of("compliance"))),
            PlainTriggerAssertions.applicationsByOrgAndBundle(actions));
    }

    private void addEvent(String orgId, String bundleName, String applicationName) {
        String payload = TestHelpers.generatePayloadContent(orgId, bundleName, applicationName, "somePolicyId", "someHostId").toString();
        helpers.addEventEmailAggregationWithSubscriptionType(orgId, bundleName, applicationName, now.minusDays(2), payload, true, WEEKLY);
    }
}
