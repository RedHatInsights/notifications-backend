package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.db.entities.NotificationHistoryEntity;
import com.redhat.cloud.notifications.db.mappers.NotificationHistoryMapper;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.UUID;

@ApplicationScoped
public class NotificationResources {

    @Inject
    Mutiny.Session session;

    @Inject
    NotificationHistoryMapper notificationHistoryMapper;

    public Uni<NotificationHistory> createNotificationHistory(NotificationHistory history) {
        return Uni.createFrom().item(() -> notificationHistoryMapper.dtoToEntity(history))
                .flatMap(notificationHistoryEntity -> session.persist(notificationHistoryEntity)
                        .call(() -> session.flush())
                        .replaceWith(notificationHistoryEntity)
                )
                .onItem().transform(notificationHistoryMapper::entityToDto);
    }

    public Multi<NotificationHistory> getNotificationHistory(String tenant, UUID endpoint) {
        // FIXME This loads more fields than before (accountId and details)
        String query = "FROM NotificationHistoryEntity WHERE accountId = :accountId AND endpoint.id = :endpointId";
        return session.createQuery(query, NotificationHistoryEntity.class)
                .setParameter("accountId", tenant)
                .setParameter("endpointId", endpoint)
                .getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable)
                .onItem().transform(notificationHistoryMapper::entityToDto);
    }

    public Uni<JsonObject> getNotificationDetails(String tenant, Query limiter, UUID endpoint, Integer historyId) {
        String query = "SELECT details FROM NotificationHistoryEntity WHERE accountId = :accountId AND endpoint.id = :endpointId AND id = :historyId";
        if (limiter != null) {
            query = limiter.getModifiedQuery(query);
        }

        Mutiny.Query<String> mutinyQuery = session.createQuery(query, String.class)
                .setParameter("accountId", tenant)
                .setParameter("endpointId", endpoint)
                .setParameter("historyId", historyId);

        if (limiter != null && limiter.getLimit() != null && limiter.getLimit().getLimit() > 0) {
            mutinyQuery = mutinyQuery.setMaxResults(limiter.getLimit().getLimit())
                    .setFirstResult(limiter.getLimit().getOffset());
        }

        return mutinyQuery.getSingleResultOrNull()
                .onItem().ifNotNull().transform(JsonObject::new);
    }
}
