package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.PagerDutyProperties;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.WebhookProperties;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.EndpointStatus.READY;
import static com.redhat.cloud.notifications.models.EndpointType.ANSIBLE;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static com.redhat.cloud.notifications.models.EndpointType.DRAWER;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.EndpointType.PAGERDUTY;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;

@ApplicationScoped
public class EndpointRepository {

    @Inject
    EntityManager entityManager;

    public List<Endpoint> getTargetEmailEndpointsForAggregation(String orgId, Set<UUID> applicationIds) {
        final String query = "SELECT DISTINCT e FROM Endpoint e, EndpointEventType eet " +
            "WHERE e = eet.endpoint AND eet.eventType.application.id IN (:applicationIds) " +
            "AND (e.orgId = :orgId OR e.orgId IS NULL) AND e.enabled IS TRUE AND e.status = :status " +
            "AND e.compositeType.type = :endpointType";

        List<Endpoint> endpoints = entityManager.createQuery(query, Endpoint.class)
            .setParameter("applicationIds", applicationIds)
            .setParameter("orgId", orgId)
            .setParameter("status", READY)
            .setParameter("endpointType", EMAIL_SUBSCRIPTION)
            .getResultList();
        loadProperties(endpoints);
        for (Endpoint endpoint : endpoints) {
            if (endpoint.getOrgId() == null) {
                entityManager.detach(endpoint);
                endpoint.setOrgId(orgId);
            }
        }
        return endpoints;
    }

    @CacheResult(cacheName = "aggregation-target-email-subscription-endpoints")
    public List<Endpoint> getTargetEmailSubscriptionEndpoints(String orgId, UUID eventTypeId) {
        String query = "SELECT DISTINCT e FROM Endpoint e JOIN e.behaviorGroupActions bga JOIN bga.behaviorGroup.behaviors b " +
                "WHERE e.enabled AND b.eventType.id = :eventTypeId AND (bga.behaviorGroup.orgId = :orgId OR bga.behaviorGroup.orgId IS NULL) " +
                "AND e.compositeType.type = :endpointType";

        List<Endpoint> endpoints = entityManager.createQuery(query, Endpoint.class)
                .setParameter("eventTypeId", eventTypeId)
                .setParameter("orgId", orgId)
                .setParameter("endpointType", EMAIL_SUBSCRIPTION)
                .getResultList();
        loadProperties(endpoints);
        return endpoints;
    }

    private void loadProperties(List<Endpoint> endpoints) {
        if (!endpoints.isEmpty()) {
            // Group endpoints in types and load in batches for each type.
            Set<Endpoint> endpointSet = new HashSet<>(endpoints);

            loadTypedProperties(WebhookProperties.class, endpointSet, ANSIBLE);
            loadTypedProperties(WebhookProperties.class, endpointSet, WEBHOOK);
            loadTypedProperties(CamelProperties.class, endpointSet, CAMEL);
            loadTypedProperties(SystemSubscriptionProperties.class, endpointSet, EMAIL_SUBSCRIPTION);
            loadTypedProperties(SystemSubscriptionProperties.class, endpointSet, DRAWER);
            loadTypedProperties(PagerDutyProperties.class, endpointSet, PAGERDUTY);
        }
    }

    private <T extends EndpointProperties> void loadTypedProperties(Class<T> typedEndpointClass, Set<Endpoint> endpoints, EndpointType type) {
        Map<UUID, Endpoint> endpointsMap = endpoints
                .stream()
                .filter(e -> e.getType().equals(type))
                .collect(Collectors.toMap(Endpoint::getId, Function.identity()));

        if (endpointsMap.size() > 0) {
            String hql = "FROM " + typedEndpointClass.getSimpleName() + " WHERE id IN (:endpointIds)";
            List<T> propList = entityManager.createQuery(hql, typedEndpointClass)
                    .setParameter("endpointIds", endpointsMap.keySet())
                    .getResultList();
            for (T props : propList) {
                if (props != null) {
                    Endpoint endpoint = endpointsMap.get(props.getId());
                    endpoint.setProperties(props);
                }
            }
        }
    }
}
