package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.CronJobRun;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import org.hibernate.Session;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class EmailAggregationResources {

    @Inject
    Session session;

    @Transactional
    public List<EmailAggregationKey> getApplicationsWithPendingAggregation(LocalDateTime start, LocalDateTime end) {
        String query = "SELECT DISTINCT NEW com.redhat.cloud.notifications.models.EmailAggregationKey(ea.accountId, ea.bundleName, ea.applicationName) " +
                "FROM EmailAggregation ea WHERE ea.created > :start AND ea.created <= :end";
        return session.createQuery(query, EmailAggregationKey.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }

    List<EmailAggregation> getEmailAggregation(EmailAggregationKey key, LocalDateTime start, LocalDateTime end) {
        String query = "FROM EmailAggregation WHERE accountId = :accountId AND bundleName = :bundleName AND applicationName = :applicationName AND created > :start AND created <= :end ORDER BY created";
        return session.createQuery(query, EmailAggregation.class)
                .setParameter("accountId", key.getAccountId())
                .setParameter("bundleName", key.getBundle())
                .setParameter("applicationName", key.getApplication())
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }

    @ActivateRequestContext
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
