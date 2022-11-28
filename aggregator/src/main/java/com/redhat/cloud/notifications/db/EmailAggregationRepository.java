package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.CronJobRun;
import com.redhat.cloud.notifications.models.EmailAggregationKey;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

@ApplicationScoped
public class EmailAggregationRepository {

    @Inject
    EntityManager entityManager;

    public List<EmailAggregationKey> getApplicationsWithPendingAggregation(String orgIdToAggregate, LocalDateTime start, LocalDateTime end) {

        String query = "SELECT DISTINCT ea.org_id, ea.bundle, ea.application FROM email_aggregation ea "
                + " WHERE ea.org_id = :orgId AND ea.created > :start AND ea.created <= :end";

        Query nativeQuery = entityManager.createNativeQuery(query)
                .setParameter("start", start)
                .setParameter("end", end)
                .setParameter("orgId", orgIdToAggregate);

        List<Object[]> records = nativeQuery.getResultList();
        return records.stream()
                .map(emailAggregationRecord -> new EmailAggregationKey((String) emailAggregationRecord[0], (String) emailAggregationRecord[1], (String) emailAggregationRecord[2]))
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

}
