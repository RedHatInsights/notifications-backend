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

    public Uni<NotificationHistory> createNotificationHistory(NotificationHistory history) {
        return Uni.createFrom().item(history)
                .onItem().transformToUni(session::persist)
                .call(session::flush)
                .replaceWith(history);
    }

    public Uni<List<NotificationHistory>> getNotificationHistory(String tenant, UUID endpoint, boolean includeDetails, Query limiter) {
        String query = "SELECT NEW NotificationHistory(nh.id, nh.accountId, nh.invocationTime, nh.invocationResult, nh.eventId, nh.endpoint, nh.created";
        if (includeDetails) {
            query += ", nh.details";
        }
        query += ") FROM NotificationHistory nh WHERE nh.accountId = :accountId AND nh.endpoint.id = :endpointId";

        if (limiter != null) {
            query = limiter.getModifiedQuery(query);
        }

        Mutiny.Query<NotificationHistory> historyQuery = session.createQuery(query, NotificationHistory.class)
                .setParameter("accountId", tenant)
                .setParameter("endpointId", endpoint);

        if (limiter != null && limiter.getLimit() != null && limiter.getLimit().getLimit() > 0) {
            historyQuery = historyQuery.setMaxResults(limiter.getLimit().getLimit())
                    .setFirstResult(limiter.getLimit().getOffset());
        }

        return historyQuery
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

    /**
     * Update a stub history item with data we have received from the Camel sender
     * @param jo Map containing the returned data
     * @return Nothing
     *
     * @see com.redhat.cloud.notifications.events.FromCamelHistoryFiller for the source of data
     */
    public Uni<Void> updateHistoryItem(Map<String, Object> jo) {

        String historyId = (String) jo.get("historyId");

        if (historyId == null || historyId.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("History Id is null"));
        }

        String outcome = (String) jo.get("outcome");
        boolean result = outcome == null ? false : outcome.startsWith("Success");
        Map details = (Map) jo.get("details");
        if (!details.containsKey("outcome")) {
            details.put("outcome", outcome);
        }
        Integer duration = (Integer) jo.get("duration");

        String updateQuery = "UPDATE NotificationHistory SET details = :details, invocationResult = :result, invocationTime= :invocationTime WHERE id = :id";
        return session.createQuery(updateQuery)
                .setParameter("details", details)
                .setParameter("result", result)
                .setParameter("id", UUID.fromString(historyId))
                .setParameter("invocationTime", (long) duration)
                .executeUpdate()
                .call(session::flush)
                .replaceWith(Uni.createFrom().voidItem());
    }
}
