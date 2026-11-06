package com.redhat.cloud.notifications.db.repositories;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;


@ApplicationScoped
public class AggregationOrgConfigRepository {

    @Inject
    EntityManager entityManager;

    /**
     * Creates an aggregation_org_config with a last run date of yesterday at defaultRunningTime time,
     * for orgs with at least one subscriber to daily digest
     * @param defaultRunningTime the default aggregation time.
     * @param executionTime the current execution time (rounded to 15-minute intervals)
     */
    @Transactional
    public void createMissingDefaultConfigurationBasedOnEvent(LocalTime defaultRunningTime, LocalDateTime executionTime) {
        String query = "INSERT INTO aggregation_org_config (org_id, scheduled_execution_time, last_run) " +
            "SELECT DISTINCT(es.org_id), CAST(:expectedRunningTime as time without time zone), CAST(:lastRun as timestamp without time zone) " +
            "FROM email_subscriptions es where es.subscription_type='DAILY' and es.subscribed = true AND " +
            "NOT EXISTS (SELECT 1 FROM aggregation_org_config agcjp WHERE es.org_id = agcjp.org_id)";

        int createdEntries = entityManager.createNativeQuery(query)
            .setParameter("expectedRunningTime", defaultRunningTime)
            .setParameter("lastRun", executionTime
                .minusDays(1)
                .withHour(defaultRunningTime.getHour())
                .withMinute(defaultRunningTime.getMinute())
                .withSecond(defaultRunningTime.getSecond())
                .withNano(defaultRunningTime.getNano()))
            .executeUpdate();

        Log.infof("Default aggregation configuration created for %d organizations", createdEntries);
    }

    @Transactional
    public void updateLastCronJobRunAccordingOrgPref(List<String> orgIdsToUpdate, LocalDateTime end) {

        String hqlQuery = "UPDATE AggregationOrgConfig ac SET ac.lastRun=:end WHERE ac.orgId IN :orgIdsToUpdate";
        Query nativeQuery = entityManager.createQuery(hqlQuery)
            .setParameter("orgIdsToUpdate", orgIdsToUpdate)
            .setParameter("end", end);

        int nbUpdatedRecords = nativeQuery.executeUpdate();
        Log.infof("Last run date was updated for %s orgId", nbUpdatedRecords);
    }

    /**
     * No-op for failed daily aggregations. By not updating last_run, failed orgs will naturally
     * retry tomorrow at the same scheduledExecutionTime (since the query filters by exact time match).
     * They won't be picked up again in the next 15-minute intervals today because those won't match
     * their scheduledExecutionTime.
     *
     * @param orgIdsToUpdate list of org IDs that failed processing
     * @param currentExecutionTime the current execution time
     */
    @Transactional
    public void updateLastRunForFailedDaily(List<String> orgIdsToUpdate, LocalDateTime currentExecutionTime) {
        if (!orgIdsToUpdate.isEmpty()) {
            Log.infof("Keeping last_run unchanged for %d failed daily aggregations (will retry tomorrow at scheduled time)", orgIdsToUpdate.size());
        }
        // Intentionally do nothing - failed orgs keep their old last_run value
        // and will be retried tomorrow when their scheduledExecutionTime matches again
    }
}
