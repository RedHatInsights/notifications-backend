package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.SubscriptionType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class SubscriptionRepository {

    @Inject
    EntityManager entityManager;

    public List<String> getSubscribers(String orgId, UUID eventTypeId, SubscriptionType subscriptionType) {
        return getSubscriptions(orgId, eventTypeId, subscriptionType, true);
    }

    public List<String> getUnsubscribers(String orgId, UUID eventTypeId, SubscriptionType subscriptionType) {
        return getSubscriptions(orgId, eventTypeId, subscriptionType, false);
    }

    private List<String> getSubscriptions(String orgId, UUID eventTypeId, SubscriptionType subscriptionType, boolean subscribed) {
        String hql = "SELECT id.userId FROM EventTypeEmailSubscription WHERE id.orgId = :orgId AND id.subscriptionType = :subscriptionType " +
                "AND eventType.id = :eventTypeId AND subscribed = :subscribed";
        return entityManager.createQuery(hql, String.class)
                .setParameter("orgId", orgId)
                .setParameter("subscriptionType", subscriptionType)
                .setParameter("eventTypeId", eventTypeId)
                .setParameter("subscribed", subscribed)
                .getResultList();
    }

    public Map<String, Set<String>> getSubscribersByEventType(String orgId, UUID appId, SubscriptionType subscriptionType) {
        return getSubscriptionsByEventType(orgId, appId, subscriptionType, true);
    }

    public Map<String, Set<String>> getUnsubscribersByEventType(String orgId, UUID appId, SubscriptionType subscriptionType) {
        return getSubscriptionsByEventType(orgId, appId, subscriptionType, false);
    }

    public Map<String, Set<String>> getSubscriptionsByEventType(String orgId, UUID appId, SubscriptionType subscriptionType, boolean subscribed) {
        String query = "SELECT eventType.name, es.id.userId FROM EventTypeEmailSubscription es WHERE id.orgId = :orgId " +
                "AND eventType.application.id = :appId AND id.subscriptionType = :subscriptionType AND subscribed = :subscribed";

        List<Object[]> records = entityManager.createQuery(query, Object[].class)
            .setParameter("orgId", orgId)
            .setParameter("appId", appId)
            .setParameter("subscriptionType", subscriptionType)
            .setParameter("subscribed", subscribed)
            .getResultList();

        // group userIds by eventType name
        Map<String, Set<String>> map = records
            .stream()
            .collect(
                Collectors.groupingBy(elt -> (String) elt[0],
                    Collectors.mapping(
                        elt -> (String)  elt[1],
                        Collectors.toSet()
                    )
                )
            );
        return map;
    }


}
