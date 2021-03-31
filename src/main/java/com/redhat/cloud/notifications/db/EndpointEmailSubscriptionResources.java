package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class EndpointEmailSubscriptionResources {

    @Inject
    Mutiny.Session session;

    public Uni<Boolean> subscribe(String accountNumber, String username, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "INSERT INTO public.endpoint_email_subscriptions(account_id, user_id, bundle, application, subscription_type) " +
                "VALUES(:accountId, :userId, :bundleName, :application, :subscriptionType) " +
                "ON CONFLICT (account_id, user_id, bundle, application, subscription_type) DO NOTHING"; // The value is already on the database, this is OK
        // HQL does not support the ON CONFLICT clause so we need a native query here
        return session.createNativeQuery(query)
                .setParameter("accountId", accountNumber)
                .setParameter("userId", username)
                .setParameter("bundleName", bundleName)
                .setParameter("application", applicationName)
                .setParameter("subscriptionType", subscriptionType.name())
                .executeUpdate()
                .replaceWith(Boolean.TRUE);
    }

    public Uni<Boolean> unsubscribe(String accountNumber, String username, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "DELETE FROM EmailSubscription WHERE id.accountId = :accountId AND id.userId = :userId " +
                "AND id.bundleName = :bundleName AND id.applicationName = :applicationName AND id.subscriptionType = :subscriptionType";
        return session.createQuery(query)
                .setParameter("accountId", accountNumber)
                .setParameter("userId", username)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("subscriptionType", subscriptionType)
                .executeUpdate()
                .call(session::flush)
                .replaceWith(Boolean.TRUE);
    }

    public Uni<EmailSubscription> getEmailSubscription(String accountNumber, String username, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "FROM EmailSubscription WHERE id.accountId = :accountId AND id.userId = :userId " +
                "AND id.bundleName = :bundleName AND id.applicationName = :applicationName AND id.subscriptionType = :subscriptionType";
        return session.createQuery(query, EmailSubscription.class)
                .setParameter("accountId", accountNumber)
                .setParameter("userId", username)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("subscriptionType", subscriptionType)
                .setMaxResults(1)
                .getSingleResultOrNull();
    }

    public Multi<EmailSubscription> getEmailSubscriptionsForUser(String accountNumber, String username) {
        String query = "FROM EmailSubscription WHERE id.accountId = :accountId AND id.userId = :userId";
        return session.createQuery(query, EmailSubscription.class)
                .setParameter("accountId", accountNumber)
                .setParameter("userId", username)
                .getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable);
    }

    public Uni<Long> getEmailSubscribersCount(String accountNumber, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "SELECT COUNT(id.userId) FROM EmailSubscription WHERE id.accountId = :accountId " +
                "AND id.bundleName = :bundleName AND id.applicationName = :applicationName AND id.subscriptionType = :subscriptionType";
        return session.createQuery(query, Long.class)
                .setParameter("accountId", accountNumber)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("subscriptionType", subscriptionType)
                .getSingleResult();
    }

    public Multi<EmailSubscription> getEmailSubscribers(String accountNumber, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "FROM EmailSubscription WHERE id.accountId = :accountId AND id.bundleName = :bundleName " +
                "AND id.applicationName = :applicationName AND id.subscriptionType = :subscriptionType";
        return session.createQuery(query, EmailSubscription.class)
                .setParameter("accountId", accountNumber)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("subscriptionType", subscriptionType)
                .getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable);
    }
}
