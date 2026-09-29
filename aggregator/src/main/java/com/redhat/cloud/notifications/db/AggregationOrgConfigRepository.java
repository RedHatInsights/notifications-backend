package com.redhat.cloud.notifications.db;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static java.time.ZoneOffset.UTC;


@ApplicationScoped
public class AggregationOrgConfigRepository {

    @Inject
    EntityManager entityManager;

    @Transactional
    public void createMissingDefaultConfigurationBasedOnEvent(LocalTime defaultRunningTime) {
        String query = "INSERT INTO aggregation_org_config (org_id, subscription_type, scheduled_execution_time, last_run) " +
            "SELECT DISTINCT(es.org_id), 'DAILY', CAST(:expectedRunningTime as time without time zone), CAST(:lastRun as timestamp without time zone) " +
            "FROM email_subscriptions es where es.subscription_type='DAILY' and es.subscribed = true AND " +
            "NOT EXISTS (SELECT 1 FROM aggregation_org_config agcjp WHERE es.org_id = agcjp.org_id AND agcjp.subscription_type = 'DAILY')";

        int createdEntries = entityManager.createNativeQuery(query)
            .setParameter("expectedRunningTime", defaultRunningTime)
            .setParameter("lastRun", LocalDateTime.now(UTC)
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

        String hqlQuery = "UPDATE AggregationOrgConfig ac SET ac.lastRun=:end WHERE ac.id.orgId IN :orgIdsToUpdate AND ac.id.subscriptionType = 'DAILY'";
        Query nativeQuery = entityManager.createQuery(hqlQuery)
            .setParameter("orgIdsToUpdate", orgIdsToUpdate)
            .setParameter("end", end);

        int nbUpdatedRecords = nativeQuery.executeUpdate();
        Log.infof("Last run date was updated for %s orgId", nbUpdatedRecords);
    }
}
