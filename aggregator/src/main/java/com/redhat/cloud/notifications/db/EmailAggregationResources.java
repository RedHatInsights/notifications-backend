package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.CronJobRun;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import org.hibernate.Session;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class EmailAggregationResources {

    @Inject
    Session session;

    public List<EmailAggregationKey> getApplicationsWithPendingAggregation(LocalDateTime start, LocalDateTime end) {
        String query = "SELECT DISTINCT NEW com.redhat.cloud.notifications.models.EmailAggregationKey(ea.accountId, ea.bundleName, ea.applicationName) " +
                "FROM EmailAggregation ea WHERE ea.created > :start AND ea.created <= :end";
        return session.createQuery(query, EmailAggregationKey.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }

    public CronJobRun getLastCronJobRun() {
        String query = "FROM CronJobRun";
        return session.createQuery(query, CronJobRun.class).getSingleResult();
    }

    @Transactional
    public void updateLastCronJobRun(LocalDateTime lastRun) {
        String query = "UPDATE CronJobRun SET lastRun = :lastRun";
        session.createQuery(query)
                .setParameter("lastRun", lastRun)
                .executeUpdate();
    }
}
