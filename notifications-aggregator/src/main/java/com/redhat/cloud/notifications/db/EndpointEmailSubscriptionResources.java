package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import org.hibernate.Session;

@ApplicationScoped
public class EndpointEmailSubscriptionResources {

    @Inject
    Session session;

    @Transactional
    public Boolean subscribe(String accountNumber, String username, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "INSERT INTO endpoint_email_subscriptions(account_id, user_id, application_id, subscription_type) " +
                "SELECT :accountId, :userId, a.id, :subscriptionType " +
                "FROM applications a, bundles b WHERE a.bundle_id = b.id AND a.name = :applicationName AND b.name = :bundleName " +
                "ON CONFLICT (account_id, user_id, application_id, subscription_type) DO NOTHING"; // The value is already on the database, this is OK
        // HQL does not support the ON CONFLICT clause so we need a native query here
        session.createNativeQuery(query)
                .setParameter("accountId", accountNumber)
                .setParameter("userId", username)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("subscriptionType", subscriptionType.name())
                .executeUpdate();
        return Boolean.TRUE;
    }

    @Transactional
    public Boolean unsubscribe(String accountNumber, String username, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "DELETE FROM EmailSubscription WHERE id.accountId = :accountId AND id.userId = :userId " +
                "AND id.applicationId = (SELECT a.id FROM Application a, Bundle b WHERE a.bundle.id = b.id " +
                "AND b.name = :bundleName AND a.name = :applicationName) AND id.subscriptionType = :subscriptionType";
        session.createQuery(query)
                .setParameter("accountId", accountNumber)
                .setParameter("userId", username)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("subscriptionType", subscriptionType)
                .executeUpdate();
        session.flush();
        return Boolean.TRUE;
    }

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
