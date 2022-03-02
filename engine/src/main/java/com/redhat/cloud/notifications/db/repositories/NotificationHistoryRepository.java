package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.session.StatelessSessionFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class NotificationHistoryRepository {

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    public NotificationHistory createNotificationHistory(NotificationHistory history) {
        history.prePersist(); // This method must be called manually while using a StatelessSession.
        statelessSessionFactory.getOrCreateSession().insert(history);
        return history;
    }

    /**
     * Update a stub history item with data we have received from the Camel sender
     *
     * @param jo Map containing the returned data
     * @return Nothing
     * @see com.redhat.cloud.notifications.events.FromCamelHistoryFiller for the source of data
     */
    public void updateHistoryItem(Map<String, Object> jo) {

        String historyId = (String) jo.get("historyId");

        if (historyId == null || historyId.isBlank()) {
            throw new IllegalArgumentException("History Id is null");
        }

        String outcome = (String) jo.get("outcome");
        boolean result = outcome == null ? false : outcome.startsWith("Success");
        Map details = (Map) jo.get("details");
        if (!details.containsKey("outcome")) {
            details.put("outcome", outcome);
        }
        Integer duration = (Integer) jo.get("duration");

        String updateQuery = "UPDATE NotificationHistory SET details = :details, invocationResult = :result, invocationTime= :invocationTime WHERE id = :id";
        statelessSessionFactory.getOrCreateSession().createQuery(updateQuery)
                .setParameter("details", details)
                .setParameter("result", result)
                .setParameter("id", UUID.fromString(historyId))
                .setParameter("invocationTime", (long) duration)
                .executeUpdate();
    }

    public Endpoint getEndpointForHistoryId(String historyId) {

        String query = "SELECT e from Endpoint e, NotificationHistory h WHERE h.id = :id AND e.id = h.endpoint.id";
        UUID hid = UUID.fromString(historyId);

        try {
            return statelessSessionFactory.getOrCreateSession().createQuery(query, Endpoint.class)
                    .setParameter("id", hid)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}
