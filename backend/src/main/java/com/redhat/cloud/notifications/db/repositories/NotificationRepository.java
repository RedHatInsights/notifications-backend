package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.vertx.core.json.JsonObject;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class NotificationRepository {

    public static final int MAX_NOTIFICATION_HISTORY_RESULTS = 500;

    private static final Logger LOGGER = Logger.getLogger(NotificationRepository.class);

    @Inject
    EntityManager entityManager;

    public List<NotificationHistory> getNotificationHistory(String tenant, UUID endpoint, boolean includeDetails, Query limiter) {
        String query = "SELECT NEW NotificationHistory(nh.id, nh.invocationTime, nh.invocationResult, nh.endpoint, nh.created";
        if (includeDetails) {
            query += ", nh.details";
        }
        query += ") FROM NotificationHistory nh WHERE nh.event.accountId = :accountId AND nh.endpoint.id = :endpointId";

        if (limiter != null) {
            query = limiter.getModifiedQuery(query);
        }

        TypedQuery<NotificationHistory> historyQuery = entityManager.createQuery(query, NotificationHistory.class)
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
    }

    public JsonObject getNotificationDetails(String tenant, UUID endpoint, UUID historyId) {
        String query = "SELECT details FROM NotificationHistory WHERE event.accountId = :accountId AND endpoint.id = :endpointId AND id = :historyId";
        try {
            Map<String, Object> map = entityManager.createQuery(query, Map.class)
                    .setParameter("accountId", tenant)
                    .setParameter("endpointId", endpoint)
                    .setParameter("historyId", historyId)
                    .getSingleResult();
            if (map == null) {
                return null;
            } else {
                return new JsonObject(map);
            }
        } catch (NoResultException e) {
            return null;
        }
    }
}
