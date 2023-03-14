package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscriptionId;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class EmailSubscriptionRepository {

    @Inject
    EntityManager entityManager;

    @Transactional
    public boolean subscribe(String accountId, String orgId, String username, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "INSERT INTO endpoint_email_subscriptions(account_id, org_id, user_id, application_id, subscription_type) " +
            "SELECT :accountId, :orgId, :userId, a.id, :subscriptionType " +
            "FROM applications a, bundles b WHERE a.bundle_id = b.id AND a.name = :applicationName AND b.name = :bundleName " +
            "ON CONFLICT (org_id, user_id, application_id, subscription_type) DO NOTHING"; // The value is already on the database, this is OK
        // HQL does not support the ON CONFLICT clause so we need a native query here
        entityManager.createNativeQuery(query)
            .setParameter("accountId", accountId)
            .setParameter("orgId", orgId)
            .setParameter("userId", username)
            .setParameter("bundleName", bundleName)
            .setParameter("applicationName", applicationName)
            .setParameter("subscriptionType", subscriptionType.name())
            .executeUpdate();

        replicateSubscribeToEventTypeLevel(orgId, username, bundleName, applicationName, subscriptionType);
        return true;
    }

    @Transactional
    public boolean unsubscribe(String orgId, String username, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "DELETE FROM EmailSubscription WHERE id.orgId = :orgId AND id.userId = :userId " +
            "AND id.applicationId = (SELECT a.id FROM Application a, Bundle b WHERE a.bundle.id = b.id " +
            "AND b.name = :bundleName AND a.name = :applicationName) AND id.subscriptionType = :subscriptionType";
        entityManager.createQuery(query)
            .setParameter("orgId", orgId)
            .setParameter("userId", username)
            .setParameter("bundleName", bundleName)
            .setParameter("applicationName", applicationName)
            .setParameter("subscriptionType", subscriptionType)
            .executeUpdate();

        replicateUnsubscribeToEventTypeLevel(orgId, username, subscriptionType, bundleName, applicationName);
        return true;
    }

    @Transactional
    protected int replicateSubscribeToEventTypeLevel(String orgId, String username, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "INSERT INTO email_subscriptions (user_id, org_id, event_type_id, subscription_type) " +
            "SELECT :userId, :orgId, et.id, :subscriptionType FROM applications app " +
            "JOIN bundles bun ON app.bundle_id = bun.id " +
            "JOIN event_type et ON app.id = et.application_id  " +
            "WHERE app.name = :applicationName AND bun.name = :bundleName " +
            "ON CONFLICT (org_id, user_id, event_type_id, subscription_type) DO NOTHING"; // The value is already on the database, this is OK

        return entityManager.createNativeQuery(query)
            .setParameter("orgId", orgId)
            .setParameter("userId", username)
            .setParameter("applicationName", applicationName)
            .setParameter("bundleName", bundleName)
            .setParameter("subscriptionType", subscriptionType.name())
            .executeUpdate();
    }

    @Transactional
    protected int replicateUnsubscribeToEventTypeLevel(String orgId, String username, EmailSubscriptionType subscriptionType, String bundleName, String applicationName) {
        String query = "DELETE FROM EventTypeEmailSubscription WHERE id.orgId = :orgId AND id.userId = :userId " +
            "AND id.eventTypeId in (SELECT ev.id FROM EventType ev, Application a, Bundle b WHERE a.bundle.id = b.id and ev.application.id = a.id " +
            "AND b.name = :bundleName AND a.name = :applicationName) AND id.subscriptionType = :subscriptionType";
        return entityManager.createQuery(query)
            .setParameter("orgId", orgId)
            .setParameter("userId", username)
            .setParameter("bundleName", bundleName)
            .setParameter("applicationName", applicationName)
            .setParameter("subscriptionType", subscriptionType)
            .executeUpdate();
    }

    @Transactional
    public int subscribeEventType(String orgId, String username, UUID eventTypeId, EmailSubscriptionType subscriptionType) {
        String query = "INSERT INTO email_subscriptions(org_id, user_id, event_type_id, subscription_type) " +
            "VALUES (:orgId, :userId, :eventTypeId, :subscriptionType ) " +
            "ON CONFLICT (org_id, user_id, event_type_id, subscription_type) DO NOTHING"; // The value is already on the database, this is OK

        // HQL does not support the ON CONFLICT clause so we need a native query here
        return entityManager.createNativeQuery(query)
            .setParameter("orgId", orgId)
            .setParameter("userId", username)
            .setParameter("eventTypeId", eventTypeId)
            .setParameter("subscriptionType", subscriptionType.name())
            .executeUpdate();
    }

    @Transactional
    public int unsubscribeEventType(String orgId, String userId, UUID eventTypeId, EmailSubscriptionType subscriptionType) {
        String query = "DELETE FROM EventTypeEmailSubscription WHERE id = :Id";
        return entityManager.createQuery(query)
            .setParameter("Id", new EventTypeEmailSubscriptionId(orgId, userId, eventTypeId, subscriptionType))
            .executeUpdate();
    }

    public List<EventTypeEmailSubscription> getEmailSubscriptionByEventType(String orgId, String username, String bundleName, String applicationName) {
        String query = "SELECT es FROM EventTypeEmailSubscription es LEFT JOIN FETCH es.eventType ev LEFT JOIN FETCH ev.application a LEFT JOIN FETCH a.bundle b " +
            "WHERE es.id.orgId = :orgId AND es.id.userId = :userId " +
            "AND b.name = :bundleName AND a.name = :applicationName";

        return entityManager.createQuery(query, EventTypeEmailSubscription.class)
            .setParameter("orgId", orgId)
            .setParameter("userId", username)
            .setParameter("bundleName", bundleName)
            .setParameter("applicationName", applicationName)
            .getResultList();
    }

    public EmailSubscription getEmailSubscription(String orgId, String username, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "SELECT es FROM EmailSubscription es LEFT JOIN FETCH es.application a LEFT JOIN FETCH a.bundle b " +
                "WHERE es.id.orgId = :orgId AND es.id.userId = :userId " +
                "AND b.name = :bundleName AND a.name = :applicationName AND es.id.subscriptionType = :subscriptionType";
        try {
            return entityManager.createQuery(query, EmailSubscription.class)
                    .setParameter("orgId", orgId)
                    .setParameter("userId", username)
                    .setParameter("bundleName", bundleName)
                    .setParameter("applicationName", applicationName)
                    .setParameter("subscriptionType", subscriptionType)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public List<EmailSubscription> getEmailSubscriptionsForUser(String orgId, String username) {
        String query = "SELECT es FROM EmailSubscription es LEFT JOIN FETCH es.application a LEFT JOIN FETCH a.bundle b " +
                "WHERE es.id.orgId = :orgId AND es.id.userId = :userId";
        return entityManager.createQuery(query, EmailSubscription.class)
                .setParameter("orgId", orgId)
                .setParameter("userId", username)
                .getResultList();
    }

    public List<EventTypeEmailSubscription> getEmailSubscriptionsPerEventTypeForUser(String orgId, String username) {
        String query = "SELECT es FROM EventTypeEmailSubscription es LEFT JOIN FETCH es.eventType ev LEFT JOIN FETCH ev.application a LEFT JOIN FETCH a.bundle b " +
            "WHERE es.id.orgId = :orgId AND es.id.userId = :userId";
        return entityManager.createQuery(query, EventTypeEmailSubscription.class)
            .setParameter("orgId", orgId)
            .setParameter("userId", username)
            .getResultList();
    }
}
