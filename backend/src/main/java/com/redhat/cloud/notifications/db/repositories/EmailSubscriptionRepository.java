package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscriptionId;
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
public class EmailSubscriptionRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    FeatureFlipper featureFlipper;

    public int subscribeEventType(String orgId, String username, UUID eventTypeId, SubscriptionType subscriptionType) {
        // Opt-in: only subscriptions are stored on database
        // Opt-on: only un-subscriptions are stored on database
        if (!subscriptionType.isSubscribedByDefault()) {
            return addEventTypeSubscription(orgId, username, eventTypeId, subscriptionType, true);
        } else {
            return deleteEventTypeSubscription(orgId, username, eventTypeId, subscriptionType);
        }
    }

    @Transactional
    public int addEventTypeSubscription(String orgId, String username, UUID eventTypeId, SubscriptionType subscriptionType, boolean subscribed) {
        String query = "INSERT INTO email_subscriptions(org_id, user_id, event_type_id, subscription_type, subscribed) " +
            "VALUES (:orgId, :userId, :eventTypeId, :subscriptionType, :subscribed) " +
            "ON CONFLICT (org_id, user_id, event_type_id, subscription_type) DO NOTHING"; // The value is already on the database, this is OK

        // HQL does not support the ON CONFLICT clause so we need a native query here
        return entityManager.createNativeQuery(query)
            .setParameter("orgId", orgId)
            .setParameter("userId", username)
            .setParameter("eventTypeId", eventTypeId)
            .setParameter("subscriptionType", subscriptionType.name())
            .setParameter("subscribed", subscribed)
            .executeUpdate();
    }

    public int unsubscribeEventType(String orgId, String userId, UUID eventTypeId, SubscriptionType subscriptionType) {
        // Opt-in: only subscriptions are stored on database
        // Opt-on: only un-subscriptions are stored on database
        if (!subscriptionType.isSubscribedByDefault()) {
            return deleteEventTypeSubscription(orgId, userId, eventTypeId, subscriptionType);
        } else {
            return addEventTypeSubscription(orgId, userId, eventTypeId, subscriptionType, false);
        }
    }

    @Transactional
    public int deleteEventTypeSubscription(String orgId, String userId, UUID eventTypeId, SubscriptionType subscriptionType) {
        String query = "DELETE FROM EventTypeEmailSubscription WHERE id = :Id";
        return entityManager.createQuery(query)
            .setParameter("Id", new EventTypeEmailSubscriptionId(orgId, userId, eventTypeId, subscriptionType))
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
