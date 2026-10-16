package com.redhat.cloud.notifications.db;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.processors.email.SubscribedEventTypeSeverities;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class SubscriptionRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    ObjectMapper objectMapper;

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

    public Map<String, Set<SubscribedEventTypeSeverities>> getSubscriptionsByEventTypeWithSeverities(String orgId, UUID appId, SubscriptionType subscriptionType) {
        String query = "SELECT id.userId, eventType.id, severities FROM EventTypeEmailSubscription WHERE id.orgId = :orgId " +
            "AND eventType.application.id = :appId AND id.subscriptionType = :subscriptionType AND subscribed is true";

        List<Object[]> records = entityManager.createQuery(query, Object[].class)
            .setParameter("orgId", orgId)
            .setParameter("appId", appId)
            .setParameter("subscriptionType", subscriptionType)
            .getResultList();

        // group eventType and severity grouped by username
        Map<String, Set<SubscribedEventTypeSeverities>> map = records
            .stream()
            .collect(
                Collectors.groupingBy(elt -> (String) elt[0],
                    Collectors.mapping(
                        elt -> {
                            Map<Severity, Boolean> severities = new HashMap<>();
                            if (elt[2] != null) {
                                severities = objectMapper
                                    .convertValue(elt[2], new TypeReference<Map<Severity, Boolean>>() { });
                            } else {
                                for (Severity severity : Severity.values()) {
                                    severities.put(severity, true);
                                }
                            }
                            return new SubscribedEventTypeSeverities((UUID) elt[1], severities);
                        },
                        Collectors.toSet()
                    )
                )
            );
        return map;
    }

}
