package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class EmailAggregationResources {

    @Inject
    Mutiny.SessionFactory sessionFactory;

    public Uni<Boolean> addEmailAggregation(EmailAggregation aggregation) {
        aggregation.prePersist(); // This method must be called manually while using a StatelessSession.
        return sessionFactory.withStatelessSession(statelessSession -> {
            return statelessSession.insert(aggregation)
                    .replaceWith(Boolean.TRUE)
                    .onFailure().recoverWithItem(Boolean.FALSE);
        });
    }

    public Uni<List<EmailAggregation>> getEmailAggregation(EmailAggregationKey key, LocalDateTime start, LocalDateTime end) {
        String query = "FROM EmailAggregation WHERE accountId = :accountId AND bundleName = :bundleName AND applicationName = :applicationName AND created > :start AND created <= :end ORDER BY created";
        return sessionFactory.withStatelessSession(statelessSession -> {
            return statelessSession.createQuery(query, EmailAggregation.class)
                    .setParameter("accountId", key.getAccountId())
                    .setParameter("bundleName", key.getBundle())
                    .setParameter("applicationName", key.getApplication())
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .getResultList();
        });
    }

    public Uni<Integer> purgeOldAggregation(EmailAggregationKey key, LocalDateTime lastUsedTime) {
        String query = "DELETE FROM EmailAggregation WHERE accountId = :accountId AND bundleName = :bundleName AND applicationName = :applicationName AND created <= :created";
        return sessionFactory.withStatelessSession(statelessSession -> {
            return statelessSession.createQuery(query)
                    .setParameter("accountId", key.getAccountId())
                    .setParameter("bundleName", key.getBundle())
                    .setParameter("applicationName", key.getApplication())
                    .setParameter("created", lastUsedTime)
                    .executeUpdate();
        });
    }
}
