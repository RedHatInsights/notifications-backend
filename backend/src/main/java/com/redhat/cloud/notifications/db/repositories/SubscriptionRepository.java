package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.models.SubscriptionType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.SubscriptionType.DRAWER;
import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;

@ApplicationScoped
public class SubscriptionRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    FeatureFlipper featureFlipper;

    public void subscribe(String orgId, String username, UUID eventTypeId, SubscriptionType subscriptionType) {
        updateSubscription(orgId, username, eventTypeId, subscriptionType, true);
    }

    public void unsubscribe(String orgId, String username, UUID eventTypeId, SubscriptionType subscriptionType) {
        updateSubscription(orgId, username, eventTypeId, subscriptionType, false);
    }

    @Transactional
    void updateSubscription(String orgId, String username, UUID eventTypeId, SubscriptionType subscriptionType, boolean subscribed) {
        // We're performing an upsert to update the user subscription.
        String sql = "INSERT INTO email_subscriptions(org_id, user_id, event_type_id, subscription_type, subscribed) " +
            "VALUES (:orgId, :userId, :eventTypeId, :subscriptionType, :subscribed) " +
            "ON CONFLICT (org_id, user_id, event_type_id, subscription_type) DO UPDATE SET subscribed = :subscribed";

        // HQL does not support the ON CONFLICT clause, so we need a native query here
        entityManager.createNativeQuery(sql)
            .setParameter("orgId", orgId)
            .setParameter("userId", username)
            .setParameter("eventTypeId", eventTypeId)
            .setParameter("subscriptionType", subscriptionType.name())
            .setParameter("subscribed", subscribed)
            .executeUpdate();
    }

    public List<EventTypeEmailSubscription> getEmailSubscriptionByEventType(String orgId, String username, String bundleName, String applicationName) {
        String query = "SELECT es FROM EventTypeEmailSubscription es LEFT JOIN FETCH es.eventType ev LEFT JOIN FETCH ev.application a LEFT JOIN FETCH a.bundle b " +
            "WHERE es.id.orgId = :orgId AND es.id.userId = :userId and es.id.subscriptionType in (:subscriptionTypes) " +
            "AND b.name = :bundleName AND a.name = :applicationName";

        return entityManager.createQuery(query, EventTypeEmailSubscription.class)
            .setParameter("orgId", orgId)
            .setParameter("userId", username)
            .setParameter("bundleName", bundleName)
            .setParameter("applicationName", applicationName)
            .setParameter("subscriptionTypes", getAvailableTypes())
            .getResultList();
    }

    public List<EventTypeEmailSubscription> getEmailSubscriptionsPerEventTypeForUser(String orgId, String username) {
        String query = "SELECT es FROM EventTypeEmailSubscription es LEFT JOIN FETCH es.eventType ev LEFT JOIN FETCH ev.application a LEFT JOIN FETCH a.bundle b " +
            "WHERE es.id.orgId = :orgId AND es.id.userId = :userId and es.id.subscriptionType in (:subscriptionTypes)";
        return entityManager.createQuery(query, EventTypeEmailSubscription.class)
            .setParameter("orgId", orgId)
            .setParameter("userId", username)
            .setParameter("subscriptionTypes", getAvailableTypes())
            .getResultList();
    }

    private List<SubscriptionType> getAvailableTypes() {
        if (featureFlipper.isDrawerEnabled()) {
            return List.of(INSTANT, DAILY, DRAWER);
        } else {
            return List.of(INSTANT, DAILY);
        }
    }
}
