package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class EmailAggregationRepository {

    private static final Logger LOGGER = Logger.getLogger(EmailAggregationRepository.class);

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    public boolean addEmailAggregation(EmailAggregation aggregation) {
        aggregation.prePersist(); // This method must be called manually while using a StatelessSession.
        try {
            statelessSessionFactory.getCurrentSession().insert(aggregation);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Email aggregation persisting failed", e);
            return false;
        }
    }

    public List<EmailAggregation> getEmailAggregation(EmailAggregationKey key, LocalDateTime start, LocalDateTime end) {
        String query = "FROM EmailAggregation WHERE accountId = :accountId AND orgId = :orgId AND bundleName = :bundleName AND applicationName = :applicationName AND created > :start AND created <= :end ORDER BY created";

        if (key.getOrgId() == null || key.getOrgId().isEmpty()) {
            return statelessSessionFactory.getCurrentSession().createQuery(query, EmailAggregation.class)
                    .setParameter("accountId", key.getAccountId())
                    .setParameter("bundleName", key.getBundle())
                    .setParameter("applicationName", key.getApplication())
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .getResultList();
        } else {
            return statelessSessionFactory.getCurrentSession().createQuery(query, EmailAggregation.class)
                    .setParameter("accountId", key.getAccountId())
                    .setParameter("orgId", key.getOrgId())
                    .setParameter("bundleName", key.getBundle())
                    .setParameter("applicationName", key.getApplication())
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .getResultList();
        }
    }

    @Transactional
    public int purgeOldAggregation(EmailAggregationKey key, LocalDateTime lastUsedTime) {
        String query = "DELETE FROM EmailAggregation WHERE accountId = :accountId AND bundleName = :bundleName AND applicationName = :applicationName AND created <= :created";
        return statelessSessionFactory.getCurrentSession().createQuery(query)
                .setParameter("accountId", key.getAccountId())
                .setParameter("bundleName", key.getBundle())
                .setParameter("applicationName", key.getApplication())
                .setParameter("created", lastUsedTime)
                .executeUpdate();
    }
}
