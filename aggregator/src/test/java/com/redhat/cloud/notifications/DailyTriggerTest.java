package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.AggregatorConfig;
import com.redhat.cloud.notifications.helpers.AggregationTimeHelper;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.helpers.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Parser;
import io.prometheus.client.CollectorRegistry;
import io.quarkus.test.InjectMock;
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

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Pins the shape of the trigger the daily job emits for an org WITHOUT the new aggregation pipeline (see
 * {@link PlainTriggerAssertions}).
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class DailyTriggerTest {

    @Inject
    ResourceHelpers helpers;

    @Inject
    DailyEmailAggregationJob dailyEmailAggregationJob;

    @InjectMock
    AggregatorConfig aggregatorConfig;

    @Inject
    @Any
    InMemoryConnector connector;

    LocalDateTime referenceTime;

    @BeforeEach
    void setUp() {
        helpers.purgeEventAggregations();
        helpers.purgeAggregationOrgConfig();
        connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL).clear();
        referenceTime = AggregationTimeHelper.computeReferenceTime();
        when(aggregatorConfig.getDefaultDailyDigestTime()).thenReturn(referenceTime.toLocalTime());
    }

    @AfterEach
    void tearDown() {
        helpers.purgeEventAggregations();
        helpers.purgeAggregationOrgConfig();
        connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL).clear();
    }

    @Test
    void dailyTriggerHasThePlainShape() {
        addScenarioEvents();

        dailyEmailAggregationJob.searchBuildAndSendAggregationCommands(referenceTime, new CollectorRegistry());

        assertPlainShape();
    }

    private void assertPlainShape() {
        List<Action> actions = connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL).received().stream()
            .map(message -> Parser.decode(String.valueOf(message.getPayload())))
            .toList();
        assertEquals(4, actions.size(), "One trigger per org and bundle: 2 orgs x 2 bundles");
        actions.forEach(action -> PlainTriggerAssertions.assertPlainTrigger(action, DailyEmailAggregationJob.EVENT_TYPE_NAME, DAILY));
        Map<String, List<String>> expectedBundles = Map.of("rhel", List.of("advisor", "policies"), "subscription-services", List.of("errata"));
        assertEquals(Map.of("someOrgId", expectedBundles, "anotherOrgId", expectedBundles), PlainTriggerAssertions.applicationsByOrgAndBundle(actions));
    }

    private void addScenarioEvents() {
        addEvent("someOrgId", "rhel", "policies");
        addEvent("someOrgId", "rhel", "advisor");
        addEvent("someOrgId", "subscription-services", "errata");
        addEvent("someOrgId", "subscription-services", "errata");
        addEvent("anotherOrgId", "rhel", "policies");
        addEvent("anotherOrgId", "rhel", "advisor");
        addEvent("anotherOrgId", "subscription-services", "errata");
    }

    private void addEvent(String orgId, String bundleName, String applicationName) {
        String payload = TestHelpers.generatePayloadContent(orgId, bundleName, applicationName, "somePolicyId", "someHostId").toString();
        helpers.addEventEmailAggregation(orgId, bundleName, applicationName, LocalDateTime.now(UTC).minusHours(5), payload, false);
    }
}
