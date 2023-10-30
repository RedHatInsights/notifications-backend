package com.redhat.cloud.notifications.recipients;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

@ApplicationScoped
public class SubscriptionRepository {

    @Inject
    EntityManager entityManager;

    @CacheResult(cacheName = "et")
    public boolean isEventTypeSubscribedByDefault(UUID eventTypeId) {
        String hql = "SELECT subscribed_by_default FROM event_type WHERE id = :eventTypeId";
        return entityManager.createNamedQuery(hql, boolean.class)
                .setParameter("eventTypeId", eventTypeId)
                .getSingleResult();
    }

    public Set<String> getSubscribers(String orgId, UUID eventTypeId, SubscriptionType subscriptionType) {
        return getSubscriptions(orgId, eventTypeId, subscriptionType, true);
    }

    public Set<String> getUnsubscribers(String orgId, UUID eventTypeId, SubscriptionType subscriptionType) {
        return getSubscriptions(orgId, eventTypeId, subscriptionType, false);
    }

    private Set<String> getSubscriptions(String orgId, UUID eventTypeId, SubscriptionType subscriptionType, boolean subscribed) {
        String hql = "SELECT user_id FROM email_subscriptions WHERE org_id = :orgId AND subscription_type = :subscriptionType " +
                "AND event_type_id = :eventTypeId AND subscribed = :subscribed";
        return entityManager.createNamedQuery(hql, String.class)
                .setParameter("orgId", orgId)
                .setParameter("subscriptionType", subscriptionType)
                .setParameter("eventTypeId", eventTypeId)
                .setParameter("subscribed", subscribed)
                .getResultStream()
                .collect(toSet());
    }
}
