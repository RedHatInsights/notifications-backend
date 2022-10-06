package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.converters.EndpointTypeConverter;
import com.redhat.cloud.notifications.db.converters.NotificationHistoryDetailsConverter;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.NotificationHistory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
public class NotificationHistoryRepository {

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @Transactional
    public void createNotificationHistory(NotificationHistory history) {
        /*
         * The following query contains a subquery that retrieves the ID of the endpoint if it still exists. There's no
         * guarantee the endpoint will still exist in the DB at the time when the history is written. If it's gone, then
         * the subquery will return null.
         */
        String hql = "INSERT INTO notification_history (id, invocation_time, invocation_result, status, details, event_id, endpoint_type, endpoint_sub_type, created, endpoint_id) " +
                "VALUES (:id, :invocationTime, :invocationResult, :status, :details, :eventId, :endpointType, :endpointSubType, :created, " +
                "(SELECT id FROM endpoints WHERE id = :endpointId))";
        history.prePersist();
        statelessSessionFactory.getCurrentSession().createNativeQuery(hql)
                .setParameter("id", history.getId())
                .setParameter("invocationTime", history.getInvocationTime())
                .setParameter("invocationResult", history.isInvocationResult())
                .setParameter("status", history.getStatus().toString())
                .setParameter("details", new NotificationHistoryDetailsConverter().convertToDatabaseColumn(history.getDetails()))
                .setParameter("eventId", history.getEvent().getId())
                .setParameter("endpointType", new EndpointTypeConverter().convertToDatabaseColumn(history.getEndpointType()))
                .setParameter("endpointSubType", history.getEndpointSubType())
                .setParameter("created", history.getCreated())
                .setParameter("endpointId", history.getEndpoint().getId())
                .executeUpdate();
    }

    /**
     * Update a stub history item with data we have received from the Camel sender
     *
     * @param jo Map containing the returned data
     * @return Nothing
     * @see com.redhat.cloud.notifications.events.FromCamelHistoryFiller for the source of data
     */
    @Transactional
    public boolean updateHistoryItem(NotificationHistory notificationHistory) {
        String updateQuery = "UPDATE NotificationHistory SET details = :details, invocationResult = :result, status = :status, invocationTime = :invocationTime WHERE id = :id";
        int count = statelessSessionFactory.getCurrentSession().createQuery(updateQuery)
                .setParameter("details", notificationHistory.getDetails())
                .setParameter("result", notificationHistory.isInvocationResult())
                .setParameter("status", notificationHistory.getStatus())
                .setParameter("id", notificationHistory.getId())
                .setParameter("invocationTime", notificationHistory.getInvocationTime())
                .executeUpdate();

        if (count == 0) {
            throw new NoResultException("Update returned no rows");
        } else if (count > 1) {
            throw new IllegalStateException("Update count was " + count);
        }

        return true;
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
