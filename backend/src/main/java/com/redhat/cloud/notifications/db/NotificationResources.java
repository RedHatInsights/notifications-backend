package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.NotificationHistory;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class NotificationResources {

    public static final int MAX_NOTIFICATION_HISTORY_RESULTS = 500;

    private static final Logger LOGGER = Logger.getLogger(NotificationResources.class);

    @Inject
    Mutiny.SessionFactory sessionFactory;

    public Uni<NotificationHistory> createNotificationHistory(NotificationHistory history) {
        history.prePersist(); // This method must be called manually while using a StatelessSession.
        return sessionFactory.withStatelessSession(statelessSession -> {
            return statelessSession.insert(history)
                    .replaceWith(history);
        });
    }

    public Uni<List<NotificationHistory>> getNotificationHistory(String tenant, UUID endpoint, boolean includeDetails, Query limiter) {
        return sessionFactory.withSession(session -> {
            String query = "SELECT NEW NotificationHistory(nh.id, nh.invocationTime, nh.invocationResult, nh.endpoint, nh.created";
            if (includeDetails) {
                query += ", nh.details";
            }
            query += ") FROM NotificationHistory nh WHERE nh.event.accountId = :accountId AND nh.endpoint.id = :endpointId";

            if (limiter != null) {
                query = limiter.getModifiedQuery(query);
            }

            Mutiny.Query<NotificationHistory> historyQuery = session.createQuery(query, NotificationHistory.class)
                    .setParameter("accountId", tenant)
                    .setParameter("endpointId", endpoint)
                    // Default limit to prevent OutOfMemoryError, it may be overridden below.
                    .setMaxResults(MAX_NOTIFICATION_HISTORY_RESULTS);

            if (limiter != null && limiter.getLimit() != null && limiter.getLimit().getLimit() > 0) {
                if (limiter.getLimit().getLimit() > MAX_NOTIFICATION_HISTORY_RESULTS) {
                    LOGGER.debugf("Too many notification history entries requested (%d), the default max limit (%d) will be enforced",
                            limiter.getLimit().getLimit(), MAX_NOTIFICATION_HISTORY_RESULTS);
                } else {
                    historyQuery = historyQuery.setMaxResults(limiter.getLimit().getLimit())
                            .setFirstResult(limiter.getLimit().getOffset());
                }
            }

            return historyQuery
                    .getResultList();
        });
    }

    public Uni<JsonObject> getNotificationDetails(String tenant, UUID endpoint, UUID historyId) {
        String query = "SELECT details FROM NotificationHistory WHERE event.accountId = :accountId AND endpoint.id = :endpointId AND id = :historyId";
        return sessionFactory.withSession(session -> {
            return session.createQuery(query, Map.class)
                    .setParameter("accountId", tenant)
                    .setParameter("endpointId", endpoint)
                    .setParameter("historyId", historyId)
                    .getSingleResultOrNull()
                    .onItem().ifNotNull().transform(JsonObject::new);
        });
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
        return sessionFactory.withStatelessSession(statelessSession -> {
            return statelessSession.createQuery(updateQuery)
                    .setParameter("details", details)
                    .setParameter("result", result)
                    .setParameter("id", UUID.fromString(historyId))
                    .setParameter("invocationTime", (long) duration)
                    .executeUpdate()
                    .replaceWith(Uni.createFrom().voidItem());
        });
    }
}
