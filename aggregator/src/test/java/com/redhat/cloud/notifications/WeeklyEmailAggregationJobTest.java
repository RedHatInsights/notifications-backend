package com.redhat.cloud.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.helpers.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.DigestTriggerOrgConfig;
import com.redhat.cloud.notifications.models.DigestTriggerOrgConfigId;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import com.redhat.cloud.notifications.utils.AggregationTimeHelper;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.SubscriptionType.WEEKLY;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class WeeklyEmailAggregationJobTest {

    private static final String DEFAULT_WEEKLY_CRON = "30 0 * * 1";
    private static final String ORG_ID_1 = "weeklyOrgId1";
    private static final String ORG_ID_2 = "weeklyOrgId2";

    @Inject
    ResourceHelpers helpers;

    @Inject
    WeeklyEmailAggregationJob weeklyEmailAggregationJob;

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
        // Compute rounded execution time (same logic as NotificationsApp)
        executionTime = AggregationTimeHelper.computeReferenceTime(now);
        helpers.purgeEventAggregations();
        helpers.purgeDigestTriggerOrgConfig();
    }

    @AfterEach
    void tearDown() {
        helpers.purgeEventAggregations();
        helpers.purgeDigestTriggerOrgConfig();
        connector.sink(WeeklyEmailAggregationJob.EGRESS_CHANNEL).clear();
    }

    @Test
    void shouldSendWeeklyAggregationsToKafka() {
        // Setup: create digest trigger configs for two orgs
        createDigestTriggerConfig(ORG_ID_1, now.minusWeeks(1), now.minusHours(1));
        createDigestTriggerConfig(ORG_ID_2, now.minusWeeks(1), now.minusHours(1));

        // Add events for both orgs
        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "policies", "policy1", "host1", now.minusDays(3));
        addWeeklyEventEmailAggregation(ORG_ID_2, "rhel", "advisor", "rec1", "host2", now.minusDays(2));

        // Execute
        weeklyEmailAggregationJob.searchBuildAndSendAggregationCommands(executionTime);

        // Verify
        List<AggregationCommand> commands = getRecordsFromKafka();
        assertEquals(2, commands.size());

        checkWeeklyAggCommand(commands, ORG_ID_1, "rhel", "policies");
        checkWeeklyAggCommand(commands, ORG_ID_2, "rhel", "advisor");
    }

    @Test
    void shouldSendWeeklyAggregationsForMultipleApplicationsInBundle() {
        createDigestTriggerConfig(ORG_ID_1, now.minusWeeks(1), now.minusHours(1));

        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "policies", "policy1", "host1", now.minusDays(3));
        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "advisor", "rec1", "host2", now.minusDays(2));
        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "compliance", "rule1", "host3", now.minusDays(1));

        weeklyEmailAggregationJob.searchBuildAndSendAggregationCommands(executionTime);

        List<Action> actions = getActionRecordsFromKafka();
        assertEquals(1, actions.size(), "Should have one action for orgId/bundle");

        Action action = actions.get(0);
        assertEquals(WeeklyEmailAggregationJob.EVENT_TYPE_NAME, action.getEventType());
        assertEquals(ORG_ID_1, action.getOrgId());

        List<AggregationCommand> commands = extractAggregationCommandsFromAction(action);
        assertEquals(3, commands.size());

        checkWeeklyAggCommand(commands, ORG_ID_1, "rhel", "policies");
        checkWeeklyAggCommand(commands, ORG_ID_1, "rhel", "advisor");
        checkWeeklyAggCommand(commands, ORG_ID_1, "rhel", "compliance");
    }

    @Test
    void shouldNotSendWhenNextRunNotReached() {
        // Next run is in the future
        createDigestTriggerConfig(ORG_ID_1, now.minusWeeks(1), now.plusHours(1));

        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "policies", "policy1", "host1", now.minusDays(3));

        weeklyEmailAggregationJob.searchBuildAndSendAggregationCommands(executionTime);

        List<AggregationCommand> commands = getRecordsFromKafka();
        assertEquals(0, commands.size(), "Should not process when next_run hasn't been reached");
    }

    @Test
    void shouldNotSendWhenNoEventsAfterLastRun() {
        // Last run was yesterday
        createDigestTriggerConfig(ORG_ID_1, now.minusDays(1), now.minusHours(1));

        // Events are older than last run
        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "policies", "policy1", "host1", now.minusDays(2));

        weeklyEmailAggregationJob.searchBuildAndSendAggregationCommands(executionTime);

        List<AggregationCommand> commands = getRecordsFromKafka();
        assertEquals(0, commands.size(), "Should not process events older than last_run");
    }

    @Test
    void shouldFilterOutOldEvents() {
        createDigestTriggerConfig(ORG_ID_1, now.minusWeeks(4), now.minusHours(1));

        // Add very old event (more than 1 week)
        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "policies", "policy1", "host1", now.minusWeeks(2));
        // Add recent event
        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "policies", "policy2", "host2", now.minusDays(3));

        weeklyEmailAggregationJob.searchBuildAndSendAggregationCommands(executionTime);

        List<AggregationCommand> commands = getRecordsFromKafka();
        assertEquals(1, commands.size());

        AggregationCommand command = commands.get(0);
        // In outage scenario (last_run > 2 days), start should be yesterday (based on executionTime)
        LocalDateTime yesterday = executionTime.minusDays(1);
        assertTrue(command.getStart().isAfter(yesterday.minusMinutes(1)));
        assertTrue(command.getStart().isBefore(yesterday.plusMinutes(1)));
    }

    @Test
    void shouldUseYesterdayForOutageRecovery() {
        // Last run is 5 days ago - simulates outage
        createDigestTriggerConfig(ORG_ID_1, now.minusDays(5), now.minusHours(1));

        // Add event from yesterday (within outage recovery window)
        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "policies", "policy1", "host1", now.minusHours(25));

        weeklyEmailAggregationJob.searchBuildAndSendAggregationCommands(executionTime);

        List<AggregationCommand> commands = getRecordsFromKafka();
        assertEquals(1, commands.size());

        AggregationCommand command = commands.get(0);
        // Start should be yesterday (outage recovery), calculated from executionTime
        LocalDateTime yesterday = executionTime.minusDays(1);
        assertTrue(command.getStart().isAfter(yesterday.minusMinutes(1)));
        assertTrue(command.getStart().isBefore(yesterday.plusMinutes(1)));
    }

    @Test
    void shouldUseActualLastRunWhenRecent() {
        // Last run is 1 day ago - recent, no outage
        LocalDateTime recentLastRun = now.minusDays(1);
        createDigestTriggerConfig(ORG_ID_1, recentLastRun, now.minusHours(1));

        // Add event after last run
        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "policies", "policy1", "host1", now.minusHours(20));

        weeklyEmailAggregationJob.searchBuildAndSendAggregationCommands(executionTime);

        List<AggregationCommand> commands = getRecordsFromKafka();
        assertEquals(1, commands.size());

        AggregationCommand command = commands.get(0);
        // Should use actual last_run as start
        assertEquals(recentLastRun.withNano(0), command.getStart().withNano(0));
    }

    @Test
    void shouldUpdateLastRunAndNextRun() {
        createDigestTriggerConfig(ORG_ID_1, now.minusWeeks(1), now.minusHours(1));

        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "policies", "policy1", "host1", now.minusDays(3));

        weeklyEmailAggregationJob.searchBuildAndSendAggregationCommands(executionTime);

        DigestTriggerOrgConfig config = helpers.findDigestTriggerOrgConfig(ORG_ID_1, WEEKLY);
        assertNotNull(config);

        // Last run should be updated to the executionTime (which is already rounded)
        assertNotNull(config.getLastRun());
        assertEquals(executionTime.withNano(0), config.getLastRun().withNano(0),
            "Last run should match the execution time");

        // Next run should be calculated and in the future
        assertNotNull(config.getNextRun());
        assertTrue(config.getNextRun().isAfter(config.getLastRun()));
    }

    @Test
    void shouldCreateMissingDefaultConfig() {
        // Create event with subscription but no config
        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "policies", "policy1", "host1", now.minusDays(3));

        weeklyEmailAggregationJob.searchBuildAndSendAggregationCommands(executionTime);

        // Verify config was created
        DigestTriggerOrgConfig config = helpers.findDigestTriggerOrgConfig(ORG_ID_1, WEEKLY);
        assertNotNull(config);
        assertEquals(DEFAULT_WEEKLY_CRON, config.getCronExpression());
        assertNotNull(config.getLastRun());
        assertNotNull(config.getNextRun());
    }

    @Test
    void shouldProcessCorrectNumberOfPairs() {
        createDigestTriggerConfig(ORG_ID_1, now.minusWeeks(1), now.minusHours(1));

        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "policies", "policy1", "host1", now.minusDays(3));
        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "advisor", "rec1", "host2", now.minusDays(2));
        addWeeklyEventEmailAggregation(ORG_ID_1, "openshift", "cluster", "cluster1", "node1", now.minusDays(1));

        List<AggregationCommand> commands = weeklyEmailAggregationJob.processAggregateEmailsWithOrgPref(now, new CollectorRegistry());

        assertEquals(3, commands.size());
        Gauge pairsProcessed = weeklyEmailAggregationJob.getPairsProcessed();
        assertEquals(3.0, pairsProcessed.get());
    }

    @Test
    void shouldHandleMultipleOrgs() {
        createDigestTriggerConfig(ORG_ID_1, now.minusWeeks(1), now.minusHours(1));
        createDigestTriggerConfig(ORG_ID_2, now.minusWeeks(1), now.minusHours(1));

        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "policies", "policy1", "host1", now.minusDays(3));
        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "advisor", "rec1", "host2", now.minusDays(2));
        addWeeklyEventEmailAggregation(ORG_ID_2, "rhel", "policies", "policy2", "host3", now.minusDays(1));

        weeklyEmailAggregationJob.searchBuildAndSendAggregationCommands(executionTime);

        List<AggregationCommand> commands = getRecordsFromKafka();
        assertEquals(3, commands.size());

        List<String> orgIds = commands.stream()
            .map(AggregationCommand::getOrgId)
            .distinct()
            .collect(Collectors.toList());

        assertEquals(2, orgIds.size());
        assertTrue(orgIds.contains(ORG_ID_1));
        assertTrue(orgIds.contains(ORG_ID_2));
    }

    @Test
    void shouldVerifyEventTypeNameIsWeeklyAggregation() {
        createDigestTriggerConfig(ORG_ID_1, now.minusWeeks(1), now.minusHours(1));
        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "policies", "policy1", "host1", now.minusDays(3));

        weeklyEmailAggregationJob.searchBuildAndSendAggregationCommands(executionTime);

        List<Action> actions = getActionRecordsFromKafka();
        assertEquals(1, actions.size());
        assertEquals(WeeklyEmailAggregationJob.EVENT_TYPE_NAME, actions.get(0).getEventType());
        assertEquals("weekly-aggregation", actions.get(0).getEventType());
    }

    @Test
    void shouldHaveEmptyContext() {
        createDigestTriggerConfig(ORG_ID_1, now.minusWeeks(1), now.minusHours(1));
        addWeeklyEventEmailAggregation(ORG_ID_1, "rhel", "policies", "policy1", "host1", now.minusDays(3));

        weeklyEmailAggregationJob.searchBuildAndSendAggregationCommands(executionTime);

        List<Action> actions = getActionRecordsFromKafka();
        assertEquals(1, actions.size());

        Action action = actions.get(0);
        assertNotNull(action.getContext());
        assertTrue(action.getContext().getAdditionalProperties().isEmpty());
    }

    // Helper methods

    private void createDigestTriggerConfig(String orgId, LocalDateTime lastRun, LocalDateTime nextRun) {
        DigestTriggerOrgConfigId configId = new DigestTriggerOrgConfigId(orgId, WEEKLY);
        DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(configId, DEFAULT_WEEKLY_CRON);
        config.setLastRun(lastRun);
        config.setNextRun(nextRun);
        helpers.addDigestTriggerOrgConfig(config);
    }

    private com.redhat.cloud.notifications.models.Event addWeeklyEventEmailAggregation(String orgId, String bundleName, String applicationName, String policyId, String inventoryId, LocalDateTime created) {
        final String payload = TestHelpers.generatePayloadContent(orgId, bundleName, applicationName, policyId, inventoryId).toString();
        return helpers.addEventEmailAggregationWithSubscriptionType(orgId, bundleName, applicationName, created, payload, true, WEEKLY);
    }

    private List<AggregationCommand> getRecordsFromKafka() {
        List<AggregationCommand> aggregationCommands = new ArrayList<>();
        InMemorySink<String> results = connector.sink(WeeklyEmailAggregationJob.EGRESS_CHANNEL);
        for (Message message : results.received()) {
            Action action = Parser.decode(String.valueOf(message.getPayload()));
            aggregationCommands.addAll(extractAggregationCommandsFromAction(action));
        }
        return aggregationCommands;
    }

    private List<Action> getActionRecordsFromKafka() {
        InMemorySink<String> results = connector.sink(WeeklyEmailAggregationJob.EGRESS_CHANNEL);
        return results.received().stream()
            .map(message -> Parser.decode(String.valueOf(message.getPayload())))
            .toList();
    }

    private List<AggregationCommand> extractAggregationCommandsFromAction(Action action) {
        List<AggregationCommand> aggregationCommands = new ArrayList<>();
        for (Event event : action.getEvents()) {
            AggregationCommand aggCommand = objectMapper.convertValue(event.getPayload().getAdditionalProperties(), AggregationCommand.class);
            EventAggregationCriterion aggregationCriteria = objectMapper.convertValue(event.getPayload().getAdditionalProperties().get("aggregationKey"), EventAggregationCriterion.class);
            aggCommand.setAggregationKey(aggregationCriteria);
            aggregationCommands.add(aggCommand);
        }
        return aggregationCommands;
    }

    private void checkWeeklyAggCommand(List<AggregationCommand> commands, String orgId, String bundleName, String applicationName) {
        Application application = helpers.findApp(bundleName, applicationName);

        assertTrue(commands.stream().anyMatch(
            com -> orgId.equals(com.getAggregationKey().getOrgId()) &&
                application.getBundleId().equals(((EventAggregationCriterion) com.getAggregationKey()).getBundleId()) &&
                application.getId().equals(((EventAggregationCriterion) com.getAggregationKey()).getApplicationId()) &&
                WEEKLY.equals(com.getSubscriptionType())
        ));
    }
}
