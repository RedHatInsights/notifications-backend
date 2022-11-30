package com.redhat.cloud.notifications.db;

import io.quarkus.logging.Log;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static java.time.ZoneOffset.UTC;


@ApplicationScoped
public class AggregationOrgConfigRepository {

    @Inject
    EntityManager entityManager;

    public void createMissingDefaultConfiguration(int defaultDailyDigestHour) {
        LocalTime defaultRunningTime = LocalTime.of(defaultDailyDigestHour, 0, 0);
        String query = "INSERT INTO aggregation_org_config (org_id, scheduled_execution_time, last_run) " +
            "SELECT DISTINCT ema.org_id, CAST(:expectedRunningTime as time without time zone), CAST(:lastRun as timestamp without time zone)FROM email_aggregation ema " +
            "WHERE NOT EXISTS (SELECT agcjp.org_id FROM aggregation_org_config agcjp WHERE ema.org_id = agcjp.org_id)";

        int createdEntries = entityManager.createNativeQuery(query)
            .setParameter("expectedRunningTime", defaultRunningTime)
            .setParameter("lastRun", LocalDateTime.now(UTC).minusYears(1))
            .executeUpdate();

        Log.infof("Default time preference must be created for %d OrdId", createdEntries);
    }
}
