package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.helpers.AggregationTimeHelper;
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
        LocalDateTime nextRun = now.minusHours(1); // Next run was 1 hour ago, so it should trigger

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
        LocalDateTime nextRun = now.minusHours(1);

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
        digestTriggerOrgConfigRepository.createMissingDefaultWeeklyConfig(DEFAULT_WEEKLY_CRON, AggregationTimeHelper.computeReferenceTime());

        // Verify config was created
        DigestTriggerOrgConfig config = resourceHelpers.findDigestTriggerOrgConfig(ORG_ID, SubscriptionType.WEEKLY);
        assertNotNull(config);
        assertEquals(ORG_ID, config.getId().orgId);
        assertEquals(SubscriptionType.WEEKLY, config.getId().subscriptionType);
        assertEquals(DEFAULT_WEEKLY_CRON, config.getCronExpression());
        assertNotNull(config.getLastRun());
        assertNotNull(config.getNextRun());
    }

    @Test
    void testUpdateLastAndNextRunForWeekly() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // Create digest trigger config
        DigestTriggerOrgConfigId configId = new DigestTriggerOrgConfigId(ORG_ID, SubscriptionType.WEEKLY);
        DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(configId, DEFAULT_WEEKLY_CRON);
        config.setLastRun(now.minusWeeks(2));
        config.setNextRun(now.minusWeeks(1));
        resourceHelpers.addDigestTriggerOrgConfig(config);

        // Update last and next run
        List<String> orgIds = List.of(ORG_ID);
        digestTriggerOrgConfigRepository.updateLastAndNextRunForWeekly(orgIds, now);

        // Verify updates
        DigestTriggerOrgConfig updatedConfig = resourceHelpers.findDigestTriggerOrgConfig(ORG_ID, SubscriptionType.WEEKLY);
        assertNotNull(updatedConfig);
        // Compare with truncated nanoseconds to avoid precision issues
        assertEquals(now.withNano(0), updatedConfig.getLastRun().withNano(0));
        assertNotNull(updatedConfig.getNextRun());
        assertTrue(updatedConfig.getNextRun().isAfter(now));
    }

    @Test
    void testMultipleOrgsWithPendingAggregations() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextRun = now.minusHours(1);

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
        LocalDateTime nextRun = now.minusHours(1);

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
    void testOutageRecoveryStartsFromYesterday() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextRun = now.minusHours(1);

        // Create digest trigger config with last_run older than 8 days (outage scenario for weekly)
        DigestTriggerOrgConfigId configId = new DigestTriggerOrgConfigId(ORG_ID, SubscriptionType.WEEKLY);
        DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(configId, DEFAULT_WEEKLY_CRON);
        config.setLastRun(now.minusDays(9)); // 9 days ago - outage
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

        // In outage recovery (last_run > 8 days old for weekly), start should be yesterday
        LocalDateTime yesterday = now.minusDays(1);
        assertTrue(command.getStart().isAfter(yesterday.minusMinutes(1)));
        assertTrue(command.getStart().isBefore(yesterday.plusMinutes(1)));

        resourceHelpers.deleteEvent(veryOldEvent);
        resourceHelpers.deleteEvent(yesterdayEvent);
    }

    @Test
    void testRecentLastRunUsesActualLastRun() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextRun = now.minusHours(1);
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
}
