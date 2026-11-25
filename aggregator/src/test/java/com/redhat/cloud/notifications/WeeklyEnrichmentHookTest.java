package com.redhat.cloud.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.config.AggregatorConfig;
import com.redhat.cloud.notifications.helpers.AggregationTimeHelper;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.helpers.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.models.AggregatedPayloadEntry;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.DigestTriggerOrgConfig;
import com.redhat.cloud.notifications.models.DigestTriggerOrgConfigId;
import com.redhat.cloud.notifications.processors.email.AggregatedPayloadBuilder;
import io.prometheus.client.CollectorRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.SubscriptionType.WEEKLY;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the enrichment hook of the weekly job with a mocked {@link AggregatedPayloadBuilder}.
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class WeeklyEnrichmentHookTest {

    private static final String DEFAULT_WEEKLY_CRON = "30 0 * * 1";
    private static final String FLAGGED_ORG = "weeklyHookOrg1";
    private static final String OTHER_ORG = "weeklyHookOrg2";

    @Inject
    ResourceHelpers helpers;

    @Inject
    WeeklyEmailAggregationJob weeklyEmailAggregationJob;

    @InjectSpy
    AggregatorConfig aggregatorConfig;

    @InjectMock
    AggregatedPayloadBuilder aggregatedPayloadBuilder;

    @Inject
    @Any
    InMemoryConnector connector;

    @Inject
    ObjectMapper objectMapper;

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
    void enrichedEntriesGoIntoTheContextOfTheFlaggedOrgOnly() {
        doReturn(true).when(aggregatorConfig).isNewAggregationPipelineEnabled(FLAGGED_ORG);
        when(aggregatedPayloadBuilder.build(anyList())).thenAnswer(invocation -> List.of(Map.of(
            "payload_details_id", UUID.randomUUID().toString(),
            "endpoint_id", UUID.randomUUID().toString())));
        for (String orgId : List.of(FLAGGED_ORG, OTHER_ORG)) {
            DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(new DigestTriggerOrgConfigId(orgId, WEEKLY), DEFAULT_WEEKLY_CRON);
            config.setLastRun(now.minusWeeks(1));
            config.setNextRun(executionTime);
            helpers.addDigestTriggerOrgConfig(config);
        }
        addEvent(FLAGGED_ORG, "rhel", "policies");
        addEvent(FLAGGED_ORG, "rhel", "advisor");
        addEvent(OTHER_ORG, "rhel", "compliance");

        weeklyEmailAggregationJob.searchBuildAndSendAggregationCommands(executionTime, new CollectorRegistry());

        List<Action> actions = connector.sink(WeeklyEmailAggregationJob.EGRESS_CHANNEL).received().stream()
            .map(message -> Parser.decode(String.valueOf(message.getPayload())))
            .toList();
        assertEquals(2, actions.size());
        for (Action action : actions) {
            Map<String, Object> contextProperties = action.getContext().getAdditionalProperties();
            if (FLAGGED_ORG.equals(action.getOrgId())) {
                assertNotNull(contextProperties.get(AggregatedPayloadEntry.CONTEXT_KEY));
                assertEquals(1, ((List<?>) contextProperties.get(AggregatedPayloadEntry.CONTEXT_KEY)).size());
            } else {
                assertFalse(contextProperties.containsKey(AggregatedPayloadEntry.CONTEXT_KEY));
            }
        }

        ArgumentCaptor<List<AggregationCommand>> commandsCaptor = ArgumentCaptor.captor();
        verify(aggregatedPayloadBuilder).build(commandsCaptor.capture());
        assertEquals(2, commandsCaptor.getValue().size());
        assertEquals(FLAGGED_ORG, commandsCaptor.getValue().getFirst().getOrgId());
    }

    private void addEvent(String orgId, String bundleName, String applicationName) {
        String payload = TestHelpers.generatePayloadContent(orgId, bundleName, applicationName, "somePolicyId", "someHostId").toString();
        helpers.addEventEmailAggregationWithSubscriptionType(orgId, bundleName, applicationName, now.minusDays(2), payload, true, WEEKLY);
    }
}
