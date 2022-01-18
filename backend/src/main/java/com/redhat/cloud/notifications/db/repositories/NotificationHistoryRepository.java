package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;
import java.util.UUID;

// TODO: Move this class to notifications-engine.

@ApplicationScoped
public class NotificationHistoryRepository {

    @Inject
    Mutiny.SessionFactory sessionFactory;

    public Uni<NotificationHistory> createNotificationHistory(NotificationHistory history) {
        history.prePersist(); // This method must be called manually while using a StatelessSession.
        return sessionFactory.withStatelessSession(statelessSession -> {
            return statelessSession.insert(history)
                    .replaceWith(history);
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

    public Uni<Endpoint> getEndpointForHistoryId(String historyId) {

        String query = "SELECT e from Endpoint e, NotificationHistory h WHERE h.id = :id AND e.id = h.endpoint.id";
        UUID hid = UUID.fromString(historyId);

        return sessionFactory.withStatelessSession(statelessSession -> statelessSession.createQuery(query, Endpoint.class)
                .setParameter("id", hid)
                .getSingleResultOrNull());
    }
}
