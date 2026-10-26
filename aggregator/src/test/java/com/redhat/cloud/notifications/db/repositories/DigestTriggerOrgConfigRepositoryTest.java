package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.DigestTriggerOrgConfig;
import com.redhat.cloud.notifications.models.DigestTriggerOrgConfigId;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class DigestTriggerOrgConfigRepositoryTest {

    private static final String ORG_ID = "weekly-org-123";
    private static final String ORG_ID_2 = "weekly-org-456";
    private static final String BUNDLE_NAME = "weekly-bundle";
    private static final String APP_NAME = "weekly-app";
    private static final JsonObject PAYLOAD1 = new JsonObject("{\"weekly\":\"data1\"}");
    private static final JsonObject PAYLOAD2 = new JsonObject("{\"weekly\":\"data2\"}");
    private static final String DEFAULT_WEEKLY_CRON = "30 0 * * 1"; // Every Monday at 00:30 UTC

    @Inject
    DigestTriggerOrgConfigRepository digestTriggerOrgConfigRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    @AfterEach
    void afterEach() {
        resourceHelpers.purgeDigestTriggerOrgConfig();
        resourceHelpers.purgeEndpoints();
        resourceHelpers.purgeEventAggregations();
    }

    @Test
    void testGetApplicationsWithPendingWeeklyAggregation() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextRun = now; // Next run matches current time exactly

        // Create digest trigger config
        DigestTriggerOrgConfigId configId = new DigestTriggerOrgConfigId(ORG_ID, SubscriptionType.WEEKLY);
        DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(configId, DEFAULT_WEEKLY_CRON);
        config.setLastRun(now.minusWeeks(1));
        config.setNextRun(nextRun);
        resourceHelpers.addDigestTriggerOrgConfig(config);

        // Add events
        Event event1 = resourceHelpers.addEventEmailAggregationWithSubscriptionType(
            ORG_ID, BUNDLE_NAME, APP_NAME, now.minusDays(3), PAYLOAD1.toString(), true, SubscriptionType.WEEKLY);
        Event event2 = resourceHelpers.addEventEmailAggregationWithSubscriptionType(
            ORG_ID, BUNDLE_NAME, APP_NAME, now.minusDays(2), PAYLOAD2.toString(), true, SubscriptionType.WEEKLY);

        // Query for pending aggregations
        List<AggregationCommand> commands = digestTriggerOrgConfigRepository.getApplicationsWithPendingWeeklyAggregation(now);

        assertEquals(1, commands.size());
        AggregationCommand command = commands.get(0);
        assertEquals(ORG_ID, command.getOrgId());
        assertEquals(SubscriptionType.WEEKLY, command.getSubscriptionType());
        assertEquals(BUNDLE_NAME, command.getAggregationKey().getBundle());
        assertEquals(APP_NAME, command.getAggregationKey().getApplication());

        resourceHelpers.deleteEvent(event1);
        resourceHelpers.deleteEvent(event2);
    }

    @Test
    void testGetApplicationsWithPendingWeeklyAggregationNoEventsAfterLastRun() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextRun = now;

        // Create digest trigger config with recent last run
        DigestTriggerOrgConfigId configId = new DigestTriggerOrgConfigId(ORG_ID, SubscriptionType.WEEKLY);
        DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(configId, DEFAULT_WEEKLY_CRON);
        config.setLastRun(now.minusDays(1)); // Last run was yesterday
        config.setNextRun(nextRun);
        resourceHelpers.addDigestTriggerOrgConfig(config);

        // Add event BEFORE last run - should not be included
        Event event1 = resourceHelpers.addEventEmailAggregationWithSubscriptionType(
            ORG_ID, BUNDLE_NAME, APP_NAME, now.minusDays(2), PAYLOAD1.toString(), true, SubscriptionType.WEEKLY);

        // Query for pending aggregations
        List<AggregationCommand> commands = digestTriggerOrgConfigRepository.getApplicationsWithPendingWeeklyAggregation(now);

        // Should be empty because all events are before last run
        assertEquals(0, commands.size());

        resourceHelpers.deleteEvent(event1);
    }

    @Test
    void testGetApplicationsWithPendingWeeklyAggregationNextRunNotReached() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextRun = now.plusHours(1); // Next run is in the future

        // Create digest trigger config
        DigestTriggerOrgConfigId configId = new DigestTriggerOrgConfigId(ORG_ID, SubscriptionType.WEEKLY);
        DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(configId, DEFAULT_WEEKLY_CRON);
        config.setLastRun(now.minusWeeks(1));
        config.setNextRun(nextRun);
        resourceHelpers.addDigestTriggerOrgConfig(config);

        // Add events
        Event event1 = resourceHelpers.addEventEmailAggregationWithSubscriptionType(
            ORG_ID, BUNDLE_NAME, APP_NAME, now.minusDays(3), PAYLOAD1.toString(), true, SubscriptionType.WEEKLY);

        // Query for pending aggregations
        List<AggregationCommand> commands = digestTriggerOrgConfigRepository.getApplicationsWithPendingWeeklyAggregation(now);

        // Should be empty because next run hasn't been reached yet
        assertEquals(0, commands.size());

        resourceHelpers.deleteEvent(event1);
    }

    @Test
    void testCreateMissingDefaultWeeklyConfig() {
        // Create a weekly subscription but no config yet
        resourceHelpers.addEventEmailAggregationWithSubscriptionType(
            ORG_ID, BUNDLE_NAME, APP_NAME, LocalDateTime.now(ZoneOffset.UTC).minusDays(1),
            PAYLOAD1.toString(), true, SubscriptionType.WEEKLY);

        // Create missing configs
        digestTriggerOrgConfigRepository.createMissingDefaultWeeklyConfig(DEFAULT_WEEKLY_CRON);

        // Verify config was created
        DigestTriggerOrgConfig config = resourceHelpers.findDigestTriggerOrgConfig(ORG_ID, SubscriptionType.WEEKLY);
        assertNotNull(config);
        assertEquals(ORG_ID, config.getId().orgId);
        assertEquals(SubscriptionType.WEEKLY, config.getId().subscriptionType);
        assertEquals(DEFAULT_WEEKLY_CRON, config.getCronExpression());
        assertNull(config.getLastRun());
        assertNotNull(config.getNextRun());
    }

    @Test
    void testUpdateLastAndNextRunForWeekly() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime previousMonday = now.minusWeeks(1);

        // Create digest trigger config - simulating last successful run was previous Monday
        DigestTriggerOrgConfigId configId = new DigestTriggerOrgConfigId(ORG_ID, SubscriptionType.WEEKLY);
        DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(configId, DEFAULT_WEEKLY_CRON);
        config.setLastRun(previousMonday); // Last successful run was previous Monday
        config.setNextRun(now); // Next run is now (matches execution time)
        resourceHelpers.addDigestTriggerOrgConfig(config);

        // Update last and next run
        List<String> orgIds = List.of(ORG_ID);
        digestTriggerOrgConfigRepository.updateLastAndNextRunForWeekly(orgIds, now);

        // Verify updates
        DigestTriggerOrgConfig updatedConfig = resourceHelpers.findDigestTriggerOrgConfig(ORG_ID, SubscriptionType.WEEKLY);
        assertNotNull(updatedConfig);
        // last_run should be calculated as next scheduled time from previousMonday
        // which should be approximately "now" (this Monday)
        assertNotNull(updatedConfig.getLastRun());
        assertTrue(updatedConfig.getLastRun().isAfter(previousMonday));
        // next_run should be calculated from current execution time
        assertNotNull(updatedConfig.getNextRun());
        assertTrue(updatedConfig.getNextRun().isAfter(now));
    }

    @Test
    void testMultipleOrgsWithPendingAggregations() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextRun = now;

        // Create configs for two orgs
        DigestTriggerOrgConfigId configId1 = new DigestTriggerOrgConfigId(ORG_ID, SubscriptionType.WEEKLY);
        DigestTriggerOrgConfig config1 = new DigestTriggerOrgConfig(configId1, DEFAULT_WEEKLY_CRON);
        config1.setLastRun(now.minusWeeks(1));
        config1.setNextRun(nextRun);
        resourceHelpers.addDigestTriggerOrgConfig(config1);

        DigestTriggerOrgConfigId configId2 = new DigestTriggerOrgConfigId(ORG_ID_2, SubscriptionType.WEEKLY);
        DigestTriggerOrgConfig config2 = new DigestTriggerOrgConfig(configId2, DEFAULT_WEEKLY_CRON);
        config2.setLastRun(now.minusWeeks(1));
        config2.setNextRun(nextRun);
        resourceHelpers.addDigestTriggerOrgConfig(config2);

        // Add events for both orgs
        Event event1 = resourceHelpers.addEventEmailAggregationWithSubscriptionType(
            ORG_ID, BUNDLE_NAME, APP_NAME, now.minusDays(3), PAYLOAD1.toString(), true, SubscriptionType.WEEKLY);
        Event event2 = resourceHelpers.addEventEmailAggregationWithSubscriptionType(
            ORG_ID_2, BUNDLE_NAME, APP_NAME, now.minusDays(2), PAYLOAD2.toString(), true, SubscriptionType.WEEKLY);

        // Query for pending aggregations
        List<AggregationCommand> commands = digestTriggerOrgConfigRepository.getApplicationsWithPendingWeeklyAggregation(now);

        assertEquals(2, commands.size());
        List<String> orgIds = commands.stream()
            .map(AggregationCommand::getOrgId)
            .collect(Collectors.toList());
        assertTrue(orgIds.contains(ORG_ID));
        assertTrue(orgIds.contains(ORG_ID_2));

        resourceHelpers.deleteEvent(event1);
        resourceHelpers.deleteEvent(event2);
    }

    @Test
    void testOldEventsAreFilteredOut() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextRun = now;

        // Create digest trigger config
        DigestTriggerOrgConfigId configId = new DigestTriggerOrgConfigId(ORG_ID, SubscriptionType.WEEKLY);
        DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(configId, DEFAULT_WEEKLY_CRON);
        config.setLastRun(now.minusWeeks(1));
        config.setNextRun(nextRun);
        resourceHelpers.addDigestTriggerOrgConfig(config);

        // Add a very old event (more than 1 week ago) - should be filtered
        Event oldEvent = resourceHelpers.addEventEmailAggregationWithSubscriptionType(
            ORG_ID, BUNDLE_NAME, APP_NAME, now.minusWeeks(2), PAYLOAD1.toString(), true, SubscriptionType.WEEKLY);

        // Add a recent event
        Event recentEvent = resourceHelpers.addEventEmailAggregationWithSubscriptionType(
            ORG_ID, BUNDLE_NAME, APP_NAME, now.minusDays(3), PAYLOAD2.toString(), true, SubscriptionType.WEEKLY);

        // Query for pending aggregations
        List<AggregationCommand> commands = digestTriggerOrgConfigRepository.getApplicationsWithPendingWeeklyAggregation(now);

        // Should only find recent event
        assertEquals(1, commands.size());
        AggregationCommand command = commands.get(0);

        // The start date should be capped at 1 week ago, not 2 weeks ago
        assertTrue(command.getStart().isAfter(now.minusWeeks(1).minusMinutes(1)));

        resourceHelpers.deleteEvent(oldEvent);
        resourceHelpers.deleteEvent(recentEvent);
    }

    @Test
    void testOldLastRunUsedDirectlyAsStartTime() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextRun = now;
        LocalDateTime oldLastRun = now.minusDays(9); // 9 days ago

        // Create digest trigger config with old last_run
        DigestTriggerOrgConfigId configId = new DigestTriggerOrgConfigId(ORG_ID, SubscriptionType.WEEKLY);
        DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(configId, DEFAULT_WEEKLY_CRON);
        config.setLastRun(oldLastRun); // 9 days ago
        config.setNextRun(nextRun);
        resourceHelpers.addDigestTriggerOrgConfig(config);

        // Add events from different times
        Event veryOldEvent = resourceHelpers.addEventEmailAggregationWithSubscriptionType(
            ORG_ID, BUNDLE_NAME, APP_NAME, now.minusDays(4), PAYLOAD1.toString(), true, SubscriptionType.WEEKLY);
        Event yesterdayEvent = resourceHelpers.addEventEmailAggregationWithSubscriptionType(
            ORG_ID, BUNDLE_NAME, APP_NAME, now.minusHours(25), PAYLOAD2.toString(), true, SubscriptionType.WEEKLY);

        // Query for pending aggregations
        List<AggregationCommand> commands = digestTriggerOrgConfigRepository.getApplicationsWithPendingWeeklyAggregation(now);

        assertEquals(1, commands.size());
        AggregationCommand command = commands.get(0);

        // With the new simple logic: start = lastRun (even if old), not "yesterday" for outage recovery
        // This ensures consistent event windows regardless of how long ago last_run was
        assertEquals(oldLastRun.withNano(0), command.getStart().withNano(0),
            "Start time should be the actual last_run value, even if it's old");

        resourceHelpers.deleteEvent(veryOldEvent);
        resourceHelpers.deleteEvent(yesterdayEvent);
    }

    @Test
    void testRecentLastRunUsesActualLastRun() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextRun = now;
        LocalDateTime recentLastRun = now.minusHours(30); // Within 2 days

        // Create digest trigger config with recent last_run
        DigestTriggerOrgConfigId configId = new DigestTriggerOrgConfigId(ORG_ID, SubscriptionType.WEEKLY);
        DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(configId, DEFAULT_WEEKLY_CRON);
        config.setLastRun(recentLastRun);
        config.setNextRun(nextRun);
        resourceHelpers.addDigestTriggerOrgConfig(config);

        // Add event after last_run
        Event recentEvent = resourceHelpers.addEventEmailAggregationWithSubscriptionType(
            ORG_ID, BUNDLE_NAME, APP_NAME, now.minusHours(20), PAYLOAD1.toString(), true, SubscriptionType.WEEKLY);

        // Query for pending aggregations
        List<AggregationCommand> commands = digestTriggerOrgConfigRepository.getApplicationsWithPendingWeeklyAggregation(now);

        assertEquals(1, commands.size());
        AggregationCommand command = commands.get(0);

        // Should use actual last_run as start (not yesterday)
        assertEquals(recentLastRun.withNano(0), command.getStart().withNano(0));

        resourceHelpers.deleteEvent(recentEvent);
    }

    @Test
    void testUpdateLastAndNextRunForWeeklyAfterRetries() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // Simulate a retry scenario:
        // - Original schedule: Monday 00:30
        // - Failed on Monday and Tuesday (next_run was updated each time, but last_run stayed unchanged)
        // - Succeeding on Wednesday
        LocalDateTime previousMonday = now.minusWeeks(1).minusDays(2); // Previous week's Monday
        LocalDateTime thisMonday = previousMonday.plusWeeks(1); // This week's Monday (the scheduled time we missed)
        LocalDateTime currentExecutionTime = now; // Wednesday (today)

        // Create config simulating the state after Monday and Tuesday failures:
        // - last_run is still at Previous Monday (unchanged during retries)
        // - next_run was updated to Wednesday by retry logic
        DigestTriggerOrgConfigId configId = new DigestTriggerOrgConfigId(ORG_ID, SubscriptionType.WEEKLY);
        DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(configId, DEFAULT_WEEKLY_CRON);
        config.setLastRun(previousMonday); // Still at previous Monday (not updated during retries)
        config.setNextRun(currentExecutionTime); // Wednesday (updated by failed retry logic)
        resourceHelpers.addDigestTriggerOrgConfig(config);

        // Update as if processing succeeded on Wednesday
        List<String> successfulOrgIds = List.of(ORG_ID);
        digestTriggerOrgConfigRepository.updateLastAndNextRunForWeekly(successfulOrgIds, currentExecutionTime);

        // Verify the update
        DigestTriggerOrgConfig updatedConfig = resourceHelpers.findDigestTriggerOrgConfig(ORG_ID, SubscriptionType.WEEKLY);
        assertNotNull(updatedConfig);

        // CRITICAL: last_run should be calculated as the next scheduled time from previousMonday
        // This gives us "this Monday" - the original scheduled time we were supposed to run at
        // This maintains the correct event window: events from this Monday to next Monday
        assertNotNull(updatedConfig.getLastRun());
        assertTrue(updatedConfig.getLastRun().isAfter(previousMonday),
            "last_run should be after previous Monday");
        assertTrue(updatedConfig.getLastRun().isBefore(currentExecutionTime) ||
                   updatedConfig.getLastRun().isEqual(currentExecutionTime),
            "last_run should be this Monday (scheduled time), not after Wednesday");

        // next_run should be calculated from Wednesday to get next Monday
        assertNotNull(updatedConfig.getNextRun());
        assertTrue(updatedConfig.getNextRun().isAfter(currentExecutionTime),
            "next_run should be after current execution time (Wednesday)");
    }

    @Test
    void testUpdateNextRunForFailedWeekly() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime originalNextRun = now.minusHours(1);

        // Create digest trigger config
        DigestTriggerOrgConfigId configId = new DigestTriggerOrgConfigId(ORG_ID, SubscriptionType.WEEKLY);
        DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(configId, DEFAULT_WEEKLY_CRON);
        config.setLastRun(now.minusWeeks(1));
        config.setNextRun(originalNextRun);
        resourceHelpers.addDigestTriggerOrgConfig(config);

        // Create second org
        DigestTriggerOrgConfigId configId2 = new DigestTriggerOrgConfigId(ORG_ID_2, SubscriptionType.WEEKLY);
        DigestTriggerOrgConfig config2 = new DigestTriggerOrgConfig(configId2, DEFAULT_WEEKLY_CRON);
        config2.setLastRun(now.minusWeeks(1));
        config2.setNextRun(originalNextRun);
        resourceHelpers.addDigestTriggerOrgConfig(config2);

        // Update failed orgs - should set next_run to tomorrow at the same time
        List<String> failedOrgIds = List.of(ORG_ID, ORG_ID_2);
        digestTriggerOrgConfigRepository.updateNextRunForFailedWeekly(failedOrgIds, now);

        // Verify next_run was updated to tomorrow
        DigestTriggerOrgConfig updatedConfig = resourceHelpers.findDigestTriggerOrgConfig(ORG_ID, SubscriptionType.WEEKLY);
        assertNotNull(updatedConfig);
        assertNotNull(updatedConfig.getNextRun());

        // Next run should be tomorrow at the same time
        LocalDateTime expectedNextRun = now.plusDays(1);
        assertEquals(expectedNextRun.withNano(0), updatedConfig.getNextRun().withNano(0));

        // Verify last_run was NOT updated (should remain the same)
        assertEquals(now.minusWeeks(1).withNano(0), updatedConfig.getLastRun().withNano(0));

        // Verify second org was also updated
        DigestTriggerOrgConfig updatedConfig2 = resourceHelpers.findDigestTriggerOrgConfig(ORG_ID_2, SubscriptionType.WEEKLY);
        assertNotNull(updatedConfig2);
        assertEquals(expectedNextRun.withNano(0), updatedConfig2.getNextRun().withNano(0));
    }

    @Test
    void testFirstRunUsesScheduledTimeMinusSevenDaysAsStart() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // Create config with lastRun = null (first run scenario)
        DigestTriggerOrgConfigId configId = new DigestTriggerOrgConfigId(ORG_ID, SubscriptionType.WEEKLY);
        DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(configId, DEFAULT_WEEKLY_CRON);
        config.setLastRun(null); // First run - no previous last_run
        config.setNextRun(now); // Running now
        resourceHelpers.addDigestTriggerOrgConfig(config);

        // Add event from the past week
        Event event = resourceHelpers.addEventEmailAggregationWithSubscriptionType(
            ORG_ID, BUNDLE_NAME, APP_NAME, now.minusDays(3), PAYLOAD1.toString(), true, SubscriptionType.WEEKLY);

        // Query for pending aggregations
        List<AggregationCommand> commands = digestTriggerOrgConfigRepository.getApplicationsWithPendingWeeklyAggregation(now);

        assertEquals(1, commands.size());
        AggregationCommand command = commands.get(0);

        // For first run (lastRun is null):
        // - scheduledTime = calculateNextRun(cron, null) - this will give us a scheduled time
        // - start = scheduledTime - 7 days (one week of events)
        // - end = scheduledTime

        LocalDateTime commandStart = command.getStart();
        LocalDateTime commandEnd = command.getEnd();

        assertNotNull(commandStart);
        assertNotNull(commandEnd);

        // Start should be 7 days before end
        long daysBetween = java.time.Duration.between(commandStart, commandEnd).toDays();
        assertEquals(7, daysBetween,
            "For first run, start should be 7 days before end (scheduledTime - 7 days)");

        // End should be a reasonable scheduled time (could be in future if today is not Monday at 00:30)
        // The key is that start = end - 7 days
        assertNotNull(commandEnd, "End time should not be null");

        resourceHelpers.deleteEvent(event);
    }

    @Test
    void testAggregationCommandUsesScheduledTimeNotExecutionTimeDuringRetries() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // Simulate retry scenario:
        // - Scheduled for Monday 00:30 (cron: "30 0 * * 1")
        // - Last successful run: some time in the past
        // - Failed on scheduled day and next day (next_run updated each time, but last_run unchanged)
        // - Succeeding later
        LocalDateTime lastRun = now.minusDays(10); // 10 days ago
        LocalDateTime executionTime = now; // Today

        // Create config simulating state after failures:
        // - last_run is 10 days ago (unchanged during retries)
        // - next_run is today (updated by failed retry logic)
        DigestTriggerOrgConfigId configId = new DigestTriggerOrgConfigId(ORG_ID, SubscriptionType.WEEKLY);
        DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(configId, DEFAULT_WEEKLY_CRON);
        config.setLastRun(lastRun); // 10 days ago (not updated during retries)
        config.setNextRun(executionTime); // Today (updated by failed retry logic)
        resourceHelpers.addDigestTriggerOrgConfig(config);

        // Add event from the past week
        Event event = resourceHelpers.addEventEmailAggregationWithSubscriptionType(
            ORG_ID, BUNDLE_NAME, APP_NAME, now.minusDays(3), PAYLOAD1.toString(), true, SubscriptionType.WEEKLY);

        // Query for pending aggregations (executing today)
        List<AggregationCommand> commands = digestTriggerOrgConfigRepository.getApplicationsWithPendingWeeklyAggregation(executionTime);

        assertEquals(1, commands.size());
        AggregationCommand command = commands.get(0);

        // CRITICAL ASSERTION: The aggregation command's end time should be calculated from the cron expression,
        // NOT the execution time
        // This ensures the event window boundary is the scheduled time, even during retries

        LocalDateTime commandEnd = command.getEnd();
        assertNotNull(commandEnd);

        // The key assertion: end time must be BEFORE execution time
        // This proves it's using the scheduled time (calculated from lastRun via cron),
        // not the execution time
        assertTrue(commandEnd.isBefore(executionTime),
            String.format("CRITICAL: End time (%s) must be BEFORE execution time (%s). " +
                "This proves the aggregation uses scheduled time from cron, not execution time. " +
                "If end time equals execution time, event windows would be incorrect during retries.",
                commandEnd, executionTime));

        // End time should also be after lastRun (it's the next scheduled time from lastRun)
        assertTrue(commandEnd.isAfter(lastRun),
            String.format("End time (%s) should be after last_run (%s)", commandEnd, lastRun));

        resourceHelpers.deleteEvent(event);
    }
}
