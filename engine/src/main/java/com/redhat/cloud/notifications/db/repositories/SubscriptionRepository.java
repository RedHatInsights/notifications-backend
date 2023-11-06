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

    public Map<String, Set<String>> getEmailSubscribersUserIdGroupedByEventType(String orgId, String bundleName, String applicationName, SubscriptionType subscriptionType) {
        // TODO Replace `subscribed` with `subscribed IS TRUE` when Quarkus depends on Hibernate ORM 6.3.0 or newer.
        String query = "SELECT eventType.name, es.id.userId FROM EventTypeEmailSubscription es WHERE id.orgId = :orgId AND eventType.application.bundle.name = :bundleName " +
            "AND eventType.application.name = :applicationName AND id.subscriptionType = :subscriptionType AND subscribed";

        List<Object[]> records = entityManager.createQuery(query)
            .setParameter("orgId", orgId)
            .setParameter("bundleName", bundleName)
            .setParameter("applicationName", applicationName)
            .setParameter("subscriptionType", subscriptionType)
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
