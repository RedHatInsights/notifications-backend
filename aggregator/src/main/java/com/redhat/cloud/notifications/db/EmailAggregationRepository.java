package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.CronJobRun;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import io.quarkus.logging.Log;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static java.util.stream.Collectors.toList;


@ApplicationScoped
public class EmailAggregationRepository {

    @Inject
    EntityManager entityManager;

    public List<AggregationCommand> getApplicationsWithPendingAggregationAccordinfOrgPref(LocalDateTime end) {
        // Must takes every EmailAggregation supposed to be processed on last 15 min
        // it covers cases when aggregation job may be run with few minutes late (ie: 05:01, 07,32)
        String query = "SELECT DISTINCT ea.orgId, ea.bundleName, ea.applicationName, acp.lastRun FROM EmailAggregation ea, AggregationOrgConfig acp WHERE " +
            "ea.orgId = acp.orgId AND ea.created > acp.lastRun AND ea.created <= :end " +
            "AND :endTime >= acp.scheduledExecutionTime AND (:endTime - acp.scheduledExecutionTime) < CAST(:cutoff as LocalTime)";
        Query hqlQuery = entityManager.createQuery(query)
                .setParameter("endTime", end.toLocalTime())
                .setParameter("cutoff", LocalTime.of(0, 15))
               .setParameter("end", end);

        List<Object[]> records = hqlQuery.getResultList();
        return records.stream()
                .map(emailAggregationRecord -> new AggregationCommand(
                    new EmailAggregationKey((String) emailAggregationRecord[0], (String) emailAggregationRecord[1], (String) emailAggregationRecord[2]),
                    (LocalDateTime) emailAggregationRecord[3],
                    end,
                    DAILY
                ))
                .collect(toList());
    }

    public List<EmailAggregationKey> getApplicationsWithPendingAggregation(LocalDateTime start, LocalDateTime end) {
        String query = "SELECT DISTINCT org_id, bundle, application FROM email_aggregation WHERE created > :start AND created <= :end";
        List<Object[]> records = entityManager.createNativeQuery(query)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();

        return records.stream()
                .map(emailAggregationRecord -> new EmailAggregationKey((String) emailAggregationRecord[0], (String) emailAggregationRecord[1], (String) emailAggregationRecord[2]))
                .collect(toList());
    }

    public CronJobRun getLastCronJobRun() {
        String query = "FROM CronJobRun";
        return entityManager.createQuery(query, CronJobRun.class).getSingleResult();
    }

    @Transactional
    public void updateLastCronJobRun(LocalDateTime lastRun) {
        String query = "UPDATE CronJobRun SET lastRun = :lastRun";
        entityManager.createQuery(query)
                .setParameter("lastRun", lastRun)
                .executeUpdate();
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
