package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.DigestTriggerOrgConfig;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import com.redhat.cloud.notifications.utils.AggregationTimeHelper;
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
import java.util.Optional;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.SubscriptionType.WEEKLY;
import static java.time.ZoneOffset.UTC;
import static java.util.stream.Collectors.toList;

@ApplicationScoped
public class DigestTriggerOrgConfigRepository {

    @Inject
    EntityManager entityManager;

    /**
     * Get applications with pending weekly aggregation where the next run time has been reached.
     * Results will be limited to events from the last week.
     * If last_run is older than 2 days, uses yesterday as start date to handle outage recovery.
     */
    public List<AggregationCommand> getApplicationsWithPendingWeeklyAggregation(LocalDateTime now) {
        LocalDateTime oneWeekAgo = now.minusWeeks(1);
        LocalDateTime yesterday = now.minusDays(1);
        List<Object[]> records;
        final LocalDateTime startTime = LocalDateTime.now(UTC);

        String query = "SELECT DISTINCT ev.orgId, ev.bundleId, ev.applicationId, dtc.lastRun, bu.name, ap.name FROM Event ev " +
            "join Application ap on ev.applicationId = ap.id join Bundle bu on ev.bundleId = bu.id " +
            "join DigestTriggerOrgConfig dtc on ev.orgId = dtc.id.orgId WHERE " +
            // Filter on WEEKLY subscription type
            "dtc.id.subscriptionType = :subscriptionType " +
            // Check that at least one user of the org subscribed for weekly digest with this event type
            "AND EXISTS (SELECT 1 FROM EventTypeEmailSubscription es where ev.orgId = es.id.orgId and es.id.subscriptionType = 'WEEKLY' and es.eventType = ev.eventType and es.subscribed is true) " +
            // Check for linked email integration linked to this event type (to honor legacy mechanism)
            "AND EXISTS (SELECT 1 FROM Endpoint ep, EndpointEventType eet where (ev.orgId = ep.orgId or ep.orgId is null) and ep.compositeType.type = 'EMAIL_SUBSCRIPTION' and eet.eventType = ev.eventType and eet.endpoint = ep) " +
            // Filter on new events since the latest run of this org aggregation, and not older than one week
            "AND (ev.created > dtc.lastRun OR dtc.lastRun is null) AND ev.created > :oneWeekAgo AND ev.created <= :now " +
            // Filter on orgs where next run time has been reached
            "AND (dtc.nextRun <= :now OR dtc.nextRun is null)";

        Query hqlQuery = entityManager.createQuery(query)
            .setParameter("subscriptionType", WEEKLY)
            .setParameter("now", now)
            .setParameter("oneWeekAgo", oneWeekAgo);

        records = hqlQuery.getResultList();

        Duration duration = Duration.between(startTime, LocalDateTime.now(UTC));
        Log.infof("Fetching weekly aggregations with pending aggregations with at least one subscriber took %d s for %d aggregations", duration.toSeconds(), records.size());

        return records.stream()
            .map(emailAggregationRecord -> {
                LocalDateTime lastRun = (LocalDateTime) emailAggregationRecord[3];
                return new AggregationCommand(
                    new EventAggregationCriterion(
                        (String) emailAggregationRecord[0],     // Org id
                        (UUID) emailAggregationRecord[1],       // bundle id
                        (UUID) emailAggregationRecord[2],       // application id
                        (String) emailAggregationRecord[4],     // bundle name
                        (String) emailAggregationRecord[5]),    // application name
                    AggregationTimeHelper.computeStartDateTime(lastRun, now, yesterday),
                    now,
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
     * @param executionTime the current execution time (rounded to 15-minute intervals)
     */
    @Transactional
    public void createMissingDefaultWeeklyConfig(String defaultCronExpression, LocalDateTime executionTime) {
        // Calculate initial last run (one week ago) and next run based on cron expression
        LocalDateTime initialLastRun = executionTime.minusWeeks(1);
        Optional<LocalDateTime> initialNextRunOpt = DigestCronUtils.calculateNextRun(defaultCronExpression, executionTime);

        if (initialNextRunOpt.isEmpty()) {
            String errorMsg = String.format("Failed to calculate initial next run for cron expression: %s. Cannot create default weekly configs.", defaultCronExpression);
            Log.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        LocalDateTime initialNextRun = initialNextRunOpt.get();

        String query = "INSERT INTO digest_trigger_org_config (org_id, subscription_type, cron_expression, last_run, next_run) " +
            "SELECT DISTINCT(es.org_id), 'WEEKLY', CAST(:cronExpression as varchar), CAST(:lastRun as timestamp without time zone), CAST(:nextRun as timestamp without time zone) " +
            "FROM email_subscriptions es where es.subscription_type='WEEKLY' and es.subscribed = true AND " +
            "NOT EXISTS (SELECT 1 FROM digest_trigger_org_config dtc WHERE es.org_id = dtc.org_id AND dtc.subscription_type = 'WEEKLY')";

        int createdEntries = entityManager.createNativeQuery(query)
            .setParameter("cronExpression", defaultCronExpression)
            .setParameter("lastRun", initialLastRun)
            .setParameter("nextRun", initialNextRun)
            .executeUpdate();

        Log.infof("Default weekly digest configuration created for %d organizations", createdEntries);
    }

    /**
     * Updates last_run and next_run for the given org IDs with WEEKLY subscription type.
     *
     * @param orgIdsToUpdate list of org IDs to update
     * @param lastRun the last run timestamp
     */
    @Transactional
    public void updateLastAndNextRunForWeekly(List<String> orgIdsToUpdate, LocalDateTime lastRun) {
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
            Optional<LocalDateTime> nextRunOpt = DigestCronUtils.calculateNextRun(config.getCronExpression(), lastRun);
            if (nextRunOpt.isPresent()) {
                config.setLastRun(lastRun);
                config.setNextRun(nextRunOpt.get());
                entityManager.merge(config);
                updated++;
            } else {
                Log.errorf("Failed to calculate next run for org %s with cron expression: %s. This org will not receive weekly digests until the cron expression is fixed.",
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

}
