package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import org.hibernate.Session;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

@ApplicationScoped
public class ResourceHelpers {

    @Inject
    Session session;

    @Inject
    EmailAggregationResources emailAggregationResources;

    public void createSubscription(String tenant, String username, String bundle, String application, EmailSubscriptionType type) {
        subscribe(tenant, username, bundle, application, type);
    }

    public void removeSubscription(String tenant, String username, String bundle, String application, EmailSubscriptionType type) {
        unsubscribe(tenant, username, bundle, application, type);
    }

    public void addEmailAggregation(String tenant, String bundle, String application, String policyId, String insightsId) {
        EmailAggregation aggregation = TestHelpers.createEmailAggregation(tenant, bundle, application, policyId, insightsId);
        emailAggregationResources.addEmailAggregation(aggregation);
    }

    @Transactional
    void subscribe(String accountNumber, String username, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
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
    }

    @Transactional
    void unsubscribe(String accountNumber, String username, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
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
    }
}
