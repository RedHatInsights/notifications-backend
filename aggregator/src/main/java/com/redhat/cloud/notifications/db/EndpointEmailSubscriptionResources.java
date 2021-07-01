package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import org.hibernate.Session;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

@ApplicationScoped
public class EndpointEmailSubscriptionResources {

    @Inject
    Session session;

    @Transactional
    public Long getEmailSubscribersCount(String accountNumber, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "SELECT COUNT(id.userId) FROM EmailSubscription WHERE id.accountId = :accountId " +
                "AND application.bundle.name = :bundleName AND application.name = :applicationName AND id.subscriptionType = :subscriptionType";
        return session.createQuery(query, Long.class)
                .setParameter("accountId", accountNumber)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("subscriptionType", subscriptionType)
                .getSingleResult();
    }
}
