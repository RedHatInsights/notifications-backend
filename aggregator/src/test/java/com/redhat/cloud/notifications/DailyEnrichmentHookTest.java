package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.AggregatorConfig;
import com.redhat.cloud.notifications.helpers.AggregationTimeHelper;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.helpers.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.models.AggregatedPayloadEntry;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import com.redhat.cloud.notifications.processors.email.AggregatedPayloadBuilder;
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
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exercises the enrichment hook of the daily job with a mocked {@link AggregatedPayloadBuilder}.
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class DailyEnrichmentHookTest {

    private static final String FLAGGED_ORG = "someOrgId";
    private static final String OTHER_ORG = "anotherOrgId";

    @Inject
    ResourceHelpers helpers;

    @Inject
    DailyEmailAggregationJob dailyEmailAggregationJob;

    @InjectMock
    AggregatorConfig aggregatorConfig;

    @InjectMock
    AggregatedPayloadBuilder aggregatedPayloadBuilder;

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
    void enrichedEntriesGoIntoTheContextOfTheFlaggedOrgOnly() {
        when(aggregatorConfig.isNewAggregationPipelineEnabled(FLAGGED_ORG)).thenReturn(true);
        when(aggregatedPayloadBuilder.build(anyList())).thenAnswer(invocation -> List.of(entry()));
        addScenarioEvents();

        run();

        List<Action> actions = emittedActions();
        assertEquals(4, actions.size());
        for (Action action : actions) {
            Map<String, Object> contextProperties = action.getContext().getAdditionalProperties();
            if (FLAGGED_ORG.equals(action.getOrgId())) {
                Object entries = contextProperties.get(AggregatedPayloadEntry.CONTEXT_KEY);
                assertNotNull(entries, "The flagged org's trigger must carry the aggregated payload entries");
                assertEquals(1, ((List<?>) entries).size());
                Map<?, ?> entry = (Map<?, ?>) ((List<?>) entries).getFirst();
                assertNotNull(UUID.fromString((String) entry.get("payload_details_id")));
                assertNotNull(UUID.fromString((String) entry.get("endpoint_id")));
            } else {
                assertFalse(contextProperties.containsKey(AggregatedPayloadEntry.CONTEXT_KEY), "An org without the flag must keep the plain trigger");
            }
        }
    }

    @Test
    void enricherReceivesTheCommandsOfOneOrgAndBundle() {
        when(aggregatorConfig.isNewAggregationPipelineEnabled(FLAGGED_ORG)).thenReturn(true);
        addScenarioEvents();

        run();

        ArgumentCaptor<List<AggregationCommand>> commandsCaptor = ArgumentCaptor.captor();
        verify(aggregatedPayloadBuilder, times(2)).build(commandsCaptor.capture());
        List<List<AggregationCommand>> calls = commandsCaptor.getAllValues();
        for (List<AggregationCommand> commands : calls) {
            assertEquals(1, commands.stream().map(AggregationCommand::getOrgId).distinct().count());
            assertEquals(FLAGGED_ORG, commands.getFirst().getOrgId());
            assertEquals(1, commands.stream().map(AggregationCommand::getBundleId).distinct().count());
        }
        assertEquals(List.of(1, 2), calls.stream().map(List::size).sorted().toList(),
            "subscription-services has one application, rhel has two");
    }

    @Test
    void emptyEnrichmentLeavesThePlainTrigger() {
        when(aggregatorConfig.isNewAggregationPipelineEnabled(anyString())).thenReturn(true);
        when(aggregatedPayloadBuilder.build(anyList())).thenReturn(List.of());
        addScenarioEvents();

        run();

        verify(aggregatedPayloadBuilder, times(4)).build(anyList());
        assertPlainTriggers();
    }

    @Test
    void enrichmentFailureSendsThePlainTriggerAndTheOrgStaysSuccessful() {
        when(aggregatorConfig.isNewAggregationPipelineEnabled(anyString())).thenReturn(true);
        when(aggregatedPayloadBuilder.build(anyList())).thenThrow(new IllegalStateException("simulated enrichment failure"));
        addScenarioEvents();
        AggregationOrgConfig config = new AggregationOrgConfig(FLAGGED_ORG, referenceTime.toLocalTime(), referenceTime.minusDays(1));
        helpers.addAggregationOrgConfig(config);
        LocalDateTime lastRunBefore = config.getLastRun();

        run();

        assertPlainTriggers();
        AggregationOrgConfig after = helpers.findAggregationOrgConfigByOrgId(FLAGGED_ORG);
        assertTrue(lastRunBefore.isBefore(after.getLastRun()), "The org must be reported successful so it is not retried");
    }

    @Test
    void flagEvaluationFailureIsTreatedAsFlagOff() {
        when(aggregatorConfig.isNewAggregationPipelineEnabled(anyString())).thenThrow(new IllegalStateException("simulated Unleash failure"));
        addScenarioEvents();

        run();

        verifyNoInteractions(aggregatedPayloadBuilder);
        assertPlainTriggers();
    }

    @Test
    void flagOffNeverCallsTheEnricher() {
        when(aggregatorConfig.isNewAggregationPipelineEnabled(eq(FLAGGED_ORG))).thenReturn(false);
        addScenarioEvents();

        run();

        verifyNoInteractions(aggregatedPayloadBuilder);
        assertPlainTriggers();
    }

    private void run() {
        dailyEmailAggregationJob.searchBuildAndSendAggregationCommands(referenceTime, new CollectorRegistry());
    }

    private List<String> emittedPayloads() {
        return connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL).received().stream()
            .map(message -> String.valueOf(message.getPayload()))
            .toList();
    }

    private List<Action> emittedActions() {
        return emittedPayloads().stream().map(Parser::decode).toList();
    }

    private void assertPlainTriggers() {
        List<Action> actions = emittedActions();
        assertEquals(4, actions.size());
        actions.forEach(action -> PlainTriggerAssertions.assertPlainTrigger(action, DailyEmailAggregationJob.EVENT_TYPE_NAME, DAILY));
    }

    private static Map<String, Object> entry() {
        return Map.of("payload_details_id", UUID.randomUUID().toString(), "endpoint_id", UUID.randomUUID().toString());
    }

    // Same scenario as DailyTriggerBaselineTest, so the output can be compared to the baseline fixture.
    private void addScenarioEvents() {
        addEvent(FLAGGED_ORG, "rhel", "policies");
        addEvent(FLAGGED_ORG, "rhel", "advisor");
        addEvent(FLAGGED_ORG, "subscription-services", "errata");
        addEvent(FLAGGED_ORG, "subscription-services", "errata");
        addEvent(OTHER_ORG, "rhel", "policies");
        addEvent(OTHER_ORG, "rhel", "advisor");
        addEvent(OTHER_ORG, "subscription-services", "errata");
    }

    private void addEvent(String orgId, String bundleName, String applicationName) {
        String payload = TestHelpers.generatePayloadContent(orgId, bundleName, applicationName, "somePolicyId", "someHostId").toString();
        helpers.addEventEmailAggregation(orgId, bundleName, applicationName, LocalDateTime.now(UTC).minusHours(5), payload, false);
    }
}
