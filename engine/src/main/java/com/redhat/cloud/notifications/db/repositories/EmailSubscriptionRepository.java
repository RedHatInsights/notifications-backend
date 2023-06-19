package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class EmailSubscriptionRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    FeatureFlipper featureFlipper;

    public List<String> getEmailSubscribersUserId(String orgId, String bundleName, String applicationName, String eventTypeName, EmailSubscriptionType subscriptionType) {
        if (featureFlipper.isUseEventTypeForSubscriptionEnabled()) {
            return getEmailSubscribersUserIdByEventType(orgId, bundleName, applicationName, eventTypeName, subscriptionType);
        }
        String query = "SELECT es.id.userId FROM EmailSubscription es WHERE id.orgId = :orgId AND application.bundle.name = :bundleName " +
                "AND application.name = :applicationName AND id.subscriptionType = :subscriptionType";
        return entityManager.createQuery(query, String.class)
                .setParameter("orgId", orgId)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("subscriptionType", subscriptionType)
                .getResultList();
    }

    private List<String> getEmailSubscribersUserIdByEventType(String orgId, String bundleName, String applicationName, String eventTypeName, EmailSubscriptionType subscriptionType) {

        String query = "SELECT es.id.userId FROM EventTypeEmailSubscription es WHERE id.orgId = :orgId AND eventType.application.bundle.name = :bundleName " +
            "AND eventType.application.name = :applicationName AND eventType.name = : eventTypeName AND id.subscriptionType = :subscriptionType";

        return entityManager.createQuery(query, String.class)
            .setParameter("orgId", orgId)
            .setParameter("bundleName", bundleName)
            .setParameter("applicationName", applicationName)
            .setParameter("eventTypeName", eventTypeName)
            .setParameter("subscriptionType", subscriptionType)
            .getResultList();
    }

    public Map<String, Set<String>> getEmailSubscribersUserIdGroupedByEventType(String orgId, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "SELECT eventType.name, es.id.userId FROM EventTypeEmailSubscription es WHERE id.orgId = :orgId AND eventType.application.bundle.name = :bundleName " +
            "AND eventType.application.name = :applicationName AND id.subscriptionType = :subscriptionType";

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
