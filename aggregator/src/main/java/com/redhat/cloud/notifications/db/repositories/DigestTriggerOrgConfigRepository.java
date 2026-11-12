package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.DigestTriggerOrgConfig;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.utils.DigestCronUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.SubscriptionType.WEEKLY;
import static java.time.ZoneOffset.UTC;
import static java.util.stream.Collectors.toList;

@ApplicationScoped
public class DigestTriggerOrgConfigRepository {

    // PostgreSQL JDBC limit is 65,535 bind parameters per statement. Batch IN-lists well below
    // that ceiling so large deployments don't hit the limit as the number of subscribed orgs grows.
    private static final int IN_CLAUSE_MAX = 32000;

    @Inject
    EntityManager entityManager;

    @Inject
    DigestCronUtils digestCronUtils;

    /**
     * Resets PROCESSING rows whose claim is older than the threshold back to PENDING.
     * Recovers orgs whose pod was killed (OOM, SIGKILL) before it could complete.
     * The threshold must exceed max_maintenance_window + max_run_duration (default 6h).
     * <p>
     * The staleness check uses claimed_at (set by claimDueWeeklyOrgs) rather than next_run.
     * This prevents a concurrent pod from immediately resetting rows that were just claimed
     * after a long pause: when the job is delayed 6+ hours, next_run is old but the claim
     * is fresh, so the row should not be treated as stale.
     * <p>
     * Rows with claimed_at = NULL (created before this column was added) fall back to the
     * previous next_run-based heuristic for backward compatibility.
     */
    @Transactional
    public int resetStaleClaims(LocalDateTime now, int thresholdHours) {
        LocalDateTime threshold = now.minusHours(thresholdHours);
        int reset = entityManager.createQuery(
            "UPDATE DigestTriggerOrgConfig SET status = 'PENDING', claimedAt = null " +
            "WHERE status = 'PROCESSING' AND id.subscriptionType = :type " +
            "AND (claimedAt < :threshold OR (claimedAt IS NULL AND nextRun < :threshold))")
            .setParameter("threshold", threshold)
            .setParameter("type", WEEKLY)
            .executeUpdate();
        if (reset > 0) {
            Log.warnf("Reset %d stale PROCESSING weekly claims (pod crash recovery)", reset);
        }
        return reset;
    }

    /**
     * Atomically claims all PENDING weekly orgs whose next_run has been reached.
     * Uses UPDATE ... RETURNING so only rows actually changed by THIS statement are returned --
     * a concurrent pod claiming the same rows gets an empty intersection, preventing double-processing.
     * Sets claimed_at to the current time so resetStaleClaims can distinguish a freshly-claimed
     * row from one that has genuinely been stuck in PROCESSING for too long.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public List<String> claimDueWeeklyOrgs(LocalDateTime now) {
        List<String> claimed = entityManager.createNativeQuery(
            "UPDATE digest_trigger_org_config SET status = 'PROCESSING', claimed_at = :now " +
            "WHERE status = 'PENDING' AND next_run <= :now AND subscription_type = 'WEEKLY' " +
            "RETURNING org_id")
            .setParameter("now", now)
            .getResultList();
        Log.infof("Claimed %d weekly orgs for processing", claimed.size());
        return claimed;
    }

    /**
     * Returns aggregation commands for the weekly orgs claimed by this pod this invocation.
     * Scoping to claimedOrgIds prevents a pod from re-processing rows owned by a still-running
     * concurrent pod -- even though both would see status = 'PROCESSING'.
     */
    public List<AggregationCommand> getApplicationsWithPendingWeeklyAggregation(LocalDateTime now, List<String> claimedOrgIds) {
        if (claimedOrgIds.isEmpty()) {
            return List.of();
        }
        final SubscriptionType subscriptionType = WEEKLY;
        final LocalDateTime statStartTime = LocalDateTime.now(UTC);

        String query = "SELECT DISTINCT ev.orgId, ev.bundleId, ev.applicationId, dtc.lastRun, bu.name, ap.name, dtc.cronExpression FROM Event ev " +
            "join Application ap on ev.applicationId = ap.id join Bundle bu on ev.bundleId = bu.id " +
            "join DigestTriggerOrgConfig dtc on ev.orgId = dtc.id.orgId WHERE " +
            // Filter on WEEKLY subscription type
            "dtc.id.subscriptionType = :subscriptionType " +
            // Filter on claimed org id for this org
            "AND dtc.id.orgId IN :claimedOrgIds " +
            // Check if at least one user of the org subscribed to weekly digest
            "AND EXISTS (SELECT 1 FROM EventTypeEmailSubscription es where ev.orgId = es.id.orgId and es.id.subscriptionType = :subscriptionType and es.eventType = ev.eventType and es.subscribed is true) " +
            // Check for linked email integration linked to this event type (to honor legacy mechanism)
            "AND EXISTS (SELECT 1 FROM Endpoint ep, EndpointEventType eet where (ev.orgId = ep.orgId or ep.orgId is null) and ep.compositeType.type = 'EMAIL_SUBSCRIPTION' and eet.eventType = ev.eventType and eet.endpoint = ep) " +
            // Use lastRun as the lower bound when available; fall back to oneWeekAgo only for first runs (null lastRun).
            // Combining both with AND would create a gap on retries: if lastRun is Oct 26 and the retry runs Nov 3,
            // oneWeekAgo becomes Oct 27 and silently drops events created between Oct 26 and Oct 27.
            "AND ev.created > COALESCE(dtc.lastRun, :oneWeekAgo) AND ev.created <= :now";

        LocalDateTime oneWeekAgo = now.minusDays(subscriptionType.getDuration().toDays());
        List<Object[]> records = new ArrayList<>();
        for (List<String> batch : partition(claimedOrgIds, IN_CLAUSE_MAX)) {
            records.addAll(entityManager.createQuery(query)
                .setParameter("subscriptionType", subscriptionType)
                .setParameter("claimedOrgIds", batch)
                .setParameter("oneWeekAgo", oneWeekAgo)
                .setParameter("now", now)
                .getResultList());
        }

        Duration duration = Duration.between(statStartTime, LocalDateTime.now(UTC));
        Log.infof("Fetching weekly aggregations with at least one subscriber took %d s for %d aggregations", duration.toSeconds(), records.size());

        return records.stream()
            .map(emailAggregationRecord -> {
                LocalDateTime lastRun = (LocalDateTime) emailAggregationRecord[3];
                String cronExpression = (String) emailAggregationRecord[6];

                LocalDateTime start;
                LocalDateTime end;
                if (lastRun == null) {
                    // First run: cover the past 7 days up to this slot.
                    // Using 'now' directly avoids the cron-utils behaviour where
                    // calculateNextRun(cron, now) returns the *next* occurrence when
                    // 'now' exactly matches the cron schedule (e.g. Monday 00:30 -> next Monday).
                    start = now.minusDays(7);
                    end = now;
                } else {
                    // end = most recent scheduled slot at or before the execution time.
                    //
                    // Why not calculateNextRun(cron, lastRun)?
                    // That expression resolves to the first occurrence of the cron AFTER lastRun.
                    // For a normal on-schedule run both give the same result, but after a schedule
                    // change they diverge:
                    //   lastRun = Mon Nov 9 (old Monday schedule)
                    //   admin changes to Wednesday 09:00 on Sun Nov 15
                    //   nextRun is correctly set to Wed Nov 18
                    //   calculateNextRun(Wed cron, Nov 9) = Nov 11  ← already in the past!
                    //   calculateCurrentSlot(Wed cron, Nov 18)     = Nov 18  ← correct
                    // Using calculateNextRun would make every subsequent digest cover a stale window.
                    //
                    // The first digest after a schedule change may cover more than 7 days:
                    //   start = Nov 9 (last successful run on old schedule)
                    //   end   = Nov 18 (first slot on new schedule)
                    //   → 9-day window, includes everything since the last digest with no gap.
                    // Subsequent digests return to the normal 7-day cadence.
                    //
                    // For retries (same cron, job ran late): both formulas give the same end because
                    //   calculateNextRun(Mon cron, lastMon) = thisMon = calculateCurrentSlot(Mon cron, Tue)
                    // so retry behaviour is unchanged.
                    end = digestCronUtils.calculateCurrentSlot(cronExpression, now);
                    start = lastRun;
                }

                return new AggregationCommand(
                    new EventAggregationCriterion(
                        (String) emailAggregationRecord[0],     // Org id
                        (UUID) emailAggregationRecord[1],       // Bundle id
                        (UUID) emailAggregationRecord[2],       // Application id
                        (String) emailAggregationRecord[4],     // Bundle name
                        (String) emailAggregationRecord[5]),    // Application name
                    start,
                    end,
                    subscriptionType
                );
            })
            .collect(toList());
    }

    /**
     * Creates digest_trigger_org_config entries for orgs with at least one weekly subscriber
     * that have no existing config yet.
     */
    @Transactional
    public void createMissingDefaultWeeklyConfig(String defaultCronExpression) {
        LocalDateTime initialNextRun = digestCronUtils.computeNextRun(defaultCronExpression);

        String query = "INSERT INTO digest_trigger_org_config (org_id, subscription_type, cron_expression, next_run, status) " +
            "SELECT DISTINCT(es.org_id), 'WEEKLY', CAST(:cronExpression as varchar), CAST(:nextRun as timestamp without time zone), 'PENDING' " +
            "FROM email_subscriptions es where es.subscription_type='WEEKLY' and es.subscribed = true AND " +
            "NOT EXISTS (SELECT 1 FROM digest_trigger_org_config dtc WHERE es.org_id = dtc.org_id AND dtc.subscription_type = 'WEEKLY')";

        int createdEntries = entityManager.createNativeQuery(query)
            .setParameter("cronExpression", defaultCronExpression)
            .setParameter("nextRun", initialNextRun)
            .executeUpdate();

        Log.infof("Default weekly digest configuration created for %d organizations", createdEntries);
    }

    /**
     * Updates last_run, next_run, and resets status to PENDING for successfully processed orgs.
     * <p>
     * last_run is set to the most recent scheduled slot at or before the execution time
     * (calculateCurrentSlot), so it always matches the window end used by
     * getApplicationsWithPendingWeeklyAggregation. This keeps the next digest's start boundary
     * consistent regardless of retries or schedule changes.
     * <p>
     * After a schedule change the first last_run on the new schedule may be ahead of the old
     * last_run by more than 7 days (e.g. Nov 9 → Nov 18 after a Mon→Wed change that lands on
     * Nov 18). That is intentional: it matches the extended first window described in
     * getApplicationsWithPendingWeeklyAggregation. Subsequent digests return to 7-day windows.
     */
    @Transactional
    public void updateLastAndNextRunForWeekly(List<String> orgIdsToUpdate, LocalDateTime currentExecutionTime) {
        if (orgIdsToUpdate.isEmpty()) {
            return;
        }

        String selectQuery = "SELECT dtc FROM DigestTriggerOrgConfig dtc WHERE dtc.id.orgId IN :orgIds AND dtc.id.subscriptionType = :subscriptionType";
        List<DigestTriggerOrgConfig> configs = new ArrayList<>();
        for (List<String> batch : partition(orgIdsToUpdate, IN_CLAUSE_MAX)) {
            configs.addAll(entityManager.createQuery(selectQuery, DigestTriggerOrgConfig.class)
                .setParameter("orgIds", batch)
                .setParameter("subscriptionType", WEEKLY)
                .getResultList());
        }

        int updated = 0;
        int skipped = 0;
        for (DigestTriggerOrgConfig config : configs) {
            try {
                LocalDateTime nextRun = digestCronUtils.calculateNextRun(config.getCronExpression(), currentExecutionTime);

                // Set last_run to the most recent scheduled slot at or before the execution time.
                // This must mirror the window end computed in getApplicationsWithPendingWeeklyAggregation
                // so that the next digest's start boundary is exactly where this one ended.
                LocalDateTime newLastRun = config.getLastRun() == null
                    ? currentExecutionTime
                    : digestCronUtils.calculateCurrentSlot(config.getCronExpression(), currentExecutionTime);

                config.setLastRun(newLastRun);
                config.setNextRun(nextRun);
                config.setStatus("PENDING");
                config.setClaimedAt(null);
                updated++;
            } catch (IllegalArgumentException e) {
                Log.errorf(e, "Failed to calculate next run for org %s with cron expression: %s. Skipping.",
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
     * Advances last_run and next_run for claimed weekly orgs that produced no events this slot,
     * and resets their status to PENDING. The caller computes noEventsOrgIds as claimedOrgIds
     * minus updatedOrgIds, so this never touches rows owned by other pods.
     */
    @Transactional
    public void advanceNextRunForOrgsWithNoEvents(LocalDateTime now, List<String> noEventsOrgIds) {
        if (noEventsOrgIds.isEmpty()) {
            return;
        }

        String selectQuery = "SELECT dtc FROM DigestTriggerOrgConfig dtc " +
            "WHERE dtc.id.subscriptionType = :subscriptionType AND dtc.id.orgId IN :noEventsOrgIds";

        List<DigestTriggerOrgConfig> configs = new ArrayList<>();
        for (List<String> batch : partition(noEventsOrgIds, IN_CLAUSE_MAX)) {
            configs.addAll(entityManager.createQuery(selectQuery, DigestTriggerOrgConfig.class)
                .setParameter("subscriptionType", WEEKLY)
                .setParameter("noEventsOrgIds", batch)
                .getResultList());
        }

        int advanced = 0;
        for (DigestTriggerOrgConfig config : configs) {
            try {
                // Same logic as updateLastAndNextRunForWeekly: last_run = current slot so that
                // the next digest starts exactly where this one ended, even after a schedule change.
                LocalDateTime newLastRun = config.getLastRun() == null
                    ? now
                    : digestCronUtils.calculateCurrentSlot(config.getCronExpression(), now);
                config.setLastRun(newLastRun);
                config.setNextRun(digestCronUtils.calculateNextRun(config.getCronExpression(), now));
                config.setStatus("PENDING");
                config.setClaimedAt(null);
                advanced++;
            } catch (IllegalArgumentException e) {
                Log.errorf(e, "Skipping nextRun advance for org %s - invalid cron: %s",
                    config.getId().orgId, config.getCronExpression());
            }
        }
        if (advanced > 0) {
            Log.infof("Advanced nextRun for %d weekly orgs with no events in this slot", advanced);
        }
    }

    /**
     * Schedules failed orgs to retry tomorrow at the same hour and resets status to PENDING.
     * last_run is preserved so the event window is correct when the retry eventually succeeds.
     */
    @Transactional
    public void updateNextRunForFailedWeekly(List<String> orgIdsToUpdate, LocalDateTime currentExecutionTime) {
        if (orgIdsToUpdate.isEmpty()) {
            return;
        }

        LocalDateTime nextRunForFailedOrgs = currentExecutionTime.plusDays(1);

        String updateQuery = "UPDATE DigestTriggerOrgConfig dtc " +
            "SET dtc.nextRun = :nextRun, dtc.status = 'PENDING', dtc.claimedAt = null " +
            "WHERE dtc.id.orgId IN :orgIds AND dtc.id.subscriptionType = :subscriptionType";

        int updated = 0;
        for (List<String> batch : partition(orgIdsToUpdate, IN_CLAUSE_MAX)) {
            updated += entityManager.createQuery(updateQuery)
                .setParameter("nextRun", nextRunForFailedOrgs)
                .setParameter("orgIds", batch)
                .setParameter("subscriptionType", WEEKLY)
                .executeUpdate();
        }

        Log.infof("Next run scheduled for tomorrow at %s for %d failed weekly aggregations", nextRunForFailedOrgs, updated);
    }

    private static <T> List<List<T>> partition(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }

}

