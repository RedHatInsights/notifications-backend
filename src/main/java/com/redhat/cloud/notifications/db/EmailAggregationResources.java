package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.db.entities.EmailAggregationEntity;
import com.redhat.cloud.notifications.db.mappers.EmailAggregationMapper;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDateTime;

@ApplicationScoped
public class EmailAggregationResources {

    @Inject
    Mutiny.Session session;

    @Inject
    EmailAggregationMapper emailAggregationMapper;

    public Uni<Boolean> addEmailAggregation(EmailAggregation aggregation) {
        return Uni.createFrom().item(() -> emailAggregationMapper.dtoToEntity(aggregation))
                .flatMap(aggregationEntity -> session.persist(aggregationEntity))
                .call(() -> session.flush())
                .replaceWith(Boolean.TRUE)
                .onFailure().recoverWithItem(Boolean.FALSE);
    }

    public Multi<EmailAggregationKey> getApplicationsWithPendingAggregation(LocalDateTime start, LocalDateTime end) {
        String query = "SELECT DISTINCT NEW com.redhat.cloud.notifications.models.EmailAggregationKey(accountId, bundleName, applicationName) FROM EmailAggregationEntity WHERE created > :start AND created <= :end";
        return session.createQuery(query, EmailAggregationKey.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable);
    }

    public Multi<EmailAggregation> getEmailAggregation(EmailAggregationKey key, LocalDateTime start, LocalDateTime end) {
        String query = "FROM EmailAggregationEntity WHERE accountId = :accountId AND bundleName = :bundleName AND applicationName = :applicationName AND created > :start AND created <= :end ORDER BY created";
        return session.createQuery(query, EmailAggregationEntity.class)
                .setParameter("accountId", key.getAccountId())
                .setParameter("bundleName", key.getBundle())
                .setParameter("applicationName", key.getApplication())
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable)
                .onItem().transform(emailAggregationMapper::entityToDto);
    }

    public Uni<Integer> purgeOldAggregation(EmailAggregationKey key, LocalDateTime lastUsedTime) {
        String query = "DELETE FROM EmailAggregationEntity WHERE account_id = :accountId AND bundleName = :bundleName AND applicationName = :applicationName AND created <= :created";
        return session.createQuery(query)
                .setParameter("accountId", key.getAccountId())
                .setParameter("bundleName", key.getBundle())
                .setParameter("applicationName", key.getApplication())
                .setParameter("created", lastUsedTime)
                .executeUpdate()
                .call(() -> session.flush());
    }
}
