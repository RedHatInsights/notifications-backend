package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.converters.NotificationHistoryDetailsConverter;
import com.redhat.cloud.notifications.events.ConnectorReceiver;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationHistory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class NotificationHistoryRepository {

    @Inject
    EntityManager entityManager;

    @Transactional
    public void createNotificationHistory(NotificationHistory history) {
        /*
         * The following query contains a subquery that retrieves the ID of the endpoint if it still exists. There's no
         * guarantee the endpoint will still exist in the DB at the time when the history is written. If it's gone, then
         * the subquery will return null.
         */
        String hql = "INSERT INTO notification_history (id, invocation_time, invocation_result, status, details, event_id, endpoint_type_v2, endpoint_sub_type, created, endpoint_id) " +
                "VALUES (:id, :invocationTime, :invocationResult, :status, :details, :eventId, :endpointType, :endpointSubType, :created, " +
                "(SELECT id FROM endpoints WHERE id = :endpointId))";
        history.prePersist();
        entityManager.createNativeQuery(hql)
                .setParameter("id", history.getId())
                .setParameter("invocationTime", history.getInvocationTime())
                .setParameter("invocationResult", history.isInvocationResult())
                .setParameter("status", history.getStatus().toString())
                .setParameter("details", new NotificationHistoryDetailsConverter().convertToDatabaseColumn(history.getDetails()))
                .setParameter("eventId", history.getEvent().getId())
                .setParameter("endpointType", history.getEndpointType().name())
                .setParameter("endpointSubType", history.getEndpointSubType())
                .setParameter("created", history.getCreated())
                .setParameter("endpointId", history.getEndpoint().getId())
                .executeUpdate();
    }

    /**
     * Update a stub history item with data we have received from the Camel sender
     *
     * @see ConnectorReceiver
     */
    @Transactional
    public boolean updateHistoryItem(NotificationHistory notificationHistory) {
        String hql = "UPDATE NotificationHistory " +
                "SET details = :details, invocationResult = :result, status = :status, invocationTime = :invocationTime " +
                "WHERE id = :id";
        int count = entityManager.createQuery(hql)
                .setParameter("details", notificationHistory.getDetails())
                .setParameter("result", notificationHistory.isInvocationResult())
                .setParameter("status", notificationHistory.getStatus())
                .setParameter("id", notificationHistory.getId())
                .setParameter("invocationTime", notificationHistory.getInvocationTime())
                .executeUpdate();
        return count > 0;
    }

    public Endpoint getEndpointForHistoryId(String historyId) {

        String query = "SELECT e from Endpoint e, NotificationHistory h WHERE h.id = :id AND e.id = h.endpoint.id";
        UUID hid = UUID.fromString(historyId);

        try {
            return entityManager.createQuery(query, Endpoint.class)
                    .setParameter("id", hid)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public Event getEventIdFromHistoryId(String historyId) {

        String query = "SELECT e from Event e, NotificationHistory h WHERE h.id = :id AND e.id = h.event.id";
        UUID hid = UUID.fromString(historyId);

        try {
            return entityManager.createQuery(query, Event.class)
                .setParameter("id", hid)
                .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}
