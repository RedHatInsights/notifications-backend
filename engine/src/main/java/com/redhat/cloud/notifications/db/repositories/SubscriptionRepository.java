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

    public Map<String, Map<Boolean, Set<String>>> getSubscriptionsByEventType(String orgId, String bundleName, String applicationName, SubscriptionType subscriptionType) {
        String query = "SELECT eventType.name, subscribed, es.id.userId FROM EventTypeEmailSubscription es WHERE id.orgId = :orgId AND eventType.application.bundle.name = :bundleName " +
                "AND eventType.application.name = :applicationName AND id.subscriptionType = :subscriptionType";

        List<Object[]> records = entityManager.createQuery(query, Object[].class)
                .setParameter("orgId", orgId)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("subscriptionType", subscriptionType)
                .getResultList();

        return records.stream()
                .collect(
                        Collectors.groupingBy(record -> (String) record[0],
                                Collectors.groupingBy(record -> (Boolean) record[1],
                                        Collectors.mapping(
                                            record -> (String) record[2],
                                            Collectors.toSet()
                                    )
                            )
                        )
                );
    }
}
