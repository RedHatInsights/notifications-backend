package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;

@ApplicationScoped
public class EndpointEmailSubscriptionResources {

    @Inject
    Mutiny.SessionFactory sessionFactory;

    public Uni<Boolean> subscribe(String accountNumber, String username, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "INSERT INTO endpoint_email_subscriptions(account_id, user_id, application_id, subscription_type) " +
                "SELECT :accountId, :userId, a.id, :subscriptionType " +
                "FROM applications a, bundles b WHERE a.bundle_id = b.id AND a.name = :applicationName AND b.name = :bundleName " +
                "ON CONFLICT (account_id, user_id, application_id, subscription_type) DO NOTHING"; // The value is already on the database, this is OK
        // HQL does not support the ON CONFLICT clause so we need a native query here
        return sessionFactory.withSession(session -> {
            return session.createNativeQuery(query)
                    .setParameter("accountId", accountNumber)
                    .setParameter("userId", username)
                    .setParameter("bundleName", bundleName)
                    .setParameter("applicationName", applicationName)
                    .setParameter("subscriptionType", subscriptionType.name())
                    .executeUpdate()
                    .replaceWith(Boolean.TRUE);
        });
    }

    public Uni<Boolean> unsubscribe(String accountNumber, String username, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "DELETE FROM EmailSubscription WHERE id.accountId = :accountId AND id.userId = :userId " +
                "AND id.applicationId = (SELECT a.id FROM Application a, Bundle b WHERE a.bundle.id = b.id " +
                "AND b.name = :bundleName AND a.name = :applicationName) AND id.subscriptionType = :subscriptionType";
        return sessionFactory.withSession(session -> {
            return session.createQuery(query)
                    .setParameter("accountId", accountNumber)
                    .setParameter("userId", username)
                    .setParameter("bundleName", bundleName)
                    .setParameter("applicationName", applicationName)
                    .setParameter("subscriptionType", subscriptionType)
                    .executeUpdate()
                    .call(session::flush)
                    .replaceWith(Boolean.TRUE);
        });
    }

    public Uni<EmailSubscription> getEmailSubscription(String accountNumber, String username, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "SELECT es FROM EmailSubscription es LEFT JOIN FETCH es.application a LEFT JOIN FETCH a.bundle b " +
                "WHERE es.id.accountId = :accountId AND es.id.userId = :userId " +
                "AND b.name = :bundleName AND a.name = :applicationName AND es.id.subscriptionType = :subscriptionType";
        return sessionFactory.withSession(session -> {
            return session.createQuery(query, EmailSubscription.class)
                    .setParameter("accountId", accountNumber)
                    .setParameter("userId", username)
                    .setParameter("bundleName", bundleName)
                    .setParameter("applicationName", applicationName)
                    .setParameter("subscriptionType", subscriptionType)
                    .getSingleResultOrNull();
        });
    }

    public Uni<List<EmailSubscription>> getEmailSubscriptionsForUser(String accountNumber, String username) {
        String query = "SELECT es FROM EmailSubscription es LEFT JOIN FETCH es.application a LEFT JOIN FETCH a.bundle b " +
                "WHERE es.id.accountId = :accountId AND es.id.userId = :userId";
        return sessionFactory.withSession(session -> {
            return session.createQuery(query, EmailSubscription.class)
                    .setParameter("accountId", accountNumber)
                    .setParameter("userId", username)
                    .getResultList();
        });
    }

    public Uni<List<String>> getEmailSubscribersUserId(String accountNumber, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "SELECT es.id.userId FROM EmailSubscription es WHERE id.accountId = :accountId AND application.bundle.name = :bundleName " +
                "AND application.name = :applicationName AND id.subscriptionType = :subscriptionType";
        return sessionFactory.withStatelessSession(statelessSession -> {
            return statelessSession.createQuery(query, String.class)
                    .setParameter("accountId", accountNumber)
                    .setParameter("bundleName", bundleName)
                    .setParameter("applicationName", applicationName)
                    .setParameter("subscriptionType", subscriptionType)
                    .getResultList();
        });
    }
}
