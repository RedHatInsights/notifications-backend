package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.NotificationHistory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class NotificationHistoryRepository {

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    public NotificationHistory createNotificationHistory(NotificationHistory history) {
        history.prePersist(); // This method must be called manually while using a StatelessSession.
        statelessSessionFactory.getCurrentSession().insert(history);
        return history;
    }

    /**
     * Update a stub history item with data we have received from the Camel sender
     *
     * @param jo Map containing the returned data
     * @return Nothing
     * @see com.redhat.cloud.notifications.events.FromCamelHistoryFiller for the source of data
     */
    @Transactional
    public void updateHistoryItem(Map<String, Object> jo) {

        String historyId = (String) jo.get("historyId");

        if (historyId == null || historyId.isBlank()) {
            throw new IllegalArgumentException("History Id is null");
        }

        String outcome = (String) jo.get("outcome");
        // TODO NOTIF-636 Remove oldResult after the Eventing team is done integrating with the new way to determine the success.
        boolean oldResult = outcome != null && outcome.startsWith("Success");
        boolean result = oldResult || jo.containsKey("successful") && ((Boolean) jo.get("successful"));
        Map details = (Map) jo.get("details");
        if (!details.containsKey("outcome")) {
            details.put("outcome", outcome);
        }
        Integer duration = (Integer) jo.get("duration");

        String updateQuery = "UPDATE NotificationHistory SET details = :details, invocationResult = :result, invocationTime= :invocationTime WHERE id = :id";
        statelessSessionFactory.getCurrentSession().createQuery(updateQuery)
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
            return statelessSessionFactory.getCurrentSession().createQuery(query, Endpoint.class)
                    .setParameter("id", hid)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}
