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
    public void createMissingDefaultConfiguration(LocalTime defaultRunningTime) {
        String query = "INSERT INTO aggregation_org_config (org_id, scheduled_execution_time, last_run) " +
            "SELECT DISTINCT ema.org_id, CAST(:expectedRunningTime as time without time zone), CAST(:lastRun as timestamp without time zone) FROM email_aggregation ema " +
            "WHERE NOT EXISTS (SELECT 1 FROM aggregation_org_config agcjp WHERE ema.org_id = agcjp.org_id)";

        int createdEntries = entityManager.createNativeQuery(query)
            .setParameter("expectedRunningTime", defaultRunningTime)
            .setParameter("lastRun", LocalDateTime.now(UTC).minusDays(1))
            .executeUpdate();

        Log.infof("Default time preference must be created for %d OrgId", createdEntries);
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
}
