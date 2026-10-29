package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.SubscriptionType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SubscriptionChangeLogRepository {

    @Inject
    EntityManager entityManager;

    @Transactional
    public void logChange(String orgId, String userId, UUID eventTypeId, SubscriptionType subscriptionType, boolean subscribed) {
        String sql = """
            INSERT INTO subscription_change_log (id, org_id, user_id, event_type_id, subscription_type, subscribed)
            VALUES (:id, :orgId, :userId, :eventTypeId, :subscriptionType, :subscribed)
            """;

        entityManager.createNativeQuery(sql)
            .setParameter("id", UUID.randomUUID())
            .setParameter("orgId", orgId)
            .setParameter("userId", userId)
            .setParameter("eventTypeId", eventTypeId)
            .setParameter("subscriptionType", subscriptionType.name())
            .setParameter("subscribed", subscribed)
            .executeUpdate();
    }

    public List<Object[]> countUnsubscribesByEventType(OffsetDateTime since) {
        String sql = """
            SELECT scl.event_type_id, et.display_name, COUNT(*)
            FROM subscription_change_log scl
            JOIN event_type et ON et.id = scl.event_type_id
            WHERE scl.subscribed = false
              AND scl.changed_at >= :since
            GROUP BY scl.event_type_id, et.display_name
            ORDER BY COUNT(*) DESC
            """;

        return entityManager.createNativeQuery(sql)
            .setParameter("since", since)
            .getResultList();
    }
}
