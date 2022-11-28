package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.AggregationCronjobParameters;
import com.redhat.cloud.notifications.models.CronJobRun;
import io.quarkus.logging.Log;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static java.time.ZoneOffset.UTC;


@ApplicationScoped
public class AggregationCronjobParameterRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    EmailAggregationRepository emailAggregationResources;

    public List<AggregationCronjobParameters> getOrgIdToProceed(LocalDateTime now) {
        LocalTime currentHour = LocalTime.of(now.getHour(), 0, 0);
        String query = "SELECT acp FROM AggregationCronjobParameters acp WHERE acp.expectedRunningTime = :currentHour";

        return entityManager.createQuery(query, AggregationCronjobParameters.class)
            .setParameter("currentHour", currentHour)
            .getResultList();
    }

    public void createMissingDefaultConfiguration(int defaultDailyDigestHour) {
        LocalTime defaultRunningTime = LocalTime.of(defaultDailyDigestHour, 0, 0);
        String query = "SELECT DISTINCT ema.org_id FROM email_aggregation ema LEFT JOIN aggregation_cronjob_parameter agcjp ON ema.org_id = agcjp.org_id WHERE agcjp.org_id IS NULL";
        List<String> orgIdWithoutPref = entityManager.createNativeQuery(query)
            .getResultList();
        Log.infof("Default time preference must be created for %d OrdId", orgIdWithoutPref.size());

        orgIdWithoutPref.stream().forEach(orgId -> entityManager.persist(new AggregationCronjobParameters(orgId, defaultRunningTime, getlastRun())));
    }

    // To be remove after initial migration. will be replaced by simply LocalDateTime.now(UTC).minusDays(1)
    @Deprecated(forRemoval = true)
    private LocalDateTime getlastRun() {
        LocalDateTime yesterdaySameHour = LocalDateTime.now(UTC).minusDays(1);
        final CronJobRun lastCronJobRun = emailAggregationResources.getLastCronJobRun();
        if (null == lastCronJobRun || lastCronJobRun.getLastRun().isAfter(yesterdaySameHour)) {
            return lastCronJobRun.getLastRun();
        } else {
            return yesterdaySameHour;
        }
    }
}
