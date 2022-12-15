package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;

@ApplicationScoped
public class EmailSubscriptionRepository {

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @Inject
    FeatureFlipper featureFlipper;

    public List<String> getEmailSubscribersUserId(String orgId, String bundleName, String applicationName, String eventType, EmailSubscriptionType subscriptionType) {
        if (featureFlipper.isUseEventTypeForAggregationEnabled()) {
            return getEmailSubscribersUserIdByEventType(orgId, bundleName, applicationName, eventType, subscriptionType);
        }
        String query = "SELECT es.id.userId FROM EmailSubscription es WHERE id.orgId = :orgId AND application.bundle.name = :bundleName " +
                "AND application.name = :applicationName AND id.subscriptionType = :subscriptionType";
        return statelessSessionFactory.getCurrentSession().createQuery(query, String.class)
                .setParameter("orgId", orgId)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("subscriptionType", subscriptionType)
                .getResultList();
    }

    public List<String> getEmailSubscribersUserIdByEventType(String orgId, String bundleName, String applicationName, String eventType, EmailSubscriptionType subscriptionType) {

        String query = "SELECT es.id.userId FROM EventTypeEmailSubscription es WHERE id.orgId = :orgId AND application.bundle.name = :bundleName " +
            "AND application.name = :applicationName AND eventType.name = : eventType AND id.subscriptionType = :subscriptionType";

        return statelessSessionFactory.getCurrentSession().createQuery(query, String.class)
            .setParameter("orgId", orgId)
            .setParameter("bundleName", bundleName)
            .setParameter("applicationName", applicationName)
            .setParameter("eventType", eventType)
            .setParameter("subscriptionType", subscriptionType)
            .getResultList();
    }
}
