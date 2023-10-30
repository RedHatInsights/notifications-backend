package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.CronJobRun;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static java.util.stream.Collectors.toList;


@ApplicationScoped
public class EmailAggregationRepository {

    @Inject
    EntityManager entityManager;

    public List<AggregationCommand> getApplicationsWithPendingAggregationAccordinfOrgPref(LocalDateTime now) {
        // Must takes every EmailAggregation supposed to be processed on last 15 min
        // it covers cases when aggregation job may be run with few minutes late (ie: 05:01, 07,32)
        String query = "SELECT DISTINCT ea.orgId, ea.bundleName, ea.applicationName, acp.lastRun FROM EmailAggregation ea, AggregationOrgConfig acp WHERE " +
            "ea.orgId = acp.orgId AND ea.created > acp.lastRun AND ea.created <= :now " +
            "AND :nowTime >= acp.scheduledExecutionTime AND (:nowTime - acp.scheduledExecutionTime) < :cutoff";
        Query hqlQuery = entityManager.createQuery(query)
                .setParameter("nowTime", now.toLocalTime())
                .setParameter("cutoff", Duration.ofMinutes(15))
               .setParameter("now", now);

        List<Object[]> records = hqlQuery.getResultList();
        return records.stream()
                .map(emailAggregationRecord -> new AggregationCommand(
                    new EmailAggregationKey((String) emailAggregationRecord[0], (String) emailAggregationRecord[1], (String) emailAggregationRecord[2]),
                    (LocalDateTime) emailAggregationRecord[3],
                    now,
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
    public void updateLastCronJobRunAccordingOrgPref(List<String> orgIdsToUpdate, LocalDateTime end) {

        String hqlQuery = "UPDATE AggregationOrgConfig ac SET ac.lastRun=:end WHERE ac.orgId IN :orgIdsToUpdate";
        Query nativeQuery = entityManager.createQuery(hqlQuery)
            .setParameter("orgIdsToUpdate", orgIdsToUpdate)
            .setParameter("end", end);

        int nbUpdatedRecords = nativeQuery.executeUpdate();
        Log.infof("Last run date was updated for %s orgId", nbUpdatedRecords);
    }

}
