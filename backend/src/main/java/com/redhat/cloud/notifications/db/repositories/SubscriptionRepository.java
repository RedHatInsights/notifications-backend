package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PSQLState;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.SubscriptionType.DRAWER;
import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;

@ApplicationScoped
public class SubscriptionRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    BackendConfig backendConfig;

    @Inject
    StatelessSession statelessSession;

    @Inject
    TemplateRepository templateRepository;

    public void subscribe(String orgId, String username, UUID eventTypeId, SubscriptionType subscriptionType) {
        updateSubscription(orgId, username, eventTypeId, subscriptionType, true);
    }

    public void unsubscribe(String orgId, String username, UUID eventTypeId, SubscriptionType subscriptionType) {
        updateSubscription(orgId, username, eventTypeId, subscriptionType, false);
    }

    @Transactional
    void updateSubscription(String orgId, String username, UUID eventTypeId, SubscriptionType subscriptionType, boolean subscribed) {

        if (subscribed && !backendConfig.isUseCommonTemplateModuleForUserPrefApisToggle()) {
            checkIfSubscriptionTypeIsSupportedForCurrentEventType(eventTypeId, subscriptionType);
        }

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

    private void checkIfSubscriptionTypeIsSupportedForCurrentEventType(UUID eventTypeId, SubscriptionType subscriptionType) {
        switch (subscriptionType) {
            case DAILY:
                templateRepository.checkIfExistAggregationEmailTemplatesByEventType(eventTypeId);
                break;
            case INSTANT:
                if (!backendConfig.isDefaultTemplateEnabled()) {
                    templateRepository.checkIfExistInstantEmailTemplateByEventType(eventTypeId);
                }
                break;
            case DRAWER:
                templateRepository.checkIfExistDrawerTemplateByEventType(eventTypeId);
                break;
            default:
                Log.infof("Subscription type %s not checked", subscriptionType);
                break;
        }
    }

    /**
     * Resubscribes all users to the event type identified by {@code eventTypeId}
     * if that event type is subscribed by default with subscriptions locked.
     * @param eventTypeId the event type identifier
     */
    @Transactional
    public void resubscribeAllUsersIfNeeded(UUID eventTypeId) {
        EventType eventType = entityManager.find(EventType.class, eventTypeId);
        if (eventType != null && eventType.isSubscribedByDefault() && eventType.isSubscriptionLocked()) {
            // We're not actually subscribing the users but rather removing any existing unsubscriptions from the DB.
            String hql = "DELETE FROM EventTypeEmailSubscription " +
                    "WHERE eventType.id = :eventTypeId AND NOT subscribed";
            entityManager.createQuery(hql)
                    .setParameter("eventTypeId", eventTypeId)
                    .executeUpdate();
        }
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
        if (backendConfig.isDrawerEnabled()) {
            return List.of(INSTANT, DAILY, DRAWER);
        } else {
            return List.of(INSTANT, DAILY);
        }
    }


    /**
     * Fetches user IDs from email subscriptions whose case is mixed.
     * @return a set of email subscription user IDs in mixed case.
     */
    public Set<String> findMixedCaseUserIds() {
        final String fetchDistinctMixedCaseUserIds =
            "SELECT " +
                "DISTINCT(user_id) " +
                "FROM " +
                "email_subscriptions " +
                "WHERE " +
                "LOWER(user_id) <> user_id ";

        return new HashSet<>(
            this.statelessSession
                .createNativeQuery(fetchDistinctMixedCaseUserIds, String.class)
                .getResultList()
        );
    }

    /**
     * Fetches the email subscriptions of the given user.
     * @param userId the user ID to fetch the subscriptions for.
     * @return the email subscriptions related to the given user ID.
     */
    public List<EventTypeEmailSubscription> findEmailSubscriptionsByUserId(final String userId) {
        final String fetchSql =
            "FROM " +
                "EventTypeEmailSubscription " +
                "WHERE " +
                "id.userId = :userId";

        return this.statelessSession
            .createQuery(fetchSql, EventTypeEmailSubscription.class)
            .setParameter("userId", userId)
            .getResultList();
    }

    /**
     * Sets the email subscription's user ID in lowercase. In the case that
     * there is a "primary key constraint violation", which signals that the
     * email subscription already exists, the given email subscription is
     * deleted instead.
     * @param eventTypeEmailSubscription the email subscription to which the
     *                                   user ID must be set in lowercase.
     */
    public void setEmailSubscriptionUserIdLowercase(final EventTypeEmailSubscription eventTypeEmailSubscription) {
        final String updateSql =
            "UPDATE " +
                "email_subscriptions " +
                "SET " +
                "user_id = LOWER(user_id) " +
                "WHERE " +
                "user_id = :userId AND " +
                "org_id = :orgId AND " +
                "event_type_id = :eventTypeId AND " +
                "subscription_type = :subscriptionType AND " +
                "subscribed = :isSubscribed";

        final Transaction updateTransaction = this.statelessSession.beginTransaction();
        try {
            this.statelessSession
                .createNativeQuery(updateSql)
                .setParameter("userId", eventTypeEmailSubscription.getUserId())
                .setParameter("orgId", eventTypeEmailSubscription.getOrgId())
                .setParameter("eventTypeId", eventTypeEmailSubscription.getEventType().getId())
                .setParameter("subscriptionType", eventTypeEmailSubscription.getSubscriptionType().name())
                .setParameter("isSubscribed", eventTypeEmailSubscription.isSubscribed())
                .executeUpdate();

            updateTransaction.commit();
            Log.debugf("[user_id: %s][org_id: %s][event_type_id: %s] Email subscription's user ID set to lowercase", eventTypeEmailSubscription.getUserId(), eventTypeEmailSubscription.getOrgId(), eventTypeEmailSubscription.getEventType().getId());
        } catch (final ConstraintViolationException e) {
            updateTransaction.rollback();

            if (PSQLState.UNIQUE_VIOLATION.getState().equals(e.getSQLState()) && "pk_email_subscriptions".equals(e.getConstraintName())) {
                final Transaction deleteTransaction = this.statelessSession.beginTransaction();

                this.statelessSession.delete(eventTypeEmailSubscription);

                deleteTransaction.commit();
                Log.debugf("[user_id: %s][org_id: %s][event_type_id: %s] Email subscription deleted", eventTypeEmailSubscription.getUserId(), eventTypeEmailSubscription.getOrgId(), eventTypeEmailSubscription.getEventType().getId());
            } else {
                throw e;
            }
        }
    }

    /**
     * Search org ids grouped by even type when at least one subscriber to instant email Notifications
     *
     * @param bundle Application bundle name
     * @param application Application name
     * @param eventTypes Event types
     * @return list of org id grouped by event types
     */
    public Map<String, List<String>> getOrgSubscriptionsPerEventType(String bundle, String application, List<String> eventTypes) {
        final String query = "SELECT et.name, string_agg(DISTINCT org_id, ',') " +
            "FROM email_subscriptions es JOIN event_type et ON event_type_id = et.id " +
            "WHERE es.subscribed is true AND EXISTS " +
            "(SELECT 1 FROM applications app JOIN bundles b ON app.bundle_id = b.id " +
            "WHERE b.name = :bundleName AND app.name = :applicationName and et.application_id = app.id and et.name in (:eventTypeNames)) group by et.name";
        List<Object[]> records = entityManager.createNativeQuery(query)
            .setParameter("bundleName", bundle)
            .setParameter("applicationName", application)
            .setParameter("eventTypeNames", eventTypes)
            .getResultList();

        return records.stream()
            .collect(Collectors
                .toMap(e -> e[0].toString(),
                    e -> List.of(e[1].toString().split(","))
                ));
    }
}
