package com.redhat.cloud.notifications.helpers;

import com.redhat.cloud.notifications.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.aggregation.EmailAggregation;
import com.redhat.cloud.notifications.models.aggregation.EmailAggregationKey;
import org.hibernate.Session;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import java.time.LocalDateTime;

@ApplicationScoped
public class ResourceHelpers {

    private static final Logger LOGGER = Logger.getLogger(ResourceHelpers.class);

    @Inject
    Session session;

    public void addEmailAggregation(String tenant, String bundle, String application, String policyId, String insightsId) {
        EmailAggregation aggregation = TestHelpers.createEmailAggregation(tenant, bundle, application, policyId, insightsId);
        addEmailAggregation(aggregation);
    }

    @Transactional
    public void subscribe(String accountNumber, String username, String bundleName, String applicationName) {
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
                .setParameter("subscriptionType", EmailSubscriptionType.DAILY.name())
                .executeUpdate();
    }

    @Transactional
    public void unsubscribe(String accountNumber, String userId, String bundleName, String applicationName) {
        String query = "DELETE FROM EmailSubscription WHERE id.accountId = :accountId AND id.userId = :userId " +
                "AND id.applicationId = (SELECT a.id FROM Application a, Bundle b WHERE a.bundle.id = b.id " +
                "AND b.name = :bundleName AND a.name = :applicationName) AND id.subscriptionType = :subscriptionType";
        session.createQuery(query)
                .setParameter("accountId", accountNumber)
                .setParameter("userId", userId)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("subscriptionType", EmailSubscriptionType.DAILY)
                .executeUpdate();
        session.flush();
    }

    @Transactional
    public Boolean addEmailAggregation(EmailAggregation aggregation) {
        try {
            session.persist(aggregation);
            session.flush();
            return Boolean.TRUE;
        } catch (Exception e) {
            LOGGER.warn("Couldn't persist aggregation!", e);
            return Boolean.FALSE;
        }
    }

    @Transactional
    public void purgeEmailSubscriptions() {
        session.createQuery("DELETE FROM EmailSubscription").executeUpdate();
        session.flush();
    }

    @Transactional
    public void purgeAggregations() {
        session.createQuery("DELETE FROM EmailAggregation").executeUpdate();
        session.flush();
    }

    @Transactional
    public Integer purgeOldAggregation(EmailAggregationKey key, LocalDateTime lastUsedTime) {
        String query = "DELETE FROM EmailAggregation WHERE accountId = :accountId AND bundleName = :bundleName AND applicationName = :applicationName AND created <= :created";
        final int result = session.createQuery(query)
                .setParameter("accountId", key.getAccountId())
                .setParameter("bundleName", key.getBundle())
                .setParameter("applicationName", key.getApplication())
                .setParameter("created", lastUsedTime)
                .executeUpdate();
        session.flush();
        return result;
    }
}
