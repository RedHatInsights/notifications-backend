package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.NotificationHistory;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class NotificationResources {

    @Inject
    Mutiny.Session session;

    @Inject
    Mutiny.StatelessSession statelessSession;

    public Uni<NotificationHistory> createNotificationHistory(NotificationHistory history) {
        history.prePersist(); // This method must be called manually while using a StatelessSession.
        return statelessSession.insert(history)
                .replaceWith(history);
    }

    public Uni<List<NotificationHistory>> getNotificationHistory(String tenant, UUID endpoint) {
        String query = "SELECT NEW NotificationHistory(nh.id, nh.accountId, nh.invocationTime, nh.invocationResult, nh.eventId, nh.endpoint, nh.created) " +
                "FROM NotificationHistory nh WHERE nh.accountId = :accountId AND nh.endpoint.id = :endpointId";
        return session.createQuery(query, NotificationHistory.class)
                .setParameter("accountId", tenant)
                .setParameter("endpointId", endpoint)
                .getResultList();
    }

    public Uni<JsonObject> getNotificationDetails(String tenant, Query limiter, UUID endpoint, UUID historyId) {
        String query = "SELECT details FROM NotificationHistory WHERE accountId = :accountId AND endpoint.id = :endpointId AND id = :historyId";
        if (limiter != null) {
            query = limiter.getModifiedQuery(query);
        }

        Mutiny.Query<Map> mutinyQuery = session.createQuery(query, Map.class)
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
