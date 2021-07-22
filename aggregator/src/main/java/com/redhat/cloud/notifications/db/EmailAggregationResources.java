package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.EmailAggregation;
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

    public List<EmailAggregation> getEmailAggregation(EmailAggregationKey key, LocalDateTime start, LocalDateTime end) {
        String query = "FROM EmailAggregation WHERE accountId = :accountId AND bundleName = :bundleName AND applicationName = :applicationName AND created > :start AND created <= :end ORDER BY created";
        return session.createQuery(query, EmailAggregation.class)
                .setParameter("accountId", key.getAccountId())
                .setParameter("bundleName", key.getBundle())
                .setParameter("applicationName", key.getApplication())
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }

    @Transactional
    public Integer purgeOldAggregation(EmailAggregationKey key, LocalDateTime lastUsedTime) {
        String query = "DELETE FROM EmailAggregation WHERE accountId = :accountId AND bundleName = :bundleName AND applicationName = :applicationName AND created <= :created";
        final int result = session.createQuery(query)
                .setParameter("accountId", key.getAccountId())
                .setParameter("bundleName", key.getBundle())
                .setParameter("applicationName", key.getApplication())
                .setParameter("created", lastUsedTime)
                .executeUpdate();
        session.flush();
        return result;
    }
}
