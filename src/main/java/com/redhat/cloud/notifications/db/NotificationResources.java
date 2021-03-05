package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class NotificationResources {

    @Inject
    Mutiny.Session session;

    public Uni<NotificationHistory> createNotificationHistory(NotificationHistory history) {
        return Uni.createFrom().item(history)
                .onItem().transform(this::addEndpointReference)
                .onItem().transformToUni(session::persist)
                .call(session::flush)
                .replaceWith(history);
    }

    public Multi<NotificationHistory> getNotificationHistory(String tenant, UUID endpoint) {
        String query = "SELECT NEW NotificationHistory(nh.id, nh.accountId, nh.invocationTime, nh.invocationResult, nh.eventId, nh.endpoint, nh.created) " +
                "FROM NotificationHistory nh WHERE nh.accountId = :accountId AND nh.endpoint.id = :endpointId";
        return session.createQuery(query, NotificationHistory.class)
                .setParameter("accountId", tenant)
                .setParameter("endpointId", endpoint)
                .getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable);
    }

    public Uni<JsonObject> getNotificationDetails(String tenant, Query limiter, UUID endpoint, Integer historyId) {
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

    /**
     * Adds to the given {@link NotificationHistory} a reference to a persistent {@link Endpoint} without actually
     * loading its state from the database. The notification history will remain unchanged if it does not contain
     * a non-null endpoint identifier.
     *
     * @param history the notification history that will hold the endpoint reference
     * @return the same notification history instance, possibly modified if an endpoint reference was added
     */
    private NotificationHistory addEndpointReference(NotificationHistory history) {
        if (history.getEndpointId() != null && history.getEndpoint() == null) {
            history.setEndpoint(session.getReference(Endpoint.class, history.getEndpointId()));
        }
        return history;
    }
}
