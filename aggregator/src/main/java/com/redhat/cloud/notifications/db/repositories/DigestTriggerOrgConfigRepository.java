package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.DigestTriggerOrgConfig;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import com.redhat.cloud.notifications.utils.DigestCronUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.SubscriptionType.WEEKLY;
import static java.time.ZoneOffset.UTC;
import static java.util.stream.Collectors.toList;

@ApplicationScoped
public class DigestTriggerOrgConfigRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    DigestCronUtils digestCronUtils;

    /**
     * Get applications with pending weekly aggregation where the next run time has been reached.
     * Results will be limited to events from the last week.
     *
     * IMPORTANT: The aggregation command time window is calculated to maintain correctness during retries.
     *
     * Example retry scenario (cron: "Every Monday at 08:30"):
     * - Last successful run: Previous Monday 08:30
     * - Monday FAILS, Tuesday FAILS, Wednesday SUCCEEDS
     * - On Wednesday: lastRun = Previous Monday (unchanged during retries)
     * - scheduledTime = calculateNextRun(cron, Previous Monday) = THIS Monday 08:30
     * - Aggregation command: start = Previous Monday (lastRun), end = THIS Monday (scheduledTime, not Wednesday!)
     * - Event window: Previous Monday → THIS Monday ✓
     *
     * For first run (lastRun is null):
     * - start = scheduledTime - 7 days
     * - end = scheduledTime
     * - Event window: one week of events
     */
    public List<AggregationCommand> getApplicationsWithPendingWeeklyAggregation(LocalDateTime now) {
        List<Object[]> records;
        final LocalDateTime startTime = LocalDateTime.now(UTC);

        String query = "SELECT DISTINCT ev.orgId, ev.bundleId, ev.applicationId, dtc.lastRun, bu.name, ap.name, dtc.cronExpression FROM Event ev " +
            "join Application ap on ev.applicationId = ap.id join Bundle bu on ev.bundleId = bu.id " +
            "join DigestTriggerOrgConfig dtc on ev.orgId = dtc.id.orgId WHERE " +
            // Filter on WEEKLY subscription type
            "dtc.id.subscriptionType = :subscriptionType " +
            // Check that at least one user of the org subscribed for weekly digest with this event type
            "AND EXISTS (SELECT 1 FROM EventTypeEmailSubscription es where ev.orgId = es.id.orgId and es.id.subscriptionType = 'WEEKLY' and es.eventType = ev.eventType and es.subscribed is true) " +
            // Check for linked email integration linked to this event type (to honor legacy mechanism)
            "AND EXISTS (SELECT 1 FROM Endpoint ep, EndpointEventType eet where (ev.orgId = ep.orgId or ep.orgId is null) and ep.compositeType.type = 'EMAIL_SUBSCRIPTION' and eet.eventType = ev.eventType and eet.endpoint = ep) " +
            // Filter on new events since the latest run of this org aggregation, and not older than nextRun - one week
            "AND (ev.created > dtc.lastRun OR dtc.lastRun is null) AND ev.created > (dtc.nextRun - 7 day) AND ev.created <= :now " +
            // Filter on orgs where next run time matches exactly (rounded to 15-minute intervals)
            "AND dtc.nextRun = :now";

        Query hqlQuery = entityManager.createQuery(query)
            .setParameter("subscriptionType", WEEKLY)
            .setParameter("now", now);

        records = hqlQuery.getResultList();

        Duration duration = Duration.between(startTime, LocalDateTime.now(UTC));
        Log.infof("Fetching weekly aggregations with pending aggregations with at least one subscriber took %d s for %d aggregations", duration.toSeconds(), records.size());

        return records.stream()
            .map(emailAggregationRecord -> {
                LocalDateTime lastRun = (LocalDateTime) emailAggregationRecord[3];
                String cronExpression = (String) emailAggregationRecord[6];

                // CRITICAL: Calculate the scheduled time (not execution time) for the event window end
                // This ensures correct time windows even during retries
                // Example: If lastRun = Previous Monday, this calculates THIS Monday (the scheduled time)
                // For first run (lastRun is null), use 'now' as the base to calculate the scheduled time
                LocalDateTime baseTime = lastRun != null ? lastRun : now;
                LocalDateTime scheduledTime = digestCronUtils.calculateNextRun(cronExpression, baseTime);

                // Start time calculation:
                // - If lastRun exists: use it (events from last run to scheduled time)
                // - If lastRun is null (first run): use scheduledTime - 7 days (one week of events)
                // This matches the query filter: ev.created > lastRun OR ev.created > (nextRun - 7 day)
                LocalDateTime start = lastRun != null ? lastRun : scheduledTime.minusDays(7);

                return new AggregationCommand(
                    new EventAggregationCriterion(
                        (String) emailAggregationRecord[0],     // Org id
                        (UUID) emailAggregationRecord[1],       // bundle id
                        (UUID) emailAggregationRecord[2],       // application id
                        (String) emailAggregationRecord[4],     // bundle name
                        (String) emailAggregationRecord[5]),    // application name
                    start,              // lastRun (if exists) or scheduledTime - 7 days
                    scheduledTime,      // Scheduled time (not execution time!)
                    WEEKLY
                );
            })
            .collect(toList());
    }

    /**
     * Creates digest_trigger_org_config entries with a last run date of one week ago,
     * for orgs with at least one subscriber to weekly digest.
     *
     * @param defaultCronExpression the default cron expression for weekly aggregation
     */
    @Transactional
    public void createMissingDefaultWeeklyConfig(String defaultCronExpression) {
        // Calculate next run based on cron expression
        LocalDateTime initialNextRun = digestCronUtils.computeNextRun(defaultCronExpression);

        String query = "INSERT INTO digest_trigger_org_config (org_id, subscription_type, cron_expression, next_run) " +
            "SELECT DISTINCT(es.org_id), 'WEEKLY', CAST(:cronExpression as varchar), CAST(:nextRun as timestamp without time zone) " +
            "FROM email_subscriptions es where es.subscription_type='WEEKLY' and es.subscribed = true AND " +
            "NOT EXISTS (SELECT 1 FROM digest_trigger_org_config dtc WHERE es.org_id = dtc.org_id AND dtc.subscription_type = 'WEEKLY')";

        int createdEntries = entityManager.createNativeQuery(query)
            .setParameter("cronExpression", defaultCronExpression)
            .setParameter("nextRun", initialNextRun)
            .executeUpdate();

        Log.infof("Default weekly digest configuration created for %d organizations", createdEntries);
    }

    /**
     * Updates last_run and next_run for the given org IDs with WEEKLY subscription type.
     *
     * CRITICAL: This method handles both normal executions and retry scenarios correctly.
     *
     * Example retry scenario (cron: "Every Monday at 08:30"):
     * - Previous successful run: Previous Monday 08:30
     * - Monday 08:30: FAILS → last_run stays Previous Monday, next_run updated to Tuesday 08:30
     * - Tuesday 08:30: FAILS → last_run still Previous Monday, next_run updated to Wednesday 08:30
     * - Wednesday 08:30: SUCCEEDS → this method is called
     *
     * At this point:
     * - config.getLastRun() = Previous Monday 08:30 (unchanged during retries)
     * - currentExecutionTime = Wednesday 08:30
     *
     * What we calculate:
     * - lastRun = calculateNextRun(cron, Previous Monday) = THIS Monday 08:30 (the scheduled time we missed!)
     * - nextRun = calculateNextRun(cron, Wednesday) = NEXT Monday 08:30
     *
     * Result:
     * - Event window on next Monday: THIS Monday 08:30 → NEXT Monday 08:30 ✓
     * - Events from Previous Monday → THIS Monday were already processed on Wednesday ✓
     * - No gaps, no duplication ✓
     *
     * @param orgIdsToUpdate list of org IDs to update
     * @param currentExecutionTime the current execution time
     */
    @Transactional
    public void updateLastAndNextRunForWeekly(List<String> orgIdsToUpdate, LocalDateTime currentExecutionTime) {
        if (orgIdsToUpdate.isEmpty()) {
            return;
        }

        // Retrieve cron expressions for the orgs that need updating
        String selectQuery = "SELECT dtc FROM DigestTriggerOrgConfig dtc WHERE dtc.id.orgId IN :orgIds AND dtc.id.subscriptionType = :subscriptionType";
        List<DigestTriggerOrgConfig> configs = entityManager.createQuery(selectQuery, DigestTriggerOrgConfig.class)
            .setParameter("orgIds", orgIdsToUpdate)
            .setParameter("subscriptionType", WEEKLY)
            .getResultList();

        int updated = 0;
        int skipped = 0;
        for (DigestTriggerOrgConfig config : configs) {
            try {
                // Calculate next_run from current execution time (normal case or retry)
                LocalDateTime nextRun = digestCronUtils.calculateNextRun(config.getCronExpression(), currentExecutionTime);

                // CRITICAL: Calculate last_run from the PREVIOUS last_run value
                // This gives us the original scheduled time, even after retries
                // Example: If last_run was "Previous Monday", this calculates "THIS Monday"
                LocalDateTime lastRun = digestCronUtils.calculateNextRun(config.getCronExpression(), config.getLastRun());

                config.setLastRun(lastRun);
                config.setNextRun(nextRun);
                updated++;
            } catch (IllegalArgumentException e) {
                Log.errorf(e, "Failed to calculate next run for org %s with cron expression: %s. This org will not receive weekly digests until the cron expression is fixed.",
                    config.getId().orgId, config.getCronExpression());
                skipped++;
            }
        }

        if (skipped > 0) {
            Log.warnf("Last run and next run updated for %d organizations, skipped %d organizations due to invalid cron expressions", updated, skipped);
        } else {
            Log.infof("Last run and next run updated for %d organizations", updated);
        }
    }

    /**
     * Updates next_run for failed weekly aggregations to retry tomorrow at the same hour.
     * This prevents failed aggregations from retrying every 15 minutes.
     *
     * IMPORTANT: This method does NOT update last_run - it stays unchanged.
     *
     * Example failure scenario (scheduled for Monday 08:30):
     * - Before failure: last_run = Previous Monday 08:30, next_run = Monday 08:30
     * - Monday 08:30: Processing FAILS → this method is called
     * - After this update: last_run = Previous Monday 08:30 (unchanged!), next_run = Tuesday 08:30
     *
     * Why this works:
     * 1. Query uses "dtc.nextRun = :now" (exact match), so org won't be picked up again today
     * 2. last_run stays at "Previous Monday", preserving the event window for when it succeeds
     * 3. When it succeeds (e.g., on Wednesday), updateLastAndNextRunForWeekly() calculates:
     *    - lastRun = calculateNextRun(cron, Previous Monday) = THIS Monday (the scheduled time)
     *    - nextRun = calculateNextRun(cron, Wednesday) = NEXT Monday
     * 4. Event window is correct: events from THIS Monday to NEXT Monday
     *
     * @param orgIdsToUpdate list of org IDs that failed processing
     * @param currentExecutionTime the current execution time
     */
    @Transactional
    public void updateNextRunForFailedWeekly(List<String> orgIdsToUpdate, LocalDateTime currentExecutionTime) {
        if (orgIdsToUpdate.isEmpty()) {
            return;
        }

        // Set next_run to tomorrow at the same hour
        // This gives 24 hours for the issue to be resolved before retry
        LocalDateTime nextRunForFailedOrgs = currentExecutionTime.plusDays(1);

        String updateQuery = "UPDATE DigestTriggerOrgConfig dtc " +
            "SET dtc.nextRun = :nextRun " +
            "WHERE dtc.id.orgId IN :orgIds AND dtc.id.subscriptionType = :subscriptionType";

        int updated = entityManager.createQuery(updateQuery)
            .setParameter("nextRun", nextRunForFailedOrgs)
            .setParameter("orgIds", orgIdsToUpdate)
            .setParameter("subscriptionType", WEEKLY)
            .executeUpdate();

        Log.infof("Next run scheduled for tomorrow at %s for %d failed weekly aggregations", nextRunForFailedOrgs, updated);
    }

}
